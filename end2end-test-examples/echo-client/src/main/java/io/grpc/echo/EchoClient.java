package io.grpc.echo;

import com.google.api.MonitoredResource;
import io.grpc.*;
import io.grpc.echo.Echo.EchoResponse;
import io.grpc.echo.Echo.BatchEchoRequest;
import io.grpc.echo.Echo.BatchEchoResponse;
import io.grpc.echo.Echo.EchoWithResponseSizeRequest;
import io.grpc.echo.Echo.StreamEchoRequest;
import io.grpc.echo.GrpcCloudapiGrpc.GrpcCloudapiBlockingStub;
import io.grpc.echo.GrpcCloudapiGrpc.GrpcCloudapiStub;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.grpc.stub.StreamObserver;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLException;

import io.opencensus.exporter.stats.stackdriver.StackdriverStatsConfiguration;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsExporter;
import io.opencensus.metrics.*;
import org.HdrHistogram.Histogram;

public class EchoClient {
  private static final int STREAMING_MIN_INTERVAL = 1000;
  private static final Logger logger = Logger.getLogger(EchoClient.class.getName());

  //  private final ManagedChannel originalChannel;
  private final ManagedChannel[] channels;
  private long blockingChannelCreated;

  private GrpcCloudapiBlockingStub blockingStub;
  private final GrpcCloudapiStub[] asyncStubs;

  private final Args args;

  private int rr;

  private MetricRegistry metricRegistry;
  private Map<String, Map<Boolean, AtomicLong>> errorCounts = new ConcurrentHashMap<>();
  final private String OTHER_STATUS = "OTHER";

  public EchoClient(Args args) throws IOException {
    this.args = args;

    setUpMetrics();

    channels = new ManagedChannel[args.numChannels];
    asyncStubs = new GrpcCloudapiStub[args.numChannels];
    rr = 0;

    for (int i = 0; i < args.numChannels; i++) {

      //      SslContextBuilder sslContextBuilder = GrpcSslContexts.forClient();
      //      SslContext sslContext =
      // sslContextBuilder.trustManager(TlsTesting.loadCert("CAcert.pem")).build();
      //
      //      ManagedChannelBuilder builder =
      //          NettyChannelBuilder.forTarget(host + ":" + port)
      //              .overrideAuthority("test_cert_2")
      //              .sslContext(sslContext);

      // ManagedChannelBuilder builder = ManagedChannelBuilder.forAddress(argObj.host, argObj.port);

      Channel channel = createChannel(i);
      if (i == 0) {
        blockingStub = GrpcCloudapiGrpc.newBlockingStub(channel);
      }
      asyncStubs[i] = GrpcCloudapiGrpc.newStub(channel);

      if (!args.reqComp.isEmpty()) {
        if (i == 0) {
          blockingStub = blockingStub.withCompression(args.reqComp);
        }
        asyncStubs[i] = asyncStubs[i].withCompression(args.reqComp);
      }
    }
  }

