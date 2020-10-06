package io.grpc.bigtable;
  
import java.io.IOException;
import com.google.cloud.bigtable.data.v2.BigtableDataClient;
import com.google.cloud.bigtable.data.v2.models.*;
import com.google.cloud.bigtable.data.v2.BigtableDataSettings;
import java.net.SocketAddress;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import io.grpc.Grpc;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.api.gax.grpc.InstantiatingGrpcChannelProvider;
import com.google.api.core.ApiFunction;
import java.util.logging.Logger;

public class Util{
	private static final Logger logger = Logger.getLogger(TestMain.class.getName());

  /**
  * Construct a BigTable DataClient that can check peer IP address.
  */
  public static final BigtableDataClient peerIpDataClient(String projectId, String instanceId) throws IOException {
    BigtableDataSettings.Builder dataSettings = BigtableDataSettings.newBuilder().setProjectId(projectId).setInstanceId(instanceId);
    // dataSettings.stubSettings().setEndpoint(dataEndPoint);
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

  private static final ClientInterceptor addressCheckInterceptor() {
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
										logger.info("peer ip = " + addr);
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
