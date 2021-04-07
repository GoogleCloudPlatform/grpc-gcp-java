package io.grpc.spanner;

import com.google.api.core.ApiFuture;
import com.google.cloud.spanner.ErrorCode;
import com.google.cloud.spanner.SpannerException;
import com.google.common.io.CharStreams;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import org.HdrHistogram.Histogram;

public class TestMain {
  private static final Logger logger = Logger.getLogger(TestMain.class.getName());
  private static Histogram histogram = new Histogram(6000L, 1);

  public static void main(String[] args) throws Exception {
    Args argsObj = new Args(args);
    setUpLogs(argsObj);

    SpannerClient spannerClient = new SpannerClient(argsObj);
    logger.log(Level.INFO, "Number of operation is {0}", argsObj.numRpcs);
    logger.log(Level.INFO, "Operation timeout is {0} ms", argsObj.timeoutMs);

    // warm up
    logger.log(Level.INFO, "Warm up start.");
    for (int i = 0; i < 10; ++i) {
      try {
        spannerClient.singleWrite();
      } catch (SpannerException e) {
      }
    }
    for (int i = 0; i < 10; ++i) {
      try {
        spannerClient.singleQuery();
      } catch (SpannerException e) {
      }
    }

    // use sync operation to measure the latency
    for (int i = 0; i < 10; ++i) {
      long start = System.currentTimeMillis();
      try {
        spannerClient.singleQuery();
      } catch (SpannerException e) {
      }
      int latency = (int) (System.currentTimeMillis() - start);
      histogram.recordValue(latency);
    }
    logger.log(
        Level.INFO,
        "Warm up complete. In total {0} RPCs. "
            + "\n         Min  p10  p25  p50  p90  p99  p99.5  p99.9  Max"
            + "\nTime(ms) {1}   {2}   {3}   {4}   {5}   {6}   {7}    {8}    {9}"
            + "\n {10}% samples has < 2x median RTT",
        new Object[] {
          histogram.getTotalCount(),
          histogram.getMinValue(),
          histogram.getValueAtPercentile(10),
          histogram.getValueAtPercentile(25),
          histogram.getValueAtPercentile(50),
          histogram.getValueAtPercentile(90),
          histogram.getValueAtPercentile(99),
          histogram.getValueAtPercentile(99.5),
          histogram.getValueAtPercentile(99.9),
          histogram.getMaxValue(),
          histogram.getPercentileAtOrBelowValue(histogram.getValueAtPercentile(50) * 2)
        });

    // use async method to measure error rate
    int numSucceed = 0;
    int numDeadlineErr = 0;
    int numInternalErr = 0;
    int numOtherErr = 0;
    List<ApiFuture<Void>> readFutureList = new ArrayList<>();
    logger.log(Level.INFO, "Async read starts");

    TcpkillManageThread tcpkillManageThread =
        new TcpkillManageThread(argsObj.logFilename + ".log", argsObj.tcpkillMs);
    if (argsObj.tcpkillMs > 0) {
      tcpkillManageThread.start();
    }
    IptablesManageThread iptablesManageThread =
        new IptablesManageThread(
            argsObj.logFilename + ".log", argsObj.iptablesMs, argsObj.iptablesDurationMs);
    if (argsObj.iptablesMs > 0) {
      iptablesManageThread.start();
    }

    for (int i = 0; i < argsObj.numRpcs; ++i) {
      readFutureList.add(spannerClient.singleQueryAsync());
      Thread.sleep(sampleExpDist(argsObj.intervalMs));
    }
    for (int i = 0; i < readFutureList.size(); ++i) {
      try {
        readFutureList.get(i).get(10_000, TimeUnit.MILLISECONDS);
        numSucceed++;
      } catch (InterruptedException e) {
        logger.log(Level.SEVERE, "Async read RPC closed with IterruptedException");
      } catch (ExecutionException e) {
        logger.log(Level.SEVERE, "Async read RPC closed with ExecutionException");
        SpannerException se = (SpannerException) e.getCause();
        if (se.getErrorCode() == ErrorCode.DEADLINE_EXCEEDED) {
          numDeadlineErr++;
        } else if (se.getErrorCode() == ErrorCode.INTERNAL) {
          numInternalErr++;
        } else {
          numOtherErr++;
        }
      } catch (TimeoutException e) {
        logger.log(Level.SEVERE, "Async read RPC closed with TimeoutException");
      }
    }

    // print result
    double numTotalRPC = numSucceed + numDeadlineErr + numInternalErr + numOtherErr;
    logger.log(
        Level.INFO,
        "{0} async read RPCs completed.\n"
            + " Deadline_exceed error rate is {1}, internal error rate is {2}, other error rate is"
            + " {3}.",
        new Object[] {
          numTotalRPC,
          String.format("%.4f", (numDeadlineErr / numTotalRPC)),
          String.format("%.4f", (numInternalErr / numTotalRPC)),
          String.format("%.4f", (numOtherErr / numTotalRPC))
        });

    tcpkillManageThread.isExit = true;
    iptablesManageThread.isExit = true;
    spannerClient.cleanUp();
  }

