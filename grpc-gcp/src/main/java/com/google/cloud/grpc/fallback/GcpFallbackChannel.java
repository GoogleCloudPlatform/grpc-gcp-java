package com.google.cloud.grpc.fallback;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.google.common.annotations.VisibleForTesting;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public class GcpFallbackChannel extends ManagedChannel {
  private static final Logger logger = Logger.getLogger(GcpFallbackChannel.class.getName());
  private final GcpFallbackChannelOptions options;
  private final ManagedChannel primaryDelegateChannel;
  private final ManagedChannel fallbackDelegateChannel;
  private final Channel primaryChannel;
  private final Channel fallbackChannel;
  private final AtomicLong primarySuccesses = new AtomicLong(0);
  private final AtomicLong primaryFailures = new AtomicLong(0);
  private final AtomicLong fallbackSuccesses = new AtomicLong(0);
  private final AtomicLong fallbackFailures = new AtomicLong(0);
  private boolean inFallbackMode = false;
  private static final String INIT_FAILURE_REASON = "init failure";

  private final ScheduledExecutorService execService;

  public GcpFallbackChannel(
      GcpFallbackChannelOptions options,
      ManagedChannel primaryChannel,
      ManagedChannel fallbackChannel) {
    this(options, primaryChannel, fallbackChannel, null);
  }

  public GcpFallbackChannel(
      GcpFallbackChannelOptions options,
      ManagedChannelBuilder<?> primaryChannelBuilder,
      ManagedChannelBuilder<?> fallbackChannelBuilder) {
    this(options, primaryChannelBuilder, fallbackChannelBuilder, null);
  }

  @VisibleForTesting
  GcpFallbackChannel(
      GcpFallbackChannelOptions options,
      ManagedChannelBuilder<?> primaryChannelBuilder,
      ManagedChannelBuilder<?> fallbackChannelBuilder,
      ScheduledExecutorService execService) {
    checkNotNull(options);
    checkNotNull(primaryChannelBuilder);
    checkNotNull(fallbackChannelBuilder);
    if (execService != null) {
      this.execService = execService;
    } else {
      this.execService = Executors.newScheduledThreadPool(3);
    }
    this.options = options;
    ManagedChannel primaryChannel = null;
    try {
      primaryChannel = primaryChannelBuilder.build();
    } catch (Exception e) {
      logger.warning(
          String.format(
              "Primary channel initialization failed: %s. Will use fallback channel.",
              e.getMessage()));
    }
    primaryDelegateChannel = primaryChannel;
    fallbackDelegateChannel = fallbackChannelBuilder.build();
    ClientInterceptor primaryMonitoringInterceptor =
        new MonitoringInterceptor(this::processPrimaryStatusCode);
    if (primaryDelegateChannel != null) {
      this.primaryChannel =
          ClientInterceptors.intercept(primaryDelegateChannel, primaryMonitoringInterceptor);
    } else {
      this.primaryChannel = null;
    }
    ClientInterceptor fallbackMonitoringInterceptor =
        new MonitoringInterceptor(this::processFallbackStatusCode);
    this.fallbackChannel =
        ClientInterceptors.intercept(fallbackDelegateChannel, fallbackMonitoringInterceptor);
    init();
  }

  @VisibleForTesting
  GcpFallbackChannel(
      GcpFallbackChannelOptions options,
      ManagedChannel primaryChannel,
      ManagedChannel fallbackChannel,
      ScheduledExecutorService execService) {
    checkNotNull(options);
    checkNotNull(primaryChannel);
    checkNotNull(fallbackChannel);
    if (execService != null) {
      this.execService = execService;
    } else {
      this.execService = Executors.newScheduledThreadPool(3);
    }
    this.options = options;
    primaryDelegateChannel = primaryChannel;
    fallbackDelegateChannel = fallbackChannel;
    ClientInterceptor primaryMonitorInterceptor =
        new MonitoringInterceptor(this::processPrimaryStatusCode);
    this.primaryChannel =
        ClientInterceptors.intercept(primaryDelegateChannel, primaryMonitorInterceptor);
    ClientInterceptor fallbackMonitorInterceptor =
        new MonitoringInterceptor(this::processFallbackStatusCode);
    this.fallbackChannel =
        ClientInterceptors.intercept(fallbackDelegateChannel, fallbackMonitorInterceptor);
    init();
  }

  public boolean isInFallbackMode() {
    return inFallbackMode || primaryChannel == null;
  }

  private void init() {
    if (options.getPrimaryProbingFunction() != null) {
      execService.scheduleAtFixedRate(
          this::probePrimary,
          options.getPrimaryProbingInterval().toMillis(),
          options.getPrimaryProbingInterval().toMillis(),
          MILLISECONDS);
    }

    if (options.getFallbackProbingFunction() != null) {
      execService.scheduleAtFixedRate(
          this::probeFallback,
          options.getFallbackProbingInterval().toMillis(),
          options.getFallbackProbingInterval().toMillis(),
          MILLISECONDS);
    }

    if (options.isEnableFallback()
        && options.getPeriod() != null
        && options.getPeriod().toMillis() > 0) {
      execService.scheduleAtFixedRate(
          this::checkErrorRates,
          options.getPeriod().toMillis(),
          options.getPeriod().toMillis(),
          MILLISECONDS);
    }
  }

  private void checkErrorRates() {
    long successes = primarySuccesses.getAndSet(0);
    long failures = primaryFailures.getAndSet(0);
    float errRate = 0f;
    if (failures + successes > 0) {
      errRate = (float) failures / (failures + successes);
    }
    // TODO: Report primary error rate.
    if (!isInFallbackMode() && options.isEnableFallback()) {
      if (failures >= options.getMinFailedCalls() && errRate >= options.getErrorRateThreshold()) {
        inFallbackMode = true;
      }
    }
    successes = fallbackSuccesses.getAndSet(0);
    failures = fallbackFailures.getAndSet(0);
    errRate = 0f;
    if (failures + successes > 0) {
      errRate = (float) failures / (failures + successes);
    }
    // TODO: Report fallback error rate.
  }

  private void processPrimaryStatusCode(Status.Code statusCode) {
    if (options.getErroneousStates().contains(statusCode)) {
      // Count error.
      primaryFailures.incrementAndGet();
    } else {
      // Count success.
      primarySuccesses.incrementAndGet();
    }
    // Report status code.
  }

  private void processFallbackStatusCode(Status.Code statusCode) {
    if (options.getErroneousStates().contains(statusCode)) {
      // Count error.
      fallbackFailures.incrementAndGet();
    } else {
      // Count success.
      fallbackSuccesses.incrementAndGet();
    }
    // Report status code.
  }

  private void probePrimary() {
    String result = "";
    if (primaryDelegateChannel == null) {
      result = "init failure";
    } else {
      result = options.getPrimaryProbingFunction().apply(primaryDelegateChannel);
    }
    // Report metric based on result.
  }

  private void probeFallback() {
    String result = options.getFallbackProbingFunction().apply(fallbackDelegateChannel);
    // Report metric based on result.
  }

  @Override
  public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
      MethodDescriptor<RequestT, ResponseT> methodDescriptor, CallOptions callOptions) {
    if (isInFallbackMode()) {
      return fallbackChannel.newCall(methodDescriptor, callOptions);
    }

    return primaryChannel.newCall(methodDescriptor, callOptions);
  }

  @Override
  public String authority() {
    if (isInFallbackMode()) {
      return fallbackChannel.authority();
    }

    return primaryChannel.authority();
  }

  @Override
  public ManagedChannel shutdown() {
    if (primaryDelegateChannel != null) {
      primaryDelegateChannel.shutdown();
    }
    fallbackDelegateChannel.shutdown();
    execService.shutdown();
    return this;
  }

  @Override
  public ManagedChannel shutdownNow() {
    if (primaryDelegateChannel != null) {
      primaryDelegateChannel.shutdownNow();
    }
    fallbackDelegateChannel.shutdownNow();
    execService.shutdownNow();
    return this;
  }

  @Override
  public boolean isShutdown() {
    if (primaryDelegateChannel != null && !primaryDelegateChannel.isShutdown()) {
      return false;
    }

    return fallbackDelegateChannel.isShutdown() && execService.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    if (primaryDelegateChannel != null && !primaryDelegateChannel.isTerminated()) {
      return false;
    }

    return fallbackDelegateChannel.isTerminated() && execService.isTerminated();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    long endTimeNanos = System.nanoTime() + unit.toNanos(timeout);
    if (primaryDelegateChannel != null) {
      boolean terminated = primaryDelegateChannel.awaitTermination(timeout, unit);
      if (!terminated) {
        return false;
      }
    }

    long awaitTimeNanos = endTimeNanos - System.nanoTime();
    boolean terminated = fallbackDelegateChannel.awaitTermination(awaitTimeNanos, NANOSECONDS);
    if (!terminated) {
      return false;
    }

    awaitTimeNanos = endTimeNanos - System.nanoTime();
    return execService.awaitTermination(awaitTimeNanos, NANOSECONDS);
  }
}
