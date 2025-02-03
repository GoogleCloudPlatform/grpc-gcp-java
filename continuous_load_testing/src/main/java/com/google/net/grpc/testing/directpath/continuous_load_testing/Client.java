package com.google.net.grpc.testing.directpath.continuous_load_testing;


import com.google.common.collect.ImmutableList;
import io.grpc.ChannelCredentials;
import io.grpc.Grpc;
import io.grpc.ManagedChannelBuilder;
import io.grpc.alts.GoogleDefaultChannelCredentials;
import io.grpc.opentelemetry.GrpcOpenTelemetry;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.integration.EmptyProtos.Empty;
import io.grpc.testing.integration.Messages.SimpleRequest;
import io.grpc.testing.integration.Messages.SimpleResponse;
import io.grpc.testing.integration.Messages.StreamingInputCallRequest;
import io.grpc.testing.integration.Messages.StreamingInputCallResponse;
import io.grpc.testing.integration.Messages.StreamingOutputCallRequest;
import io.grpc.testing.integration.Messages.StreamingOutputCallResponse;
import io.grpc.testing.integration.TestServiceGrpc;
import io.grpc.testing.integration.TestServiceGrpc.TestServiceStub;
import io.opentelemetry.contrib.gcp.resource.GCPResourceProvider;
import io.opentelemetry.sdk.autoconfigure.ResourceConfiguration;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.View;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import com.google.cloud.opentelemetry.metric.GoogleCloudMetricExporter;
import com.google.cloud.opentelemetry.metric.MetricConfiguration;

public class Client {

  private enum Method {
    EmptyCall, UnaryCall, StreamingInputCall, StreamingOutputCall, FullDuplexCall, HalfDuplexCall
  }

  private static final Logger logger = Logger.getLogger(Client.class.getName());
  private static final String BACKEND = "google-c2p:///directpathgrpctesting-pa.googleapis.com";
  private static final Set<Method> methods = new HashSet<>(Arrays.asList(Method.values()));
  private static int concurrency = 1;
  private static int num_of_requests = 10;

  private static final Collection<String> METRICS =
      ImmutableList.of(
          "grpc.lb.wrr.rr_fallback",
          "grpc.lb.wrr.endpoint_weight_not_yet_usable",
          "grpc.lb.wrr.endpoint_weight_stale",
          "grpc.lb.wrr.endpoint_weights",
          "grpc.lb.rls.cache_entries",
          "grpc.lb.rls.cache_size",
          "grpc.lb.rls.default_target_picks",
          "grpc.lb.rls.target_picks",
          "grpc.lb.rls.failed_picks",
          "grpc.xds_client.connected",
          "grpc.xds_client.server_failure",
          "grpc.xds_client.resource_updates_valid",
          "grpc.xds_client.resource_updates_invalid",
          "grpc.xds_client.resources",
          "grpc.client.attempt.sent_total_compressed_message_size",
          "grpc.client.attempt.rcvd_total_compressed_message_size",
          "grpc.client.attempt.started",
          "grpc.client.attempt.duration",
          "grpc.client.call.duration");

