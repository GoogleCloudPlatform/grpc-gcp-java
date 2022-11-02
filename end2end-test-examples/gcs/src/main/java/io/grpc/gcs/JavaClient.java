package io.grpc.gcs;

import static io.grpc.gcs.Args.METHOD_RANDOM;
import static io.grpc.gcs.Args.METHOD_READ;
import static io.grpc.gcs.Args.METHOD_WRITE;

import com.google.cloud.ReadChannel;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public abstract class JavaClient {
  private static final Logger logger = Logger.getLogger(JavaClient.class.getName());

  private Args args;
  private ObjectResolver objectResolver;
  private Storage client;

  protected JavaClient(Args args, Storage storage) {
    this.args = args;
    this.objectResolver = new ObjectResolver(args.obj, args.objFormat, args.objStart, args.objStop);
    this.client = storage;
  }

  public void startCalls(ResultTable results) throws InterruptedException, IOException {
    if (args.threads == 0) {
      switch (args.method) {
        case METHOD_READ:
          makeMediaRequest(results, /*threadId=*/ 1);
          break;
        case METHOD_RANDOM:
          makeRandomMediaRequest(results, /*threadId=*/ 1);
          break;
        case METHOD_WRITE:
          makeInsertRequest(results, /*threadId=*/ 1);
          break;
        default:
          logger.warning("Please provide valid methods with --method");
      }
    } else {
      ThreadPoolExecutor threadPoolExecutor =
          (ThreadPoolExecutor) Executors.newFixedThreadPool(args.threads);
      switch (args.method) {
        case METHOD_READ:
          for (int i = 0; i < args.threads; i++) {
            int finalI = i;
            Runnable task = () -> makeMediaRequest(results, finalI + 1);
            threadPoolExecutor.execute(task);
          }
          break;
        case METHOD_RANDOM:
          for (int i = 0; i < args.threads; i++) {
            int finalI = i;
            Runnable task =
                () -> {
                  try {
                    makeRandomMediaRequest(results, finalI + 1);
                  } catch (IOException e) {
                    e.printStackTrace();
                  }
                };
            threadPoolExecutor.execute(task);
          }
          break;
        case METHOD_WRITE:
          for (int i = 0; i < args.threads; i++) {
            int finalI = i;
            Runnable task = () -> makeInsertRequest(results, finalI + 1);
            threadPoolExecutor.execute(task);
          }
          break;
        default:
          logger.warning("Please provide valid methods with --method");
      }
      threadPoolExecutor.shutdown();
      if (!threadPoolExecutor.awaitTermination(30, TimeUnit.MINUTES)) {
        threadPoolExecutor.shutdownNow();
      }
    }
  }

  public void makeMediaRequest(ResultTable results, int threadId) {
    for (int i = 0; i < args.calls; i++) {
      String object = objectResolver.Resolve(threadId, i);
      BlobId blobId = BlobId.of(args.bkt, object);
      long start = System.currentTimeMillis();
      byte[] content = client.readAllBytes(blobId);
      // String contentString = new String(content, UTF_8);
      // logger.info("contentString: " + contentString);
      long dur = System.currentTimeMillis() - start;
      results.reportResult(args.bkt, object, content.length, dur);
    }
  }

  public void makeRandomMediaRequest(ResultTable results, int threadId) throws IOException {
    Random r = new Random();

    String object = objectResolver.Resolve(threadId, /*objectId=*/ 0);
    BlobId blobId = BlobId.of(args.bkt, object);

    int capacity = args.buffSize * 1024;
    ByteBuffer buff = ByteBuffer.allocate(capacity);

    for (int i = 0; i < args.calls; i++) {
      buff.clear();
      int bound = args.size - args.buffSize;
      long offset = r.nextInt(bound) * 1024L;

      long start = System.currentTimeMillis();
      try (
          ReadChannel reader = client.reader(blobId);
          // we care that the bytes move, not what we will do after they move, route to /dev/null
          WritableByteChannel out = Channels.newChannel(ByteStreams.nullOutputStream())
      ) {
        // set the chunkSize to that of our buffer to try and align range read size behavior
        reader.setChunkSize(capacity);
        // set begin offset
        reader.seek(offset);
        // set end offset (limit is counted from 0, not seek)
        reader.limit(offset + capacity);

        long copy = copy(reader, out, buff);
        long dur = System.currentTimeMillis() - start;
        if (copy != capacity) {
          logger.warning(String.format("Unexpected number of bytes. Expected: %d but was: %d", capacity, copy));
        }
        results.reportResult(args.bkt, object, copy, dur);
      }
    }
  }

  public void makeInsertRequest(ResultTable results, int threadId) {
    int totalBytes = args.size * 1024;
    byte[] data = new byte[totalBytes];
    for (int i = 0; i < args.calls; i++) {
      String object = objectResolver.Resolve(threadId, i);
      BlobId blobId = BlobId.of(args.bkt, object);
      long start = System.currentTimeMillis();
      client.create(BlobInfo.newBuilder(blobId).build(), data);
      long dur = System.currentTimeMillis() - start;
      results.reportResult(args.bkt, object, totalBytes, dur);
    }
  }

  private static long copy(ReadableByteChannel from, WritableByteChannel to, ByteBuffer buf)
      throws IOException {
    long total = 0;
    while (from.read(buf) != -1) {
      buf.flip();
      while (buf.hasRemaining()) {
        total += to.write(buf);
      }
      buf.clear();
    }
    return total;
  }
}
