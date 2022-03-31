package io.grpc.echo;

import io.grpc.*;

public class MetricsClientInterceptor implements ClientInterceptor {
    final private EchoClient echoClient;

    public MetricsClientInterceptor(EchoClient echoClient) {
        this.echoClient = echoClient;
    }

    public static boolean needsInterception(Args args) {
        return !args.metricName.isEmpty();
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            private boolean sawGfe = false;

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.put(Metadata.Key.of("x-return-encrypted-headers", Metadata.ASCII_STRING_MARSHALLER), "true");
                super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                    @Override
                    public void onHeaders(Metadata headers) {
                        if (!sawGfe) {
                            sawGfe = headers.containsKey(Metadata.Key.of("x-encrypted-debug-headers", Metadata.ASCII_STRING_MARSHALLER));
                        }
                        super.onHeaders(headers);
                    }

                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        // Report status, saw GFE
                        if (!status.equals(Status.OK)) {
                            echoClient.registerRpcError(status, sawGfe);
                        }
                        super.onClose(status, trailers);
                    }
                }, headers);
            }
        };
    }
}
