package io.grpc.echo;

import static io.opencensus.exporter.trace.stackdriver.StackdriverExporter.createAndRegister;
import io.grpc.Channel;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.echo.Echo.EchoResponse;
import io.grpc.echo.Echo.EchoWithResponseSizeRequest;
import io.grpc.stub.StreamObserver;
import io.opencensus.common.Scope;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.config.TraceConfig;
import io.opencensus.trace.samplers.Samplers;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

public class EchoClient {
  private static final Logger logger = Logger.getLogger(EchoClient.class.getName());

  private static final String DEFAULT_HOST = "staging-grpc-cfe-benchmarks-with-esf.googleapis.com";
  private static final int PORT = 443;

  private final ManagedChannel originalChannel;
  private final GrpcCloudapiGrpc.GrpcCloudapiBlockingStub blockingStub;
  private final GrpcCloudapiGrpc.GrpcCloudapiStub stub;

  public EchoClient(String host, int port, String cookie, boolean corp) {
    originalChannel = ManagedChannelBuilder.forAddress(host, port).build();

    ClientInterceptor interceptor = new HeaderClientInterceptor(cookie, corp);
    Channel interceptingChannel = ClientInterceptors.intercept(originalChannel, interceptor);
    blockingStub = GrpcCloudapiGrpc.newBlockingStub(interceptingChannel);
    stub = GrpcCloudapiGrpc.newStub(originalChannel);
  }

  public void shutdown() throws InterruptedException {
    originalChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }

  public void asyncEcho(String message, CountDownLatch latch, List<Long> timeList) {
    EchoWithResponseSizeRequest request =
        EchoWithResponseSizeRequest.newBuilder().setEchoMsg(message).setResponseSize(10).build();
    stub.echoWithResponseSize(request, new StreamObserver<EchoResponse>() {
      long start = System.currentTimeMillis();

      @Override
      public void onNext(EchoResponse value) {
      }

      @Override
      public void onError(Throwable t) {
        latch.countDown();
        Status status = Status.fromThrowable(t);
        logger.log(Level.WARNING, "Encountered an error in echo RPC. Status: " + status);
        t.printStackTrace();
      }

      @Override
      public void onCompleted() {
        long now = System.currentTimeMillis();
        timeList.add(now - start);
        latch.countDown();
      }
    }); }

  public void echo(EchoWithResponseSizeRequest request, Tracer tracer, List<Long> timeList) {
    if (tracer != null) {
      try (Scope scope = tracer.spanBuilder("echo_java").startScopedSpan()) {
        try {
          blockingStub.echoWithResponseSize(request);
          logger.info("Sent echo with tracer on");
        } catch (StatusRuntimeException e) {
          logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
        }
      }
    } else {
      try {
        long start = System.currentTimeMillis();
        blockingStub.echoWithResponseSize(request);
        if (timeList != null) {
          timeList.add(System.currentTimeMillis() - start);
        }
      } catch (StatusRuntimeException e) {
        logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
      }
    }
  }

  private static String generateString(int numBytes) {
    StringBuilder sb = new StringBuilder(numBytes);
    for (int i = 0; i < numBytes; i++) {
      sb.append('x');
    }
    return sb.toString();
  }

  /** return a global tracer */
  private static Tracer getTracer() {
    try {
      createAndRegister();
    } catch (IOException e) {
      return null;
    }
    return Tracing.getTracer();
  }

  private static void printResult(int numRpcs, int payload, List<Long> timeList) {
    Collections.sort(timeList);
    System.out.println(
        String.format(
            "[Number of RPCs: %d, "
                + "Payload Size: %dKB]\n"
                + "\t\tAvg"
                + "\tMin"
                + "\tp50"
                + "\tp90"
                + "\tp99"
                + "\tMax\n"
                + "  Time(ms)\t%d\t%d\t%d\t%d\t%d\t%d",
            numRpcs,
            payload,
            timeList.stream().mapToLong(Long::longValue).sum() / timeList.size(),
            timeList.get(0),
            timeList.get((int) (timeList.size() * 0.5)),
            timeList.get((int) (timeList.size() * 0.9)),
            timeList.get((int) (timeList.size() * 0.99)),
            timeList.get(timeList.size() - 1)));
  }

  public static void main(String[] args) throws Exception {
    ArgumentParser parser =
        ArgumentParsers.newFor("Echo client test")
            .build()
            .defaultHelp(true)
            .description("Echo client java binary");

    parser.addArgument("--payloadKB").type(Integer.class).setDefault(1);
    parser.addArgument("--numRpcs").type(Integer.class).setDefault(5);
    parser.addArgument("--tracer").type(Boolean.class).setDefault(false);
    parser.addArgument("--cookie").type(String.class).setDefault("");
    parser.addArgument("--wait").type(Integer.class).setDefault(0);
    parser.addArgument("--corp").type(Boolean.class).setDefault(false);
    parser.addArgument("--warmup").type(Integer.class).setDefault(5);
    parser.addArgument("--host").type(String.class).setDefault(DEFAULT_HOST);

    Namespace ns = parser.parseArgs(args);

    // Read args
    int payloadKB = ns.getInt("payloadKB");
    int numRpcs = ns.getInt("numRpcs");
    boolean enableTracer = ns.getBoolean("tracer");
    String cookie = ns.getString("cookie");
    int wait = ns.getInt("wait");
    int warmup = ns.getInt("warmup");
    boolean corp = ns.getBoolean("corp");
    String host = ns.getString("host");
    String message = generateString(payloadKB * 1024);

    // Prepare tracer
    Tracer tracer = null;
    if (enableTracer) {
      tracer = getTracer();
      if (tracer == null) {
        logger.log(Level.WARNING, "Cannot get tracer");
        return;
      }
    }
    TraceConfig globalTraceConfig = Tracing.getTraceConfig();
    globalTraceConfig.updateActiveTraceParams(
        globalTraceConfig.getActiveTraceParams().toBuilder()
            .setSampler(Samplers.alwaysSample())
            .build());


    // Starting test
    System.out.println("Start sending " + payloadKB + "KB bytes for " + numRpcs + " times...");
    EchoClient client = new EchoClient(host, PORT, cookie, corp);
    EchoWithResponseSizeRequest request =
        EchoWithResponseSizeRequest.newBuilder().setEchoMsg(message).setResponseSize(10).build();
    List<Long> timeList = new ArrayList<>(numRpcs);
    try {
      // Warm up calls
      for (int i = 0; i < warmup; i++) {
        client.echo(request, null, null);
      }

      for (int i = 0; i < numRpcs; i++) {
        client.echo(request, tracer, timeList);
        if (wait > 0) {
          Thread.sleep(wait * 1000);
        }
      }
      printResult(numRpcs, payloadKB, timeList);
    } finally {
      client.shutdown();
    }
  }
}