  private void setUpMetrics() throws IOException {
    if (args.metricName.isEmpty()) {
      return;
    }
    metricRegistry = Metrics.getMetricRegistry();

    String hostname = "unknown";
    try {
      hostname = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      logger.log(Level.WARNING, "Cannot get hostname", e);
    }

    final String pid = new File("/proc/self").getCanonicalFile().getName();

    Map<LabelKey, LabelValue> labels = new HashMap<>();
    labels.put(
            LabelKey.create("prober_task", "Prober task identifier"),
            LabelValue.create(args.metricTaskPrefix + pid + "@" + hostname)
    );
    if (!args.metricProbeName.isEmpty()) {
      labels.put(
              LabelKey.create("probe_name", "Prober name"),
              LabelValue.create(args.metricProbeName)
      );
    }

    final DerivedLongGauge presenceMetric =
            metricRegistry.addDerivedLongGauge(args.metricName + "/presence", MetricOptions.builder()
                    .setDescription("Number of prober instances running")
                    .setUnit("1")
                    .setConstantLabels(labels)
                    .build());

    final List<LabelKey> errorKeys = new ArrayList<>();
    errorKeys.add(LabelKey.create("code", "The gRPC error code"));
    errorKeys.add(LabelKey.create("sawGfe", "Whether Google load balancer response headers were present"));

    final DerivedLongCumulative errorsMetric =
            metricRegistry.addDerivedLongCumulative(args.metricName + "/error-count", MetricOptions.builder()
                    .setDescription("Number of RPC errors")
                    .setUnit("1")
                    .setConstantLabels(labels)
                    .setLabelKeys(errorKeys)
                    .build());

    final List<LabelValue> emptyValues = new ArrayList<>();
    presenceMetric.removeTimeSeries(emptyValues);
    presenceMetric.createTimeSeries(emptyValues, this, echoClient -> 1L);

    final List<String> reportedStatuses = new ArrayList<>();
    reportedStatuses.add(Status.Code.DEADLINE_EXCEEDED.toString());
    reportedStatuses.add(Status.Code.UNAVAILABLE.toString());
    reportedStatuses.add(Status.Code.CANCELLED.toString());
    reportedStatuses.add(Status.Code.ABORTED.toString());
    reportedStatuses.add(Status.Code.INTERNAL.toString());
    reportedStatuses.add(OTHER_STATUS);

    for (String status : reportedStatuses) {
      errorCounts.putIfAbsent(status, new ConcurrentHashMap<>());
      errorCounts.get(status).putIfAbsent(false, new AtomicLong());
      errorCounts.get(status).putIfAbsent(true, new AtomicLong());

      for (boolean sawGfe : Arrays.asList(false, true)) {
        final List<LabelValue> errorValues = new ArrayList<>();
        errorValues.add(LabelValue.create(status));
        errorValues.add(LabelValue.create(String.valueOf(sawGfe)));

        errorsMetric.removeTimeSeries(errorValues);
        errorsMetric.createTimeSeries(errorValues, this, echoClient -> echoClient.reportRpcErrors(status, sawGfe));
      }
    }
    try {
      // Enable OpenCensus exporters to export metrics to Stackdriver Monitoring.
      // Exporters use Application Default Credentials to authenticate.
      // See https://developers.google.com/identity/protocols/application-default-credentials
      // for more details.
      // The minimum reporting period for Stackdriver is 1 minute.
      StackdriverStatsExporter.createAndRegister(StackdriverStatsConfiguration.builder()
                      .setMonitoredResource(MonitoredResource.newBuilder().setType("global").build())
              .build());
      logger.log(Level.INFO, "Stackdriver metrics enabled!");
    } catch (IOException e) {
      logger.log(Level.SEVERE, "StackdriverStatsExporter.createAndRegister()", e);
      throw e;
    }
  }

  private NettyChannelBuilder getChannelBuilder() throws SSLException {
    NettyChannelBuilder builder = NettyChannelBuilder.forTarget(args.host + ":" +args.port)
        .sslContext(GrpcSslContexts.forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .build());
    if (!args.overrideService.isEmpty()) {
      builder.overrideAuthority(args.overrideService);
    }

    if (args.insecure) {
      builder.usePlaintext();
    }

    return builder;
  }

  private static void watchStateChange(ManagedChannel channel, ConnectivityState currentState, int i) {
    channel.notifyWhenStateChanged(currentState, () -> {
      ConnectivityState newState = channel.getState(false);
      logger.fine(String.format("Channel %d state changed: %s -> %s", i, currentState, newState));
      watchStateChange(channel, newState, i);
    });
  }

  private Channel createChannel(int i) throws SSLException {
    ManagedChannel managedChannel = getChannelBuilder().build();
    ConnectivityState currentState = managedChannel.getState(false);
    watchStateChange(managedChannel, currentState, i);
    channels[i] = managedChannel;
    Channel channel = managedChannel;
    if (HeaderClientInterceptor.needsInterception(args)) {
      ClientInterceptor interceptor = new HeaderClientInterceptor(args);
      channel = ClientInterceptors.intercept(channel, interceptor);
    }
    if (MetricsClientInterceptor.needsInterception(args)) {
      ClientInterceptor interceptor = new MetricsClientInterceptor(this);
      channel = ClientInterceptors.intercept(channel, interceptor);
    }
    if (i == 0) {
      blockingChannelCreated = System.currentTimeMillis();
    }
    return channel;
  }

  private void reCreateBlockingStub() throws SSLException {
    if (!channels[0].isShutdown()) {
      channels[0].shutdown();
    }
    Channel channel = createChannel(0);
    blockingStub = GrpcCloudapiGrpc.newBlockingStub(channel);
    if (!args.reqComp.isEmpty()) {
      blockingStub = blockingStub.withCompression(args.reqComp);
    }
  }