  public static void main(String[] args) {
    logger.info("DirectPath Continuous Load Testing Client Started.");
    initializeLogManager();
    parseArgs(args);
    GrpcOpenTelemetry grpcOpenTelemetry = initializeOpenTelemetry();

    ChannelCredentials credentials = GoogleDefaultChannelCredentials.create();
    ManagedChannelBuilder<?> builder = Grpc.newChannelBuilder(BACKEND, credentials);
    grpcOpenTelemetry.configureChannelBuilder(builder);
    TestServiceStub stub = TestServiceGrpc.newStub(builder.build());

    if (methods.contains(Method.EmptyCall)) {
      ExecuteEmptyCalls(stub);
    }
    if (methods.contains(Method.UnaryCall)) {
      ExecuteUnaryCalls(stub);
    }
    if (methods.contains(Method.StreamingInputCall)) {
      ExecuteStreamingInputCalls(stub);
    }
    if (methods.contains(Method.StreamingOutputCall)) {
      ExecuteStreamingOutputCalls(stub);
    }
    if (methods.contains(Method.FullDuplexCall)) {
      ExecuteFullDuplexCalls(stub);
    }
    if (methods.contains(Method.HalfDuplexCall)) {
      ExecuteHalfDuplexCalls(stub);
    }

    //noinspection InfiniteLoopStatement
    while (true) {
      try {
        //noinspection BusyWait
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static void parseArgs(String[] args) {
    for (String arg : args) {
      if (arg.startsWith("--methods=")) {
        methods.clear();
        String[] methodsInArgs = arg.substring("--methods=".length()).split(",");
        for (String methodInArgs : methodsInArgs) {
          methods.add(Method.valueOf(methodInArgs));
        }
      }
      if (arg.startsWith("--concurrency=")) {
        concurrency = Integer.parseInt(arg.substring("--concurrency".length()));
      }
      if (arg.startsWith("--num_of_requests=")) {
        num_of_requests = Integer.parseInt(arg.substring("--num_of_requests".length()));
      }
    }
  }

  private static GrpcOpenTelemetry initializeOpenTelemetry() {
    MetricExporter cloudMonitoringExporter =
        GoogleCloudMetricExporter.createWithConfiguration(
            MetricConfiguration.builder()
                .setUseServiceTimeSeries(true)
                .setInstrumentationLibraryLabelsEnabled(false)
                .setPrefix("directpathgrpctesting-pa.googleapis.com/client")
                .build()
        );
    Resource resource = ResourceConfiguration.createEnvironmentResource();
    GCPResourceProvider resourceProvider = new GCPResourceProvider();
    SdkMeterProviderBuilder providerBuilder = SdkMeterProvider.builder()
        .setResource(resource.merge(Resource.create(resourceProvider.getAttributes())))
        .registerMetricReader(
            PeriodicMetricReader.builder(cloudMonitoringExporter)
                .setInterval(java.time.Duration.ofSeconds(90))
                .build());
    // Replaces the dots with slashes in each metric, which is needed by stackdriver.
    for (String metric : METRICS) {
      providerBuilder.registerView(
          InstrumentSelector.builder().setName(metric).build(),
          View.builder().setName(metric.replace(".", "/")).build());
    }
    OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
        .setMeterProvider(providerBuilder.build()).build();
    return GrpcOpenTelemetry.newBuilder().sdk(openTelemetrySdk)
        .addOptionalLabel("grpc.lb.locality").enableMetrics(METRICS).build();
  }

  private static void ExecuteEmptyCalls(TestServiceStub stub) {
    final StreamObserver<Empty> observer = new StreamObserver<>() {
      @Override
      public void onNext(Empty value) {
      }

      @Override
      public void onError(Throwable t) {
        stub.emptyCall(Empty.getDefaultInstance(), this);
      }

      @Override
      public void onCompleted() {
        stub.emptyCall(Empty.getDefaultInstance(), this);
      }
    };
    Thread t = new Thread(() -> stub.emptyCall(Empty.getDefaultInstance(), observer));
    t.start();
  }

  private static void ExecuteUnaryCalls(TestServiceStub stub) {
    SimpleRequest request = SimpleRequest.newBuilder().build();
    final StreamObserver<SimpleResponse> observer = new StreamObserver<>() {
      @Override
      public void onNext(SimpleResponse value) {

      }

      @Override
      public void onError(Throwable t) {
        stub.unaryCall(request, this);
      }

      @Override
      public void onCompleted() {
        stub.unaryCall(request, this);
      }
    };
    for (int i = 0; i < concurrency; i++) {
      Thread t = new Thread(() -> stub.unaryCall(request, observer));
      t.start();
    }
  }

  private static void ExecuteStreamingInputCalls(TestServiceStub stub) {
    StreamingInputCallRequest request = StreamingInputCallRequest.newBuilder().build();
    final StreamObserver<StreamingInputCallResponse> responseObserver = new StreamObserver<>() {
      @Override
      public void onNext(StreamingInputCallResponse value) {
      }

      @Override
      public void onError(Throwable t) {
        StreamObserver<StreamingInputCallRequest> requestObserver = stub.streamingInputCall(
            this);
        for (int i = 0; i < num_of_requests; i++) {
          requestObserver.onNext(request);
        }
      }

      @Override
      public void onCompleted() {
        StreamObserver<StreamingInputCallRequest> requestObserver = stub.streamingInputCall(
            this);
        for (int i = 0; i < num_of_requests; i++) {
          requestObserver.onNext(request);
        }
      }
    };
    for (int i = 0; i < concurrency; i++) {
      Thread t = new Thread(() -> {
        StreamObserver<StreamingInputCallRequest> requestObserver = stub.streamingInputCall(
            responseObserver);
        for (int i1 = 0; i1 < num_of_requests; i1++) {
          requestObserver.onNext(request);
        }
      });
      t.start();
    }
  }

  private static void ExecuteStreamingOutputCalls(TestServiceStub stub) {
    StreamingOutputCallRequest request = StreamingOutputCallRequest.newBuilder().build();
    final StreamObserver<StreamingOutputCallResponse> responseObserver = new StreamObserver<>() {
      @Override
      public void onNext(StreamingOutputCallResponse value) {
      }

      @Override
      public void onError(Throwable t) {
        stub.streamingOutputCall(request, this);
      }

      @Override
      public void onCompleted() {
        stub.streamingOutputCall(request, this);
      }
    };
    for (int i = 0; i < concurrency; i++) {
      Thread t = new Thread(() -> stub.streamingOutputCall(request, responseObserver));
      t.start();
    }
  }

  private static void ExecuteFullDuplexCalls(TestServiceStub stub) {
    StreamingOutputCallRequest request = StreamingOutputCallRequest.newBuilder().build();
    final StreamObserver<StreamingOutputCallResponse> responseObserver = new StreamObserver<>() {
      @Override
      public void onNext(StreamingOutputCallResponse value) {
      }

      @Override
      public void onError(Throwable t) {
        StreamObserver<StreamingOutputCallRequest> requestObserver = stub.fullDuplexCall(
            this);
        for (int i = 0; i < num_of_requests; i++) {
          requestObserver.onNext(request);
        }
      }

      @Override
      public void onCompleted() {
        StreamObserver<StreamingOutputCallRequest> requestObserver = stub.fullDuplexCall(
            this);
        for (int i = 0; i < num_of_requests; i++) {
          requestObserver.onNext(request);
        }
      }
    };
    for (int i = 0; i < concurrency; i++) {
      Thread t = new Thread(() -> {
        StreamObserver<StreamingOutputCallRequest> requestObserver = stub.fullDuplexCall(
            responseObserver);
        for (int i1 = 0; i1 < num_of_requests; i1++) {
          requestObserver.onNext(request);
        }
      });
      t.start();
    }
  }

  private static void ExecuteHalfDuplexCalls(TestServiceStub stub) {
    StreamingOutputCallRequest request = StreamingOutputCallRequest.newBuilder().build();
    final StreamObserver<StreamingOutputCallResponse> responseObserver = new StreamObserver<>() {
      @Override
      public void onNext(StreamingOutputCallResponse value) {
      }

      @Override
      public void onError(Throwable t) {
        StreamObserver<StreamingOutputCallRequest> requestObserver = stub.halfDuplexCall(
            this);
        for (int i = 0; i < num_of_requests; i++) {
          requestObserver.onNext(request);
        }
      }

      @Override
      public void onCompleted() {
        StreamObserver<StreamingOutputCallRequest> requestObserver = stub.halfDuplexCall(
            this);
        for (int i = 0; i < num_of_requests; i++) {
          requestObserver.onNext(request);
        }
        requestObserver.onCompleted();
      }
    };
    for (int i = 0; i < concurrency; i++) {
      Thread t = new Thread(() -> {
        StreamObserver<StreamingOutputCallRequest> requestObserver = stub.halfDuplexCall(
            responseObserver);
        for (int i1 = 0; i1 < num_of_requests; i1++) {
          requestObserver.onNext(request);
        }
        requestObserver.onCompleted();
      });
      t.start();
    }
  }

  private static void initializeLogManager() {
    try {
      LogManager.getLogManager()
          .readConfiguration(Client.class.getResourceAsStream("/logging.properties"));
    } catch (IOException e) {
      logger.info(e.toString());
    }
  }
}
