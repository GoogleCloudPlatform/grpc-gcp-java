package com.google.api.servicecontrol.v1;

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
 * [Google Service Control API](/service-control/overview)
 * Lets clients check and report operations against a [managed
 * service](https://cloud.google.com/service-management/reference/rpc/google.api/servicemanagement.v1#google.api.servicemanagement.v1.ManagedService).
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.23.0-SNAPSHOT)",
    comments = "Source: google/api/servicecontrol/v1/service_controller.proto")
public final class ServiceControllerGrpc {

  private ServiceControllerGrpc() {}

  public static final String SERVICE_NAME = "google.api.servicecontrol.v1.ServiceController";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.google.api.servicecontrol.v1.CheckRequest,
      com.google.api.servicecontrol.v1.CheckResponse> getCheckMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Check",
      requestType = com.google.api.servicecontrol.v1.CheckRequest.class,
      responseType = com.google.api.servicecontrol.v1.CheckResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.google.api.servicecontrol.v1.CheckRequest,
      com.google.api.servicecontrol.v1.CheckResponse> getCheckMethod() {
    io.grpc.MethodDescriptor<com.google.api.servicecontrol.v1.CheckRequest, com.google.api.servicecontrol.v1.CheckResponse> getCheckMethod;
    if ((getCheckMethod = ServiceControllerGrpc.getCheckMethod) == null) {
      synchronized (ServiceControllerGrpc.class) {
        if ((getCheckMethod = ServiceControllerGrpc.getCheckMethod) == null) {
          ServiceControllerGrpc.getCheckMethod = getCheckMethod = 
              io.grpc.MethodDescriptor.<com.google.api.servicecontrol.v1.CheckRequest, com.google.api.servicecontrol.v1.CheckResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "google.api.servicecontrol.v1.ServiceController", "Check"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.google.api.servicecontrol.v1.CheckRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.google.api.servicecontrol.v1.CheckResponse.getDefaultInstance()))
                  .setSchemaDescriptor(new ServiceControllerMethodDescriptorSupplier("Check"))
                  .build();
          }
        }
     }
     return getCheckMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.google.api.servicecontrol.v1.ReportRequest,
      com.google.api.servicecontrol.v1.ReportResponse> getReportMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Report",
      requestType = com.google.api.servicecontrol.v1.ReportRequest.class,
      responseType = com.google.api.servicecontrol.v1.ReportResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.google.api.servicecontrol.v1.ReportRequest,
      com.google.api.servicecontrol.v1.ReportResponse> getReportMethod() {
    io.grpc.MethodDescriptor<com.google.api.servicecontrol.v1.ReportRequest, com.google.api.servicecontrol.v1.ReportResponse> getReportMethod;
    if ((getReportMethod = ServiceControllerGrpc.getReportMethod) == null) {
      synchronized (ServiceControllerGrpc.class) {
        if ((getReportMethod = ServiceControllerGrpc.getReportMethod) == null) {
          ServiceControllerGrpc.getReportMethod = getReportMethod = 
              io.grpc.MethodDescriptor.<com.google.api.servicecontrol.v1.ReportRequest, com.google.api.servicecontrol.v1.ReportResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "google.api.servicecontrol.v1.ServiceController", "Report"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.google.api.servicecontrol.v1.ReportRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.google.api.servicecontrol.v1.ReportResponse.getDefaultInstance()))
                  .setSchemaDescriptor(new ServiceControllerMethodDescriptorSupplier("Report"))
                  .build();
          }
        }
     }
     return getReportMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ServiceControllerStub newStub(io.grpc.Channel channel) {
    return new ServiceControllerStub(channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ServiceControllerBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new ServiceControllerBlockingStub(channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ServiceControllerFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new ServiceControllerFutureStub(channel);
  }

  /**
   * <pre>
   * [Google Service Control API](/service-control/overview)
   * Lets clients check and report operations against a [managed
   * service](https://cloud.google.com/service-management/reference/rpc/google.api/servicemanagement.v1#google.api.servicemanagement.v1.ManagedService).
   * </pre>
   */
  public static abstract class ServiceControllerImplBase implements io.grpc.BindableService {

    /**
     * <pre>
     * Checks an operation with Google Service Control to decide whether
     * the given operation should proceed. It should be called before the
     * operation is executed.
     * If feasible, the client should cache the check results and reuse them for
     * 60 seconds. In case of server errors, the client can rely on the cached
     * results for longer time.
     * NOTE: the [CheckRequest][google.api.servicecontrol.v1.CheckRequest] has the
     * size limit of 64KB.
     * This method requires the `servicemanagement.services.check` permission
     * on the specified service. For more information, see
     * [Google Cloud IAM](https://cloud.google.com/iam).
     * </pre>
     */
    public void check(com.google.api.servicecontrol.v1.CheckRequest request,
        io.grpc.stub.StreamObserver<com.google.api.servicecontrol.v1.CheckResponse> responseObserver) {
      asyncUnimplementedUnaryCall(getCheckMethod(), responseObserver);
    }

    /**
     * <pre>
     * Reports operation results to Google Service Control, such as logs and
     * metrics. It should be called after an operation is completed.
     * If feasible, the client should aggregate reporting data for up to 5
     * seconds to reduce API traffic. Limiting aggregation to 5 seconds is to
     * reduce data loss during client crashes. Clients should carefully choose
     * the aggregation time window to avoid data loss risk more than 0.01%
     * for business and compliance reasons.
     * NOTE: the [ReportRequest][google.api.servicecontrol.v1.ReportRequest] has
     * the size limit of 1MB.
     * This method requires the `servicemanagement.services.report` permission
     * on the specified service. For more information, see
     * [Google Cloud IAM](https://cloud.google.com/iam).
     * </pre>
     */
    public void report(com.google.api.servicecontrol.v1.ReportRequest request,
        io.grpc.stub.StreamObserver<com.google.api.servicecontrol.v1.ReportResponse> responseObserver) {
      asyncUnimplementedUnaryCall(getReportMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getCheckMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                com.google.api.servicecontrol.v1.CheckRequest,
                com.google.api.servicecontrol.v1.CheckResponse>(
                  this, METHODID_CHECK)))
          .addMethod(
            getReportMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                com.google.api.servicecontrol.v1.ReportRequest,
                com.google.api.servicecontrol.v1.ReportResponse>(
                  this, METHODID_REPORT)))
          .build();
    }
  }

  /**
   * <pre>
   * [Google Service Control API](/service-control/overview)
   * Lets clients check and report operations against a [managed
   * service](https://cloud.google.com/service-management/reference/rpc/google.api/servicemanagement.v1#google.api.servicemanagement.v1.ManagedService).
   * </pre>
   */
  public static final class ServiceControllerStub extends io.grpc.stub.AbstractStub<ServiceControllerStub> {
    private ServiceControllerStub(io.grpc.Channel channel) {
      super(channel);
    }

    private ServiceControllerStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ServiceControllerStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new ServiceControllerStub(channel, callOptions);
    }

    /**
     * <pre>
     * Checks an operation with Google Service Control to decide whether
     * the given operation should proceed. It should be called before the
     * operation is executed.
     * If feasible, the client should cache the check results and reuse them for
     * 60 seconds. In case of server errors, the client can rely on the cached
     * results for longer time.
     * NOTE: the [CheckRequest][google.api.servicecontrol.v1.CheckRequest] has the
     * size limit of 64KB.
     * This method requires the `servicemanagement.services.check` permission
     * on the specified service. For more information, see
     * [Google Cloud IAM](https://cloud.google.com/iam).
     * </pre>
     */
    public void check(com.google.api.servicecontrol.v1.CheckRequest request,
        io.grpc.stub.StreamObserver<com.google.api.servicecontrol.v1.CheckResponse> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getCheckMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Reports operation results to Google Service Control, such as logs and
     * metrics. It should be called after an operation is completed.
     * If feasible, the client should aggregate reporting data for up to 5
     * seconds to reduce API traffic. Limiting aggregation to 5 seconds is to
     * reduce data loss during client crashes. Clients should carefully choose
     * the aggregation time window to avoid data loss risk more than 0.01%
     * for business and compliance reasons.
     * NOTE: the [ReportRequest][google.api.servicecontrol.v1.ReportRequest] has
     * the size limit of 1MB.
     * This method requires the `servicemanagement.services.report` permission
     * on the specified service. For more information, see
     * [Google Cloud IAM](https://cloud.google.com/iam).
     * </pre>
     */
    public void report(com.google.api.servicecontrol.v1.ReportRequest request,
        io.grpc.stub.StreamObserver<com.google.api.servicecontrol.v1.ReportResponse> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getReportMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * <pre>
   * [Google Service Control API](/service-control/overview)
   * Lets clients check and report operations against a [managed
   * service](https://cloud.google.com/service-management/reference/rpc/google.api/servicemanagement.v1#google.api.servicemanagement.v1.ManagedService).
   * </pre>
   */
  public static final class ServiceControllerBlockingStub extends io.grpc.stub.AbstractStub<ServiceControllerBlockingStub> {
    private ServiceControllerBlockingStub(io.grpc.Channel channel) {
      super(channel);
    }

    private ServiceControllerBlockingStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ServiceControllerBlockingStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new ServiceControllerBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Checks an operation with Google Service Control to decide whether
     * the given operation should proceed. It should be called before the
     * operation is executed.
     * If feasible, the client should cache the check results and reuse them for
     * 60 seconds. In case of server errors, the client can rely on the cached
     * results for longer time.
     * NOTE: the [CheckRequest][google.api.servicecontrol.v1.CheckRequest] has the
     * size limit of 64KB.
     * This method requires the `servicemanagement.services.check` permission
     * on the specified service. For more information, see
     * [Google Cloud IAM](https://cloud.google.com/iam).
     * </pre>
     */
    public com.google.api.servicecontrol.v1.CheckResponse check(com.google.api.servicecontrol.v1.CheckRequest request) {
      return blockingUnaryCall(
          getChannel(), getCheckMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Reports operation results to Google Service Control, such as logs and
     * metrics. It should be called after an operation is completed.
     * If feasible, the client should aggregate reporting data for up to 5
     * seconds to reduce API traffic. Limiting aggregation to 5 seconds is to
     * reduce data loss during client crashes. Clients should carefully choose
     * the aggregation time window to avoid data loss risk more than 0.01%
     * for business and compliance reasons.
     * NOTE: the [ReportRequest][google.api.servicecontrol.v1.ReportRequest] has
     * the size limit of 1MB.
     * This method requires the `servicemanagement.services.report` permission
     * on the specified service. For more information, see
     * [Google Cloud IAM](https://cloud.google.com/iam).
     * </pre>
     */
    public com.google.api.servicecontrol.v1.ReportResponse report(com.google.api.servicecontrol.v1.ReportRequest request) {
      return blockingUnaryCall(
          getChannel(), getReportMethod(), getCallOptions(), request);
    }
  }

  /**
   * <pre>
   * [Google Service Control API](/service-control/overview)
   * Lets clients check and report operations against a [managed
   * service](https://cloud.google.com/service-management/reference/rpc/google.api/servicemanagement.v1#google.api.servicemanagement.v1.ManagedService).
   * </pre>
   */
  public static final class ServiceControllerFutureStub extends io.grpc.stub.AbstractStub<ServiceControllerFutureStub> {
    private ServiceControllerFutureStub(io.grpc.Channel channel) {
      super(channel);
    }

    private ServiceControllerFutureStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ServiceControllerFutureStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new ServiceControllerFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Checks an operation with Google Service Control to decide whether
     * the given operation should proceed. It should be called before the
     * operation is executed.
     * If feasible, the client should cache the check results and reuse them for
     * 60 seconds. In case of server errors, the client can rely on the cached
     * results for longer time.
     * NOTE: the [CheckRequest][google.api.servicecontrol.v1.CheckRequest] has the
     * size limit of 64KB.
     * This method requires the `servicemanagement.services.check` permission
     * on the specified service. For more information, see
     * [Google Cloud IAM](https://cloud.google.com/iam).
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.google.api.servicecontrol.v1.CheckResponse> check(
        com.google.api.servicecontrol.v1.CheckRequest request) {
      return futureUnaryCall(
          getChannel().newCall(getCheckMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Reports operation results to Google Service Control, such as logs and
     * metrics. It should be called after an operation is completed.
     * If feasible, the client should aggregate reporting data for up to 5
     * seconds to reduce API traffic. Limiting aggregation to 5 seconds is to
     * reduce data loss during client crashes. Clients should carefully choose
     * the aggregation time window to avoid data loss risk more than 0.01%
     * for business and compliance reasons.
     * NOTE: the [ReportRequest][google.api.servicecontrol.v1.ReportRequest] has
     * the size limit of 1MB.
     * This method requires the `servicemanagement.services.report` permission
     * on the specified service. For more information, see
     * [Google Cloud IAM](https://cloud.google.com/iam).
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.google.api.servicecontrol.v1.ReportResponse> report(
        com.google.api.servicecontrol.v1.ReportRequest request) {
      return futureUnaryCall(
          getChannel().newCall(getReportMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_CHECK = 0;
  private static final int METHODID_REPORT = 1;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final ServiceControllerImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(ServiceControllerImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_CHECK:
          serviceImpl.check((com.google.api.servicecontrol.v1.CheckRequest) request,
              (io.grpc.stub.StreamObserver<com.google.api.servicecontrol.v1.CheckResponse>) responseObserver);
          break;
        case METHODID_REPORT:
          serviceImpl.report((com.google.api.servicecontrol.v1.ReportRequest) request,
              (io.grpc.stub.StreamObserver<com.google.api.servicecontrol.v1.ReportResponse>) responseObserver);
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

  private static abstract class ServiceControllerBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ServiceControllerBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.google.api.servicecontrol.v1.ServiceControllerProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("ServiceController");
    }
  }

  private static final class ServiceControllerFileDescriptorSupplier
      extends ServiceControllerBaseDescriptorSupplier {
    ServiceControllerFileDescriptorSupplier() {}
  }

  private static final class ServiceControllerMethodDescriptorSupplier
      extends ServiceControllerBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    ServiceControllerMethodDescriptorSupplier(String methodName) {
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
      synchronized (ServiceControllerGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ServiceControllerFileDescriptorSupplier())
              .addMethod(getCheckMethod())
              .addMethod(getReportMethod())
              .build();
        }
      }
    }
    return result;
  }
}
