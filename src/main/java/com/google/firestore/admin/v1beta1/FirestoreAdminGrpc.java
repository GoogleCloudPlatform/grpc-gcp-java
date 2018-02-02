package com.google.firestore.admin.v1beta1;

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
 * The Cloud Firestore Admin API.
 * This API provides several administrative services for Cloud Firestore.
 * # Concepts
 * Project, Database, Namespace, Collection, and Document are used as defined in
 * the Google Cloud Firestore API.
 * Operation: An Operation represents work being performed in the background.
 * # Services
 * ## Index
 * The index service manages Cloud Firestore indexes.
 * Index creation is performed asynchronously.
 * An Operation resource is created for each such asynchronous operation.
 * The state of the operation (including any errors encountered)
 * may be queried via the Operation resource.
 * ## Metadata
 * Provides metadata and statistical information about data in Cloud Firestore.
 * The data provided as part of this API may be stale.
 * ## Operation
 * The Operations collection provides a record of actions performed for the
 * specified Project (including any Operations in progress). Operations are not
 * created directly but through calls on other collections or resources.
 * An Operation that is not yet done may be cancelled. The request to cancel is
 * asynchronous and the Operation may continue to run for some time after the
 * request to cancel is made.
 * An Operation that is done may be deleted so that it is no longer listed as
 * part of the Operation collection.
 * Operations are created by service `FirestoreAdmin`, but are accessed via
 * service `google.longrunning.Operations`.
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.11.0-SNAPSHOT)",
    comments = "Source: google/firestore/admin/v1beta1/firestore_admin.proto")
public final class FirestoreAdminGrpc {

  private FirestoreAdminGrpc() {}

  public static final String SERVICE_NAME = "google.firestore.admin.v1beta1.FirestoreAdmin";

  // Static method descriptors that strictly reflect the proto.
  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  @java.lang.Deprecated // Use {@link #getCreateIndexMethod()} instead. 
  public static final io.grpc.MethodDescriptor<com.google.firestore.admin.v1beta1.CreateIndexRequest,
      com.google.longrunning.Operation> METHOD_CREATE_INDEX = getCreateIndexMethod();

