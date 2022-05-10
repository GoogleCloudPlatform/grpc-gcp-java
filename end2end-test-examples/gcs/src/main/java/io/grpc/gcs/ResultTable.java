package io.grpc.gcs;

import com.google.gson.Gson;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ResultTable {
  private Args args;
  private int warmupCount;
  private long startTime;
  private long endTime;

  private static class Result {
    public long startTime;
    public long duration;
  }

  private List<Result> results;

  public ResultTable(Args args) {
    this.args = args;
    this.warmupCount = args.warmups * args.threads;
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

  public void reportResult(String bucket, String object, long bytes, long duration) {
    int ord;
    synchronized (this) {
      Result result = new Result();
      result.startTime = System.currentTimeMillis() - duration;
      result.duration = duration;
      results.add(result);
      ord = results.size();
    }
    if (this.args.verboseResult) {
      System.out.format("### Result: ord=%d bucket=%s object=%s bytes=%d elapsed=%d%s\n", ord,
          bucket, object, bytes, duration, results.size() <= warmupCount ? " [WARM-UP]" : "");
      System.out.flush();
    }
  }

  private static class BenchmarkResult {
    public Long min, p50, p90, p99, p999;
    public double qps;
  }

  public void printResult() throws IOException {
    synchronized (this) {
      results.subList(0, Math.min(results.size(), warmupCount)).clear();

      if (results.size() == 0)
        return;

      int n = results.size();
      long totalDur =
          results.get(n - 1).startTime + results.get(n - 1).duration - results.get(0).startTime;
      double totalSec = totalDur / 1000.0;

      Collections.sort(results, new Comparator<Result>() {
        @Override
        public int compare(Result o1, Result o2) {
          return Long.compare(o1.duration, o2.duration);
        }
      });

      Gson gson = new Gson();
      BenchmarkResult benchmarkResult = new BenchmarkResult();
      benchmarkResult.min = results.get(0).duration;
      benchmarkResult.p50 = results.get((int) (n * 0.05)).duration;
      benchmarkResult.p90 = results.get((int) (n * 0.90)).duration;
      benchmarkResult.p99 = results.get((int) (n * 0.99)).duration;
      benchmarkResult.p999 = results.get((int) (n * 0.999)).duration;
      benchmarkResult.qps = n / totalSec;
      if (!args.latencyFilename.isEmpty()) {
        FileWriter writer = new FileWriter(args.latencyFilename);
        gson.toJson(benchmarkResult, writer);
        writer.close();
      }
      System.out.println(String.format(
          "****** Test Results [client: %s, method: %s, size: %d, threads: %d, dp: %s, calls:"
              + " %d, qps: %f]: \n" + "\t\tMin\tp5\tp10\tp25\tp50\tp75\tp90\tp99\tMax\tTotal\n"
              + "  Time(ms)\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\n",
          args.client, args.method, args.size, args.threads, args.dp, n, n / totalSec,
          results.get(0).duration, results.get((int) (n * 0.05)).duration,
          results.get((int) (n * 0.1)).duration, results.get((int) (n * 0.25)).duration,
          results.get((int) (n * 0.50)).duration, results.get((int) (n * 0.75)).duration,
          results.get((int) (n * 0.90)).duration, results.get((int) (n * 0.99)).duration,
          results.get(n - 1).duration, totalDur));
    }
  }
}