  public void shutdown() throws InterruptedException {
    for (ManagedChannel channel : channels) {
      channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  private GrpcCloudapiStub getNextAsyncStub() {
    GrpcCloudapiStub next = asyncStubs[rr];
    rr = (rr + 1) % asyncStubs.length;
    return next;
  }


  public void asyncEcho(int id, CountDownLatch latch, Histogram histogram) {
    EchoWithResponseSizeRequest request = EchoWithResponseSizeRequest.newBuilder()
        .setEchoMsg(generatePayload(args.reqSize * 1024))
        .setResponseSize(args.resSize)
        .build();
    GrpcCloudapiStub stub = getNextAsyncStub();
    stub.withDeadlineAfter(args.timeout, TimeUnit.MILLISECONDS).echoWithResponseSize(
        request,
        new StreamObserver<EchoResponse>() {
          long start = System.currentTimeMillis();

          @Override
          public void onNext(EchoResponse value) {}

          @Override
          public void onError(Throwable t) {
            if (latch != null) latch.countDown();
            Status status = Status.fromThrowable(t);
            long elapsed = System.currentTimeMillis() - start;
            logger.warning(String.format("Encountered an error in %dth echo RPC (startTime: %s, elapsed: %dms). Status: %s", id, new Timestamp(start), elapsed, status));
            t.printStackTrace();
          }

          @Override
          public void onCompleted() {
            long now = System.currentTimeMillis();
            if (histogram != null) histogram.recordValue(now - start);
            if (latch != null) latch.countDown();
            //logger.info(String.format("%dth echo RPC succeeded. Start time: %s. Requests left: %d", id, new Timestamp(start), latch.getCount()));
          }
        });
  }

  private String generatePayload(int numBytes) {
    StringBuilder sb = new StringBuilder(numBytes);
    for (int i = 0; i < numBytes; i++) {
      sb.append('x');
    }
    return sb.toString();
  }

  void streamingEcho() {
    long start = 0;
    try {
      StreamEchoRequest request = StreamEchoRequest.newBuilder()
          .setMessageCount(args.numRpcs)
          .setMessageInterval(Math.max(args.interval, STREAMING_MIN_INTERVAL))
          .build();
      start = System.currentTimeMillis();
      Iterator<EchoResponse> iter = blockingStub.echoStream(request);
      for (long counter = 1; iter.hasNext(); ++counter) {
        EchoResponse resp = iter.next();
        long elapsed = System.currentTimeMillis() - start;
        logger.info(String.format("Got %d EchoResponse after %dms.", counter, elapsed));
      }
    } catch (StatusRuntimeException e) {
      long elapsed = System.currentTimeMillis() - start;
      logger.warning(String.format("EchoStream RPC failed after %dms: %s", elapsed, e.getStatus()));
      e.printStackTrace();
    }
  }

  void blockingEcho(Histogram histogram) throws SSLException {
    long start = 0;
    try {
      if (args.resType == 0) {
        EchoWithResponseSizeRequest request = EchoWithResponseSizeRequest.newBuilder()
            .setEchoMsg(generatePayload(args.reqSize * 1024))
            .setResponseSize(args.resSize)
            .build();
        start = System.currentTimeMillis();
        if (args.recreateChannelSeconds >= 0 && blockingChannelCreated < start - args.recreateChannelSeconds * 1000) {
          reCreateBlockingStub();
        }
        blockingStub
            .withDeadlineAfter(args.timeout, TimeUnit.MILLISECONDS)
            .echoWithResponseSize(request);
      } else {
        BatchEchoRequest request = BatchEchoRequest.newBuilder()
            .setEchoMsg(generatePayload(args.reqSize * 1024))
            .setResponseType(args.resType)
            .build();
        start = System.currentTimeMillis();
        BatchEchoResponse response = blockingStub
            .withDeadlineAfter(args.timeout, TimeUnit.MILLISECONDS)
            .batchEcho(request);
        List<Integer> sizeList = new ArrayList<>();
        for (EchoResponse r : response.getEchoResponsesList()) {
          sizeList.add(r.getSerializedSize());
        }
        logger.info("Got EchoResponsesList with sizes: " + Arrays.toString(sizeList.toArray()));
      }
      long cost = System.currentTimeMillis() - start;
      if (histogram != null) histogram.recordValue(cost);
      if (args.numRpcs == 0) {
        logger.info(String.format("RPC succeeded after %d ms.", cost));
      }
    } catch (StatusRuntimeException e) {
      long elapsed = System.currentTimeMillis() - start;
      logger.warning(String.format("RPC failed after %d ms: %s", elapsed, e.getStatus()));
      e.printStackTrace();
    }
  }

  public void echo(int id, CountDownLatch latch, Histogram histogram) throws SSLException {
    if (args.stream) {
      streamingEcho();
      return;
    }
    if (args.async) {
      asyncEcho(id, latch, histogram);
      //logger.info("Async request: sent rpc#: " + rpcIndex);
    } else {
      blockingEcho(histogram);
    }
    //logger.info("Sync request: sent rpc#: " + rpcIndex);
  }

  private String statusToMetricLabel(Status status) {
    switch (status.getCode()) {
      case ABORTED:
      case CANCELLED:
      case DEADLINE_EXCEEDED:
      case UNAVAILABLE:
      case INTERNAL:
        return status.getCode().toString();
      default:
        return OTHER_STATUS;
    }
  }

  void registerRpcError(Status status, boolean sawGfe) {
    errorCounts.get(statusToMetricLabel(status)).get(sawGfe).incrementAndGet();
  }

  private long reportRpcErrors(String status, boolean sawGfe) {
    return errorCounts.get(status).get(sawGfe).get();
  }
}
