package io.grpc.echo;

import io.grpc.Channel;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.echo.Echo.EchoResponse;
import io.grpc.echo.Echo.EchoWithResponseSizeRequest;
import io.grpc.echo.GrpcCloudapiGrpc.GrpcCloudapiBlockingStub;
import io.grpc.echo.GrpcCloudapiGrpc.GrpcCloudapiStub;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.grpc.stub.StreamObserver;
import io.opencensus.common.Scope;
import io.opencensus.trace.Tracer;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.net.ssl.SSLException;

public class EchoClient {
  private static final LocalLogger logger = LocalLogger.getLogger(EchoClient.class.getName());

  //  private final ManagedChannel originalChannel;
  private final ManagedChannel[] channels;

  private final GrpcCloudapiBlockingStub blockingStub;
  private final GrpcCloudapiStub[] asyncStubs;

  private int rr;

  public EchoClient(Args argObj) throws SSLException {

    channels = new ManagedChannel[argObj.numChannels];
    asyncStubs = new GrpcCloudapiStub[argObj.numChannels];
    rr = 0;

    for (int i = 0; i < argObj.numChannels; i++) {

      //      SslContextBuilder sslContextBuilder = GrpcSslContexts.forClient();
      //      SslContext sslContext =
      // sslContextBuilder.trustManager(TlsTesting.loadCert("CAcert.pem")).build();
      //
      //      ManagedChannelBuilder builder =
      //          NettyChannelBuilder.forTarget(host + ":" + port)
      //              .overrideAuthority("test_cert_2")
      //              .sslContext(sslContext);

      // ManagedChannelBuilder builder = ManagedChannelBuilder.forAddress(argObj.host, argObj.port);
      NettyChannelBuilder builder = NettyChannelBuilder.forTarget(argObj.host + ":" +argObj.port)
          .sslContext(GrpcSslContexts.forClient()
              .trustManager(InsecureTrustManagerFactory.INSTANCE)
              .build());
      if (!argObj.overrideService.isEmpty()) {
        builder.overrideAuthority(argObj.overrideService);
      }

      if (argObj.insecure) {
        builder = builder.usePlaintext();
      }
      channels[i] = builder.build();
      if (!argObj.compression.isEmpty()) {
        asyncStubs[i] = GrpcCloudapiGrpc.newStub(channels[i]).withCompression(argObj.compression);
      } else {
        asyncStubs[i] = GrpcCloudapiGrpc.newStub(channels[i]);
      }
    }

    // blocking stub test only needs one channel.
    ClientInterceptor interceptor = new HeaderClientInterceptor(argObj.cookie, argObj.corp);
    Channel interceptingChannel = ClientInterceptors.intercept(channels[0], interceptor);
    if (!argObj.compression.isEmpty()) {
      blockingStub = GrpcCloudapiGrpc.newBlockingStub(interceptingChannel).withCompression(argObj.compression);
    } else {
      blockingStub = GrpcCloudapiGrpc.newBlockingStub(interceptingChannel);
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


  public void asyncEcho(
      EchoWithResponseSizeRequest request, CountDownLatch latch, List<Long> timeList) {
    GrpcCloudapiStub stub = getNextAsyncStub();
    stub.echoWithResponseSize(
        request,
        new StreamObserver<EchoResponse>() {
          long start = System.currentTimeMillis();

          @Override
          public void onNext(EchoResponse value) {}

          @Override
          public void onError(Throwable t) {
            latch.countDown();
            Status status = Status.fromThrowable(t);
            logger.warning("Encountered an error in echo RPC. Status: " + status);
            t.printStackTrace();
          }

          @Override
          public void onCompleted() {
            long now = System.currentTimeMillis();
            if (timeList != null) {
              timeList.add(now - start);
            }
            latch.countDown();
            //logger.info("** Requests left: " + latch.getCount() + ", current rr: " + rr);
          }
        });
  }

  void doSingleCall(EchoWithResponseSizeRequest request, List<Long> timeList) {
    long start = System.currentTimeMillis();
    blockingStub.echoWithResponseSize(request);
    if (timeList != null) {
      timeList.add(System.currentTimeMillis() - start);
    }
  }

  public void echo(EchoWithResponseSizeRequest request, CountDownLatch latch,
      Tracer tracer, List<Long> timeList, int rpcIndex) {
    if (latch != null) {
      asyncEcho(request, latch, timeList);
      //logger.info("Async request: sent rpc#: " + rpcIndex);
      return;
    }

    if (tracer != null) {
      try (Scope scope = tracer.spanBuilder("echo_java").startScopedSpan()) {
        doSingleCall(request, timeList);
      }
    } else {
      doSingleCall(request, timeList);
    }
    //logger.info("Sync request: sent rpc#: " + rpcIndex);
  }
}
