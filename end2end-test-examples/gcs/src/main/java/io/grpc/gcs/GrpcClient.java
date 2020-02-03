package io.grpc.gcs;

import com.google.auth.oauth2.GoogleCredentials;
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
import io.grpc.auth.MoreCallCredentials;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.HdrHistogram.Histogram;

public class GrpcClient {
  private static final Logger logger = Logger.getLogger(GrpcClient.class.getName());

  private ManagedChannel channel;
  private StorageGrpc.StorageBlockingStub blockingStub;
  private StorageGrpc.StorageStub asyncStub;
  private Args args;

  private static final String METHOD_GET_MEDIA = "media";
  private static final String METHOD_INSERT = "insert";

  private static final String SCOPE = "https://www.googleapis.com/auth/cloud-platform";

  public GrpcClient(Args args) {
    this.args = args;
    GoogleCredentials creds;
    try {
      creds = GoogleCredentials.getApplicationDefault();
    } catch (IOException e) {
      e.printStackTrace();
      return;
    }
    this.channel = ManagedChannelBuilder.forAddress(args.host, args.port).build();
    this.blockingStub = StorageGrpc.newBlockingStub(channel).withCallCredentials(
        MoreCallCredentials.from(creds.createScoped(SCOPE)));
    this.asyncStub = StorageGrpc.newStub(channel).withCallCredentials(
        MoreCallCredentials.from(creds.createScoped(SCOPE)));
  }

  public void startCalls(Histogram histogram) throws InterruptedException {
    try {
      switch (args.method) {
        case METHOD_GET_MEDIA:
          makeMediaRequest(histogram);
        case METHOD_INSERT:
          makeInsertRequest(histogram);
      }
    } finally {
      channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  private void makeMediaRequest(Histogram histogram) {
    GetObjectMediaRequest mediaRequest =
        GetObjectMediaRequest.newBuilder().setBucket(args.bkt).setObject(args.obj).build();

    for (int i = 0; i < args.calls; i++) {
      long start = System.currentTimeMillis();
      // Object o = blockingStub.getObject(request);
      Iterator<GetObjectMediaResponse> resIterator = blockingStub.getObjectMedia(mediaRequest);
      while (resIterator.hasNext()) {
        GetObjectMediaResponse res = resIterator.next();
        //logger.info("result: " + res.getChecksummedData());
      }
      long dur = System.currentTimeMillis() - start;
      logger.info("time cost for getObjectMedia: " + dur + "ms");
      histogram.recordValue(dur);
    }
  }

  private void makeInsertRequest(Histogram histogram) throws InterruptedException {
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
          logger.info("Got object: " + value.getName());
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
          logger.info("time cost for insertObject: " + dur + "ms");
          histogram.recordValue(dur);
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

        InsertObjectRequest req = getInsertRequest(isFirst, isLast, offset, ByteString.copyFrom(data, offset, add));
        requestObserver.onNext(req);
        if (finishLatch.getCount() == 0) {
          logger.warning("Stream completed before finishing sending requests");
          return;
        }

        offset += add;
      }
      requestObserver.onCompleted();

      if (!finishLatch.await(1, TimeUnit.MINUTES)) {
        logger.warning("insertObject cannot finish within 1 minutes");
      }
    }

  }

  private InsertObjectRequest getInsertRequest(boolean first, boolean last, int offset, ByteString bytes) {
    InsertObjectRequest.Builder builder = InsertObjectRequest.newBuilder();
    if (first) {
      builder.setInsertObjectSpec(
          InsertObjectSpec.newBuilder().setResource(
              Object.newBuilder().setBucket(args.bkt).setName(args.obj)
          ).build()
      );
    }

    //Hasher hasher = Hashing.crc32c().newHasher();
    //for (ByteBuffer buffer : data.asReadOnlyByteBufferList()) {
    //  hasher.putBytes(buffer);
    //}
    //int checksum = hasher.hash().asInt();

    builder.setChecksummedData(ChecksummedData.newBuilder().setContent(bytes).build());
    builder.setWriteOffset(offset);
    if (last) {
      builder.setFinishWrite(true);
    }
    return builder.build();
  }
}
