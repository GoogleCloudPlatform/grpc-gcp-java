package io.grpc.gcs;

import static io.grpc.gcs.Args.CLIENT_GCSIO_GRPC;
import static io.grpc.gcs.Args.CLIENT_GCSIO_HTTP;
import static io.grpc.gcs.Args.CLIENT_GRPC;
import static io.grpc.gcs.Args.CLIENT_YOSHI;

import com.google.gson.Gson;
import java.io.FileWriter;
import java.io.IOException;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.logging.Logger;
import org.conscrypt.Conscrypt;

public class TestMain {
  private static final Logger logger = Logger.getLogger(TestMain.class.getName());

  private static class BenchmarkResult {
    public Long min, p50, p90, p99, p999;
    public double qps;
  }

  private static void printResult(FileWriter writer, ArrayList<Long> results)
      throws IOException {
    if (results.size() == 0) return;
    Collections.sort(results);
    int n = results.size();
    double totalSeconds = 0;
    for (Long ms : results) {
      totalSeconds += ms / 1000.0;
    }

    Gson gson = new Gson();
    BenchmarkResult benchmarkResult = new BenchmarkResult();
    benchmarkResult.min = results.get(0);
    benchmarkResult.p50 = results.get((int) (n * 0.05));
    benchmarkResult.p90 = results.get((int) (n * 0.90));
    benchmarkResult.p99 = results.get((int) (n * 0.99));
    benchmarkResult.p999 = results.get((int) (n * 0.999));
    benchmarkResult.qps = n / totalSeconds;
    gson.toJson(benchmarkResult, writer);
    writer.close();
    System.out.println(String.format(
        "============ Test Results: \n"
            + "\t\tMin\tp5\tp10\tp25\tp50\tp75\tp90\tp99\tMax\n"
            + "  Time(ms)\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\n",
        results.get(0),
        results.get((int) (n * 0.05)),
        results.get((int) (n * 0.1)),
        results.get((int) (n * 0.25)),
        results.get((int) (n * 0.50)),
        results.get((int) (n * 0.75)),
        results.get((int) (n * 0.90)),
        results.get((int) (n * 0.99)),
        results.get(n - 1)));
  }

  public static void main(String[] args) throws Exception {
    Args a = new Args(args);
    if (a.conscrypt) {
      Security.insertProviderAt(Conscrypt.newProvider(), 1);
    }
    ArrayList<Long> results = new ArrayList<>();
    switch (a.client) {
      case CLIENT_YOSHI:
        System.out.println("**** Using Yoshi client library");
        HttpClient httpClient = new HttpClient(a);
        httpClient.startCalls(results);
        break;
      case CLIENT_GCSIO_HTTP:
        System.out.println("**** Using gcsio http library");
        GcsioClient gcsioHttpClient = new GcsioClient(a, false);
        gcsioHttpClient.startCalls(results);
        break;
      case CLIENT_GCSIO_GRPC:
        System.out.println("**** Using gcsio grpc library");
        GcsioClient gcsioGrpcClient = new GcsioClient(a, true);
        gcsioGrpcClient.startCalls(results);
        break;
      case CLIENT_GRPC:
        System.out.println("**** Using protoc generated stub");
        GrpcClient grpcClient = new GrpcClient(a);
        grpcClient.startCalls(results);
        break;
      default:
        logger.warning("Please provide --client");
    }
    FileWriter writer = new FileWriter(a.latencyFilename);
    printResult(writer, results);
  }
}
