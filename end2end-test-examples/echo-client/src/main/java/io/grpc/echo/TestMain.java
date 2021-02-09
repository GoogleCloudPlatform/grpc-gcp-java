package io.grpc.echo;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.concurrent.CountDownLatch;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import javax.net.ssl.SSLException;
import org.HdrHistogram.Histogram;

public class TestMain {
  private static final int INFINITE_REQUESTS_MIN_DELAY = 1000;
  private static final Logger logger = Logger.getLogger(TestMain.class.getName());

  private static void printResult(Args arg, long totalPayload,
      long duration, Histogram histogram) {

    String resultFileName = String.format("qps%d_chan%d_rpcs%d_size%d_%sresult.txt",
        arg.qps, arg.numChannels, arg.numRpcs, arg.reqSize, (arg.async ? "async_" : ""));
    try {
      histogram.outputPercentileDistribution(new PrintStream(new FileOutputStream(resultFileName)), 1.0);
    } catch (FileNotFoundException e) {
      logger.warning("File not found: " + e.getMessage());
    }


    System.out.println(
        String.format("%d channels, %d total rpcs sent"
                + "\nPayload Size per request = %dKB"
                + "\nPer sec Payload = %.2f MB (exact amount of KB = %d)"
                //+ "\n\t\tAvg"
                + "\n\t\tMin"
                + "\tp50"
                + "\tp90"
                + "\tp99"
                + "\tp99.9"
                + "\tMax\n"
                + "  Time(ms)\t%d\t%d\t%d\t%d\t%d\t%d",
            arg.numChannels, histogram.getTotalCount(),
            arg.reqSize,
            (0.1 * totalPayload / duration), totalPayload,
            //histogram.getMean(),
            histogram.getMinValue(),
            histogram.getValueAtPercentile(50),
            histogram.getValueAtPercentile(90),
            histogram.getValueAtPercentile(99),
            histogram.getValueAtPercentile(99.9),
            histogram.getValueAtPercentile(100)));

    System.out.println("\n** histogram percentile distribution output file: " + resultFileName);
  }

  private static void warmup(EchoClient client, Args args) throws InterruptedException {
    int numCalls = args.warmup * args.numChannels;
    CountDownLatch latch = new CountDownLatch(numCalls);
    for (int i = 0; i < numCalls; i++) {
      client.echo(i, latch, null);
    }
    if (args.async) {
      latch.await();
    }
  }

  private static void runTest(Args args, EchoClient client) throws InterruptedException {

    int rpcsToDo = args.numRpcs;
    CountDownLatch latch = new CountDownLatch(rpcsToDo);
    Histogram histogram = new Histogram(60000000L, 1);

    long totalPayloadSize = 0;
    long startFirst = System.currentTimeMillis();
    for (int i = 0; args.stream || rpcsToDo == 0 || i < rpcsToDo; i++) {
      if (args.async) {
        if (args.distrib != null) {
          int sample = args.distrib.sample();
          if (sample > 0) {
            //logger.info("sleeping for: " + sample);
            Thread.sleep(sample);
          }
        }
      }

      if (!args.stream && (args.interval > 0 || rpcsToDo == 0)) {
        int delay = args.interval;
        if (rpcsToDo == 0 && delay < INFINITE_REQUESTS_MIN_DELAY) {
          delay = INFINITE_REQUESTS_MIN_DELAY;
        }
        Thread.sleep(delay);
      }
      client.echo(i, latch, histogram);
      totalPayloadSize += args.reqSize;
    }

    long totalSendTime = System.currentTimeMillis() - startFirst;
    if (args.async) {
      latch.await();
    }
    long totalRecvTime = System.currentTimeMillis() - startFirst;

    logger.info("TEST DONE.\n"
        + "=============================================================\n"
        + "Total Send time = " + totalSendTime
        + "ms, Total Receive time = " + totalRecvTime + "ms");
    printResult(args, totalPayloadSize, totalRecvTime, histogram);
  }

  private static void execTask(Args argObj) throws InterruptedException, SSLException {
    EchoClient client = new EchoClient(argObj);
    try {
      logger.info("Start warm up...");
      warmup(client, argObj);

      logger.info("Warm up done. Start benchmark tests...");
      runTest(argObj, client);
    } finally {
      client.shutdown();
    }
  }

  public static void main(String[] args) throws Exception {
    Args argObj = new Args(args);

    if (argObj.fineLogs) {
        LogManager.getLogManager().readConfiguration(
            TestMain.class.getResourceAsStream("/logging.properties"));
    }

    execTask(argObj);
  }
}
