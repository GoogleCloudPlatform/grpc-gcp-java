package io.grpc.gcs;

import static io.grpc.gcs.Args.METHOD_RANDOM;
import static io.grpc.gcs.Args.METHOD_READ;
import static io.grpc.gcs.Args.METHOD_WRITE;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.cloud.hadoop.gcsio.GoogleCloudStorageFileSystem;
import com.google.cloud.hadoop.gcsio.GoogleCloudStorageFileSystemOptions;
import com.google.cloud.hadoop.gcsio.GoogleCloudStorageOptions;
import com.google.cloud.hadoop.gcsio.GoogleCloudStorageReadOptions;
import com.google.cloud.hadoop.util.AsyncWriteChannelOptions;
import com.google.common.base.Strings;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class GcsioClient {
  private static final Logger logger = Logger.getLogger(GcsioClient.class.getName());
  private static final String SCOPE = "https://www.googleapis.com/auth/cloud-platform";

  private Args args;
  private ObjectResolver objectResolver;
  private GoogleCloudStorageOptions gcsOpts;
  private GoogleCredential creds;

  public GcsioClient(Args args, boolean grpcEnabled) throws IOException {
    this.args = args;
    this.objectResolver = new ObjectResolver(args.obj, args.objFormat, args.objStart, args.objStop);
    if (args.access_token.equals("")) {
      this.creds = GoogleCredential.getApplicationDefault().createScoped(Arrays.asList(SCOPE));
    } else if (args.access_token.equals("-")) {
      this.creds = null;
    } else {
      logger.warning("Please provide valid --access_token");
    }

    GoogleCloudStorageOptions.Builder optsBuilder =
        GoogleCloudStorageOptions.builder()
            .setAppName("weiranf-app")
            .setGrpcEnabled(grpcEnabled)
            .setStorageRootUrl("https://" + args.host)
            .setStorageServicePath(args.service_path)
            .setTrafficDirectorEnabled(args.td)
            .setDirectPathPreferred(args.dp)
            .setReadChannelOptions(
                GoogleCloudStorageReadOptions.builder()
                    .setGrpcChecksumsEnabled(args.checksum)
                    .build())
            .setWriteChannelOptions(
                AsyncWriteChannelOptions.builder().setGrpcChecksumsEnabled(args.checksum).build());
    if (!Strings.isNullOrEmpty(args.host2)) {
      optsBuilder.setGrpcServerAddress(args.host2);
    }
    this.gcsOpts = optsBuilder.build();
  }

  public void startCalls(ResultTable results) throws InterruptedException, IOException {
    if (args.threads == 1) {
      switch (args.method) {
        case METHOD_READ:
          makeMediaRequest(results, /*threadId=*/ 1);
          break;
        case METHOD_RANDOM:
          makeRandomMediaRequest(results, /*threadId=*/ 1);
          break;
        case METHOD_WRITE:
          makeWriteRequest(results, /*threadId=*/ 1);
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
              int finalI = i;
              Runnable task =
                  () -> {
                    try {
                      makeMediaRequest(results, finalI + 1);
                    } catch (IOException e) {
                      e.printStackTrace();
                    }
                  };
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
              Runnable task =
                  () -> {
                    try {
                      makeWriteRequest(results, finalI + 1);
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

  private void makeMediaRequest(ResultTable results, int threadId) throws IOException {
    GoogleCloudStorageFileSystem gcsfs =
        new GoogleCloudStorageFileSystem(
            creds,
            GoogleCloudStorageFileSystemOptions.builder().setCloudStorageOptions(gcsOpts).build());
    long totalSize = args.size * 1024L;
    int buffSize = (args.buffSize == 0 ? 32 * 1024 : args.buffSize) * 1024;
    ByteBuffer buff = ByteBuffer.allocate(buffSize);
    for (int i = 0; i < args.calls; i++) {
      long receivedSize = 0;
      long start = System.currentTimeMillis();
      String object = objectResolver.Resolve(threadId, i);
      URI uri = URI.create("gs://" + args.bkt + "/" + object);
      ReadableByteChannel readChannel = gcsfs.open(uri);
      while (receivedSize < totalSize) {
        int r = readChannel.read(buff);
        if (r < 0) break;
        buff.clear();
        receivedSize += r;
      }
      long dur = System.currentTimeMillis() - start;
      buff.clear();
      readChannel.close();
      results.reportResult(args.bkt, object, receivedSize, dur);
    }

    gcsfs.close();
  }

  private void makeRandomMediaRequest(ResultTable results, int threadId) throws IOException {
    GoogleCloudStorageFileSystem gcsfs =
        new GoogleCloudStorageFileSystem(
            creds,
            GoogleCloudStorageFileSystemOptions.builder().setCloudStorageOptions(gcsOpts).build());

    Random r = new Random();

    String object = objectResolver.Resolve(threadId, /*objectId=*/ 0);
    URI uri = URI.create("gs://" + args.bkt + "/" + object);
    GoogleCloudStorageReadOptions readOpts = gcsOpts.getReadChannelOptions();

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
      results.reportResult(args.bkt, object, args.buffSize * 1024, dur);
    }
    reader.close();

    gcsfs.close();
  }

  private void makeWriteRequest(ResultTable results, int threadId)
      throws IOException, InterruptedException {
    GoogleCloudStorageFileSystem gcsfs =
        new GoogleCloudStorageFileSystem(
            creds,
            GoogleCloudStorageFileSystemOptions.builder().setCloudStorageOptions(gcsOpts).build());

    int size = args.size * 1024;
    Random rd = new Random();
    byte[] randBytes = new byte[size];
    rd.nextBytes(randBytes);

    for (int i = 0; i < args.calls; i++) {
      long start = System.currentTimeMillis();
      String object = objectResolver.Resolve(threadId, i);
      URI uri = URI.create("gs://" + args.bkt + "/" + object);
      WritableByteChannel writeChannel = gcsfs.create(uri);
      ByteBuffer buff = ByteBuffer.wrap(randBytes);
      writeChannel.write(buff);
      // write operation is async, need to call close() to wait for finish.
      writeChannel.close();
      long dur = System.currentTimeMillis() - start;
      results.reportResult(args.bkt, object, size, dur);
    }

    gcsfs.close();
  }
}
