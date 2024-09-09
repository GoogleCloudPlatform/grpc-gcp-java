package com.google.net.grpc.testing.directpath.continuous_load_testing;


import io.grpc.ManagedChannelBuilder;
import io.grpc.testing.integration.EmptyProtos.Empty;
import io.grpc.testing.integration.TestServiceGrpc;
import io.grpc.testing.integration.TestServiceGrpc.TestServiceBlockingStub;
import io.grpc.testing.integration.TestServiceGrpc.TestServiceStub;

public class Client {
  private static String BACKEND = "google-c2p:///directpathgrpctesting-pa.googleapis.com";

  public static void main(String[] args) {
    ManagedChannelBuilder builder = ManagedChannelBuilder.forTarget(BACKEND);
    TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(builder.build());
    stub.emptyCall(Empty.getDefaultInstance());
  }
}
