package io.grpc.gcs;

import static io.grpc.gcs.Args.METHOD_READ;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.cloud.hadoop.gcsio.GoogleCloudStorageFileSystem;
import com.google.cloud.hadoop.gcsio.GoogleCloudStorageFileSystemOptions;
import com.google.cloud.hadoop.gcsio.GoogleCloudStorageOptions;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.logging.Logger;

public class GcsioClient {
  private Args args;
  private GoogleCloudStorageFileSystem gcsfs;
  private static final Logger logger = Logger.getLogger(GcsioClient.class.getName());

  public GcsioClient(Args args) throws IOException {
    this.args = args;
    GoogleCredential creds = GoogleCredential.getApplicationDefault();

    this.gcsfs = new GoogleCloudStorageFileSystem(creds, GoogleCloudStorageFileSystemOptions.builder().setCloudStorageOptions(
        GoogleCloudStorageOptions.builder().setAppName("weiranf-app").build()).build());

  }

  public void startCalls(ArrayList<Long> results) throws InterruptedException, IOException {
    try {
      switch (args.method) {
        case METHOD_READ:
          makeMediaRequest(results);
          break;
        default:
          logger.warning("Please provide valid methods with --method");
      }
    } finally {
      gcsfs.close();
    }
  }

  private void makeMediaRequest(ArrayList<Long> results) throws IOException {
    int size = args.size * 1024;

    URI uri = URI.create("gs://" + args.bkt + "/" + args.obj);

    for (int i = 0; i < args.calls; i++) {
      ReadableByteChannel readChannel = gcsfs.open(uri);
      long start = System.currentTimeMillis();
      ByteBuffer buff = ByteBuffer.allocate(size);
      readChannel.read(buff);
      readChannel.close();
      long dur = System.currentTimeMillis() - start;
      if (buff.remaining() > 0) {
        logger.warning("Got remaining bytes: " + buff.remaining());
      }
      logger.info("time cost for reading bytes: " + dur + "ms");
      results.add(dur);
    }
  }
}
