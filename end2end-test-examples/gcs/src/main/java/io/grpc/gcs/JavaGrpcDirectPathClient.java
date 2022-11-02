package io.grpc.gcs;

import com.google.cloud.storage.StorageOptions;

public class JavaGrpcDirectPathClient extends JavaClient {

  public JavaGrpcDirectPathClient(Args args) {
    super(args, StorageOptions.grpc().setAttemptDirectPath(true).build().getService());
  }

}
