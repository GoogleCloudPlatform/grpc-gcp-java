package com.google.cloud.grpc;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.grpc.GcpManagedChannelOptions.GcpChannelPoolOptions;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ChannelIdPropagationTest {

  private static class FakeMarshaller<T> implements MethodDescriptor.Marshaller<T> {
    @Override
    public InputStream stream(T value) {
      return null;
    }

    @Override
    public T parse(InputStream stream) {
      return null;
    }
  }

  @Test
  public void testChannelIdKeySetWithoutAffinity() {
    ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress("localhost", 443);
    final AtomicInteger channelId = new AtomicInteger(-1);

    ClientInterceptor channelIdInterceptor =
        new ClientInterceptor() {
          @Override
          public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
              MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
            return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                next.newCall(method, callOptions)) {
              @Override
              public void start(Listener<RespT> responseListener, Metadata headers) {
                if (callOptions.getOption(GcpManagedChannel.CHANNEL_ID_KEY) != null) {
                  channelId.set(callOptions.getOption(GcpManagedChannel.CHANNEL_ID_KEY));
                }
                super.start(responseListener, headers);
              }
            };
          }
        };

    // Add interceptor to the delegate builder so it runs on the underlying channels.
    builder.intercept(channelIdInterceptor);

    // Creating a pool.
    final GcpManagedChannel pool =
        (GcpManagedChannel)
            GcpManagedChannelBuilder.forDelegateBuilder(builder)
                .withOptions(
                    GcpManagedChannelOptions.newBuilder()
                        .withChannelPoolOptions(
                            GcpChannelPoolOptions.newBuilder().setMinSize(3).setMaxSize(3).build())
                        .build())
                .build();

    MethodDescriptor<String, String> methodDescriptor =
        MethodDescriptor.<String, String>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("test/method")
            .setRequestMarshaller(new FakeMarshaller<>())
            .setResponseMarshaller(new FakeMarshaller<>())
            .build();

    // Use the pool directly (interceptor is already inside)
    ClientCall<String, String> newCall = pool.newCall(methodDescriptor, CallOptions.DEFAULT);
    Metadata headers = new Metadata();

    // First call (should initialize channel and correct ID)
    newCall.start(
        new ForwardingClientCall.SimpleForwardingClientCall.Listener<String>() {}, headers);

    // Should be one of the possible 3 ids: 0, 1, 2.
    assertThat(channelId.get()).isAnyOf(0, 1, 2);

    // Attempt 2
    newCall = pool.newCall(methodDescriptor, CallOptions.DEFAULT);
    newCall.start(
        new ForwardingClientCall.SimpleForwardingClientCall.Listener<String>() {}, headers);

    // Should be one of the possible 3 ids: 0, 1, 2.
    assertThat(channelId.get()).isAnyOf(0, 1, 2);

    pool.shutdownNow();
  }

  @Test
  public void testChannelIdAffinityRoutesToExistingChannelWithoutAffinityMap() {
    ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress("localhost", 443);
    final AtomicInteger channelId = new AtomicInteger(-1);

    builder.intercept(channelIdCapturingInterceptor(channelId));

    final GcpManagedChannel pool =
        (GcpManagedChannel)
            GcpManagedChannelBuilder.forDelegateBuilder(builder)
                .withOptions(
                    GcpManagedChannelOptions.newBuilder()
                        .withChannelPoolOptions(
                            GcpChannelPoolOptions.newBuilder().setMinSize(3).setMaxSize(3).build())
                        .build())
                .build();

    MethodDescriptor<String, String> methodDescriptor = testMethodDescriptor();
    AtomicReference<Integer> channelIdRef = new AtomicReference<>(1);

    ClientCall<String, String> newCall =
        pool.newCall(
            methodDescriptor,
            CallOptions.DEFAULT.withOption(
                GcpManagedChannel.CHANNEL_ID_AFFINITY_KEY, channelIdRef));
    newCall.start(
        new ForwardingClientCall.SimpleForwardingClientCall.Listener<String>() {}, new Metadata());

    assertThat(channelId.get()).isEqualTo(1);
    assertThat(channelIdRef.get()).isEqualTo(1);
    assertThat(pool.affinityKeyToChannelRef).isEmpty();

    pool.shutdownNow();
  }

  @Test
  public void testChannelIdAffinityPicksAndStoresChannelWhenUnsetOrStale() {
    ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress("localhost", 443);
    final AtomicInteger channelId = new AtomicInteger(-1);

    builder.intercept(channelIdCapturingInterceptor(channelId));

    final GcpManagedChannel pool =
        (GcpManagedChannel)
            GcpManagedChannelBuilder.forDelegateBuilder(builder)
                .withOptions(
                    GcpManagedChannelOptions.newBuilder()
                        .withChannelPoolOptions(
                            GcpChannelPoolOptions.newBuilder().setMinSize(3).setMaxSize(3).build())
                        .build())
                .build();

    MethodDescriptor<String, String> methodDescriptor = testMethodDescriptor();
    AtomicReference<Integer> channelIdRef = new AtomicReference<>();

    ClientCall<String, String> newCall =
        pool.newCall(
            methodDescriptor,
            CallOptions.DEFAULT.withOption(
                GcpManagedChannel.CHANNEL_ID_AFFINITY_KEY, channelIdRef));
    newCall.start(
        new ForwardingClientCall.SimpleForwardingClientCall.Listener<String>() {}, new Metadata());

    assertThat(channelIdRef.get()).isAnyOf(0, 1, 2);
    assertThat(channelId.get()).isEqualTo(channelIdRef.get());
    assertThat(pool.affinityKeyToChannelRef).isEmpty();

    channelIdRef.set(99);
    newCall =
        pool.newCall(
            methodDescriptor,
            CallOptions.DEFAULT.withOption(
                GcpManagedChannel.CHANNEL_ID_AFFINITY_KEY, channelIdRef));
    newCall.start(
        new ForwardingClientCall.SimpleForwardingClientCall.Listener<String>() {}, new Metadata());

    assertThat(channelIdRef.get()).isAnyOf(0, 1, 2);
    assertThat(channelId.get()).isEqualTo(channelIdRef.get());
    assertThat(pool.affinityKeyToChannelRef).isEmpty();

    pool.shutdownNow();
  }

  private static MethodDescriptor<String, String> testMethodDescriptor() {
    return MethodDescriptor.<String, String>newBuilder()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("test/method")
        .setRequestMarshaller(new FakeMarshaller<>())
        .setResponseMarshaller(new FakeMarshaller<>())
        .build();
  }

  private static ClientInterceptor channelIdCapturingInterceptor(AtomicInteger channelId) {
    return new ClientInterceptor() {
      @Override
      public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
          MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
            next.newCall(method, callOptions)) {
          @Override
          public void start(Listener<RespT> responseListener, Metadata headers) {
            if (callOptions.getOption(GcpManagedChannel.CHANNEL_ID_KEY) != null) {
              channelId.set(callOptions.getOption(GcpManagedChannel.CHANNEL_ID_KEY));
            }
            super.start(responseListener, headers);
          }
        };
      }
    };
  }
}
