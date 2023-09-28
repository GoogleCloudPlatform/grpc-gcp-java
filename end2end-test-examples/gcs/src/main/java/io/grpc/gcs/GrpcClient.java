package io.grpc.gcs;

import static io.grpc.gcs.Args.METHOD_RANDOM;
import static io.grpc.gcs.Args.METHOD_READ;
import static io.grpc.gcs.Args.METHOD_WRITE;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import com.google.storage.v2.ChecksummedData;
import com.google.storage.v2.Object;
import com.google.storage.v2.ReadObjectRequest;
import com.google.storage.v2.ReadObjectResponse;
import com.google.storage.v2.ServiceConstants.Values;
import com.google.storage.v2.StorageGrpc;
import com.google.storage.v2.StorageGrpc.StorageBlockingStub;
import com.google.storage.v2.WriteObjectRequest;
import com.google.storage.v2.WriteObjectResponse;
import com.google.storage.v2.WriteObjectSpec;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.alts.ComputeEngineChannelBuilder;
import io.grpc.alts.GoogleDefaultChannelCredentials;
import io.grpc.auth.MoreCallCredentials;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class GrpcClient {
  private static final Logger logger = Logger.getLogger(GrpcClient.class.getName());

  // ZeroCopy version of GetObjectMedia Method
  private static final ZeroCopyMessageMarshaller ReadObjectResponseMarshaller =
      new ZeroCopyMessageMarshaller(ReadObjectResponse.getDefaultInstance());
  private static final MethodDescriptor<ReadObjectRequest, ReadObjectResponse> readObjectMethod =
      StorageGrpc.getReadObjectMethod().toBuilder()
          .setResponseMarshaller(ReadObjectResponseMarshaller)
          .build();
  private final boolean useZeroCopy;

  private Args args;
  private ObjectResolver objectResolver;
  private ManagedChannel[] channels;
  private GoogleCredentials creds;

  private static final String SCOPE = "https://www.googleapis.com/auth/cloud-platform";
  private static final String V2_BUCKET_NAME_PREFIX = "projects/_/buckets/";
  static final Metadata.Key<String> X_GOOG_REQUEST_PARAMS_KEY =
      Metadata.Key.of("x-goog-request-params", Metadata.ASCII_STRING_MARSHALLER);

  private static String toV2BucketName(String v1BucketName) {
    return V2_BUCKET_NAME_PREFIX + v1BucketName;
  }

  private static Metadata addMetadataForBucketName(Metadata metadata, String v1BucketName) {
    metadata.put(
        X_GOOG_REQUEST_PARAMS_KEY, String.format("bucket=%s", toV2BucketName(v1BucketName)));
    return metadata;
  }

  public GrpcClient(Args args) throws IOException {
    this.args = args;
    this.objectResolver = new ObjectResolver(args.obj, args.objFormat, args.objStart, args.objStop);
    if (args.access_token.equals("")) {
      this.creds = GoogleCredentials.getApplicationDefault().createScoped(SCOPE);
    } else if (args.access_token.equals("-")) {
      this.creds = null;
    } else {
      logger.warning("Please provide valid --access_token");
    }

    ManagedChannelBuilder channelBuilder;
    if (args.td) {
      String target = "google-c2p:///" + args.host;
      channelBuilder =
          Grpc.newChannelBuilder(target, GoogleDefaultChannelCredentials.newBuilder().build());
    } else if (args.dp) {
      ComputeEngineChannelBuilder gceChannelBuilder =
          ComputeEngineChannelBuilder.forAddress(args.host, args.port);

      String policy = args.rr ? "round_robin" : "pick_first";
      ImmutableMap<String, java.lang.Object> policyStrategy =
          ImmutableMap.<String, java.lang.Object>of(policy, ImmutableMap.of());
      ImmutableMap<String, java.lang.Object> childPolicy =
          ImmutableMap.<String, java.lang.Object>of(
              "childPolicy", ImmutableList.of(policyStrategy));
      ImmutableMap<String, java.lang.Object> grpcLbPolicy =
          ImmutableMap.<String, java.lang.Object>of("grpclb", childPolicy);
      ImmutableMap<String, java.lang.Object> loadBalancingConfig =
          ImmutableMap.<String, java.lang.Object>of(
              "loadBalancingConfig", ImmutableList.of(grpcLbPolicy));
      gceChannelBuilder.defaultServiceConfig(loadBalancingConfig);

      if (args.flowControlWindow > 0) {
        Field delegateField = null;
        try {
          delegateField = ComputeEngineChannelBuilder.class.getDeclaredField("delegate");
          delegateField.setAccessible(true);

          NettyChannelBuilder delegateBuilder =
              (NettyChannelBuilder) delegateField.get(gceChannelBuilder);
          delegateBuilder.flowControlWindow(args.flowControlWindow);
        } catch (NoSuchFieldException | IllegalAccessException e) {
          e.printStackTrace();
          logger.warning("Failed to set flow-control window, will use default value.");
        }
      }
      channelBuilder = gceChannelBuilder;
    } else {
      NettyChannelBuilder nettyChannelBuilder =
          NettyChannelBuilder.forAddress(args.host, args.port);
      if (args.flowControlWindow > 0) {
        nettyChannelBuilder.flowControlWindow(args.flowControlWindow);
      }
      channelBuilder = nettyChannelBuilder;
    }

    // Create the same number of channels as the number of threads.
    this.channels = new ManagedChannel[args.threads];
    if (args.rr) {
      // For round-robin, all threads share the same channel.
      ManagedChannel singleChannel = channelBuilder.build();
      for (int i = 0; i < args.threads; i++) {
        channels[i] = singleChannel;
      }
    } else {
      // For pick-first, each thread has its own unique channel.
      for (int i = 0; i < args.threads; i++) {
        channels[i] = channelBuilder.build();
      }
    }

    if (args.zeroCopy == 0) {
      useZeroCopy = ZeroCopyReadinessChecker.isReady();
    } else {
      useZeroCopy = args.zeroCopy > 0;
    }
    logger.info("useZeroCopy: " + useZeroCopy);
  }

  public void startCalls(ResultTable results) throws InterruptedException {
    ManagedChannel channel = this.channels[0];
    if (args.threads == 1) {
      try {
        switch (args.method) {
          case METHOD_READ:
            makeReadObjectRequest(channel, results, /* threadId= */ 1);
            break;
          case METHOD_RANDOM:
            makeRandomReadRequest(channel, results, /* threadId= */ 1);
            break;
          case METHOD_WRITE:
            makeInsertRequest(channel, results, /* threadId= */ 1);
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
              Runnable task =
                  () -> makeReadObjectRequest(this.channels[finalI], results, finalI + 1);
              threadPoolExecutor.execute(task);
            }
            break;
          case METHOD_RANDOM:
            for (int i = 0; i < args.threads; i++) {
              int finalI = i;
              Runnable task =
                  () -> makeRandomReadRequest(this.channels[finalI], results, finalI + 1);
              threadPoolExecutor.execute(task);
            }
            break;
          case METHOD_WRITE:
            for (int i = 0; i < args.threads; i++) {
              int finalI = i;
              Runnable task =
                  () -> {
                    try {
                      makeInsertRequest(this.channels[finalI], results, finalI + 1);
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

  private void makeReadObjectRequest(ManagedChannel channel, ResultTable results, int threadId) {
    StorageGrpc.StorageBlockingStub blockingStub = StorageGrpc.newBlockingStub(channel);
    if (creds != null) {
      blockingStub = blockingStub.withCallCredentials(MoreCallCredentials.from(creds));
    }
    // Metadata for RLS
    blockingStub =
        blockingStub.withInterceptors(
            MetadataUtils.newAttachHeadersInterceptor(
                addMetadataForBucketName(new Metadata(), args.bkt)));

    byte[] scratch = new byte[4 * 1024 * 1024];
    for (int i = 0; i < args.calls; i++) {
      String object = objectResolver.Resolve(threadId, i);
      ReadObjectRequest readRequest =
          ReadObjectRequest.newBuilder()
              .setBucket(toV2BucketName(args.bkt))
              .setObject(object)
              .build();

      long start = System.currentTimeMillis();
      long totalBytes = 0;
      Iterator<ReadObjectResponse> resIterator;
      if (useZeroCopy) {
        resIterator =
            io.grpc.stub.ClientCalls.blockingServerStreamingCall(
                blockingStub.getChannel(),
                readObjectMethod,
                blockingStub.getCallOptions(),
                readRequest);
      } else {
        resIterator = blockingStub.readObject(readRequest);
      }
      try {
        while (true) {
          ReadObjectResponse res = resIterator.next();
          // When zero-copy mashaller is used, the stream that backs ReadObjectResponse
          // should be closed when the mssage is no longed needed so that all buffers in
          // the
          // stream can be reclaimed. If zero-copy is not used, stream will be null.
          InputStream stream = ReadObjectResponseMarshaller.popStream(res);
          try {
            // Just copy to scratch memory to ensure its data is consumed.
            ByteString content = res.getChecksummedData().getContent();
            totalBytes += content.size();
            content.copyTo(scratch, 0);
          } finally {
            if (stream != null) {
              try {
                stream.close();
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            }
          }
        }
      } catch (NoSuchElementException e) {
      }
      long dur = System.currentTimeMillis() - start;
      results.reportResult(args.bkt, object, totalBytes, dur);
    }
  }

  private void makeRandomReadRequest(ManagedChannel channel, ResultTable results, int threadId) {
    StorageBlockingStub blockingStub = StorageGrpc.newBlockingStub(channel);
    if (creds != null) {
      blockingStub = blockingStub.withCallCredentials(MoreCallCredentials.from(creds));
    }
    // Metadata for RLS
    blockingStub =
        blockingStub.withInterceptors(
            MetadataUtils.newAttachHeadersInterceptor(
                addMetadataForBucketName(new Metadata(), args.bkt)));

    String object = objectResolver.Resolve(threadId, /* objectId= */ 0);
    ReadObjectRequest.Builder reqBuilder =
        ReadObjectRequest.newBuilder().setBucket(toV2BucketName(args.bkt)).setObject(object);
    Random r = new Random();

    long buffSize = args.buffSize * 1024;
    byte[] scratch = new byte[4 * 1024 * 1024];
    for (int i = 0; i < args.calls; i++) {
      long offset = (long) r.nextInt(args.size - args.buffSize) * 1024;
      reqBuilder.setReadOffset(offset);
      reqBuilder.setReadLimit(buffSize);
      ReadObjectRequest req = reqBuilder.build();

      long start = System.currentTimeMillis();
      Iterator<ReadObjectResponse> resIterator = blockingStub.readObject(req);
      while (resIterator.hasNext()) {
        ReadObjectResponse res = resIterator.next();
        ByteString content = res.getChecksummedData().getContent();
        content.copyTo(scratch, 0);
      }
      long dur = System.currentTimeMillis() - start;
      results.reportResult(args.bkt, object, buffSize, dur);
    }
  }

  private void makeInsertRequest(ManagedChannel channel, ResultTable results, int threadId)
      throws InterruptedException {
    StorageGrpc.StorageStub asyncStub = StorageGrpc.newStub(channel);
    if (creds != null) {
      asyncStub = asyncStub.withCallCredentials(MoreCallCredentials.from(creds));
    }
    // Metadata for RLS
    asyncStub =
        asyncStub.withInterceptors(
            MetadataUtils.newAttachHeadersInterceptor(
                addMetadataForBucketName(new Metadata(), args.bkt)));

    int totalBytes = args.size * 1024;
    byte[] data = new byte[totalBytes];
    for (int i = 0; i < args.calls; i++) {
      String obj = objectResolver.Resolve(threadId, i);

      int offset = 0;
      boolean isFirst = true;
      boolean isLast = false;

      final CountDownLatch finishLatch = new CountDownLatch(1);
      StreamObserver<WriteObjectResponse> responseObserver =
          new StreamObserver<WriteObjectResponse>() {
            long start = System.currentTimeMillis();

            @Override
            public void onNext(WriteObjectResponse value) {}

            @Override
            public void onError(Throwable t) {
              logger.warning("InsertObject failed with: " + Status.fromThrowable(t));
              finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
              long dur = System.currentTimeMillis() - start;
              results.reportResult(args.bkt, obj, totalBytes, dur);
              finishLatch.countDown();
            }
          };

      StreamObserver<WriteObjectRequest> requestObserver = asyncStub.writeObject(responseObserver);

      while (offset < totalBytes) {
        int add;
        if (offset + Values.MAX_WRITE_CHUNK_BYTES_VALUE <= totalBytes) {
          add = Values.MAX_WRITE_CHUNK_BYTES_VALUE;
        } else {
          add = totalBytes - offset;
        }
        if (offset + add == totalBytes) {
          isLast = true;
        }

        WriteObjectRequest req =
            getWriteRequest(isFirst, isLast, offset, ByteString.copyFrom(data, offset, add), obj);
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
    }
  }

  private WriteObjectRequest getWriteRequest(
      boolean first, boolean last, int offset, ByteString bytes, String obj) {
    WriteObjectRequest.Builder builder = WriteObjectRequest.newBuilder();
    if (first) {
      builder.setWriteObjectSpec(
          WriteObjectSpec.newBuilder()
              .setResource(Object.newBuilder().setBucket(toV2BucketName(args.bkt)).setName(obj))
              .build());
    }

    builder.setChecksummedData(ChecksummedData.newBuilder().setContent(bytes).build());
    builder.setWriteOffset(offset);
    if (last) {
      builder.setFinishWrite(true);
    }
    return builder.build();
  }
}
