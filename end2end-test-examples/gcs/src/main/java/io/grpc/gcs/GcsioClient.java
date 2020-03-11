package io.grpc.gcs;

import static io.grpc.gcs.Args.METHOD_RANDOM;
import static io.grpc.gcs.Args.METHOD_READ;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.cloud.hadoop.gcsio.GoogleCloudStorageFileSystem;
import com.google.cloud.hadoop.gcsio.GoogleCloudStorageFileSystemOptions;
import com.google.cloud.hadoop.gcsio.GoogleCloudStorageOptions;
import com.google.cloud.hadoop.gcsio.GoogleCloudStorageReadOptions;
import com.google.cloud.hadoop.gcsio.GoogleCloudStorageReadOptions.Fadvise;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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

  public void startCalls(List<Long> results) throws InterruptedException, IOException {
    if (args.threads == 0) {
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
    } else {
      ThreadPoolExecutor threadPoolExecutor =
          (ThreadPoolExecutor) Executors.newFixedThreadPool(args.threads);
      try {
        switch (args.method) {
          case METHOD_READ:
            for (int i = 0; i < args.threads; i++) {
              Runnable task = () -> {
                try {
                  makeMediaRequest(results);
                } catch (IOException e) {
                  e.printStackTrace();
                }
              };
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
      } finally {
        threadPoolExecutor.shutdownNow();
        gcsfs.close();
      }
    }
  }

  private void makeMediaRequest(List<Long> results) throws IOException {
    int size = args.buffSize * 1024;

    URI uri = URI.create("gs://" + args.bkt + "/" + args.obj);

    ByteBuffer buff = ByteBuffer.allocate(size);
    for (int i = 0; i < args.calls; i++) {
      ReadableByteChannel readChannel = gcsfs.open(uri);
      long start = System.currentTimeMillis();
      readChannel.read(buff);
      long dur = System.currentTimeMillis() - start;
      if (buff.remaining() > 0) {
        logger.warning("Got remaining bytes: " + buff.remaining());
      }
      buff.clear();
      readChannel.close();
      //logger.info("time cost for reading bytes: " + dur + "ms");
      results.add(dur);
    }
  }

  private void makeRandomMediaRequest(List<Long> results) throws IOException {

    Random r = new Random();

    URI uri = URI.create("gs://" + args.bkt + "/" + args.obj);
    
    GoogleCloudStorageReadOptions readOpts = GoogleCloudStorageReadOptions.builder().build();

    SeekableByteChannel reader = gcsfs.open(uri, readOpts);
    for (int i = 0; i < args.calls; i++) {
      long offset = (long) r.nextInt(args.size - args.buffSize) * 1024;
      long start = System.currentTimeMillis();
      ByteBuffer buff = ByteBuffer.allocate(args.buffSize * 1024);
      reader.position(offset);
      reader.read(buff);
      long dur = System.currentTimeMillis() - start;
      if (buff.remaining() > 0) {
        logger.warning("Got remaining bytes: " + buff.remaining());
      }
      logger.info("time cost for reading bytes: " + dur + "ms");
      results.add(dur);
    }
    reader.close();
  }
}
