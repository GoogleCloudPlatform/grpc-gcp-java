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
  private static List<Integer> payloads = Arrays.asList(100);
  private static List<EchoWithResponseSizeRequest> requests = new ArrayList<>();
  private static String generatePayload(int numBytes) {
    StringBuilder sb = new StringBuilder(numBytes);
    for (int i = 0; i < numBytes; i++) {
      sb.append('x');
    }
    return sb.toString();
  }

  private static void printResult(long numRpcs, Args arg, List<Long> timeList, int total,
      long duration) {
    if (timeList == null || timeList.isEmpty()) return;
    int avg = total/timeList.size();
    long totalKb = (avg * numRpcs);
    Collections.sort(timeList);
    logger.info(
        String.format("%d qps, %d channels, %d total rpc's sent"
                + "\nAvg Payload Size per request = %dKB"
                + "\nPer sec Payload = %d MB (exact amount of KB = %d)"
                + "\n\t\tAvg"
                + "\tMin"
                + "\tp50"
                + "\tp90"
                + "\tp99"
                + "\tMax\n"
                + "  Time(ms)\t%d\t%d\t%d\t%d\t%d\t%d",
            arg.qps, arg.numChannels, numRpcs,
            avg,
            (totalKb / duration), totalKb,
            timeList.stream().mapToLong(Long::longValue).sum() / timeList.size(),
            timeList.get(0),
            timeList.get((int) (timeList.size() * 0.5)),
            timeList.get((int) (timeList.size() * 0.9)),
            timeList.get((int) (timeList.size() * 0.99)),
            timeList.get(timeList.size() - 1)));
  }

  private static int nextRequestIndex = 0;
  private static void runTest(Args args, EchoClient client, int payloadSize,
      EchoWithResponseSizeRequest request, Tracer tracer, boolean isWarmup)
      throws InterruptedException {
    long rpcsToDo = (isWarmup) ? 10 : args.numRpcs;
    List<Long> timeList = new ArrayList<>();
    CountDownLatch latch = args.waitfordone ? new CountDownLatch((int)rpcsToDo) : null;

    int totalPayloadSize = 0;
    long startFirst = System.currentTimeMillis();
    for (int i = 0; i < rpcsToDo; i++) {
      if (!isWarmup) {
        if (args.distrib != null) {
          int sample = args.distrib.sample();
          if (sample > 0) {
            //logger.info("sleeping for: " + sample);
            Thread.sleep(sample);
          }
        }
      }

      // for async, randomize the request size
      if (args.async) {
        request = requests.get(nextRequestIndex);
        payloadSize = payloads.get(nextRequestIndex);
        nextRequestIndex = ++nextRequestIndex % requests.size();
      }
      client.echo(request, latch, tracer, timeList);
      totalPayloadSize += payloadSize;
    }

    long endSendTime = System.currentTimeMillis() - startFirst;
    if (args.waitfordone) {
      latch.await();
    }
    long endRecvTime = System.currentTimeMillis() - startFirst;

    if (isWarmup) return;
    logger.info("Total Send time = " + endSendTime
        + "ms, Total Receive time = " + endRecvTime + "ms");
    printResult(rpcsToDo, args, timeList, totalPayloadSize, endSendTime);
  }

  private static void execTask(Args argObj)
      throws InterruptedException, SSLException {
    EchoClient client = new EchoClient(argObj);

    // Warmup
    logger.info("Start warm up...");
    runTest(argObj, client, 10, requests.get(0), null,true);
    Tracer tracer = (argObj.enableTracer) ? new TracerManager().getTracer() : null;

    logger.info("Warm up done. Start benchmark tests...");
    try {
      if (argObj.async) {
        runTest(argObj, client, -1, null, tracer, false);
      } else {
        for (int i = 0; i < payloads.size(); i++) {
          runTest(argObj, client, payloads.get(i), requests.get(i), tracer, false);
        }
      }
    } finally {
      client.shutdown();
    }
  }

  public static void main(String[] args) throws Exception {
    Args argObj = new Args(args);

    for (int j = 0; j < payloads.size(); j++) {
      requests.add(EchoWithResponseSizeRequest.newBuilder()
          .setEchoMsg(generatePayload(payloads.get(j) * 1024))
          .setResponseSize(10)
          .build());
    }

    try {
      execTask(argObj);
    } catch (InterruptedException | SSLException e) {
      logger.info("interrupted?" + e.getMessage());
    }
  }
}
