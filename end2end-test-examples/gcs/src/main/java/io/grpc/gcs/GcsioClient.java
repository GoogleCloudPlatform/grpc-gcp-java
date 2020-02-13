package io.grpc.gcs;

import static io.grpc.gcs.Args.METHOD_RANDOM;
import static io.grpc.gcs.Args.METHOD_READ;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.cloud.hadoop.gcsio.GoogleCloudStorageFileSystem;
import com.google.cloud.hadoop.gcsio.GoogleCloudStorageFileSystemOptions;
import com.google.cloud.hadoop.gcsio.GoogleCloudStorageOptions;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.logging.Logger;

public class GcsioClient {
  private static final String SCOPE = "https://www.googleapis.com/auth/cloud-platform";

  private Args args;
  private GoogleCloudStorageFileSystem gcsfs;
  private static final Logger logger = Logger.getLogger(GcsioClient.class.getName());

  public GcsioClient(Args args, boolean grpcEnabled) throws IOException {
    this.args = args;
    GoogleCredential creds = GoogleCredential.getApplicationDefault().createScoped(Arrays.asList(SCOPE));

    GoogleCloudStorageOptions gcsOpts =
        GoogleCloudStorageOptions.builder()
            .setAppName("weiranf-app")
            .setGrpcEnabled(grpcEnabled)
            .build();

    this.gcsfs = new GoogleCloudStorageFileSystem(creds,
        GoogleCloudStorageFileSystemOptions.builder()
            .setCloudStorageOptions(gcsOpts)
            .build()
    );

  }

  public void startCalls(ArrayList<Long> results) throws InterruptedException, IOException {
    try {
      switch (args.method) {
        case METHOD_READ:
          makeMediaRequest(results);
          break;
        case METHOD_RANDOM:
          makeRandomMediaRequest(results);
          break;
        default:
          logger.warning("Please provide valid methods with --method");
      }
    } finally {
      gcsfs.close();
    }
  }

  private void makeMediaRequest(ArrayList<Long> results) throws IOException {
    int size = args.buffSize * 1024;

    URI uri = URI.create("gs://" + args.bkt + "/" + args.obj);

    for (int i = 0; i < args.calls; i++) {
      long start = System.currentTimeMillis();
      ReadableByteChannel readChannel = gcsfs.open(uri);
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

  private void makeRandomMediaRequest(ArrayList<Long> results) throws IOException {

    Random r = new Random();

    URI uri = URI.create("gs://" + args.bkt + "/" + args.obj);

    for (int i = 0; i < args.calls; i++) {
      long offset = (long) r.nextInt(args.size - args.buffSize) * 1024;
      long start = System.currentTimeMillis();
      ByteBuffer buff = ByteBuffer.allocate(args.buffSize * 1024);
      SeekableByteChannel reader = gcsfs.open(uri);
      reader.position(offset);
      reader.read(buff);
      reader.close();
      long dur = System.currentTimeMillis() - start;
      if (buff.remaining() > 0) {
        logger.warning("Got remaining bytes: " + buff.remaining());
      }
      logger.info("time cost for reading bytes: " + dur + "ms");
      results.add(dur);
    }
  }
}