  private static volatile io.grpc.MethodDescriptor<com.google.firestore.admin.v1beta1.CreateIndexRequest,
      com.google.longrunning.Operation> getCreateIndexMethod;

  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  public static io.grpc.MethodDescriptor<com.google.firestore.admin.v1beta1.CreateIndexRequest,
      com.google.longrunning.Operation> getCreateIndexMethod() {
    io.grpc.MethodDescriptor<com.google.firestore.admin.v1beta1.CreateIndexRequest, com.google.longrunning.Operation> getCreateIndexMethod;
    if ((getCreateIndexMethod = FirestoreAdminGrpc.getCreateIndexMethod) == null) {
      synchronized (FirestoreAdminGrpc.class) {
        if ((getCreateIndexMethod = FirestoreAdminGrpc.getCreateIndexMethod) == null) {
          FirestoreAdminGrpc.getCreateIndexMethod = getCreateIndexMethod = 
              io.grpc.MethodDescriptor.<com.google.firestore.admin.v1beta1.CreateIndexRequest, com.google.longrunning.Operation>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "google.firestore.admin.v1beta1.FirestoreAdmin", "CreateIndex"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.google.firestore.admin.v1beta1.CreateIndexRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.google.longrunning.Operation.getDefaultInstance()))
                  .setSchemaDescriptor(new FirestoreAdminMethodDescriptorSupplier("CreateIndex"))
                  .build();
          }
        }
     }
     return getCreateIndexMethod;
  }
  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  @java.lang.Deprecated // Use {@link #getListIndexesMethod()} instead. 
  public static final io.grpc.MethodDescriptor<com.google.firestore.admin.v1beta1.ListIndexesRequest,
      com.google.firestore.admin.v1beta1.ListIndexesResponse> METHOD_LIST_INDEXES = getListIndexesMethod();

  private static volatile io.grpc.MethodDescriptor<com.google.firestore.admin.v1beta1.ListIndexesRequest,
      com.google.firestore.admin.v1beta1.ListIndexesResponse> getListIndexesMethod;

  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  public static io.grpc.MethodDescriptor<com.google.firestore.admin.v1beta1.ListIndexesRequest,
      com.google.firestore.admin.v1beta1.ListIndexesResponse> getListIndexesMethod() {
    io.grpc.MethodDescriptor<com.google.firestore.admin.v1beta1.ListIndexesRequest, com.google.firestore.admin.v1beta1.ListIndexesResponse> getListIndexesMethod;
    if ((getListIndexesMethod = FirestoreAdminGrpc.getListIndexesMethod) == null) {
      synchronized (FirestoreAdminGrpc.class) {
        if ((getListIndexesMethod = FirestoreAdminGrpc.getListIndexesMethod) == null) {
          FirestoreAdminGrpc.getListIndexesMethod = getListIndexesMethod = 
              io.grpc.MethodDescriptor.<com.google.firestore.admin.v1beta1.ListIndexesRequest, com.google.firestore.admin.v1beta1.ListIndexesResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "google.firestore.admin.v1beta1.FirestoreAdmin", "ListIndexes"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.google.firestore.admin.v1beta1.ListIndexesRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.google.firestore.admin.v1beta1.ListIndexesResponse.getDefaultInstance()))
                  .setSchemaDescriptor(new FirestoreAdminMethodDescriptorSupplier("ListIndexes"))
                  .build();
          }
        }
     }
     return getListIndexesMethod;
  }
  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  @java.lang.Deprecated // Use {@link #getGetIndexMethod()} instead. 
  public static final io.grpc.MethodDescriptor<com.google.firestore.admin.v1beta1.GetIndexRequest,
      com.google.firestore.admin.v1beta1.Index> METHOD_GET_INDEX = getGetIndexMethod();

  private static volatile io.grpc.MethodDescriptor<com.google.firestore.admin.v1beta1.GetIndexRequest,
      com.google.firestore.admin.v1beta1.Index> getGetIndexMethod;

  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  public static io.grpc.MethodDescriptor<com.google.firestore.admin.v1beta1.GetIndexRequest,
      com.google.firestore.admin.v1beta1.Index> getGetIndexMethod() {
    io.grpc.MethodDescriptor<com.google.firestore.admin.v1beta1.GetIndexRequest, com.google.firestore.admin.v1beta1.Index> getGetIndexMethod;
    if ((getGetIndexMethod = FirestoreAdminGrpc.getGetIndexMethod) == null) {
      synchronized (FirestoreAdminGrpc.class) {
        if ((getGetIndexMethod = FirestoreAdminGrpc.getGetIndexMethod) == null) {
          FirestoreAdminGrpc.getGetIndexMethod = getGetIndexMethod = 
              io.grpc.MethodDescriptor.<com.google.firestore.admin.v1beta1.GetIndexRequest, com.google.firestore.admin.v1beta1.Index>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "google.firestore.admin.v1beta1.FirestoreAdmin", "GetIndex"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.google.firestore.admin.v1beta1.GetIndexRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.google.firestore.admin.v1beta1.Index.getDefaultInstance()))
                  .setSchemaDescriptor(new FirestoreAdminMethodDescriptorSupplier("GetIndex"))
                  .build();
          }
        }
     }
     return getGetIndexMethod;
  }
  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  @java.lang.Deprecated // Use {@link #getDeleteIndexMethod()} instead. 
  public static final io.grpc.MethodDescriptor<com.google.firestore.admin.v1beta1.DeleteIndexRequest,
      com.google.protobuf.Empty> METHOD_DELETE_INDEX = getDeleteIndexMethod();

  private static volatile io.grpc.MethodDescriptor<com.google.firestore.admin.v1beta1.DeleteIndexRequest,
      com.google.protobuf.Empty> getDeleteIndexMethod;

  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  public static io.grpc.MethodDescriptor<com.google.firestore.admin.v1beta1.DeleteIndexRequest,
      com.google.protobuf.Empty> getDeleteIndexMethod() {
    io.grpc.MethodDescriptor<com.google.firestore.admin.v1beta1.DeleteIndexRequest, com.google.protobuf.Empty> getDeleteIndexMethod;
    if ((getDeleteIndexMethod = FirestoreAdminGrpc.getDeleteIndexMethod) == null) {
      synchronized (FirestoreAdminGrpc.class) {
        if ((getDeleteIndexMethod = FirestoreAdminGrpc.getDeleteIndexMethod) == null) {
          FirestoreAdminGrpc.getDeleteIndexMethod = getDeleteIndexMethod = 
              io.grpc.MethodDescriptor.<com.google.firestore.admin.v1beta1.DeleteIndexRequest, com.google.protobuf.Empty>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "google.firestore.admin.v1beta1.FirestoreAdmin", "DeleteIndex"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.google.firestore.admin.v1beta1.DeleteIndexRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.google.protobuf.Empty.getDefaultInstance()))
                  .setSchemaDescriptor(new FirestoreAdminMethodDescriptorSupplier("DeleteIndex"))
                  .build();
          }
        }
     }
     return getDeleteIndexMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static FirestoreAdminStub newStub(io.grpc.Channel channel) {
    return new FirestoreAdminStub(channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static FirestoreAdminBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new FirestoreAdminBlockingStub(channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static FirestoreAdminFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new FirestoreAdminFutureStub(channel);
  }

  /**
   * <pre>
   * The Cloud Firestore Admin API.
   * This API provides several administrative services for Cloud Firestore.
   * # Concepts
   * Project, Database, Namespace, Collection, and Document are used as defined in
   * the Google Cloud Firestore API.
   * Operation: An Operation represents work being performed in the background.
   * # Services
   * ## Index
   * The index service manages Cloud Firestore indexes.
   * Index creation is performed asynchronously.
   * An Operation resource is created for each such asynchronous operation.
   * The state of the operation (including any errors encountered)
   * may be queried via the Operation resource.
   * ## Metadata
   * Provides metadata and statistical information about data in Cloud Firestore.
   * The data provided as part of this API may be stale.
   * ## Operation
   * The Operations collection provides a record of actions performed for the
   * specified Project (including any Operations in progress). Operations are not
   * created directly but through calls on other collections or resources.
   * An Operation that is not yet done may be cancelled. The request to cancel is
   * asynchronous and the Operation may continue to run for some time after the
   * request to cancel is made.
   * An Operation that is done may be deleted so that it is no longer listed as
   * part of the Operation collection.
   * Operations are created by service `FirestoreAdmin`, but are accessed via
   * service `google.longrunning.Operations`.
   * </pre>
   */
  public static abstract class FirestoreAdminImplBase implements io.grpc.BindableService {

    /**
     * <pre>
     * Creates the specified index.
     * A newly created index's initial state is `CREATING`. On completion of the
     * returned [google.longrunning.Operation][google.longrunning.Operation], the state will be `READY`.
     * If the index already exists, the call will return an `ALREADY_EXISTS`
     * status.
     * During creation, the process could result in an error, in which case the
     * index will move to the `ERROR` state. The process can be recovered by
     * fixing the data that caused the error, removing the index with
     * [delete][google.firestore.admin.v1beta1.FirestoreAdmin.DeleteIndex], then re-creating the index with
     * [create][google.firestore.admin.v1beta1.FirestoreAdmin.CreateIndex].
     * Indexes with a single field cannot be created.
     * </pre>
     */
    public void createIndex(com.google.firestore.admin.v1beta1.CreateIndexRequest request,
        io.grpc.stub.StreamObserver<com.google.longrunning.Operation> responseObserver) {
      asyncUnimplementedUnaryCall(getCreateIndexMethod(), responseObserver);
    }

    /**
     * <pre>
     * Lists the indexes that match the specified filters.
     * </pre>
     */
    public void listIndexes(com.google.firestore.admin.v1beta1.ListIndexesRequest request,
        io.grpc.stub.StreamObserver<com.google.firestore.admin.v1beta1.ListIndexesResponse> responseObserver) {
      asyncUnimplementedUnaryCall(getListIndexesMethod(), responseObserver);
    }

    /**
     * <pre>
     * Gets an index.
     * </pre>
     */
    public void getIndex(com.google.firestore.admin.v1beta1.GetIndexRequest request,
        io.grpc.stub.StreamObserver<com.google.firestore.admin.v1beta1.Index> responseObserver) {
      asyncUnimplementedUnaryCall(getGetIndexMethod(), responseObserver);
    }

    /**
     * <pre>
     * Deletes an index.
     * </pre>
     */
    public void deleteIndex(com.google.firestore.admin.v1beta1.DeleteIndexRequest request,
        io.grpc.stub.StreamObserver<com.google.protobuf.Empty> responseObserver) {
      asyncUnimplementedUnaryCall(getDeleteIndexMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getCreateIndexMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                com.google.firestore.admin.v1beta1.CreateIndexRequest,
                com.google.longrunning.Operation>(
                  this, METHODID_CREATE_INDEX)))
          .addMethod(
            getListIndexesMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                com.google.firestore.admin.v1beta1.ListIndexesRequest,
                com.google.firestore.admin.v1beta1.ListIndexesResponse>(
                  this, METHODID_LIST_INDEXES)))
          .addMethod(
            getGetIndexMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                com.google.firestore.admin.v1beta1.GetIndexRequest,
                com.google.firestore.admin.v1beta1.Index>(
                  this, METHODID_GET_INDEX)))
          .addMethod(
            getDeleteIndexMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                com.google.firestore.admin.v1beta1.DeleteIndexRequest,
                com.google.protobuf.Empty>(
                  this, METHODID_DELETE_INDEX)))
          .build();
    }
  }

  /**
   * <pre>
   * The Cloud Firestore Admin API.
   * This API provides several administrative services for Cloud Firestore.
   * # Concepts
   * Project, Database, Namespace, Collection, and Document are used as defined in
   * the Google Cloud Firestore API.
   * Operation: An Operation represents work being performed in the background.
   * # Services
   * ## Index
   * The index service manages Cloud Firestore indexes.
   * Index creation is performed asynchronously.
   * An Operation resource is created for each such asynchronous operation.
   * The state of the operation (including any errors encountered)
   * may be queried via the Operation resource.
   * ## Metadata
   * Provides metadata and statistical information about data in Cloud Firestore.
   * The data provided as part of this API may be stale.
   * ## Operation
   * The Operations collection provides a record of actions performed for the
   * specified Project (including any Operations in progress). Operations are not
   * created directly but through calls on other collections or resources.
   * An Operation that is not yet done may be cancelled. The request to cancel is
   * asynchronous and the Operation may continue to run for some time after the
   * request to cancel is made.
   * An Operation that is done may be deleted so that it is no longer listed as
   * part of the Operation collection.
   * Operations are created by service `FirestoreAdmin`, but are accessed via
   * service `google.longrunning.Operations`.
   * </pre>
   */
  public static final class FirestoreAdminStub extends io.grpc.stub.AbstractStub<FirestoreAdminStub> {
    private FirestoreAdminStub(io.grpc.Channel channel) {
      super(channel);
    }

    private FirestoreAdminStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected FirestoreAdminStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new FirestoreAdminStub(channel, callOptions);
    }

    /**
     * <pre>
     * Creates the specified index.
     * A newly created index's initial state is `CREATING`. On completion of the
     * returned [google.longrunning.Operation][google.longrunning.Operation], the state will be `READY`.
     * If the index already exists, the call will return an `ALREADY_EXISTS`
     * status.
     * During creation, the process could result in an error, in which case the
     * index will move to the `ERROR` state. The process can be recovered by
     * fixing the data that caused the error, removing the index with
     * [delete][google.firestore.admin.v1beta1.FirestoreAdmin.DeleteIndex], then re-creating the index with
     * [create][google.firestore.admin.v1beta1.FirestoreAdmin.CreateIndex].
     * Indexes with a single field cannot be created.
     * </pre>
     */
    public void createIndex(com.google.firestore.admin.v1beta1.CreateIndexRequest request,
        io.grpc.stub.StreamObserver<com.google.longrunning.Operation> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getCreateIndexMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Lists the indexes that match the specified filters.
     * </pre>
     */
    public void listIndexes(com.google.firestore.admin.v1beta1.ListIndexesRequest request,
        io.grpc.stub.StreamObserver<com.google.firestore.admin.v1beta1.ListIndexesResponse> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getListIndexesMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Gets an index.
     * </pre>
     */
    public void getIndex(com.google.firestore.admin.v1beta1.GetIndexRequest request,
        io.grpc.stub.StreamObserver<com.google.firestore.admin.v1beta1.Index> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getGetIndexMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Deletes an index.
     * </pre>
     */
    public void deleteIndex(com.google.firestore.admin.v1beta1.DeleteIndexRequest request,
        io.grpc.stub.StreamObserver<com.google.protobuf.Empty> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getDeleteIndexMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * <pre>
   * The Cloud Firestore Admin API.
   * This API provides several administrative services for Cloud Firestore.
   * # Concepts
   * Project, Database, Namespace, Collection, and Document are used as defined in
   * the Google Cloud Firestore API.
   * Operation: An Operation represents work being performed in the background.
   * # Services
   * ## Index
   * The index service manages Cloud Firestore indexes.
   * Index creation is performed asynchronously.
   * An Operation resource is created for each such asynchronous operation.
   * The state of the operation (including any errors encountered)
   * may be queried via the Operation resource.
   * ## Metadata
   * Provides metadata and statistical information about data in Cloud Firestore.
   * The data provided as part of this API may be stale.
   * ## Operation
   * The Operations collection provides a record of actions performed for the
   * specified Project (including any Operations in progress). Operations are not
   * created directly but through calls on other collections or resources.
   * An Operation that is not yet done may be cancelled. The request to cancel is
   * asynchronous and the Operation may continue to run for some time after the
   * request to cancel is made.
   * An Operation that is done may be deleted so that it is no longer listed as
   * part of the Operation collection.
   * Operations are created by service `FirestoreAdmin`, but are accessed via
   * service `google.longrunning.Operations`.
   * </pre>
   */
  public static final class FirestoreAdminBlockingStub extends io.grpc.stub.AbstractStub<FirestoreAdminBlockingStub> {
    private FirestoreAdminBlockingStub(io.grpc.Channel channel) {
      super(channel);
    }

    private FirestoreAdminBlockingStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected FirestoreAdminBlockingStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new FirestoreAdminBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Creates the specified index.
     * A newly created index's initial state is `CREATING`. On completion of the
     * returned [google.longrunning.Operation][google.longrunning.Operation], the state will be `READY`.
     * If the index already exists, the call will return an `ALREADY_EXISTS`
     * status.
     * During creation, the process could result in an error, in which case the
     * index will move to the `ERROR` state. The process can be recovered by
     * fixing the data that caused the error, removing the index with
     * [delete][google.firestore.admin.v1beta1.FirestoreAdmin.DeleteIndex], then re-creating the index with
     * [create][google.firestore.admin.v1beta1.FirestoreAdmin.CreateIndex].
     * Indexes with a single field cannot be created.
     * </pre>
     */
    public com.google.longrunning.Operation createIndex(com.google.firestore.admin.v1beta1.CreateIndexRequest request) {
      return blockingUnaryCall(
          getChannel(), getCreateIndexMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Lists the indexes that match the specified filters.
     * </pre>
     */
    public com.google.firestore.admin.v1beta1.ListIndexesResponse listIndexes(com.google.firestore.admin.v1beta1.ListIndexesRequest request) {
      return blockingUnaryCall(
          getChannel(), getListIndexesMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Gets an index.
     * </pre>
     */
    public com.google.firestore.admin.v1beta1.Index getIndex(com.google.firestore.admin.v1beta1.GetIndexRequest request) {
      return blockingUnaryCall(
          getChannel(), getGetIndexMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Deletes an index.
     * </pre>
     */
    public com.google.protobuf.Empty deleteIndex(com.google.firestore.admin.v1beta1.DeleteIndexRequest request) {
      return blockingUnaryCall(
          getChannel(), getDeleteIndexMethod(), getCallOptions(), request);
    }
  }

  /**
   * <pre>
   * The Cloud Firestore Admin API.
   * This API provides several administrative services for Cloud Firestore.
   * # Concepts
   * Project, Database, Namespace, Collection, and Document are used as defined in
   * the Google Cloud Firestore API.
   * Operation: An Operation represents work being performed in the background.
   * # Services
   * ## Index
   * The index service manages Cloud Firestore indexes.
   * Index creation is performed asynchronously.
   * An Operation resource is created for each such asynchronous operation.
   * The state of the operation (including any errors encountered)
   * may be queried via the Operation resource.
   * ## Metadata
   * Provides metadata and statistical information about data in Cloud Firestore.
   * The data provided as part of this API may be stale.
   * ## Operation
   * The Operations collection provides a record of actions performed for the
   * specified Project (including any Operations in progress). Operations are not
   * created directly but through calls on other collections or resources.
   * An Operation that is not yet done may be cancelled. The request to cancel is
   * asynchronous and the Operation may continue to run for some time after the
   * request to cancel is made.
   * An Operation that is done may be deleted so that it is no longer listed as
   * part of the Operation collection.
   * Operations are created by service `FirestoreAdmin`, but are accessed via
   * service `google.longrunning.Operations`.
   * </pre>
   */
  public static final class FirestoreAdminFutureStub extends io.grpc.stub.AbstractStub<FirestoreAdminFutureStub> {
    private FirestoreAdminFutureStub(io.grpc.Channel channel) {
      super(channel);
    }

    private FirestoreAdminFutureStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected FirestoreAdminFutureStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new FirestoreAdminFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Creates the specified index.
     * A newly created index's initial state is `CREATING`. On completion of the
     * returned [google.longrunning.Operation][google.longrunning.Operation], the state will be `READY`.
     * If the index already exists, the call will return an `ALREADY_EXISTS`
     * status.
     * During creation, the process could result in an error, in which case the
     * index will move to the `ERROR` state. The process can be recovered by
     * fixing the data that caused the error, removing the index with
     * [delete][google.firestore.admin.v1beta1.FirestoreAdmin.DeleteIndex], then re-creating the index with
     * [create][google.firestore.admin.v1beta1.FirestoreAdmin.CreateIndex].
     * Indexes with a single field cannot be created.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.google.longrunning.Operation> createIndex(
        com.google.firestore.admin.v1beta1.CreateIndexRequest request) {
      return futureUnaryCall(
          getChannel().newCall(getCreateIndexMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Lists the indexes that match the specified filters.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.google.firestore.admin.v1beta1.ListIndexesResponse> listIndexes(
        com.google.firestore.admin.v1beta1.ListIndexesRequest request) {
      return futureUnaryCall(
          getChannel().newCall(getListIndexesMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Gets an index.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.google.firestore.admin.v1beta1.Index> getIndex(
        com.google.firestore.admin.v1beta1.GetIndexRequest request) {
      return futureUnaryCall(
          getChannel().newCall(getGetIndexMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Deletes an index.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.google.protobuf.Empty> deleteIndex(
        com.google.firestore.admin.v1beta1.DeleteIndexRequest request) {
      return futureUnaryCall(
          getChannel().newCall(getDeleteIndexMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_CREATE_INDEX = 0;
  private static final int METHODID_LIST_INDEXES = 1;
  private static final int METHODID_GET_INDEX = 2;
  private static final int METHODID_DELETE_INDEX = 3;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final FirestoreAdminImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(FirestoreAdminImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_CREATE_INDEX:
          serviceImpl.createIndex((com.google.firestore.admin.v1beta1.CreateIndexRequest) request,
              (io.grpc.stub.StreamObserver<com.google.longrunning.Operation>) responseObserver);
          break;
        case METHODID_LIST_INDEXES:
          serviceImpl.listIndexes((com.google.firestore.admin.v1beta1.ListIndexesRequest) request,
              (io.grpc.stub.StreamObserver<com.google.firestore.admin.v1beta1.ListIndexesResponse>) responseObserver);
          break;
        case METHODID_GET_INDEX:
          serviceImpl.getIndex((com.google.firestore.admin.v1beta1.GetIndexRequest) request,
              (io.grpc.stub.StreamObserver<com.google.firestore.admin.v1beta1.Index>) responseObserver);
          break;
        case METHODID_DELETE_INDEX:
          serviceImpl.deleteIndex((com.google.firestore.admin.v1beta1.DeleteIndexRequest) request,
              (io.grpc.stub.StreamObserver<com.google.protobuf.Empty>) responseObserver);
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

  private static abstract class FirestoreAdminBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    FirestoreAdminBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.google.firestore.admin.v1beta1.FirestoreAdminProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("FirestoreAdmin");
    }
  }

  private static final class FirestoreAdminFileDescriptorSupplier
      extends FirestoreAdminBaseDescriptorSupplier {
    FirestoreAdminFileDescriptorSupplier() {}
  }

  private static final class FirestoreAdminMethodDescriptorSupplier
      extends FirestoreAdminBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    FirestoreAdminMethodDescriptorSupplier(String methodName) {
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
      synchronized (FirestoreAdminGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new FirestoreAdminFileDescriptorSupplier())
              .addMethod(getCreateIndexMethod())
              .addMethod(getListIndexesMethod())
              .addMethod(getGetIndexMethod())
              .addMethod(getDeleteIndexMethod())
              .build();
        }
      }
    }
    return result;
  }
}
