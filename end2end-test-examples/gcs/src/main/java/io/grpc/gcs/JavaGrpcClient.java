package io.grpc.gcs;

import com.google.cloud.storage.StorageOptions;

public class JavaGrpcClient extends JavaClient {

  public JavaGrpcClient(Args args) {
    super(args, StorageOptions.grpc().build().getService());
  }

}
