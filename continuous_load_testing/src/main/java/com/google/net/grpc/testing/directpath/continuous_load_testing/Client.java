package com.google.net.grpc.testing.directpath.continuous_load_testing;


import io.grpc.ManagedChannelBuilder;
import io.grpc.testing.integration.EmptyProtos.Empty;
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
    try {
      LogManager.getLogManager()
          .readConfiguration(Client.class.getResourceAsStream("/logging.properties"));
    } catch (IOException e) {
      logger.info(e.toString());
    }

    ManagedChannelBuilder builder = ManagedChannelBuilder.forTarget(BACKEND);
    TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(builder.build());
    stub.emptyCall(Empty.getDefaultInstance());
  }
}
