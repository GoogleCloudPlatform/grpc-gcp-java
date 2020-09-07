package com.google.cloud;
  
import java.io.*;
import com.google.cloud.bigtable.data.v2.BigtableDataClient;
import com.google.cloud.bigtable.data.v2.models.*;
import com.google.cloud.bigtable.data.v2.BigtableDataSettings;
import java.net.*;
import io.grpc.*;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.api.gax.grpc.InstantiatingGrpcChannelProvider;
import com.google.api.core.ApiFunction;

public class Util{
  // Construct a bigtable dataclient with checking peer IP enabled
  public static BigtableDataClient peerIpDataClient(String projectId, String instanceId, String dataEndPoint) throws IOException {
    BigtableDataSettings.Builder dataSettings = BigtableDataSettings.newBuilder().setProjectId(projectId).setInstanceId(instanceId);
    //dataSettings.stubSettings().setEndpoint(dataEndPoint);
    TransportChannelProvider channelProvider =
      dataSettings.stubSettings().getTransportChannelProvider();
    InstantiatingGrpcChannelProvider defaultTransportProvider =
      (InstantiatingGrpcChannelProvider) channelProvider;
    InstantiatingGrpcChannelProvider instrumentedTransportChannelProvider =
      defaultTransportProvider
      .toBuilder()
      .setChannelConfigurator(
          new ApiFunction<ManagedChannelBuilder, ManagedChannelBuilder>() {
            @Override
            public ManagedChannelBuilder apply(ManagedChannelBuilder builder) {
              builder.intercept(addressCheckInterceptor());
              return builder;
            }
          })
    .build();
    dataSettings.stubSettings().setTransportChannelProvider(instrumentedTransportChannelProvider);
    return BigtableDataClient.create(dataSettings.build());
  }

  // Captures the request attributes "Grpc.TRANSPORT_ATTR_REMOTE_ADDR" when connection is
  // established and check the peer IP address 
  private static ClientInterceptor addressCheckInterceptor() {
    return new ClientInterceptor() {
      @Override
      public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
          MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        final ClientCall<ReqT, RespT> clientCall = next.newCall(method, callOptions);
        return new SimpleForwardingClientCall<ReqT, RespT>(clientCall) {
          @Override
          public void start(Listener<RespT> responseListener, Metadata headers) {
            super.start(
                new SimpleForwardingClientCallListener<RespT>(responseListener) {
                  @Override
                  public void onHeaders(Metadata headers) {
                    // Check peer IP after connection is established.
                    SocketAddress remoteAddr =
                        clientCall.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
                    InetAddress inetAddress = ((InetSocketAddress) remoteAddr).getAddress();
                    String addr = inetAddress.getHostAddress();
                    System.out.println("peer ip = " + addr);
                    super.onHeaders(headers);
                  }
                },
                headers);
          }
        };
      }
    };
  }
}
