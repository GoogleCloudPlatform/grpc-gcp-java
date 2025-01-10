package com.google.cloud.grpc.metrics;

import com.google.cloud.grpc.metrics.GcpMetrics.Attr;
import com.google.cloud.grpc.metrics.GcpMetrics.ConfigurableMetric;
import com.google.cloud.grpc.metrics.GcpMetrics.Metric;
import com.google.common.base.Preconditions;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientStreamTracer;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import java.util.HashMap;
import java.util.Map;

public class GcpMetricsInterceptor implements ClientInterceptor {
  public static final CallOptions.Key<CallTimekeeper> GCP_TIMEKEEPER_KEY =
      CallOptions.Key.create("GCPCallTimekeeper");

  private static final ClientStreamTracer.Factory metricStreamTracerFactory =
      new GcpMetricsStreamTracer.Factory();

  private final GcpMetricsRecorder recorder;

  public static class CallTimekeeper {
    private final Map<String, String> attributes = new HashMap<>();
    private long start = 0;
    private long streamTracerCreated = 0;
    private long nameDelayed = 0;
    private long pendingStreamCreated = 0;
    private long streamCreated = 0;
    private long headersSent = 0;
    private long headersReceived = 0;
    private long streamClosed = 0;
    private long finish = 0;

    private GcpMetricsRecorder recorder;

    public CallTimekeeper() {
      start = System.nanoTime();
    }

    public CallTimekeeper(CallOptions callOptions, GcpMetricsRecorder recorder) {
      start = System.nanoTime();
      this.recorder = recorder;
    }

    public void onStreamTracer() {
      streamTracerCreated = System.nanoTime();
    }

    public void nameResolutionDelayedBy(Long nanos) {
      this.nameDelayed = nanos;
    }

    public void pendingStreamCreated() {
      pendingStreamCreated = System.nanoTime();
    }

    public void streamCreated() {
      streamCreated = System.nanoTime();
    }

    public void outboundHeaders() {
      headersSent = System.nanoTime();
    }

    public void inboundHeaders() {
      headersReceived = System.nanoTime();
    }

    public void streamClosed() {
      streamClosed = System.nanoTime();
    }

    public void onClose() {
      finish = System.nanoTime();
      calcAndReport();
    }

    private void calcAndReport() {
      long totalTime = finish - start;

      Map<ConfigurableMetric, Number> recs = new HashMap<>();

      if (streamTracerCreated > 0) {
        recs.put(Metric.START_DELAY, streamTracerCreated - start);
      }

      if (pendingStreamCreated > 0) {
        recs.put(Metric.RESOLUTION_DELAY, pendingStreamCreated - streamTracerCreated);
      }

      if (streamCreated > 0) {
        if (pendingStreamCreated > 0) {
          recs.put(Metric.CONNECTION_DELAY, streamCreated - pendingStreamCreated);
        } else {
          recs.put(Metric.CONNECTION_DELAY, 0);
        }
      }
      if (headersSent > 0) {
        // May include marshalling.
        if (pendingStreamCreated > 0) {
          // When connection was establishing.
          recs.put(Metric.SEND_DELAY, headersSent - streamCreated);
        } else {
          // When connection was ready.
          recs.put(Metric.SEND_DELAY, headersSent - streamTracerCreated);
        }
      }
      if (headersReceived > 0) {
        recs.put(Metric.HEADERS_LATENCY, headersReceived - headersSent);
      }
      if (streamClosed > 0) {
        if (headersSent > 0) {
          recs.put(Metric.RESPONSE_LATENCY, streamClosed - headersSent);
        }
        // Includes unmarshalling and interceptors logic. I.e. does not include application
        // processing after onClose in GcpMetricsInterceptor is called.
        recs.put(Metric.PROCESSING_DELAY, finish - streamClosed);
      }

      recs.put(Metric.TOTAL_LATENCY, totalTime);

      recorder.recordAll(recs, attributes);
    }

    public void setAttribute(Attr attr, String value) {
      setAttribute(attr.toString(), value);
    }

    public void setAttribute(String name, String value) {
      if (value == null) {
        attributes.remove(name);
        return;
      }
      attributes.put(name, value);
    }
  }

  public GcpMetricsInterceptor(GcpMetricsRecorder recorder) {
    Preconditions.checkNotNull(recorder);
    this.recorder = recorder;
  }

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions, Channel channel) {

    CallTimekeeper tk = new CallTimekeeper(callOptions, recorder);
    tk.setAttribute(GcpMetrics.Attr.METHOD, methodDescriptor.getFullMethodName());
    tk.setAttribute(GcpMetrics.Attr.METHOD_TYPE, methodDescriptor.getType().toString());

    ClientCall<ReqT, RespT> call =
        channel.newCall(
            methodDescriptor,
            callOptions
                .withStreamTracerFactory(metricStreamTracerFactory)
                .withOption(GCP_TIMEKEEPER_KEY, tk));

    return new SimpleForwardingClientCall<ReqT, RespT>(call) {
      public void start(Listener<RespT> responseListener, Metadata headers) {
        Listener<RespT> newListener =
            new SimpleForwardingClientCallListener<RespT>(responseListener) {
              @Override
              public void onClose(Status status, Metadata trailers) {
                super.onClose(status, trailers);
                tk.setAttribute(GcpMetrics.Attr.STATUS, status.getCode().toString());
                tk.onClose();
              }
            };
        delegate().start(newListener, headers);
      }
    };
  }
}
