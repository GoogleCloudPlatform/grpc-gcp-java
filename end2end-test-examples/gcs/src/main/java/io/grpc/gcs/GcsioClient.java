package io.grpc.gcs;

import static io.grpc.gcs.Args.METHOD_RANDOM;
import static io.grpc.gcs.Args.METHOD_READ;
import static io.grpc.gcs.Args.METHOD_WRITE;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.cloud.hadoop.gcsio.GoogleCloudStorageFileSystem;
import com.google.cloud.hadoop.gcsio.GoogleCloudStorageFileSystemOptions;
import com.google.cloud.hadoop.gcsio.GoogleCloudStorageOptions;
import com.google.cloud.hadoop.gcsio.GoogleCloudStorageReadOptions;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class GcsioClient {
  private static final Logger logger = Logger.getLogger(GcsioClient.class.getName());
  private static final String SCOPE = "https://www.googleapis.com/auth/cloud-platform";

  private Args args;
  private GoogleCloudStorageOptions gcsOpts;
  private GoogleCredential creds;

  public GcsioClient(Args args, boolean grpcEnabled) throws IOException {
    this.args = args;
    this.creds = GoogleCredential.getApplicationDefault().createScoped(Arrays.asList(SCOPE));

    this.gcsOpts = GoogleCloudStorageOptions.builder()
            .setAppName("weiranf-app")
            .setGrpcEnabled(grpcEnabled)
            .build();
  }

  public void startCalls(ResultTable results) throws InterruptedException, IOException {
    if (args.threads == 1) {
      switch (args.method) {
        case METHOD_READ:
          makeMediaRequest(results);
          break;
        case METHOD_WRITE:
          makeWriteRequest(results, 0);
          break;
        case METHOD_RANDOM:
          makeRandomMediaRequest(results);
          break;
        default:
          logger.warning("Please provide valid methods with --method");
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
	  case METHOD_WRITE:
            for (int i = 0; i < args.threads; i++) {
              int finalI = i;
              Runnable task = () -> {
                try {
                  makeWriteRequest(results, finalI);
                } catch (IOException | InterruptedException e) {
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

  private void makeMediaRequest(ResultTable results) throws IOException {
    GoogleCloudStorageFileSystem gcsfs = new GoogleCloudStorageFileSystem(creds,
        GoogleCloudStorageFileSystemOptions.builder()
            .setCloudStorageOptions(gcsOpts)
            .build()
    );

    int size = args.size * 1024;

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
      results.reportResult(dur);
    }

    gcsfs.close();
  }

  private void makeWriteRequest(ResultTable results, int idx) throws IOException, InterruptedException {
    GoogleCloudStorageFileSystem gcsfs = new GoogleCloudStorageFileSystem(creds,
        GoogleCloudStorageFileSystemOptions.builder()
            .setCloudStorageOptions(gcsOpts)
            .build()
    );

    int size = args.size * 1024;
    Random rd = new Random();
    byte[] randBytes = new byte[size];
    rd.nextBytes(randBytes);

    URI uri = URI.create("gs://" + args.bkt + "/" + args.obj + "_" + idx);
    for (int i = 0; i < args.calls; i++) {
      WritableByteChannel writeChannel = gcsfs.create(uri);
      ByteBuffer buff = ByteBuffer.wrap(randBytes);
      long start = System.currentTimeMillis();
      writeChannel.write(buff);
      writeChannel.close();
      // write operation is async, need to call close() to wait for finish.
      long dur = System.currentTimeMillis() - start;
      results.reportResult(dur);
      if (dur < 1000) {
        Thread.sleep(1000 - dur); // Avoid limit of 1 qps for updating the same object
      }
    }

    gcsfs.close();
  }

  private void makeRandomMediaRequest(ResultTable results) throws IOException {
    GoogleCloudStorageFileSystem gcsfs = new GoogleCloudStorageFileSystem(creds,
        GoogleCloudStorageFileSystemOptions.builder()
            .setCloudStorageOptions(gcsOpts)
            .build()
    );

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
      results.reportResult(dur);
    }
    reader.close();

    gcsfs.close();
  }
}
