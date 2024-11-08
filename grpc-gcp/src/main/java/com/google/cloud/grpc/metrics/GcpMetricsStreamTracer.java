package com.google.cloud.grpc.metrics;

import com.google.cloud.grpc.metrics.GcpMetricsInterceptor.CallTimekeeper;
import io.grpc.Attributes;
import io.grpc.ClientStreamTracer;
import io.grpc.Metadata;
import io.grpc.Status;
import java.util.logging.Logger;

public class GcpMetricsStreamTracer extends ClientStreamTracer {
  private static final Logger logger = Logger.getLogger(GcpMetricsStreamTracer.class.getName());
  private final long callStartedAt;
  private volatile long nameResolvedIn = 0;
  private volatile long connectionReadyIn = 0;
  private volatile long sentIn = 0;
  private long headersLatency = 0;

  private CallTimekeeper tk;

  public GcpMetricsStreamTracer(StreamInfo streamInfo) {
    tk = streamInfo.getCallOptions().getOption(GcpMetricsInterceptor.GCP_TIMEKEEPER_KEY);
    tk.onStreamTracer();
    tk.setAttribute(
        GcpMetrics.Attr.TRANSPARENT_RETRY, String.valueOf(streamInfo.isTransparentRetry()));

    callStartedAt = System.nanoTime();
    Long nameDelayed =
        streamInfo.getCallOptions().getOption(ClientStreamTracer.NAME_RESOLUTION_DELAYED);

    if (nameDelayed != null) {
      tk.nameResolutionDelayedBy(nameDelayed);
    }
  }

  public void createPendingStream() {
    // Name resolution is completed and the connection starts getting established. This method is
    // only invoked on the streams that encounter such delay.
    if (nameResolvedIn == 0) {
      tk.pendingStreamCreated();
      nameResolvedIn = System.nanoTime() - callStartedAt;
    } else {
      logger.fine(
          "createPendingStream called more than once. Already measures as " + nameResolvedIn);
    }
  }

  public void streamCreated(Attributes transportAttrs, Metadata headers) {
    // The stream is being created on a ready transport.
    if (connectionReadyIn == 0) {
      tk.streamCreated();
      connectionReadyIn = System.nanoTime() - callStartedAt - nameResolvedIn;
    } else {
      logger.fine(
          "OOOPS, streamCreated called more than once. Already measures as " + connectionReadyIn);
    }
  }

  public void outboundHeaders() {
    // Headers has been sent to the socket.
    if (sentIn == 0) {
      tk.outboundHeaders();
      sentIn = System.nanoTime() - callStartedAt - nameResolvedIn - connectionReadyIn;
    } else {
      logger.fine("outboundHeaders called more than once. Already measures as " + sentIn);
    }
  }

  public void inboundHeaders() {
    // Headers has been received from the server.
    if (headersLatency == 0) {
      tk.inboundHeaders();
      headersLatency =
          System.nanoTime() - callStartedAt - nameResolvedIn - connectionReadyIn - sentIn;
    } else {
      logger.fine("inboundHeaders called more than once. Already measures as " + headersLatency);
    }
  }

  public void streamClosed(Status status) {
    // Stream is closed. This will be called exactly once.
    tk.streamClosed();
  }

  public static class Factory extends ClientStreamTracer.Factory {
    public ClientStreamTracer newClientStreamTracer(StreamInfo info, Metadata headers) {
      return new GcpMetricsStreamTracer(info);
    }
  }
}
