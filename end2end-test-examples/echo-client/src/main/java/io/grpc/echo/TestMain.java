package io.grpc.echo;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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

  private static void warmup(EchoClient client, Args args) throws SSLException, InterruptedException {
    int numCalls = args.warmup * args.numChannels;
    CountDownLatch latch = new CountDownLatch(numCalls);
    for (int i = 0; i < numCalls; i++) {
      client.echo(i, latch, null);
    }
    if (args.async) {
      latch.await();
    }
  }

  private static void runTest(Args args, EchoClient client) throws SSLException, InterruptedException {

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

  private static void execTask(Args argObj) throws InterruptedException, IOException {
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

  private static void setUpLogs(Args args) throws IOException {
    if (!args.fineLogs && args.logFilename.isEmpty() && args.logConfig.isEmpty()) {
      return;
    }
    if (!args.logConfig.isEmpty()) {
      File configFile = new File(args.logConfig);
      InputStream configStream = new FileInputStream(configFile);
      LogManager.getLogManager().readConfiguration(configStream);
      return;
    }
    String handlers = "java.util.logging.ConsoleHandler";
    String consoleHandlerProps = "java.util.logging.ConsoleHandler.level = ALL\n"
        + "java.util.logging.ConsoleHandler.formatter = java.util.logging.SimpleFormatter\n";
    String fileHandlerProps = "";
    if (!args.logFilename.isEmpty()) {
      if (args.disableConsoleLog) {
        handlers = "java.util.logging.FileHandler";
        consoleHandlerProps = "";
      } else {
        handlers = handlers + ", java.util.logging.FileHandler";
      }
      fileHandlerProps = "java.util.logging.FileHandler.level = ALL\n"
          + "java.util.logging.FileHandler.formatter = java.util.logging.SimpleFormatter\n";
      String filename = args.logFilename;
      if (args.logMaxFiles > 0) {
        filename += ".%g";
        fileHandlerProps += "java.util.logging.FileHandler.count = " + args.logMaxFiles + "\n";
      }
      filename += ".log";
      if (args.logMaxSize > 0) {
        fileHandlerProps += "java.util.logging.FileHandler.limit = " + args.logMaxSize + "\n";
      }
      fileHandlerProps += "java.util.logging.FileHandler.pattern = " + filename + "\n";
    }
    String fineProps = "";
    if (args.fineLogs) {
      fineProps = ".level = FINE\n";
    }
    LogManager.getLogManager().readConfiguration(new ByteArrayInputStream((
        "handlers=" + handlers + "\n"
            + consoleHandlerProps
            + fileHandlerProps
            + fineProps
            + "java.util.logging.SimpleFormatter.format=%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tN %2$s%n%4$s: %5$s%6$s%n\n")
        .getBytes(StandardCharsets.UTF_8)
    ));
  }

  public static void main(String[] args) throws Exception {
    Args argObj = new Args(args);

    setUpLogs(argObj);
    execTask(argObj);
  }
}
