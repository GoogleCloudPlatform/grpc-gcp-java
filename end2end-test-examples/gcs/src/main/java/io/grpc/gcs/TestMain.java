package io.grpc.gcs;

import static io.grpc.gcs.Args.CLIENT_GCSIO_GRPC;
import static io.grpc.gcs.Args.CLIENT_GCSIO_HTTP;
import static io.grpc.gcs.Args.CLIENT_GRPC;
import static io.grpc.gcs.Args.CLIENT_YOSHI;

import com.google.gson.Gson;
import io.grpc.netty.shaded.io.grpc.netty.InternalHandlerSettings;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    }
    if (a.autoWindow) {
      InternalHandlerSettings.enable(true);
      InternalHandlerSettings.autoWindowOn(true);
    }
    ResultTable results = new ResultTable(a);
    long start = 0;
    long totalDur = 0;
    switch (a.client) {
      case CLIENT_YOSHI:
        HttpClient httpClient = new HttpClient(a);
        results.start();
        httpClient.startCalls(results);
        results.stop();
        break;
      case CLIENT_GCSIO_HTTP:
        GcsioClient gcsioHttpClient = new GcsioClient(a, false);
        results.start();
        gcsioHttpClient.startCalls(results);
        results.stop();
        break;
      case CLIENT_GCSIO_GRPC:
        GcsioClient gcsioGrpcClient = new GcsioClient(a, true);
        results.start();
        gcsioGrpcClient.startCalls(results);
        results.stop();
        break;
      case CLIENT_GRPC:
        GrpcClient grpcClient = new GrpcClient(a);
        results.start();
        grpcClient.startCalls(results);
        results.stop();
        break;
      default:
        logger.warning("Please provide --client");
    }
    results.printResult();
  }
}
