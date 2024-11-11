package com.google.net.grpc.testing.directpath.continuous_load_testing;


import io.grpc.ManagedChannelBuilder;
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
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class Client {

  private enum Method {
    EmptyCall, UnaryCall, StreamingInputCall, StreamingOutputCall, FullDuplexCall, HalfDuplexCall
  }

  private static Logger logger = Logger.getLogger(Client.class.getName());
  private static String BACKEND = "google-c2p:///directpathgrpctesting-pa.googleapis.com";
  private static Set<Method> methods = new HashSet<>(Arrays.asList(Method.values()));
  private static int concurrency = 1;
  private static int num_of_requests = 10;

  public static void main(String[] args) {
    initializeLogManager();
    parseArgs(args);

    ManagedChannelBuilder builder = ManagedChannelBuilder.forTarget(BACKEND);
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
    while (true) {
      try {
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

  private static void ExecuteEmptyCalls(TestServiceStub stub) {
    final StreamObserver<Empty> observer = new StreamObserver<Empty>() {
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
    Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
        stub.emptyCall(Empty.getDefaultInstance(), observer);
      }
    });
    t.start();
  }

  private static void ExecuteUnaryCalls(TestServiceStub stub) {
    SimpleRequest request = SimpleRequest.newBuilder().build();
    final StreamObserver<SimpleResponse> observer = new StreamObserver<SimpleResponse>() {
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
      Thread t = new Thread(new Runnable() {
        @Override
        public void run() {
          stub.unaryCall(request, observer);
        }
      });
      t.start();
    }
  }

  private static void ExecuteStreamingInputCalls(TestServiceStub stub) {
    StreamingInputCallRequest request = StreamingInputCallRequest.newBuilder().build();
    final StreamObserver<StreamingInputCallResponse> responseObserver = new StreamObserver<StreamingInputCallResponse>() {
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
      Thread t = new Thread(new Runnable() {
        @Override
        public void run() {
          StreamObserver<StreamingInputCallRequest> requestObserver = stub.streamingInputCall(
              responseObserver);
          for (int i = 0; i < num_of_requests; i++) {
            requestObserver.onNext(request);
          }
        }
      });
      t.start();
    }
  }

  private static void ExecuteStreamingOutputCalls(TestServiceStub stub) {
    StreamingOutputCallRequest request = StreamingOutputCallRequest.newBuilder().build();
    final StreamObserver<StreamingOutputCallResponse> responseObserver = new StreamObserver<StreamingOutputCallResponse>() {
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
      Thread t = new Thread(new Runnable() {
        @Override
        public void run() {
          stub.streamingOutputCall(request, responseObserver);
        }
      });
      t.start();
    }
  }

  private static void ExecuteFullDuplexCalls(TestServiceStub stub) {
    StreamingOutputCallRequest request = StreamingOutputCallRequest.newBuilder().build();
    final StreamObserver<StreamingOutputCallResponse> responseObserver = new StreamObserver<StreamingOutputCallResponse>() {
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
      Thread t = new Thread(new Runnable() {
        @Override
        public void run() {
          StreamObserver<StreamingOutputCallRequest> requestObserver = stub.fullDuplexCall(
              responseObserver);
          for (int i = 0; i < num_of_requests; i++) {
            requestObserver.onNext(request);
          }
        }
      });
      t.start();
    }
  }

  private static void ExecuteHalfDuplexCalls(TestServiceStub stub) {
    StreamingOutputCallRequest request = StreamingOutputCallRequest.newBuilder().build();
    final StreamObserver<StreamingOutputCallResponse> responseObserver = new StreamObserver<StreamingOutputCallResponse>() {
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
      Thread t = new Thread(new Runnable() {
        @Override
        public void run() {
          StreamObserver<StreamingOutputCallRequest> requestObserver = stub.halfDuplexCall(
              responseObserver);
          for (int i = 0; i < num_of_requests; i++) {
            requestObserver.onNext(request);
          }
          requestObserver.onCompleted();
        }
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
