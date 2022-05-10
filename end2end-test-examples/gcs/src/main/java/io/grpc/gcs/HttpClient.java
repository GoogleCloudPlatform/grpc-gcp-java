package io.grpc.gcs;

import static io.grpc.gcs.Args.METHOD_RANDOM;
import static io.grpc.gcs.Args.METHOD_READ;
import static io.grpc.gcs.Args.METHOD_WRITE;

import com.google.cloud.ReadChannel;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class HttpClient {
  private static final Logger logger = Logger.getLogger(HttpClient.class.getName());

  private Args args;
  private ObjectResolver objectResolver;
  private Storage client;

  public HttpClient(Args args) {
    this.args = args;
    this.objectResolver = new ObjectResolver(args.obj, args.objFormat, args.objStart, args.objStop);
    this.client = StorageOptions.getDefaultInstance().getService();
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
      // logger.info("time cost for readAllBytes: " + dur + "ms");
      // logger.info("total KB received: " + content.length/1024);
      results.reportResult(args.bkt, object, content.length, dur);
    }
  }

  public void makeRandomMediaRequest(ResultTable results, int threadId) throws IOException {
    Random r = new Random();

    String object = objectResolver.Resolve(threadId, /*objectId=*/ 0);
    BlobId blobId = BlobId.of(args.bkt, object);
    ReadChannel reader = client.reader(blobId);
    for (int i = 0; i < args.calls; i++) {
      long offset = (long) r.nextInt(args.size - args.buffSize) * 1024;
      reader.seek(offset);

      long start = System.currentTimeMillis();
      ByteBuffer buff = ByteBuffer.allocate(args.buffSize * 1024);
      reader.read(buff);
      long dur = System.currentTimeMillis() - start;
      if (buff.remaining() > 0) {
        logger.warning("Got remaining bytes: " + buff.remaining());
      }
      logger.info("total KB received: " + buff.position() / 1024);
      logger.info("time cost for random reading: " + dur + "ms");
      buff.clear();
      results.reportResult(args.bkt, object, args.buffSize * 1024, dur);
    }
    reader.close();
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
      logger.info("time cost for creating blob: " + dur + "ms");
      results.reportResult(args.bkt, object, totalBytes, dur);
    }
  }
}
