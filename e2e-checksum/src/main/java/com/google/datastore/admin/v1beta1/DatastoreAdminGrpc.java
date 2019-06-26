package com.google.datastore.admin.v1beta1;

import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ClientCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ClientCalls.asyncClientStreamingCall;
import static io.grpc.stub.ClientCalls.asyncServerStreamingCall;
import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.blockingServerStreamingCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ServerCalls.asyncClientStreamingCall;
import static io.grpc.stub.ServerCalls.asyncServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;

/**
 * <pre>
 * Google Cloud Datastore Admin API
 * The Datastore Admin API provides several admin services for Cloud Datastore.
 * -----------------------------------------------------------------------------
 * ## Concepts
 * Project, namespace, kind, and entity as defined in the Google Cloud Datastore
 * API.
 * Operation: An Operation represents work being performed in the background.
 * EntityFilter: Allows specifying a subset of entities in a project. This is
 * specified as a combination of kinds and namespaces (either or both of which
 * may be all).
 * -----------------------------------------------------------------------------
 * ## Services
 * # Export/Import
 * The Export/Import service provides the ability to copy all or a subset of
 * entities to/from Google Cloud Storage.
 * Exported data may be imported into Cloud Datastore for any Google Cloud
 * Platform project. It is not restricted to the export source project. It is
 * possible to export from one project and then import into another.
 * Exported data can also be loaded into Google BigQuery for analysis.
 * Exports and imports are performed asynchronously. An Operation resource is
 * created for each export/import. The state (including any errors encountered)
 * of the export/import may be queried via the Operation resource.
 * # Operation
 * The Operations collection provides a record of actions performed for the
 * specified project (including any operations in progress). Operations are not
 * created directly but through calls on other collections or resources.
 * An operation that is not yet done may be cancelled. The request to cancel is
 * asynchronous and the operation may continue to run for some time after the
 * request to cancel is made.
 * An operation that is done may be deleted so that it is no longer listed as
 * part of the Operation collection.
 * ListOperations returns all pending operations, but not completed operations.
 * Operations are created by service DatastoreAdmin,
 * but are accessed via service google.longrunning.Operations.
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.23.0-SNAPSHOT)",
    comments = "Source: google/datastore/admin/v1beta1/datastore_admin.proto")
public final class DatastoreAdminGrpc {

  private DatastoreAdminGrpc() {}

  public static final String SERVICE_NAME = "google.datastore.admin.v1beta1.DatastoreAdmin";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.google.datastore.admin.v1beta1.ExportEntitiesRequest,
      com.google.longrunning.Operation> getExportEntitiesMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ExportEntities",
      requestType = com.google.datastore.admin.v1beta1.ExportEntitiesRequest.class,
      responseType = com.google.longrunning.Operation.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.google.datastore.admin.v1beta1.ExportEntitiesRequest,
      com.google.longrunning.Operation> getExportEntitiesMethod() {
    io.grpc.MethodDescriptor<com.google.datastore.admin.v1beta1.ExportEntitiesRequest, com.google.longrunning.Operation> getExportEntitiesMethod;
    if ((getExportEntitiesMethod = DatastoreAdminGrpc.getExportEntitiesMethod) == null) {
      synchronized (DatastoreAdminGrpc.class) {
        if ((getExportEntitiesMethod = DatastoreAdminGrpc.getExportEntitiesMethod) == null) {
          DatastoreAdminGrpc.getExportEntitiesMethod = getExportEntitiesMethod = 
              io.grpc.MethodDescriptor.<com.google.datastore.admin.v1beta1.ExportEntitiesRequest, com.google.longrunning.Operation>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "google.datastore.admin.v1beta1.DatastoreAdmin", "ExportEntities"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.google.datastore.admin.v1beta1.ExportEntitiesRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.google.longrunning.Operation.getDefaultInstance()))
                  .setSchemaDescriptor(new DatastoreAdminMethodDescriptorSupplier("ExportEntities"))
                  .build();
          }
        }
     }
     return getExportEntitiesMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.google.datastore.admin.v1beta1.ImportEntitiesRequest,
      com.google.longrunning.Operation> getImportEntitiesMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ImportEntities",
      requestType = com.google.datastore.admin.v1beta1.ImportEntitiesRequest.class,
      responseType = com.google.longrunning.Operation.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.google.datastore.admin.v1beta1.ImportEntitiesRequest,
      com.google.longrunning.Operation> getImportEntitiesMethod() {
    io.grpc.MethodDescriptor<com.google.datastore.admin.v1beta1.ImportEntitiesRequest, com.google.longrunning.Operation> getImportEntitiesMethod;
    if ((getImportEntitiesMethod = DatastoreAdminGrpc.getImportEntitiesMethod) == null) {
      synchronized (DatastoreAdminGrpc.class) {
        if ((getImportEntitiesMethod = DatastoreAdminGrpc.getImportEntitiesMethod) == null) {
          DatastoreAdminGrpc.getImportEntitiesMethod = getImportEntitiesMethod = 
              io.grpc.MethodDescriptor.<com.google.datastore.admin.v1beta1.ImportEntitiesRequest, com.google.longrunning.Operation>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "google.datastore.admin.v1beta1.DatastoreAdmin", "ImportEntities"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.google.datastore.admin.v1beta1.ImportEntitiesRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.google.longrunning.Operation.getDefaultInstance()))
                  .setSchemaDescriptor(new DatastoreAdminMethodDescriptorSupplier("ImportEntities"))
                  .build();
          }
        }
     }
     return getImportEntitiesMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static DatastoreAdminStub newStub(io.grpc.Channel channel) {
    return new DatastoreAdminStub(channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static DatastoreAdminBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new DatastoreAdminBlockingStub(channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static DatastoreAdminFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new DatastoreAdminFutureStub(channel);
  }

  /**
   * <pre>
   * Google Cloud Datastore Admin API
   * The Datastore Admin API provides several admin services for Cloud Datastore.
   * -----------------------------------------------------------------------------
   * ## Concepts
   * Project, namespace, kind, and entity as defined in the Google Cloud Datastore
   * API.
   * Operation: An Operation represents work being performed in the background.
   * EntityFilter: Allows specifying a subset of entities in a project. This is
   * specified as a combination of kinds and namespaces (either or both of which
   * may be all).
   * -----------------------------------------------------------------------------
   * ## Services
   * # Export/Import
   * The Export/Import service provides the ability to copy all or a subset of
   * entities to/from Google Cloud Storage.
   * Exported data may be imported into Cloud Datastore for any Google Cloud
   * Platform project. It is not restricted to the export source project. It is
   * possible to export from one project and then import into another.
   * Exported data can also be loaded into Google BigQuery for analysis.
   * Exports and imports are performed asynchronously. An Operation resource is
   * created for each export/import. The state (including any errors encountered)
   * of the export/import may be queried via the Operation resource.
   * # Operation
   * The Operations collection provides a record of actions performed for the
   * specified project (including any operations in progress). Operations are not
   * created directly but through calls on other collections or resources.
   * An operation that is not yet done may be cancelled. The request to cancel is
   * asynchronous and the operation may continue to run for some time after the
   * request to cancel is made.
   * An operation that is done may be deleted so that it is no longer listed as
   * part of the Operation collection.
   * ListOperations returns all pending operations, but not completed operations.
   * Operations are created by service DatastoreAdmin,
   * but are accessed via service google.longrunning.Operations.
   * </pre>
   */
  public static abstract class DatastoreAdminImplBase implements io.grpc.BindableService {

    /**
     * <pre>
     * Exports a copy of all or a subset of entities from Google Cloud Datastore
     * to another storage system, such as Google Cloud Storage. Recent updates to
     * entities may not be reflected in the export. The export occurs in the
     * background and its progress can be monitored and managed via the
     * Operation resource that is created. The output of an export may only be
     * used once the associated operation is done. If an export operation is
     * cancelled before completion it may leave partial data behind in Google
     * Cloud Storage.
     * </pre>
     */
    public void exportEntities(com.google.datastore.admin.v1beta1.ExportEntitiesRequest request,
        io.grpc.stub.StreamObserver<com.google.longrunning.Operation> responseObserver) {
      asyncUnimplementedUnaryCall(getExportEntitiesMethod(), responseObserver);
    }

    /**
     * <pre>
     * Imports entities into Google Cloud Datastore. Existing entities with the
     * same key are overwritten. The import occurs in the background and its
     * progress can be monitored and managed via the Operation resource that is
     * created. If an ImportEntities operation is cancelled, it is possible
     * that a subset of the data has already been imported to Cloud Datastore.
     * </pre>
     */
    public void importEntities(com.google.datastore.admin.v1beta1.ImportEntitiesRequest request,
        io.grpc.stub.StreamObserver<com.google.longrunning.Operation> responseObserver) {
      asyncUnimplementedUnaryCall(getImportEntitiesMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getExportEntitiesMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                com.google.datastore.admin.v1beta1.ExportEntitiesRequest,
                com.google.longrunning.Operation>(
                  this, METHODID_EXPORT_ENTITIES)))
          .addMethod(
            getImportEntitiesMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                com.google.datastore.admin.v1beta1.ImportEntitiesRequest,
                com.google.longrunning.Operation>(
                  this, METHODID_IMPORT_ENTITIES)))
          .build();
    }
  }

  /**
   * <pre>
   * Google Cloud Datastore Admin API
   * The Datastore Admin API provides several admin services for Cloud Datastore.
   * -----------------------------------------------------------------------------
   * ## Concepts
   * Project, namespace, kind, and entity as defined in the Google Cloud Datastore
   * API.
   * Operation: An Operation represents work being performed in the background.
   * EntityFilter: Allows specifying a subset of entities in a project. This is
   * specified as a combination of kinds and namespaces (either or both of which
   * may be all).
   * -----------------------------------------------------------------------------
   * ## Services
   * # Export/Import
   * The Export/Import service provides the ability to copy all or a subset of
   * entities to/from Google Cloud Storage.
   * Exported data may be imported into Cloud Datastore for any Google Cloud
   * Platform project. It is not restricted to the export source project. It is
   * possible to export from one project and then import into another.
   * Exported data can also be loaded into Google BigQuery for analysis.
   * Exports and imports are performed asynchronously. An Operation resource is
   * created for each export/import. The state (including any errors encountered)
   * of the export/import may be queried via the Operation resource.
   * # Operation
   * The Operations collection provides a record of actions performed for the
   * specified project (including any operations in progress). Operations are not
   * created directly but through calls on other collections or resources.
   * An operation that is not yet done may be cancelled. The request to cancel is
   * asynchronous and the operation may continue to run for some time after the
   * request to cancel is made.
   * An operation that is done may be deleted so that it is no longer listed as
   * part of the Operation collection.
   * ListOperations returns all pending operations, but not completed operations.
   * Operations are created by service DatastoreAdmin,
   * but are accessed via service google.longrunning.Operations.
   * </pre>
   */
  public static final class DatastoreAdminStub extends io.grpc.stub.AbstractStub<DatastoreAdminStub> {
    private DatastoreAdminStub(io.grpc.Channel channel) {
      super(channel);
    }

    private DatastoreAdminStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DatastoreAdminStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new DatastoreAdminStub(channel, callOptions);
    }

    /**
     * <pre>
     * Exports a copy of all or a subset of entities from Google Cloud Datastore
     * to another storage system, such as Google Cloud Storage. Recent updates to
     * entities may not be reflected in the export. The export occurs in the
     * background and its progress can be monitored and managed via the
     * Operation resource that is created. The output of an export may only be
     * used once the associated operation is done. If an export operation is
     * cancelled before completion it may leave partial data behind in Google
     * Cloud Storage.
     * </pre>
     */
    public void exportEntities(com.google.datastore.admin.v1beta1.ExportEntitiesRequest request,
        io.grpc.stub.StreamObserver<com.google.longrunning.Operation> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getExportEntitiesMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Imports entities into Google Cloud Datastore. Existing entities with the
     * same key are overwritten. The import occurs in the background and its
     * progress can be monitored and managed via the Operation resource that is
     * created. If an ImportEntities operation is cancelled, it is possible
     * that a subset of the data has already been imported to Cloud Datastore.
     * </pre>
     */
    public void importEntities(com.google.datastore.admin.v1beta1.ImportEntitiesRequest request,
        io.grpc.stub.StreamObserver<com.google.longrunning.Operation> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getImportEntitiesMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * <pre>
   * Google Cloud Datastore Admin API
   * The Datastore Admin API provides several admin services for Cloud Datastore.
   * -----------------------------------------------------------------------------
   * ## Concepts
   * Project, namespace, kind, and entity as defined in the Google Cloud Datastore
   * API.
   * Operation: An Operation represents work being performed in the background.
   * EntityFilter: Allows specifying a subset of entities in a project. This is
   * specified as a combination of kinds and namespaces (either or both of which
   * may be all).
   * -----------------------------------------------------------------------------
   * ## Services
   * # Export/Import
   * The Export/Import service provides the ability to copy all or a subset of
   * entities to/from Google Cloud Storage.
   * Exported data may be imported into Cloud Datastore for any Google Cloud
   * Platform project. It is not restricted to the export source project. It is
   * possible to export from one project and then import into another.
   * Exported data can also be loaded into Google BigQuery for analysis.
   * Exports and imports are performed asynchronously. An Operation resource is
   * created for each export/import. The state (including any errors encountered)
   * of the export/import may be queried via the Operation resource.
   * # Operation
   * The Operations collection provides a record of actions performed for the
   * specified project (including any operations in progress). Operations are not
   * created directly but through calls on other collections or resources.
   * An operation that is not yet done may be cancelled. The request to cancel is
   * asynchronous and the operation may continue to run for some time after the
   * request to cancel is made.
   * An operation that is done may be deleted so that it is no longer listed as
   * part of the Operation collection.
   * ListOperations returns all pending operations, but not completed operations.
   * Operations are created by service DatastoreAdmin,
   * but are accessed via service google.longrunning.Operations.
   * </pre>
   */
  public static final class DatastoreAdminBlockingStub extends io.grpc.stub.AbstractStub<DatastoreAdminBlockingStub> {
    private DatastoreAdminBlockingStub(io.grpc.Channel channel) {
      super(channel);
    }

    private DatastoreAdminBlockingStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DatastoreAdminBlockingStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new DatastoreAdminBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Exports a copy of all or a subset of entities from Google Cloud Datastore
     * to another storage system, such as Google Cloud Storage. Recent updates to
     * entities may not be reflected in the export. The export occurs in the
     * background and its progress can be monitored and managed via the
     * Operation resource that is created. The output of an export may only be
     * used once the associated operation is done. If an export operation is
     * cancelled before completion it may leave partial data behind in Google
     * Cloud Storage.
     * </pre>
     */
    public com.google.longrunning.Operation exportEntities(com.google.datastore.admin.v1beta1.ExportEntitiesRequest request) {
      return blockingUnaryCall(
          getChannel(), getExportEntitiesMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Imports entities into Google Cloud Datastore. Existing entities with the
     * same key are overwritten. The import occurs in the background and its
     * progress can be monitored and managed via the Operation resource that is
     * created. If an ImportEntities operation is cancelled, it is possible
     * that a subset of the data has already been imported to Cloud Datastore.
     * </pre>
     */
    public com.google.longrunning.Operation importEntities(com.google.datastore.admin.v1beta1.ImportEntitiesRequest request) {
      return blockingUnaryCall(
          getChannel(), getImportEntitiesMethod(), getCallOptions(), request);
    }
  }

  /**
   * <pre>
   * Google Cloud Datastore Admin API
   * The Datastore Admin API provides several admin services for Cloud Datastore.
   * -----------------------------------------------------------------------------
   * ## Concepts
   * Project, namespace, kind, and entity as defined in the Google Cloud Datastore
   * API.
   * Operation: An Operation represents work being performed in the background.
   * EntityFilter: Allows specifying a subset of entities in a project. This is
   * specified as a combination of kinds and namespaces (either or both of which
   * may be all).
   * -----------------------------------------------------------------------------
   * ## Services
   * # Export/Import
   * The Export/Import service provides the ability to copy all or a subset of
   * entities to/from Google Cloud Storage.
   * Exported data may be imported into Cloud Datastore for any Google Cloud
   * Platform project. It is not restricted to the export source project. It is
   * possible to export from one project and then import into another.
   * Exported data can also be loaded into Google BigQuery for analysis.
   * Exports and imports are performed asynchronously. An Operation resource is
   * created for each export/import. The state (including any errors encountered)
   * of the export/import may be queried via the Operation resource.
   * # Operation
   * The Operations collection provides a record of actions performed for the
   * specified project (including any operations in progress). Operations are not
   * created directly but through calls on other collections or resources.
   * An operation that is not yet done may be cancelled. The request to cancel is
   * asynchronous and the operation may continue to run for some time after the
   * request to cancel is made.
   * An operation that is done may be deleted so that it is no longer listed as
   * part of the Operation collection.
   * ListOperations returns all pending operations, but not completed operations.
   * Operations are created by service DatastoreAdmin,
   * but are accessed via service google.longrunning.Operations.
   * </pre>
   */
  public static final class DatastoreAdminFutureStub extends io.grpc.stub.AbstractStub<DatastoreAdminFutureStub> {
    private DatastoreAdminFutureStub(io.grpc.Channel channel) {
      super(channel);
    }

    private DatastoreAdminFutureStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DatastoreAdminFutureStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new DatastoreAdminFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Exports a copy of all or a subset of entities from Google Cloud Datastore
     * to another storage system, such as Google Cloud Storage. Recent updates to
     * entities may not be reflected in the export. The export occurs in the
     * background and its progress can be monitored and managed via the
     * Operation resource that is created. The output of an export may only be
     * used once the associated operation is done. If an export operation is
     * cancelled before completion it may leave partial data behind in Google
     * Cloud Storage.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.google.longrunning.Operation> exportEntities(
        com.google.datastore.admin.v1beta1.ExportEntitiesRequest request) {
      return futureUnaryCall(
          getChannel().newCall(getExportEntitiesMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Imports entities into Google Cloud Datastore. Existing entities with the
     * same key are overwritten. The import occurs in the background and its
     * progress can be monitored and managed via the Operation resource that is
     * created. If an ImportEntities operation is cancelled, it is possible
     * that a subset of the data has already been imported to Cloud Datastore.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.google.longrunning.Operation> importEntities(
        com.google.datastore.admin.v1beta1.ImportEntitiesRequest request) {
      return futureUnaryCall(
          getChannel().newCall(getImportEntitiesMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_EXPORT_ENTITIES = 0;
  private static final int METHODID_IMPORT_ENTITIES = 1;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final DatastoreAdminImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(DatastoreAdminImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_EXPORT_ENTITIES:
          serviceImpl.exportEntities((com.google.datastore.admin.v1beta1.ExportEntitiesRequest) request,
              (io.grpc.stub.StreamObserver<com.google.longrunning.Operation>) responseObserver);
          break;
        case METHODID_IMPORT_ENTITIES:
          serviceImpl.importEntities((com.google.datastore.admin.v1beta1.ImportEntitiesRequest) request,
              (io.grpc.stub.StreamObserver<com.google.longrunning.Operation>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  private static abstract class DatastoreAdminBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    DatastoreAdminBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.google.datastore.admin.v1beta1.DatastoreAdminProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("DatastoreAdmin");
    }
  }

  private static final class DatastoreAdminFileDescriptorSupplier
      extends DatastoreAdminBaseDescriptorSupplier {
    DatastoreAdminFileDescriptorSupplier() {}
  }

  private static final class DatastoreAdminMethodDescriptorSupplier
      extends DatastoreAdminBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    DatastoreAdminMethodDescriptorSupplier(String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (DatastoreAdminGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new DatastoreAdminFileDescriptorSupplier())
              .addMethod(getExportEntitiesMethod())
              .addMethod(getImportEntitiesMethod())
              .build();
        }
      }
    }
    return result;
  }
}
