package io.grpc.gcs;

import com.google.cloud.storage.StorageOptions;

public class JavaJsonClient extends JavaClient {

  public JavaJsonClient(Args args) {
    super(args, StorageOptions.http().build().getService());
  }

}
