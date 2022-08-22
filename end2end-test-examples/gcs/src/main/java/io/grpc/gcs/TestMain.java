package io.grpc.gcs;

import static io.grpc.gcs.Args.CLIENT_GRPC;
import static io.grpc.gcs.Args.CLIENT_GCSIO_GRPC;
import static io.grpc.gcs.Args.CLIENT_GCSIO_JSON;
import static io.grpc.gcs.Args.CLIENT_JAVA_GRPC;
import static io.grpc.gcs.Args.CLIENT_JAVA_JSON;

import java.io.FileInputStream;
import java.security.Security;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import org.conscrypt.Conscrypt;

public class TestMain {
  private static final Logger logger = Logger.getLogger(TestMain.class.getName());

  public static void main(String[] args) throws Exception {
    Args a = new Args(args);
    if (a.verboseLog) {
      LogManager.getLogManager().readConfiguration(new FileInputStream("logging.properties"));
    }
    if (a.conscrypt) {
      Security.insertProviderAt(Conscrypt.newProvider(), 1);
    } else if (a.conscrypt_notm) {
      Security.insertProviderAt(Conscrypt.newProviderBuilder().provideTrustManager(false).build(),
          1);
    }
    ResultTable results = new ResultTable(a);
    switch (a.client) {
      case CLIENT_GRPC:
        GrpcClient grpcClient = new GrpcClient(a);
        results.start();
        grpcClient.startCalls(results);
        results.stop();
        break;
      case CLIENT_GCSIO_GRPC:
        GcsioClient gcsioGrpcClient = new GcsioClient(a, true);
        results.start();
        gcsioGrpcClient.startCalls(results);
        results.stop();
        break;
      case CLIENT_GCSIO_JSON:
        GcsioClient gcsioJsonClient = new GcsioClient(a, false);
        results.start();
        gcsioJsonClient.startCalls(results);
        results.stop();
        break;
      case CLIENT_JAVA_GRPC:
        logger.warning("Not supported yet");
        break;
      case CLIENT_JAVA_JSON:
        JavaClient javaClient = new JavaClient(a);
        results.start();
        javaClient.startCalls(results);
        results.stop();
        break;
      default:
        logger.warning("Please provide --client");
    }
    results.printResult();
  }
}
