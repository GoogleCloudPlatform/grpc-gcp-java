package io.grpc.echo;

import io.grpc.echo.Echo.EchoWithResponseSizeRequest;
import io.opencensus.trace.Tracer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;
import javax.net.ssl.SSLException;

public class TestMain {
  private static final Logger logger = Logger.getLogger(TestMain.class.getName());

  private static String generatePayload(int numBytes) {
    StringBuilder sb = new StringBuilder(numBytes);
    for (int i = 0; i < numBytes; i++) {
      sb.append('x');
    }
    return sb.toString();
  }

  private static void printResult(int numRpcs, Args arg, List<Long> timeList, long totalPayload,
      long duration) {
    if (timeList == null || timeList.isEmpty()) return;
    double avg = 0;
    for (long cost : timeList) {
      avg += 1.0 * cost / timeList.size();
    }
    Collections.sort(timeList);
    System.out.println(
        String.format("%d qps, %d channels, %d total rpcs sent"
                + "\nPayload Size per request = %dKB"
                + "\nPer sec Payload = %.2f MB (exact amount of KB = %d)"
                + "\n\t\tAvg"
                + "\tMin"
                + "\tp50"
                + "\tp90"
                + "\tp99"
                + "\tMax\n"
                + "  Time(ms)\t%.2f\t%d\t%d\t%d\t%d\t%d",
            arg.qps, arg.numChannels, numRpcs,
            arg.reqSize,
            (0.1 * totalPayload / duration), totalPayload,
            avg,
            timeList.get(0),
            timeList.get((int) (timeList.size() * 0.5)),
            timeList.get((int) (timeList.size() * 0.9)),
            timeList.get((int) (timeList.size() * 0.99)),
            timeList.get(timeList.size() - 1)));
  }

  private static void runTest(Args args, EchoClient client,
      EchoWithResponseSizeRequest request, boolean isWarmup) throws InterruptedException {
    int rpcsToDo = (isWarmup) ? 10 : args.numRpcs;
    List<Long> timeList = new ArrayList<>();
    long[] startTimeArr = new long[rpcsToDo];
    long[] endTimeArr = new long[rpcsToDo];
    CountDownLatch latch = args.async ? new CountDownLatch(rpcsToDo) : null;

    long totalPayloadSize = 0;
    long startFirst = System.currentTimeMillis();
    for (int i = 0; i < rpcsToDo; i++) {
      if (!isWarmup && args.async) {
        if (args.distrib != null) {
          int sample = args.distrib.sample();
          if (sample > 0) {
            //logger.info("sleeping for: " + sample);
            Thread.sleep(sample);
          }
        }
      }

      client.echo(i, request, latch, timeList, startTimeArr, endTimeArr);
      totalPayloadSize += args.reqSize;
    }

    long totalSendTime = System.currentTimeMillis() - startFirst;
    if (args.async) {
      latch.await();
    }
    long totalRecvTime = System.currentTimeMillis() - startFirst;

    if (isWarmup) return;
    logger.info("TEST DONE.\n"
        + "=============================================================\n"
        + "Total Send time = " + totalSendTime
        + "ms, Total Receive time = " + totalRecvTime + "ms");
    printResult(rpcsToDo, args, timeList, totalPayloadSize, totalRecvTime);
  }

  private static void execTask(Args argObj)
      throws InterruptedException, SSLException {
    EchoClient client = new EchoClient(argObj);

    EchoWithResponseSizeRequest request = EchoWithResponseSizeRequest.newBuilder()
        .setEchoMsg(generatePayload(argObj.reqSize * 1024))
        .setResponseSize(argObj.rspSize)
        .build();

    // Warmup
    logger.info("Start warm up...");
    runTest(argObj, client, request, true);

    logger.info("Warm up done. Start benchmark tests...");
    try {
      runTest(argObj, client, request, false);
    } finally {
      client.shutdown();
    }
  }

  public static void main(String[] args) throws Exception {
    Args argObj = new Args(args);
    try {
      execTask(argObj);
    } catch (InterruptedException | SSLException e) {
      logger.info("interrupted?" + e.getMessage());
    }
  }
}
