package io.grpc.echo;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.Context;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import java.util.logging.Logger;

public class HeaderClientInterceptor implements ClientInterceptor {
  private int counter = 0;
  private boolean header;
  private String cookie;
  private String resComp;
  private String token;
  private boolean debugHeader;

  private static final Logger logger = Logger.getLogger(HeaderClientInterceptor.class.getName());

  public HeaderClientInterceptor(Args args) {
    this.cookie = args.cookie;
    this.header = args.header;
    this.resComp = args.resComp;
    this.token = args.token;
    this.debugHeader = args.debugHeader;
  }

  public static boolean needsInterception(Args args) {
    return args.header || args.debugHeader || !args.cookie.isEmpty() || !args.token.isEmpty() || !args.resComp.isEmpty();
  }

  /**
   * Intercept {@link ClientCall} creation by the {@code next} {@link Channel}.
   *
   * <p>Many variations of interception are possible. Complex implementations may return a wrapper
   * around the result of {@code next.newCall()}, whereas a simpler implementation may just modify
   * the header metadata prior to returning the result of {@code next.newCall()}.
   *
   * <p>{@code next.newCall()} <strong>must not</strong> be called under a different {@link Context}
   * other than the current {@code Context}. The outcome of such usage is undefined and may cause
   * memory leak due to unbounded chain of {@code Context}s.
   *
   * @param method the remote method to be called.
   * @param callOptions the runtime options to be applied to this call.
   * @param next the channel which is being intercepted.
   * @return the call object for the remote operation, never {@code null}.
   */
  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
    return new SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
      @Override
      public void start(Listener<RespT> responseListener, Metadata headers) {
        if (counter == 10 && !cookie.isEmpty()) {
          //only add cookie header to the 11th request
          logger.info("Adding cookie to header: " + cookie);
          headers.put(Metadata.Key.of("Cookie", Metadata.ASCII_STRING_MARSHALLER), cookie);
        }

        if (!resComp.isEmpty()) {
          headers.put(Metadata.Key.of("x-response-encoding", Metadata.ASCII_STRING_MARSHALLER), resComp);
        }

        if (!token.isEmpty()) {
          headers.put(Metadata.Key.of("x-custom-auth-ticket", Metadata.ASCII_STRING_MARSHALLER), token);
        }

        if (debugHeader) {
          headers.put(Metadata.Key.of("x-return-encrypted-headers", Metadata.ASCII_STRING_MARSHALLER), "true");
        }

        logger.info("Header from client: " + headers);

        super.start(new SimpleForwardingClientCallListener<RespT>(responseListener) {
          @Override
          public void onHeaders(Metadata headers) {
            if (HeaderClientInterceptor.this.header) {
              logger.info("Header received from server: " + headers);
            }
            super.onHeaders(headers);
          }
        }, headers);

        counter++;
      }
    };
  }
}
