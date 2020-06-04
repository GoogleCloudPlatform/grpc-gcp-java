package io.grpc.echo;

import io.grpc.Channel;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.echo.Echo.EchoResponse;
import io.grpc.echo.Echo.EchoWithResponseSizeRequest;
import io.grpc.echo.GrpcCloudapiGrpc.GrpcCloudapiBlockingStub;
import io.grpc.echo.GrpcCloudapiGrpc.GrpcCloudapiStub;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.grpc.stub.StreamObserver;
import java.sql.Timestamp;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLException;
import org.HdrHistogram.Histogram;

public class EchoClient {
  private static final int DEADLINE_MINUTES = 60;
  private static final Logger logger = Logger.getLogger(EchoClient.class.getName());

  //  private final ManagedChannel originalChannel;
  private final ManagedChannel[] channels;

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
      NettyChannelBuilder builder = NettyChannelBuilder.forTarget(args.host + ":" +args.port)
          .sslContext(GrpcSslContexts.forClient()
              .trustManager(InsecureTrustManagerFactory.INSTANCE)
              .build());
      if (!args.overrideService.isEmpty()) {
        builder.overrideAuthority(args.overrideService);
      }

      if (args.insecure) {
        builder = builder.usePlaintext();
      }
      channels[i] = builder.build();

      Channel channel;
      if (args.header || !args.resComp.isEmpty()) {
        ClientInterceptor interceptor = new HeaderClientInterceptor(args);
        channel = ClientInterceptors.intercept(channels[i], interceptor);
      } else {
        channel = channels[i];
      }

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


  public void asyncEcho(int id, EchoWithResponseSizeRequest request, CountDownLatch latch,
      Histogram histogram) {
    GrpcCloudapiStub stub = getNextAsyncStub();
    stub.withDeadlineAfter(DEADLINE_MINUTES, TimeUnit.MINUTES).echoWithResponseSize(
        request,
        new StreamObserver<EchoResponse>() {
          long start = System.currentTimeMillis();

          @Override
          public void onNext(EchoResponse value) {}

          @Override
          public void onError(Throwable t) {
            if (latch != null) latch.countDown();
            Status status = Status.fromThrowable(t);
            logger.warning(String.format("Encountered an error in %dth echo RPC (startTime: %s). Status: %s", id, new Timestamp(start), status));
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

  void blockingEcho(EchoWithResponseSizeRequest request, Histogram histogram) {
    try {
      long start = System.currentTimeMillis();
      blockingStub.echoWithResponseSize(request);
      long cost = System.currentTimeMillis() - start;
      if (histogram != null) histogram.recordValue(cost);
    } catch (StatusRuntimeException e) {
      logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
      e.printStackTrace();
    }
  }

  public void echo(int id, EchoWithResponseSizeRequest request, CountDownLatch latch, Histogram histogram) {
    if (args.async) {
      asyncEcho(id, request, latch, histogram);
      //logger.info("Async request: sent rpc#: " + rpcIndex);
    } else {
      blockingEcho(request, histogram);
    }
    //logger.info("Sync request: sent rpc#: " + rpcIndex);
  }
}