  /*
   * This thread manages iptables operation
   */
  private static class IptablesManageThread extends Thread {
    public volatile boolean isExit = false;
    private int iptablesMs;
    private int iptablesDurationMs;
    private String logFilename;

    IptablesManageThread(String name, int interval, int duration) {
      logFilename = name;
      iptablesMs = interval;
      iptablesDurationMs = duration;
    }

    @Override
    public void run() {
      logger.log(Level.FINE, "Iptables manage thread: start, log file is {0}", logFilename);
      while (!isExit) {
        IptablesThread iptablesThread = new IptablesThread(logFilename, iptablesDurationMs);
        iptablesThread.start();
        try {
          Thread.sleep(iptablesMs + iptablesDurationMs);
        } catch (InterruptedException e) {
        }
      }
      logger.log(Level.FINE, "Iptables manage thread: exit.");
    }
  }

  /*
   * This thread use iptables to blackhole a port
   */
  private static class IptablesThread extends Thread {
    private String logFilename;
    private int iptablesDurationMs;

    IptablesThread(String name, int duration) {
      logFilename = name;
      iptablesDurationMs = Math.max(duration, 5000);
    }

    @Override
    public void run() {
      logger.log(Level.FINE, "Iptables thread: start");
      if (logFilename.isEmpty()) {
        logger.log(Level.FINE, "Please specify logFilename and enable fineLog");
        return;
      }
      try {
        String grep_cmd =
            "grep 'OUTBOUND HEADERS' "
                + logFilename
                + " | grep 'ExecuteStreamingSql' | tail -1 | awk '{print $4}' | awk '{print"
                + " substr($0,length($0)-4)}' | tr '\n' ' '";
        Process grep_process = Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", grep_cmd});
        grep_process.waitFor();
        String port =
            CharStreams.toString(new InputStreamReader(grep_process.getInputStream(), "UTF-8"));
        if (port.isEmpty()) {
          logger.log(Level.INFO, "Iptables thread: could not find a valid port");
          return;
        }

        String blackhole_cmd = "sudo iptables -I INPUT -p tcp --dport " + port + " -j DROP";
        logger.log(Level.FINE, "Iptables thread: blockhole port " + port);
        Process blackhole_process =
            Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", blackhole_cmd});
        blackhole_process.waitFor();

        Thread.sleep(iptablesDurationMs);

        String whitelist_cmd = "sudo iptables -I INPUT -p tcp --dport " + port + " -j ACCEPT";
        logger.log(Level.FINE, "Iptables thread: whitelist port " + port);
        Process whitelist_process =
            Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", whitelist_cmd});
        whitelist_process.waitFor();
      } catch (IOException | InterruptedException e) {
      }
      logger.log(Level.FINE, "Iptables thread: exit");
    }
  }

