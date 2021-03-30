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
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class TestMain {
  private static final Logger logger = Logger.getLogger(TestMain.class.getName());
  private static List<Integer> latencyList = new ArrayList<Integer>();

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
        spannerClient.singleRead();
      } catch (SpannerException e) {
      }
    }

    // use sync operation to measure the latency
    for (int i = 0; i < 100; ++i) {
      long start = System.currentTimeMillis();
      try {
        spannerClient.singleRead();
      } catch (SpannerException e) {
      }
      int latency = (int) (System.currentTimeMillis() - start);
      latencyList.add(latency);
    }
    Collections.sort(latencyList);
    logger.log(
        Level.INFO,
        "Warm up complete. In total {0} sync read RPCs. Minimal latency is {1}, median latency is {2} ms, maximum latency"
            + " is {3} ms.",
        new Object[] {
          latencyList.size(),
          latencyList.get(0),
          latencyList.get(latencyList.size() / 2),
          latencyList.get(latencyList.size() - 1)
        });

    // use async method to measure error rate
    int numSucceed = 0;
    int numDeadlineErr = 0;
    int numInternalErr = 0;
    int numOtherErr = 0;
    List<ApiFuture<Void>> readFutureList = new ArrayList<>();
    SideThread sideThread = new SideThread(argsObj.logFilename + ".log", argsObj.tcpkillMs);
    if (argsObj.tcpkillMs > 0) {
      sideThread.start();
    }
    logger.log(Level.INFO, "Async read starts");
    for (int i = 0; i < argsObj.numRpcs; ++i) {
      readFutureList.add(spannerClient.singleReadAsync());
      Thread.sleep(sampleExpDist(argsObj.timeoutMs));
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
    sideThread.isExit = true;
    double numTotalRPC = numSucceed + numDeadlineErr + numInternalErr + numOtherErr;
    logger.log(
        Level.INFO,
        "{0} async read RPCs completed.\n"
            + " Deadline_exceed error rate is {1}, internal error rate is {2}, other error rate is"
            + " {3}.",
        new Object[] {
          numTotalRPC,
          String.format("%.3f", (numDeadlineErr / numTotalRPC)),
          String.format("%.3f", (numInternalErr / numTotalRPC)),
          String.format("%.3f", (numOtherErr / numTotalRPC))
        });

    spannerClient.cleanUp();
  }

  /*
   * A side thread used to do tcpkill or iptables
   */
  private static class SideThread extends Thread {
    public volatile boolean isExit = false;
    private int tcpkillMs;
    private String logFilename;

    SideThread(String name, int interval) {
      logFilename = name;
      tcpkillMs = interval;
    }

    @Override
    public void run() {
      logger.log(Level.FINE, "Side thread start. Log file is {0}", logFilename);
      while (!isExit) {
        TcpKillThread tcpKillThread = new TcpKillThread(logFilename);
        tcpKillThread.start();
        try {
          Thread.sleep(tcpkillMs);
        } catch (InterruptedException e) {
        }
        tcpKillThread.interrupt();
      }
      logger.log(Level.FINE, "Side thread exit.");
    }
  }

  /*
   * A thread that run tcpkill
   */
  private static class TcpKillThread extends Thread {
    private String logFilename;
    private int lastPort = -1;

    TcpKillThread(String name) {
      logFilename = name;
    }

    @Override
    public void run() {
      logger.log(Level.FINE, "TcpKill thread: start");
      if (logFilename.isEmpty()) {
        return;
      }
      try {
        String grep_cmd =
            "grep 'OUTBOUND HEADERS' "
                + logFilename
                + " | grep 'StreamingRead' | tail -1 | awk '{print $4}' | awk '{print"
                + " substr($0,length($0)-4)}'";
        Process grep_process = Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", grep_cmd});
        grep_process.waitFor();
        String port =
            CharStreams.toString(new InputStreamReader(grep_process.getInputStream(), "UTF-8"));
        if (port.isEmpty()) {
          logger.log(Level.INFO, "TcpKill thread: could not find a valid port");
          return;
        }
        logger.log(Level.INFO, "TcpKill thread: kill port " + port);
        String tcpkill_cmd = "sudo tcpkill port " + port;
        Process tcpkill_process =
            Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", tcpkill_cmd});
        tcpkill_process.waitFor();
      } catch (IOException | InterruptedException e) {
      }
      logger.log(Level.FINE, "TcpKill thread: exit");
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
