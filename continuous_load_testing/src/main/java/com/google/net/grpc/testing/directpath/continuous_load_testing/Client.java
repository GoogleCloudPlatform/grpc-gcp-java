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
import io.grpc.testing.integration.TestServiceGrpc.TestServiceBlockingStub;
import io.grpc.testing.integration.TestServiceGrpc.TestServiceStub;
import java.io.IOException;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class Client {

  private static Logger logger = Logger.getLogger(Client.class.getName());
  private static String BACKEND = "google-c2p:///directpathgrpctesting-pa.googleapis.com";

  public static void main(String[] args) {
    initializeLogManager();

    ManagedChannelBuilder builder = ManagedChannelBuilder.forTarget(BACKEND);
    TestServiceStub stub = TestServiceGrpc.newStub(builder.build());
    // TODO(fengli): Make it configurable.
    ExecuteEmptyCalls(stub);
    ExecuteUnaryCalls(stub);
    ExecuteStreamingInputCalls(stub);
    ExecuteStreamingOutputCalls(stub);
    ExecuteFullDuplexCalls(stub);
    ExecuteHalfDuplexCalls(stub);
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
    Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
        stub.unaryCall(request, observer);
      }
    });
    t.start();
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
        // TODO(fengli): Make it configurable
        for (int i = 0; i < 10; i++) {
          requestObserver.onNext(request);
        }
      }

      @Override
      public void onCompleted() {
        StreamObserver<StreamingInputCallRequest> requestObserver = stub.streamingInputCall(
            this);
        // TODO(fengli): Make it configurable
        for (int i = 0; i < 10; i++) {
          requestObserver.onNext(request);
        }
      }
    };
    Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
        StreamObserver<StreamingInputCallRequest> requestObserver = stub.streamingInputCall(
            responseObserver);
        // TODO(fengli): Make it configurable
        for (int i = 0; i < 10; i++) {
          requestObserver.onNext(request);
        }
      }
    });
    t.start();
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
    Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
        stub.streamingOutputCall(request, responseObserver);
      }
    });
    t.start();
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
        // TODO(fengli): Make it configurable
        for (int i = 0; i < 10; i++) {
          requestObserver.onNext(request);
        }
      }

      @Override
      public void onCompleted() {
        StreamObserver<StreamingOutputCallRequest> requestObserver = stub.fullDuplexCall(
            this);
        // TODO(fengli): Make it configurable
        for (int i = 0; i < 10; i++) {
          requestObserver.onNext(request);
        }
      }
    };
    Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
        StreamObserver<StreamingOutputCallRequest> requestObserver = stub.fullDuplexCall(
            responseObserver);
        // TODO(fengli): Make it configurable
        for (int i = 0; i < 10; i++) {
          requestObserver.onNext(request);
        }
      }
    });
    t.start();
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
        // TODO(fengli): Make it configurable
        for (int i = 0; i < 10; i++) {
          requestObserver.onNext(request);
        }
      }

      @Override
      public void onCompleted() {
        StreamObserver<StreamingOutputCallRequest> requestObserver = stub.halfDuplexCall(
            this);
        // TODO(fengli): Make it configurable
        for (int i = 0; i < 10; i++) {
          requestObserver.onNext(request);
        }
        requestObserver.onCompleted();
      }
    };
    Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
        StreamObserver<StreamingOutputCallRequest> requestObserver = stub.halfDuplexCall(
            responseObserver);
        // TODO(fengli): Make it configurable
        for (int i = 0; i < 10; i++) {
          requestObserver.onNext(request);
        }
        requestObserver.onCompleted();
      }
    });
    t.start();
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