  /*
   * This thread manages tcpkill operation
   */
  private static class TcpkillManageThread extends Thread {
    public volatile boolean isExit = false;
    private int tcpkillMs;
    private String logFilename;

    TcpkillManageThread(String name, int interval) {
      logFilename = name;
      tcpkillMs = interval;
    }

    @Override
    public void run() {
      logger.log(Level.FINE, "Tcpkill manage thread: start, log file is {0}", logFilename);
      try {
        String cmd = "sudo killall tcpkill";
        Process process = Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", cmd});
        process.waitFor();
        logger.log(Level.INFO, "Tcpkill manage thread: killall tcpkill");
      } catch (IOException | InterruptedException e) {
      }
      while (!isExit) {
        TcpkillThread tcpKillThread = new TcpkillThread(logFilename);
        tcpKillThread.start();
        try {
          Thread.sleep(tcpkillMs);
        } catch (InterruptedException e) {
        }
        tcpKillThread.interrupt();
      }
      logger.log(Level.FINE, "Tcpkill manage thread: exit.");
    }
  }

  /*
   * This thread use tcpkill to kill a port
   */
  private static class TcpkillThread extends Thread {
    private String logFilename;

    TcpkillThread(String name) {
      logFilename = name;
    }

    @Override
    public void run() {
      logger.log(Level.FINE, "Tcpkill thread: start");
      if (logFilename.isEmpty()) {
        logger.log(Level.FINE, "Please specify logFilename and enable fineLog");
        return;
      }
      try {
        String grep_cmd =
            "grep 'OUTBOUND HEADERS' "
                + logFilename
                + " | grep 'ExecuteStreamingSql' | tail -1 | awk '{print $4}' | awk '{print"
                + " substr($0,length($0)-4)}' | tr '\n' ' '";
        Process grep_process = Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", grep_cmd});
        grep_process.waitFor();
        String port =
            CharStreams.toString(new InputStreamReader(grep_process.getInputStream(), "UTF-8"));
        if (port.isEmpty()) {
          logger.log(Level.INFO, "Tcpkill thread: could not find a valid port");
          return;
        }
        logger.log(Level.INFO, "Tcpkill thread: kill port " + port);
        String tcpkill_cmd = "sudo tcpkill port " + port;
        Process tcpkill_process =
            Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", tcpkill_cmd});
        tcpkill_process.waitFor();
      } catch (IOException | InterruptedException e) {
      }
      logger.log(Level.FINE, "Tcpkill thread: exit");
    }
  }

  /*
   * Sample an exponential distribution
   */
  private static int sampleExpDist(int expectation) {
    return (int) (-expectation * Math.log(1 - (new Random().nextDouble())) + 0.5);
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
    String consoleHandlerProps =
        "java.util.logging.ConsoleHandler.level = ALL\n"
            + "java.util.logging.ConsoleHandler.formatter = java.util.logging.SimpleFormatter\n";
    String fileHandlerProps = "";
    if (!args.logFilename.isEmpty()) {
      if (args.disableConsoleLog) {
        handlers = "java.util.logging.FileHandler";
        consoleHandlerProps = "";
      } else {
        handlers = handlers + ", java.util.logging.FileHandler";
      }
      fileHandlerProps =
          "java.util.logging.FileHandler.level = ALL\n"
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
      fineProps = ".level = FINEST\n";
    }
    LogManager.getLogManager()
        .readConfiguration(
            new ByteArrayInputStream(
                ("handlers="
                        + handlers
                        + "\n"
                        + consoleHandlerProps
                        + fileHandlerProps
                        + fineProps
                        + "java.util.logging.SimpleFormatter.format=%1$tY-%1$tm-%1$td"
                        + " %1$tH:%1$tM:%1$tS.%1$tN %2$s%n%4$s: %5$s%6$s%n\n")
                    .getBytes(StandardCharsets.UTF_8)));
  }
}
