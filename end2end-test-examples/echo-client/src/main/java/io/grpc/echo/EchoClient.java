package io.grpc.echo;

import io.grpc.Channel;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
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
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.net.ssl.SSLException;
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

  public EchoClient(Args args) throws SSLException {
    this.args = args;

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
}
