package io.grpc.gcs;

import com.google.gson.Gson;
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


public class ResultTable {
  private Args args;
  private Long startTime;
  private Long endTime;
  private List<Long> results;

  public ResultTable(Args args) {
    this.args = args;
    this.results = new ArrayList<>();
  }

  public void start() {
    synchronized (this) {
      startTime = System.currentTimeMillis();
    }
  }

  public void stop() {
    synchronized (this) {
      endTime = System.currentTimeMillis();  
    }
  }

  public void reportResult(long duration) {
    int ord;
    synchronized (this) {
      results.add(duration);
      ord = results.size();
    }
    if (this.args.verboseResult) {
      System.out.format("### Result: ord=%d elapsed=%d\n", ord, duration);
      System.out.flush();
    }
  }

  private static class BenchmarkResult {
    public Long min, p50, p90, p99, p999;
    public double qps;
  }

  public void printResult() throws IOException {
    synchronized (this) {
      if (results.size() == 0) return;
      Collections.sort(results);
      int n = results.size();
      double totalSeconds = 0;
      long totalDur = endTime - startTime;
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
      if (!args.latencyFilename.isEmpty()) {
        FileWriter writer = new FileWriter(args.latencyFilename);
        gson.toJson(benchmarkResult, writer);
        writer.close();
      }
      System.out.println(String.format(
          "****** Test Results [client: %s, method: %s, size: %d, threads: %d, dp: %s, calls: %d]: \n"
              + "\t\tMin\tp5\tp10\tp25\tp50\tp75\tp90\tp99\tMax\tTotal\n"
              + "  Time(ms)\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\n",
          args.client,
          args.method,
          args.size,
          args.threads,
          args.dp,
          n,
          results.get(0),
          results.get((int) (n * 0.05)),
          results.get((int) (n * 0.1)),
          results.get((int) (n * 0.25)),
          results.get((int) (n * 0.50)),
          results.get((int) (n * 0.75)),
          results.get((int) (n * 0.90)),
          results.get((int) (n * 0.99)),
          results.get(n - 1),
          totalDur
      ));
    } 
  }
}
