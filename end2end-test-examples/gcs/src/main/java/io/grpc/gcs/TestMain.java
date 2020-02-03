package io.grpc.gcs;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.logging.Logger;
import org.HdrHistogram.Histogram;

public class TestMain {
  private static final Logger logger = Logger.getLogger(TestMain.class.getName());

  private static void printResult(Histogram histogram) {
    System.out.println(
        String.format( "============ Test Results: \n"
                + "\t\tMin\tp5\tp10\tp25\tp50\tp75\tp90\tp99\tMax\n"
                + "  Time(ms)\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d",
            histogram.getMinValue(),
            histogram.getValueAtPercentile(5),
            histogram.getValueAtPercentile(10),
            histogram.getValueAtPercentile(25),
            histogram.getValueAtPercentile(50),
            histogram.getValueAtPercentile(75),
            histogram.getValueAtPercentile(90),
            histogram.getValueAtPercentile(99),
            histogram.getValueAtPercentile(100)));
  }

  public static void main(String[] args) throws Exception {
    Args a = new Args(args);
    Histogram histogram = new Histogram(60000000L, 1);
    if (a.http) {
      System.out.println("Making http call");
      HttpClient client = new HttpClient(a);
      client.startCalls(histogram);
    } else {
      GrpcClient client = new GrpcClient(a);
      client.startCalls(histogram);
    }

    printResult(histogram);
  }
}
