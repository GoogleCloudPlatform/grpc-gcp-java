package io.grpc.gcs;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.storage.Storage;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;


public class HttpClient {
  private static final Logger logger = Logger.getLogger(HttpClient.class.getName());

  private Args args;
  private Storage client;

  private static final String SCOPE = "https://www.googleapis.com/auth/cloud-platform";
  private static final String METHOD_GET_MEDIA = "media";
  private static final String METHOD_INSERT = "insert";

  public HttpClient(Args args) {
    this.args = args;
    this.client = StorageOptions.getDefaultInstance().getService();

  }

  public void startCalls(ArrayList<Long> results) throws InterruptedException {
    try {
      switch (args.method) {
        case METHOD_GET_MEDIA:
          makeMediaRequest(results);
          break;
        case METHOD_INSERT:
          makeInsertRequest(results);
          break;
        default:
          logger.warning("Please provide valid methods with --method");
      }
    } finally {
    }
  }

  public void makeMediaRequest(ArrayList<Long> results) {
    BlobId blobId = BlobId.of(args.bkt, args.obj);
    for (int i = 0; i < args.calls; i++) {
      long start = System.currentTimeMillis();
      byte[] content = client.readAllBytes(blobId);
      //String contentString = new String(content, UTF_8);
      //logger.info("contentString: " + contentString);
      long dur = System.currentTimeMillis() - start;
      logger.info("time cost for readAllBytes: " + dur + "ms");
      logger.info("total mb received: " + content.length/1024/1024);
      results.add(dur);
    }
  }

  public void makeInsertRequest(ArrayList<Long> results) {
    int totalBytes = args.size * 1024;
    byte[] data = new byte[totalBytes];
    BlobId blobId = BlobId.of(args.bkt, args.obj);
    for (int i = 0; i < args.calls; i++) {
      long start = System.currentTimeMillis();
      client.create(
          BlobInfo.newBuilder(blobId).build(),
          data
      );
      long dur = System.currentTimeMillis() - start;
      logger.info("time cost for creating blob: " + dur + "ms");
      results.add(dur);
    }
  }
}
