package io.grpc.gcs;

import static io.grpc.gcs.Args.METHOD_RANDOM;
import static io.grpc.gcs.Args.METHOD_READ;
import static io.grpc.gcs.Args.METHOD_WRITE;
import static io.grpc.gcs.Args.DEFAULT_HOST;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.google.storage.v1.ChecksummedData;
import com.google.google.storage.v1.GetObjectMediaRequest;
import com.google.google.storage.v1.GetObjectMediaResponse;
import com.google.google.storage.v1.InsertObjectRequest;
import com.google.google.storage.v1.InsertObjectSpec;
import com.google.google.storage.v1.Object;
import com.google.google.storage.v1.ServiceConstants.Values;
import com.google.google.storage.v1.StorageGrpc;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.alts.ComputeEngineChannelBuilder;
import io.grpc.auth.MoreCallCredentials;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class GrpcClient {
  private static final Logger logger = Logger.getLogger(GrpcClient.class.getName());

  private ManagedChannel[] channels;
  private Args args;
  private GoogleCredentials creds;

  private static final String SCOPE = "https://www.googleapis.com/auth/cloud-platform";

  public GrpcClient(Args args) {
    this.args = args;
    try {
      this.creds = GoogleCredentials.getApplicationDefault();
    } catch (IOException e) {
      e.printStackTrace();
      return;
    }

    ManagedChannelBuilder channelBuilder;
    if (args.dp) {
      ComputeEngineChannelBuilder gceChannelBuilder = ComputeEngineChannelBuilder.forAddress(args.host, args.port);

      ImmutableMap<String, java.lang.Object> pickFirstStrategy =
          ImmutableMap.<String, java.lang.Object>of("pick_first", ImmutableMap.of());
      ImmutableMap<String, java.lang.Object> childPolicy =
          ImmutableMap.<String, java.lang.Object>of("childPolicy", ImmutableList.of(pickFirstStrategy));
      ImmutableMap<String, java.lang.Object> grpcLbPolicy =
          ImmutableMap.<String, java.lang.Object>of("grpclb", childPolicy);
      ImmutableMap<String, java.lang.Object> loadBalancingConfig =
          ImmutableMap.<String, java.lang.Object>of("loadBalancingConfig", ImmutableList.of(grpcLbPolicy));

      gceChannelBuilder.defaultServiceConfig(loadBalancingConfig);

      if (args.flowControlWindow > 0) {
        Field delegateField = null;
        try {
          delegateField = ComputeEngineChannelBuilder.class.getDeclaredField("delegate");
          delegateField.setAccessible(true);

          NettyChannelBuilder delegateBuilder = (NettyChannelBuilder) delegateField.get(gceChannelBuilder);
          delegateBuilder.flowControlWindow(args.flowControlWindow);
        } catch (NoSuchFieldException | IllegalAccessException e) {
          e.printStackTrace();
          logger.warning("Failed to set flow-control window, will use default value.");
        }
      }
      channelBuilder = gceChannelBuilder;
    } else {
      NettyChannelBuilder nettyChannelBuilder = NettyChannelBuilder.forAddress(args.host, args.port);
      if (args.flowControlWindow > 0) {
        nettyChannelBuilder.flowControlWindow(args.flowControlWindow);
      }
      channelBuilder = nettyChannelBuilder;
    }

    // Create the same number of channels as the number of threads.
    this.channels = new ManagedChannel[args.threads];
    for (int i = 0; i < args.threads; i++) {
      channels[i] = channelBuilder.build();
    }
  }

  public void startCalls(List<Long> results) throws InterruptedException {
    ManagedChannel channel = this.channels[0];
    if (args.threads == 1) {
      try {
        switch (args.method) {
          case METHOD_READ:
            makeMediaRequest(channel, results);
            break;
          case METHOD_RANDOM:
            makeRandomMediaRequest(channel, results);
            break;
          case METHOD_WRITE:
            makeInsertRequest(channel, results, 0);
            break;
          default:
            logger.warning("Please provide valid methods with --method");
        }
      } finally {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
      }
    } else {
      ThreadPoolExecutor threadPoolExecutor =
          (ThreadPoolExecutor) Executors.newFixedThreadPool(args.threads);
      try {
        switch (args.method) {
          case METHOD_READ:
            for (int i = 0; i < args.threads; i++) {
              int finalI = i;
              Runnable task = () -> makeMediaRequest(this.channels[finalI], results);
              threadPoolExecutor.execute(task);
            }
            break;
          case METHOD_RANDOM:
            for (int i = 0; i < args.threads; i++) {
              int finalI = i;
              Runnable task = () -> makeRandomMediaRequest(this.channels[finalI], results);
              threadPoolExecutor.execute(task);
            }
            break;
          case METHOD_WRITE:
            for (int i = 0; i < args.threads; i++) {
              int finalI = i;
              Runnable task = () -> {
                try {
                  makeInsertRequest(this.channels[finalI], results, finalI);
                } catch (InterruptedException e) {
                  e.printStackTrace();
                }
              };
              threadPoolExecutor.execute(task);
            }
            break;
          default:
            logger.warning("Please provide valid methods with --method");
        }
      } finally {
        threadPoolExecutor.shutdown();
        if (!threadPoolExecutor.awaitTermination(30, TimeUnit.MINUTES)) {
          threadPoolExecutor.shutdownNow();
        }
      }
    }
  }

  private void makeMediaRequest(ManagedChannel channel, List<Long> results) {
    StorageGrpc.StorageBlockingStub blockingStub =
        StorageGrpc.newBlockingStub(channel).withCallCredentials(
            MoreCallCredentials.from(creds.createScoped(SCOPE)));
      if (args.host.equals(Args.DEFAULT_HOST)) {
        blockingStub = blockingStub.withCallCredentials(
            MoreCallCredentials.from(creds.createScoped(SCOPE)));
      }

    GetObjectMediaRequest mediaRequest =
        GetObjectMediaRequest.newBuilder().setBucket(args.bkt).setObject(args.obj).build();

    for (int i = 0; i < args.calls; i++) {
      long start = System.currentTimeMillis();
      Iterator<GetObjectMediaResponse> resIterator = blockingStub.getObjectMedia(mediaRequest);
      while (resIterator.hasNext()) {
        GetObjectMediaResponse res = resIterator.next();
      }
      long dur = System.currentTimeMillis() - start;
      results.add(dur);
    }
  }

  private void makeRandomMediaRequest(ManagedChannel channel, List<Long> results) {
    StorageGrpc.StorageBlockingStub blockingStub =
        StorageGrpc.newBlockingStub(channel).withCallCredentials(
            MoreCallCredentials.from(creds.createScoped(SCOPE)));

    GetObjectMediaRequest.Builder reqBuilder =
        GetObjectMediaRequest.newBuilder().setBucket(args.bkt).setObject(args.obj);
    Random r = new Random();

    long buffSize = args.buffSize * 1024;

    for (int i = 0; i < args.calls; i++) {
      long offset = (long) r.nextInt(args.size - args.buffSize) * 1024;
      reqBuilder.setReadOffset(offset);
      reqBuilder.setReadLimit(buffSize);
      GetObjectMediaRequest req = reqBuilder.build();

      long start = System.currentTimeMillis();
      Iterator<GetObjectMediaResponse> resIterator = blockingStub.getObjectMedia(req);
      int itr = 0;
      long bytesRead = 0;
      while (resIterator.hasNext()) {
        itr++;
        GetObjectMediaResponse res = resIterator.next();
        bytesRead += res.getChecksummedData().getSerializedSize();
        //logger.info("result: " + res.getChecksummedData());
      }
      long dur = System.currentTimeMillis() - start;
      logger.info("time cost for getObjectMedia: " + dur + "ms");
      logger.info("total iterations: " + itr);
      logger.info("start pos: " + offset + ", read lenth: " + buffSize + ", total KB read: " + bytesRead / 1024);
      results.add(dur);
    }
  }

  private void makeInsertRequest(ManagedChannel channel, List<Long> results, int idx) throws InterruptedException {
    StorageGrpc.StorageStub asyncStub = StorageGrpc.newStub(channel).withCallCredentials(
        MoreCallCredentials.from(creds.createScoped(SCOPE)));
    if (args.host.equals(Args.DEFAULT_HOST)) {
      asyncStub = asyncStub.withCallCredentials(
          MoreCallCredentials.from(creds.createScoped(SCOPE)));
    }

    int totalBytes = args.size * 1024;
    byte[] data = new byte[totalBytes];
    for (int i = 0; i < args.calls; i++) {
      int offset = 0;
      boolean isFirst = true;
      boolean isLast = false;

      final CountDownLatch finishLatch = new CountDownLatch(1);
      StreamObserver<Object> responseObserver = new StreamObserver<Object>() {
        long start = System.currentTimeMillis();

        @Override
        public void onNext(Object value) {
        }

        @Override
        public void onError(Throwable t) {
          logger.warning("InsertObject failed with: " + Status.fromThrowable(t));
          finishLatch.countDown();
        }

        @Override
        public void onCompleted() {
          finishLatch.countDown();
          long dur = System.currentTimeMillis() - start;
          results.add(dur);
        }
      };

      StreamObserver<InsertObjectRequest> requestObserver = asyncStub.insertObject(responseObserver);

      while (offset < totalBytes) {
        int add;
        if (offset + Values.MAX_WRITE_CHUNK_BYTES_VALUE <= totalBytes) {
          add =  Values.MAX_WRITE_CHUNK_BYTES_VALUE;
        } else {
          add = totalBytes - offset;
        }
        if (offset + add == totalBytes) {
          isLast = true;
        }

        InsertObjectRequest req = getInsertRequest(isFirst, isLast, offset, ByteString.copyFrom(data, offset, add), idx);
        requestObserver.onNext(req);
        if (finishLatch.getCount() == 0) {
          logger.warning("Stream completed before finishing sending requests");
          return;
        }

        offset += add;
      }
      requestObserver.onCompleted();

      if (!finishLatch.await(20, TimeUnit.MINUTES)) {
        logger.warning("insertObject cannot finish within 20 minutes");
      }

      Thread.sleep(1000); // Avoid request limit for updating a single object
    }

  }

  private InsertObjectRequest getInsertRequest(boolean first, boolean last, int offset, ByteString bytes, int idx) {
    InsertObjectRequest.Builder builder = InsertObjectRequest.newBuilder();
    if (first) {
      builder.setInsertObjectSpec(
          InsertObjectSpec.newBuilder().setResource(
              Object.newBuilder().setBucket(args.bkt).setName(args.obj + "_" + idx)
          ).build()
      );
    }

    builder.setChecksummedData(ChecksummedData.newBuilder().setContent(bytes).build());
    builder.setWriteOffset(offset);
    if (last) {
      builder.setFinishWrite(true);
    }
    return builder.build();
  }
}
