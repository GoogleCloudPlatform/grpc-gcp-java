package io.grpc.gcs;

import static io.grpc.gcs.Args.METHOD_RANDOM;
import static io.grpc.gcs.Args.METHOD_READ;
import static io.grpc.gcs.Args.METHOD_WRITE;

import com.google.api.client.googleapis.media.MediaHttpDownloader;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.StorageObject;
import com.google.cloud.http.HttpTransportOptions;
import com.google.cloud.storage.StorageOptions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class JavaApiServicesClient {
  private static final Logger logger = Logger.getLogger(JavaClient.class.getName());

  private Args args;
  private ObjectResolver objectResolver;
  private Storage client;

  public JavaApiServicesClient(Args args) {
    this.args = args;
    this.objectResolver = new ObjectResolver(args.obj, args.objFormat, args.objStart, args.objStop);
    /* Use StorageOptions library defaults */
    StorageOptions storageOptions = StorageOptions.getDefaultInstance();
    HttpTransportOptions transportOptions = (HttpTransportOptions)storageOptions.getTransportOptions();
    HttpRequestInitializer initializer = transportOptions.getHttpRequestInitializer(storageOptions);
    HttpTransport transport = transportOptions.getHttpTransportFactory().create();
    this.client = new Storage.Builder(transport, new JacksonFactory(), initializer).build();
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
      long start = System.currentTimeMillis();
      byte[] content = new byte[0];
      try {
        Storage.Objects.Get req = client
                .objects()
                .get(args.bkt, object);
        req.setReturnRawInputStream(true);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        req.executeMedia().download(output);
        content = output.toByteArray();
      } catch (IOException ioException) {
        System.err.println(ioException);
      }
      long dur = System.currentTimeMillis() - start;
      results.reportResult(args.bkt, object, content.length, dur);
    }
  }

  public void makeRandomMediaRequest(ResultTable results, int threadId) throws IOException {
    Random r = new Random();
    String object = objectResolver.Resolve(threadId, /*objectId=*/ 0);
    for (int i = 0; i < args.calls; i++) {
      long offset = (long) r.nextInt(args.size - args.buffSize) * 1024;
      long start = System.currentTimeMillis();
      ByteBuffer buff = ByteBuffer.allocate(args.buffSize * 1024);
      try {
        Storage.Objects.Get req = client.objects().get(args.bkt, object);
        if(offset > 0) {
          req.getRequestHeaders().setRange(String.format("bytes=%d-", offset));
        }
        req.setReturnRawInputStream(false);
        MediaHttpDownloader mediaHttpDownloader = req.getMediaHttpDownloader();
        mediaHttpDownloader.setDirectDownloadEnabled(true);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        req.executeMedia().download(byteArrayOutputStream);
        buff.put(byteArrayOutputStream.toByteArray());
      } catch (IOException ioException) {
        System.err.println(ioException);
      }
      long dur = System.currentTimeMillis() - start;
      if (buff.remaining() > 0) {
        logger.warning("Got remaining bytes: " + buff.remaining());
      }
      buff.clear();
      results.reportResult(args.bkt, object, args.buffSize * 1024, dur);
    }
  }

  public void makeInsertRequest(ResultTable results, int threadId) {
    int totalBytes = args.size * 1024;
    byte[] data = new byte[totalBytes];
    for (int i = 0; i < args.calls; i++) {
      String object = objectResolver.Resolve(threadId, i);
      long start = System.currentTimeMillis();
      try {
        StorageObject storageObject = new StorageObject();
        storageObject.setBucket(args.bkt).setName(object);
        Storage.Objects.Insert insert =
                client.objects().insert(
                        args.bkt, storageObject,
                        new InputStreamContent("application/octet-stream", new ByteArrayInputStream(data)));
        insert.getMediaHttpUploader().setDirectUploadEnabled(true);
        insert.execute();
      } catch (IOException ioException) {
        System.err.println(ioException);
      }
      long dur = System.currentTimeMillis() - start;
      results.reportResult(args.bkt, object, totalBytes, dur);
    }
  }
}
