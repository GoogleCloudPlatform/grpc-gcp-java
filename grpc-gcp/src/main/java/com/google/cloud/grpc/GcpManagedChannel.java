/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.grpc;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.cloud.grpc.GcpManagedChannelOptions.GcpChannelPoolOptions;
import com.google.cloud.grpc.GcpManagedChannelOptions.GcpMetricsOptions;
import com.google.cloud.grpc.GcpManagedChannelOptions.GcpResiliencyOptions;
import com.google.cloud.grpc.proto.AffinityConfig;
import com.google.cloud.grpc.proto.ApiConfig;
import com.google.cloud.grpc.proto.MethodConfig;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.TextFormat;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ConnectivityState;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.opencensus.common.ToLongFunction;
import io.opencensus.metrics.DerivedLongCumulative;
import io.opencensus.metrics.DerivedLongGauge;
import io.opencensus.metrics.LabelKey;
import io.opencensus.metrics.LabelValue;
import io.opencensus.metrics.MetricOptions;
import io.opencensus.metrics.MetricRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** A channel management factory that implements grpc.Channel APIs. */
public class GcpManagedChannel extends ManagedChannel {
  private static final Logger logger = Logger.getLogger(GcpManagedChannel.class.getName());
  static final AtomicInteger channelPoolIndex = new AtomicInteger();

  // Counter for tracking channel ids.
  final AtomicInteger nextChannelId = new AtomicInteger();
  static final int DEFAULT_MAX_CHANNEL = 10;
  static final int DEFAULT_MAX_STREAM = 100;
  public static final Context.Key<Boolean> DISABLE_AFFINITY_CTX_KEY =
      Context.keyWithDefault("DisableAffinity", false);
  public static final CallOptions.Key<Boolean> DISABLE_AFFINITY_KEY =
      CallOptions.Key.createWithDefault("DisableAffinity", false);
  public static final Context.Key<String> AFFINITY_CTX_KEY = Context.key("AffinityKey");
  public static final CallOptions.Key<String> AFFINITY_KEY = CallOptions.Key.create("AffinityKey");

  @GuardedBy("this")
  private Integer bindingIndex = -1;

  private final ManagedChannelBuilder<?> delegateChannelBuilder;
  private final GcpManagedChannelOptions options;
  private final boolean fallbackEnabled;
  private final boolean unresponsiveDetectionEnabled;
  private final int unresponsiveMs;
  private final int unresponsiveDropCount;
  private int maxSize = DEFAULT_MAX_CHANNEL;
  private int minSize = 0;
  private int initSize = 0;
  private int minRpcPerChannel = 0;
  private int maxRpcPerChannel = 0;
  private Duration scaleDownInterval = Duration.ZERO;
  private boolean isDynamicScalingEnabled = false;
  private int maxConcurrentStreamsLowWatermark = DEFAULT_MAX_STREAM;
  private Duration affinityKeyLifetime = Duration.ZERO;

  @VisibleForTesting final Map<String, AffinityConfig> methodToAffinity = new HashMap<>();

  @VisibleForTesting
  final Map<String, ChannelRef> affinityKeyToChannelRef = new ConcurrentHashMap<>();

  @VisibleForTesting final Map<String, Long> affinityKeyLastUsed = new ConcurrentHashMap<>();

  // Map from a broken channel id to the remapped affinity keys (key => ready channel id).
  private final Map<Integer, Map<String, Integer>> fallbackMap = new ConcurrentHashMap<>();

  // The channel pool.
  @VisibleForTesting final List<ChannelRef> channelRefs = new CopyOnWriteArrayList<>();
  // A set of channels that we removed from the pool and wait for their RPCs to be completed before
  // we can shut them down.
  final Set<ChannelRef> removedChannelRefs = new HashSet<>();

  private final ExecutorService stateNotificationExecutor =
      Executors.newCachedThreadPool(
          new ThreadFactoryBuilder().setNameFormat("gcp-mc-state-notifications-%d").build());

  // Callbacks to call when state changes.
  @GuardedBy("this")
  private List<Runnable> stateChangeCallbacks = new LinkedList<>();

  // Metrics configuration.
  private MetricRegistry metricRegistry;
  private final List<LabelKey> labelKeys = new ArrayList<>();
  private final List<LabelKey> labelKeysWithResult =
      new ArrayList<>(
          Collections.singletonList(
              LabelKey.create(GcpMetricsConstants.RESULT_LABEL, GcpMetricsConstants.RESULT_DESC)));
  private final List<LabelKey> labelKeysWithDirection =
      new ArrayList<>(
          Collections.singletonList(
              LabelKey.create(
                  GcpMetricsConstants.DIRECTION_LABEL, GcpMetricsConstants.DIRECTION_LABEL_DESC)));
  private final List<LabelValue> labelValues = new ArrayList<>();
  private final List<LabelValue> labelValuesSuccess =
      new ArrayList<>(
          Collections.singletonList(LabelValue.create(GcpMetricsConstants.RESULT_SUCCESS)));
  private final List<LabelValue> labelValuesError =
      new ArrayList<>(
          Collections.singletonList(LabelValue.create(GcpMetricsConstants.RESULT_ERROR)));
  private final List<LabelValue> labelValuesUp =
      new ArrayList<>(
          Collections.singletonList(LabelValue.create(GcpMetricsConstants.DIRECTION_UP)));
  private final List<LabelValue> labelValuesDown =
      new ArrayList<>(
          Collections.singletonList(LabelValue.create(GcpMetricsConstants.DIRECTION_DOWN)));
  private String metricPrefix;
  private final String metricPoolIndex =
      String.format("pool-%d", channelPoolIndex.incrementAndGet());
  private final Map<String, Long> cumulativeMetricValues = new ConcurrentHashMap<>();
  private final ScheduledExecutorService backgroundService =
      Executors.newSingleThreadScheduledExecutor();

  // Metrics counters.
  private final AtomicInteger readyChannels = new AtomicInteger();
  private int minChannels = 0;
  private int maxChannels = 0;
  private int minReadyChannels = 0;
  private int maxReadyChannels = 0;
  private final AtomicLong numChannelConnect = new AtomicLong();
  private final AtomicLong numChannelDisconnect = new AtomicLong();
  private long minReadinessTime = 0;
  private long maxReadinessTime = 0;
  private final AtomicLong totalReadinessTime = new AtomicLong();
  private final AtomicLong readinessTimeOccurrences = new AtomicLong();
  private final AtomicInteger totalActiveStreams = new AtomicInteger();
  private int minActiveStreams = 0;
  private int maxActiveStreams = 0;
  private int minTotalActiveStreams = 0;
  private int maxTotalActiveStreams = 0;
  private int maxTotalActiveStreamsForScaleDown = 0;
  private long minOkCalls = 0;
  private long maxOkCalls = 0;
  private final AtomicLong totalOkCalls = new AtomicLong();
  private boolean minOkReported = false;
  private boolean maxOkReported = false;
  private long minErrCalls = 0;
  private long maxErrCalls = 0;
  private final AtomicLong totalErrCalls = new AtomicLong();
  private boolean minErrReported = false;
  private boolean maxErrReported = false;
  private final AtomicInteger minAffinity = new AtomicInteger();
  private final AtomicInteger maxAffinity = new AtomicInteger();
  private final AtomicInteger totalAffinityCount = new AtomicInteger();
  private final AtomicLong fallbacksSucceeded = new AtomicLong();
  private final AtomicLong fallbacksFailed = new AtomicLong();
  private final AtomicLong unresponsiveDetectionCount = new AtomicLong();
  private long minUnresponsiveMs = 0;
  private long maxUnresponsiveMs = 0;
  private long minUnresponsiveDrops = 0;
  private long maxUnresponsiveDrops = 0;
  private long scaleUpCount = 0;
  private long scaleDownCount = 0;

  /**
   * Constructor for GcpManagedChannel.
   *
   * @param delegateChannelBuilder the underlying delegate ManagedChannelBuilder.
   * @param apiConfig the ApiConfig object for configuring GcpManagedChannel.
   * @param options the options for GcpManagedChannel.
   */
  public GcpManagedChannel(
      ManagedChannelBuilder<?> delegateChannelBuilder,
      ApiConfig apiConfig,
      GcpManagedChannelOptions options) {
    loadApiConfig(apiConfig);
    this.delegateChannelBuilder = delegateChannelBuilder;
    this.options = options;
    logger.finer(
        log(
            "Created with api config: %s, and options: %s",
            apiConfig == null ? "null" : TextFormat.shortDebugString(apiConfig), options));
    initOptions();
    GcpResiliencyOptions resiliencyOptions = options.getResiliencyOptions();
    if (resiliencyOptions != null) {
      fallbackEnabled = resiliencyOptions.isNotReadyFallbackEnabled();
      unresponsiveDetectionEnabled = resiliencyOptions.isUnresponsiveDetectionEnabled();
      unresponsiveMs = resiliencyOptions.getUnresponsiveDetectionMs();
      unresponsiveDropCount = resiliencyOptions.getUnresponsiveDetectionDroppedCount();
    } else {
      fallbackEnabled = false;
      unresponsiveDetectionEnabled = false;
      unresponsiveMs = 0;
      unresponsiveDropCount = 0;
    }
    initChannels();
    GcpChannelPoolOptions channelPoolOptions = options.getChannelPoolOptions();
    if (channelPoolOptions != null) {
      affinityKeyLifetime = channelPoolOptions.getAffinityKeyLifetime();
      initCleanupTask(channelPoolOptions.getCleanupInterval());
      initScaleDownChecker(channelPoolOptions.getScaleDownInterval());
    }
  }

  /**
   * Constructor for GcpManagedChannel. Deprecated. Use the one without the poolSize and set the
   * maximum pool size in options. However, note that if setting the pool size from options then
   * concurrent streams low watermark (even the default one) will be also taken from the options and
   * not apiConfig.
   *
   * @param delegateChannelBuilder the underlying delegate ManagedChannelBuilder.
   * @param apiConfig the ApiConfig object for configuring GcpManagedChannel.
   * @param poolSize maximum number of channels the pool can have.
   * @param options the options for GcpManagedChannel.
   */
  @Deprecated
  public GcpManagedChannel(
      ManagedChannelBuilder<?> delegateChannelBuilder,
      ApiConfig apiConfig,
      int poolSize,
      GcpManagedChannelOptions options) {
    this(delegateChannelBuilder, apiConfig, options);
    if (poolSize != 0) {
      logger.finer(log("Pool size adjusted to %d", poolSize));
      this.maxSize = poolSize;
    }
  }

  private void cleanupAffinityKeys() {
    final long cutoff = System.nanoTime() - affinityKeyLifetime.toNanos();
    affinityKeyLastUsed.forEach(
        (String key, Long time) -> {
          if (time < cutoff) {
            unbind(Collections.singletonList(key));
          }
        });
  }

  private synchronized void checkScaleDown() {
    if (!isDynamicScalingEnabled) {
      return;
    }

    // Number of channels to support maximum seen (since last check) concurrent streams
    // with lowest desired utilization (minRpcPerChannel).
    int desiredSize =
        maxTotalActiveStreamsForScaleDown / minRpcPerChannel
            + ((maxTotalActiveStreamsForScaleDown % minRpcPerChannel == 0) ? 0 : 1);

    // Reset maxTotalActiveStreamsForScaleDown.
    maxTotalActiveStreamsForScaleDown = totalActiveStreams.get();

    int scaleDownTo = Math.max(minSize, desiredSize);
    // Remove those extra channels that are the oldest.
    removeOldestChannels(channelRefs.size() - scaleDownTo);

    // Shutdown removed channels where all RPCs are completed.
    List<ChannelRef> completedChRefs =
        removedChannelRefs.stream()
            .filter(chRef -> (chRef.getActiveStreamsCount() == 0))
            .collect(Collectors.toList());
    removedChannelRefs.removeAll(completedChRefs);
    for (ChannelRef channelRef : completedChRefs) {
      channelRef.getChannel().shutdown();
      // Remove channel from broken channels map.
      fallbackMap.remove(channelRef.getId());
    }
  }

  private void removeOldestChannels(int num) {
    if (num <= 0) {
      return;
    }

    // Select longest connected channels (or disconnected channels).
    final List<ChannelRef> channelsToRemove =
        channelRefs.stream()
            .sorted(Comparator.comparing(ChannelRef::getConnectedSinceNanos))
            .limit(num)
            .collect(Collectors.toList());

    // Remove from active channels.
    channelRefs.removeAll(channelsToRemove);

    for (ChannelRef channelRef : channelsToRemove) {
      channelRef.resetAffinityCount();
      if (channelRef.getState() == ConnectivityState.READY) {
        decReadyChannels(false);
      }
    }

    // Remove affinity keys mapping for the channels.
    affinityKeyToChannelRef
        .keySet()
        .removeIf(key -> channelsToRemove.contains(affinityKeyToChannelRef.get(key)));

    // Keep them aside to wait for all RPCs to complete.
    removedChannelRefs.addAll(channelsToRemove);

    // Track minimum number of channels for metrics.
    minChannels = Math.min(minChannels, channelRefs.size());
    scaleDownCount += channelsToRemove.size();

    // Removing a channel may change channel pool state.
    executeStateChangeCallbacks();
  }

  private Supplier<String> log(Supplier<String> messageSupplier) {
    return () -> String.format("%s: %s", metricPoolIndex, messageSupplier.get());
  }

  private String log(String message) {
    return String.format("%s: %s", metricPoolIndex, message);
  }

  private String log(String format, Object... args) {
    return String.format("%s: %s", metricPoolIndex, String.format(format, args));
  }

  private synchronized void initChannels() {
    while (Math.max(minSize, initSize) - getNumberOfChannels() > 0) {
      createNewChannel();
    }
  }

  private void initOptions() {
    GcpManagedChannelOptions.GcpChannelPoolOptions poolOptions = options.getChannelPoolOptions();
    if (poolOptions != null) {
      maxSize = poolOptions.getMaxSize();
      minSize = poolOptions.getMinSize();
      maxConcurrentStreamsLowWatermark = poolOptions.getConcurrentStreamsLowWatermark();
      initSize = poolOptions.getInitSize();
      minRpcPerChannel = poolOptions.getMinRpcPerChannel();
      maxRpcPerChannel = poolOptions.getMaxRpcPerChannel();
      scaleDownInterval = poolOptions.getScaleDownInterval();
      isDynamicScalingEnabled =
          minRpcPerChannel > 0 && maxRpcPerChannel > 0 && !scaleDownInterval.isZero();
    }
    initMetrics();
  }

  private synchronized void initCleanupTask(Duration cleanupInterval) {
    if (cleanupInterval.isZero()) {
      return;
    }
    backgroundService.scheduleAtFixedRate(
        this::cleanupAffinityKeys,
        cleanupInterval.toMillis(),
        cleanupInterval.toMillis(),
        MILLISECONDS);
  }

  private synchronized void initScaleDownChecker(Duration scaleDownInterval) {
    if (!isDynamicScalingEnabled || scaleDownInterval.isZero()) {
      return;
    }

    backgroundService.scheduleAtFixedRate(
        this::checkScaleDown,
        scaleDownInterval.toMillis(),
        scaleDownInterval.toMillis(),
        MILLISECONDS);
  }

  private synchronized void initLogMetrics() {
    backgroundService.scheduleAtFixedRate(this::logMetrics, 60, 60, SECONDS);
  }

  private void logMetricsOptions() {
    if (options.getMetricsOptions() != null) {
      logger.fine(log("Metrics options: %s", options.getMetricsOptions()));
    }
  }

  private void logChannelsStats() {
    logger.fine(
        log(
            "Active streams counts: [%s]",
            Joiner.on(", ")
                .join(
                    channelRefs.stream().mapToInt(ChannelRef::getActiveStreamsCount).iterator())));
    logger.fine(
        log(
            "Removed channels active streams counts: [%s]",
            Joiner.on(", ")
                .join(
                    removedChannelRefs.stream()
                        .mapToInt(ChannelRef::getActiveStreamsCount)
                        .iterator())));
    logger.fine(
        log(
            "Affinity counts: [%s]",
            Joiner.on(", ")
                .join(channelRefs.stream().mapToInt(ChannelRef::getAffinityCount).iterator())));
  }

  private void initMetrics() {
    final GcpMetricsOptions metricsOptions = options.getMetricsOptions();
    if (metricsOptions == null) {
      logger.info(log("Metrics options are empty. Metrics disabled."));
      initLogMetrics();
      return;
    }
    logMetricsOptions();
    if (metricsOptions.getMetricRegistry() == null) {
      logger.info(log("Metric registry is null. Metrics disabled."));
      initLogMetrics();
      return;
    }
    logger.info(log("Metrics enabled."));

    metricRegistry = metricsOptions.getMetricRegistry();
    labelKeys.addAll(metricsOptions.getLabelKeys());
    labelKeysWithResult.addAll(metricsOptions.getLabelKeys());
    labelKeysWithDirection.addAll(metricsOptions.getLabelKeys());
    labelValues.addAll(metricsOptions.getLabelValues());
    labelValuesSuccess.addAll(metricsOptions.getLabelValues());
    labelValuesError.addAll(metricsOptions.getLabelValues());
    labelValuesUp.addAll(metricsOptions.getLabelValues());
    labelValuesDown.addAll(metricsOptions.getLabelValues());

    final LabelKey poolKey =
        LabelKey.create(GcpMetricsConstants.POOL_INDEX_LABEL, GcpMetricsConstants.POOL_INDEX_DESC);
    labelKeys.add(poolKey);
    labelKeysWithResult.add(poolKey);
    labelKeysWithDirection.add(poolKey);
    final LabelValue poolIndex = LabelValue.create(metricPoolIndex);
    labelValues.add(poolIndex);
    labelValuesSuccess.add(poolIndex);
    labelValuesError.add(poolIndex);
    labelValuesUp.add(poolIndex);
    labelValuesDown.add(poolIndex);

    metricPrefix = metricsOptions.getNamePrefix();

    createDerivedLongGaugeTimeSeries(
        GcpMetricsConstants.METRIC_MIN_READY_CHANNELS,
        "The minimum number of channels simultaneously in the READY state.",
        GcpMetricsConstants.COUNT,
        this,
        GcpManagedChannel::reportMinReadyChannels);

    createDerivedLongGaugeTimeSeries(
        GcpMetricsConstants.METRIC_MAX_READY_CHANNELS,
        "The maximum number of channels simultaneously in the READY state.",
        GcpMetricsConstants.COUNT,
        this,
        GcpManagedChannel::reportMaxReadyChannels);

    createDerivedLongGaugeTimeSeries(
        GcpMetricsConstants.METRIC_NUM_CHANNELS,
        "The number of channels currently in the pool.",
        GcpMetricsConstants.COUNT,
        this,
        GcpManagedChannel::reportNumChannels);

    createDerivedLongGaugeTimeSeries(
        GcpMetricsConstants.METRIC_MIN_CHANNELS,
        "The minimum number of channels in the pool.",
        GcpMetricsConstants.COUNT,
        this,
        GcpManagedChannel::reportMinChannels);

    createDerivedLongGaugeTimeSeries(
        GcpMetricsConstants.METRIC_MAX_CHANNELS,
        "The maximum number of channels in the pool.",
        GcpMetricsConstants.COUNT,
        this,
        GcpManagedChannel::reportMaxChannels);

    createDerivedLongGaugeTimeSeries(
        GcpMetricsConstants.METRIC_MAX_ALLOWED_CHANNELS,
        "The maximum number of channels allowed in the pool. (The poll max size)",
        GcpMetricsConstants.COUNT,
        this,
        GcpManagedChannel::reportMaxAllowedChannels);

    createDerivedLongCumulativeTimeSeries(
        GcpMetricsConstants.METRIC_NUM_CHANNEL_DISCONNECT,
        "The number of disconnections (occurrences when a channel deviates from the READY state)",
        GcpMetricsConstants.COUNT,
        this,
        GcpManagedChannel::reportNumChannelDisconnect);

    createDerivedLongCumulativeTimeSeries(
        GcpMetricsConstants.METRIC_NUM_CHANNEL_CONNECT,
        "The number of times when a channel reached the READY state.",
        GcpMetricsConstants.COUNT,
        this,
        GcpManagedChannel::reportNumChannelConnect);

    createDerivedLongGaugeTimeSeries(
        GcpMetricsConstants.METRIC_MIN_CHANNEL_READINESS_TIME,
        "The minimum time it took to transition a channel to the READY state.",
        GcpMetricsConstants.MICROSECOND,
        this,
        GcpManagedChannel::reportMinReadinessTime);

    createDerivedLongGaugeTimeSeries(
        GcpMetricsConstants.METRIC_AVG_CHANNEL_READINESS_TIME,
        "The average time it took to transition a channel to the READY state.",
        GcpMetricsConstants.MICROSECOND,
        this,
        GcpManagedChannel::reportAvgReadinessTime);

    createDerivedLongGaugeTimeSeries(
        GcpMetricsConstants.METRIC_MAX_CHANNEL_READINESS_TIME,
        "The maximum time it took to transition a channel to the READY state.",
        GcpMetricsConstants.MICROSECOND,
        this,
        GcpManagedChannel::reportMaxReadinessTime);

    createDerivedLongGaugeTimeSeries(
        GcpMetricsConstants.METRIC_MIN_ACTIVE_STREAMS,
        "The minimum number of active streams on any channel.",
        GcpMetricsConstants.COUNT,
        this,
        GcpManagedChannel::reportMinActiveStreams);

    createDerivedLongGaugeTimeSeries(
        GcpMetricsConstants.METRIC_MAX_ACTIVE_STREAMS,
        "The maximum number of active streams on any channel.",
        GcpMetricsConstants.COUNT,
        this,
        GcpManagedChannel::reportMaxActiveStreams);

    createDerivedLongGaugeTimeSeries(
        GcpMetricsConstants.METRIC_MIN_TOTAL_ACTIVE_STREAMS,
        "The minimum total number of active streams across all channels.",
        GcpMetricsConstants.COUNT,
        this,
        GcpManagedChannel::reportMinTotalActiveStreams);

    createDerivedLongGaugeTimeSeries(
        GcpMetricsConstants.METRIC_MAX_TOTAL_ACTIVE_STREAMS,
        "The maximum total number of active streams across all channels.",
        GcpMetricsConstants.COUNT,
        this,
        GcpManagedChannel::reportMaxTotalActiveStreams);

    createDerivedLongGaugeTimeSeries(
        GcpMetricsConstants.METRIC_MIN_AFFINITY,
        "The minimum number of affinity count on any channel.",
        GcpMetricsConstants.COUNT,
        this,
        GcpManagedChannel::reportMinAffinity);

    createDerivedLongGaugeTimeSeries(
        GcpMetricsConstants.METRIC_MAX_AFFINITY,
        "The maximum number of affinity count on any channel.",
        GcpMetricsConstants.COUNT,
        this,
        GcpManagedChannel::reportMaxAffinity);

    createDerivedLongGaugeTimeSeries(
        GcpMetricsConstants.METRIC_NUM_AFFINITY,
        "The total number of affinity count across all channels.",
        GcpMetricsConstants.COUNT,
        this,
        GcpManagedChannel::reportNumAffinity);

    createDerivedLongGaugeTimeSeriesWithResult(
        GcpMetricsConstants.METRIC_MIN_CALLS,
        "The minimum number of completed calls on any channel.",
        GcpMetricsConstants.COUNT,
        this,
        GcpManagedChannel::reportMinOkCalls,
        GcpManagedChannel::reportMinErrCalls);

    createDerivedLongGaugeTimeSeriesWithResult(
        GcpMetricsConstants.METRIC_MAX_CALLS,
        "The maximum number of completed calls on any channel.",
        GcpMetricsConstants.COUNT,
        this,
        GcpManagedChannel::reportMaxOkCalls,
        GcpManagedChannel::reportMaxErrCalls);

    createDerivedLongCumulativeTimeSeriesWithResult(
        GcpMetricsConstants.METRIC_NUM_CALLS_COMPLETED,
        "The number of calls completed across all channels.",
        GcpMetricsConstants.COUNT,
        this,
        GcpManagedChannel::reportTotalOkCalls,
        GcpManagedChannel::reportTotalErrCalls);

    createDerivedLongCumulativeTimeSeriesWithResult(
        GcpMetricsConstants.METRIC_NUM_FALLBACKS,
        "The number of calls that had fallback to another channel.",
        GcpMetricsConstants.COUNT,
        this,
        GcpManagedChannel::reportSucceededFallbacks,
        GcpManagedChannel::reportFailedFallbacks);

    createDerivedLongCumulativeTimeSeries(
        GcpMetricsConstants.METRIC_NUM_UNRESPONSIVE_DETECTIONS,
        "The number of unresponsive connections detected.",
        GcpMetricsConstants.COUNT,
        this,
        GcpManagedChannel::reportUnresponsiveDetectionCount);

    createDerivedLongGaugeTimeSeries(
        GcpMetricsConstants.METRIC_MIN_UNRESPONSIVE_DETECTION_TIME,
        "The minimum time it took to detect an unresponsive connection.",
        GcpMetricsConstants.MILLISECOND,
        this,
        GcpManagedChannel::reportMinUnresponsiveMs);

    createDerivedLongGaugeTimeSeries(
        GcpMetricsConstants.METRIC_MAX_UNRESPONSIVE_DETECTION_TIME,
        "The maximum time it took to detect an unresponsive connection.",
        GcpMetricsConstants.MILLISECOND,
        this,
        GcpManagedChannel::reportMaxUnresponsiveMs);

    createDerivedLongGaugeTimeSeries(
        GcpMetricsConstants.METRIC_MIN_UNRESPONSIVE_DROPPED_CALLS,
        "The minimum calls dropped before detection of an unresponsive connection.",
        GcpMetricsConstants.MILLISECOND,
        this,
        GcpManagedChannel::reportMinUnresponsiveDrops);

    createDerivedLongGaugeTimeSeries(
        GcpMetricsConstants.METRIC_MAX_UNRESPONSIVE_DROPPED_CALLS,
        "The maximum calls dropped before detection of an unresponsive connection.",
        GcpMetricsConstants.MILLISECOND,
        this,
        GcpManagedChannel::reportMaxUnresponsiveDrops);

    createDerivedLongCumulativeTimeSeriesWithDirection(
        GcpMetricsConstants.METRIC_CHANNEL_POOL_SCALING,
        "The number of channels channel pool scaled up or down.",
        GcpMetricsConstants.COUNT,
        this,
        GcpManagedChannel::reportScaleUp,
        GcpManagedChannel::reportScaleDown);
  }

  private void logGauge(String key, long value) {
    logger.fine(log("stat: %s = %d", key, value));
  }

  private void logCumulative(String key, long value) {
    logger.fine(
        log(
            () -> {
              Long prevValue = cumulativeMetricValues.put(key, value);
              long logValue = prevValue == null ? value : value - prevValue;
              return String.format("stat: %s = %d", key, logValue);
            }));
  }

  @VisibleForTesting
  void logMetrics() {
    logMetricsOptions();
    logChannelsStats();
    reportMinReadyChannels();
    reportMaxReadyChannels();
    reportMinChannels();
    reportMaxChannels();
    reportNumChannels();
    reportMaxAllowedChannels();
    reportScaleUp();
    reportScaleDown();
    reportNumChannelDisconnect();
    reportNumChannelConnect();
    reportMinReadinessTime();
    reportAvgReadinessTime();
    reportMaxReadinessTime();
    reportMinActiveStreams();
    reportMaxActiveStreams();
    reportMinTotalActiveStreams();
    reportMaxTotalActiveStreams();
    reportMinAffinity();
    reportMaxAffinity();
    reportNumAffinity();
    reportMinOkCalls();
    reportMinErrCalls();
    reportMaxOkCalls();
    reportMaxErrCalls();
    reportTotalOkCalls();
    reportTotalErrCalls();
    reportSucceededFallbacks();
    reportFailedFallbacks();
    reportUnresponsiveDetectionCount();
    reportMinUnresponsiveMs();
    reportMaxUnresponsiveMs();
    reportMinUnresponsiveDrops();
    reportMaxUnresponsiveDrops();
  }

  private MetricOptions createMetricOptions(
      String description, List<LabelKey> labelKeys, String unit) {
    return MetricOptions.builder()
        .setDescription(description)
        .setLabelKeys(labelKeys)
        .setUnit(unit)
        .build();
  }

  private <T> void createDerivedLongGaugeTimeSeries(
      String name, String description, String unit, T obj, ToLongFunction<T> func) {
    final DerivedLongGauge metric =
        metricRegistry.addDerivedLongGauge(
            metricPrefix + name, createMetricOptions(description, labelKeys, unit));

    metric.removeTimeSeries(labelValues);
    metric.createTimeSeries(labelValues, obj, func);
  }

  private <T> void createDerivedLongGaugeTimeSeriesWithResult(
      String name,
      String description,
      String unit,
      T obj,
      ToLongFunction<T> funcSucc,
      ToLongFunction<T> funcErr) {
    final DerivedLongGauge metric =
        metricRegistry.addDerivedLongGauge(
            metricPrefix + name, createMetricOptions(description, labelKeysWithResult, unit));

    metric.removeTimeSeries(labelValuesSuccess);
    metric.createTimeSeries(labelValuesSuccess, obj, funcSucc);
    metric.removeTimeSeries(labelValuesError);
    metric.createTimeSeries(labelValuesError, obj, funcErr);
  }

  private <T> void createDerivedLongCumulativeTimeSeriesWithDirection(
      String name,
      String description,
      String unit,
      T obj,
      ToLongFunction<T> funcUp,
      ToLongFunction<T> funcDown) {
    final DerivedLongCumulative metric =
        metricRegistry.addDerivedLongCumulative(
            metricPrefix + name, createMetricOptions(description, labelKeysWithDirection, unit));

    metric.removeTimeSeries(labelValuesUp);
    metric.createTimeSeries(labelValuesUp, obj, funcUp);
    metric.removeTimeSeries(labelValuesDown);
    metric.createTimeSeries(labelValuesDown, obj, funcDown);
  }

  private <T> void createDerivedLongCumulativeTimeSeries(
      String name, String description, String unit, T obj, ToLongFunction<T> func) {
    final DerivedLongCumulative metric =
        metricRegistry.addDerivedLongCumulative(
            metricPrefix + name, createMetricOptions(description, labelKeys, unit));

    metric.removeTimeSeries(labelValues);
    metric.createTimeSeries(labelValues, obj, func);
  }

  private <T> void createDerivedLongCumulativeTimeSeriesWithResult(
      String name,
      String description,
      String unit,
      T obj,
      ToLongFunction<T> funcSucc,
      ToLongFunction<T> funcErr) {
    final DerivedLongCumulative metric =
        metricRegistry.addDerivedLongCumulative(
            metricPrefix + name, createMetricOptions(description, labelKeysWithResult, unit));

    metric.removeTimeSeries(labelValuesSuccess);
    metric.createTimeSeries(labelValuesSuccess, obj, funcSucc);
    metric.removeTimeSeries(labelValuesError);
    metric.createTimeSeries(labelValuesError, obj, funcErr);
  }

  private long reportNumChannels() {
    int value = getNumberOfChannels();
    logGauge(GcpMetricsConstants.METRIC_NUM_CHANNELS, value);
    return value;
  }

  private long reportMinChannels() {
    int value = minChannels;
    minChannels = getNumberOfChannels();
    logGauge(GcpMetricsConstants.METRIC_MIN_CHANNELS, value);
    return value;
  }

  private long reportMaxChannels() {
    int value = maxChannels;
    maxChannels = getNumberOfChannels();
    logGauge(GcpMetricsConstants.METRIC_MAX_CHANNELS, value);
    return value;
  }

  private long reportMaxAllowedChannels() {
    logGauge(GcpMetricsConstants.METRIC_MAX_ALLOWED_CHANNELS, maxSize);
    return maxSize;
  }

  private long reportMinReadyChannels() {
    int value = minReadyChannels;
    minReadyChannels = readyChannels.get();
    logGauge(GcpMetricsConstants.METRIC_MIN_READY_CHANNELS, value);
    return value;
  }

  private long reportMaxReadyChannels() {
    int value = maxReadyChannels;
    maxReadyChannels = readyChannels.get();
    logGauge(GcpMetricsConstants.METRIC_MAX_READY_CHANNELS, value);
    return value;
  }

  private long reportNumChannelConnect() {
    long value = numChannelConnect.get();
    logCumulative(GcpMetricsConstants.METRIC_NUM_CHANNEL_CONNECT, value);
    return value;
  }

  private long reportNumChannelDisconnect() {
    long value = numChannelDisconnect.get();
    logCumulative(GcpMetricsConstants.METRIC_NUM_CHANNEL_DISCONNECT, value);
    return value;
  }

  private long reportMinReadinessTime() {
    long value = minReadinessTime;
    minReadinessTime = 0;
    logGauge(GcpMetricsConstants.METRIC_MIN_CHANNEL_READINESS_TIME, value);
    return value;
  }

  private long reportAvgReadinessTime() {
    long value = 0;
    long total = totalReadinessTime.getAndSet(0);
    long occ = readinessTimeOccurrences.getAndSet(0);
    if (occ != 0) {
      value = total / occ;
    }
    logGauge(GcpMetricsConstants.METRIC_AVG_CHANNEL_READINESS_TIME, value);
    return value;
  }

  private long reportMaxReadinessTime() {
    long value = maxReadinessTime;
    maxReadinessTime = 0;
    logGauge(GcpMetricsConstants.METRIC_MAX_CHANNEL_READINESS_TIME, value);
    return value;
  }

  private int reportMinActiveStreams() {
    int value = minActiveStreams;
    minActiveStreams =
        channelRefs.stream().mapToInt(ChannelRef::getActiveStreamsCount).min().orElse(0);
    logGauge(GcpMetricsConstants.METRIC_MIN_ACTIVE_STREAMS, value);
    return value;
  }

  private int reportMaxActiveStreams() {
    int value = maxActiveStreams;
    maxActiveStreams =
        channelRefs.stream().mapToInt(ChannelRef::getActiveStreamsCount).max().orElse(0);
    logGauge(GcpMetricsConstants.METRIC_MAX_ACTIVE_STREAMS, value);
    return value;
  }

  private int reportMinTotalActiveStreams() {
    int value = minTotalActiveStreams;
    minTotalActiveStreams = totalActiveStreams.get();
    logGauge(GcpMetricsConstants.METRIC_MIN_TOTAL_ACTIVE_STREAMS, value);
    return value;
  }

  private int reportMaxTotalActiveStreams() {
    int value = maxTotalActiveStreams;
    maxTotalActiveStreams = totalActiveStreams.get();
    logGauge(GcpMetricsConstants.METRIC_MAX_TOTAL_ACTIVE_STREAMS, value);
    return value;
  }

  private int reportMinAffinity() {
    int value =
        minAffinity.getAndSet(
            channelRefs.stream().mapToInt(ChannelRef::getAffinityCount).min().orElse(0));
    logGauge(GcpMetricsConstants.METRIC_MIN_AFFINITY, value);
    return value;
  }

  private int reportMaxAffinity() {
    int value =
        maxAffinity.getAndSet(
            channelRefs.stream().mapToInt(ChannelRef::getAffinityCount).max().orElse(0));
    logGauge(GcpMetricsConstants.METRIC_MAX_AFFINITY, value);
    return value;
  }

  private int reportNumAffinity() {
    int value = totalAffinityCount.get();
    logGauge(GcpMetricsConstants.METRIC_NUM_AFFINITY, value);
    return value;
  }

  private synchronized long reportMinOkCalls() {
    minOkReported = true;
    calcMinMaxOkCalls();
    logGauge(GcpMetricsConstants.METRIC_MIN_CALLS + "_ok", minOkCalls);
    return minOkCalls;
  }

  private synchronized long reportMaxOkCalls() {
    maxOkReported = true;
    calcMinMaxOkCalls();
    logGauge(GcpMetricsConstants.METRIC_MAX_CALLS + "_ok", maxOkCalls);
    return maxOkCalls;
  }

  private long reportTotalOkCalls() {
    long value = totalOkCalls.get();
    logCumulative(GcpMetricsConstants.METRIC_NUM_CALLS_COMPLETED + "_ok", value);
    return value;
  }

  private LongSummaryStatistics calcStatsAndLog(String logLabel, ToLongFunction<ChannelRef> func) {
    StringBuilder str = new StringBuilder(logLabel + ": [");
    final LongSummaryStatistics stats =
        channelRefs.stream()
            .mapToLong(
                ch -> {
                  long count = func.applyAsLong(ch);
                  if (str.charAt(str.length() - 1) != '[') {
                    str.append(", ");
                  }
                  str.append(count);
                  return count;
                })
            .summaryStatistics();

    str.append("]");
    logger.fine(log(str.toString()));
    return stats;
  }

  private void calcMinMaxOkCalls() {
    if (minOkReported && maxOkReported) {
      minOkReported = false;
      maxOkReported = false;
      return;
    }
    final LongSummaryStatistics stats = calcStatsAndLog("Ok calls", ChannelRef::getAndResetOkCalls);
    minOkCalls = stats.getMin();
    maxOkCalls = stats.getMax();
  }

  private synchronized long reportMinErrCalls() {
    minErrReported = true;
    calcMinMaxErrCalls();
    logGauge(GcpMetricsConstants.METRIC_MIN_CALLS + "_err", minErrCalls);
    return minErrCalls;
  }

  private synchronized long reportMaxErrCalls() {
    maxErrReported = true;
    calcMinMaxErrCalls();
    logGauge(GcpMetricsConstants.METRIC_MAX_CALLS + "_err", maxErrCalls);
    return maxErrCalls;
  }

  private long reportTotalErrCalls() {
    long value = totalErrCalls.get();
    logCumulative(GcpMetricsConstants.METRIC_NUM_CALLS_COMPLETED + "_err", value);
    return value;
  }

  private void calcMinMaxErrCalls() {
    if (minErrReported && maxErrReported) {
      minErrReported = false;
      maxErrReported = false;
      return;
    }
    final LongSummaryStatistics stats =
        calcStatsAndLog("Failed calls", ChannelRef::getAndResetErrCalls);
    minErrCalls = stats.getMin();
    maxErrCalls = stats.getMax();
  }

  private long reportSucceededFallbacks() {
    long value = fallbacksSucceeded.get();
    logCumulative(GcpMetricsConstants.METRIC_NUM_FALLBACKS + "_ok", value);
    return value;
  }

  private long reportFailedFallbacks() {
    long value = fallbacksFailed.get();
    logCumulative(GcpMetricsConstants.METRIC_NUM_FALLBACKS + "_fail", value);
    return value;
  }

  private long reportUnresponsiveDetectionCount() {
    long value = unresponsiveDetectionCount.get();
    logCumulative(GcpMetricsConstants.METRIC_NUM_UNRESPONSIVE_DETECTIONS, value);
    return value;
  }

  private long reportMinUnresponsiveMs() {
    long value = minUnresponsiveMs;
    minUnresponsiveMs = 0;
    logGauge(GcpMetricsConstants.METRIC_MIN_UNRESPONSIVE_DETECTION_TIME, value);
    return value;
  }

  private long reportMaxUnresponsiveMs() {
    long value = maxUnresponsiveMs;
    maxUnresponsiveMs = 0;
    logGauge(GcpMetricsConstants.METRIC_MAX_UNRESPONSIVE_DETECTION_TIME, value);
    return value;
  }

  private long reportMinUnresponsiveDrops() {
    long value = minUnresponsiveDrops;
    minUnresponsiveDrops = 0;
    logGauge(GcpMetricsConstants.METRIC_MIN_UNRESPONSIVE_DROPPED_CALLS, value);
    return value;
  }

  private long reportMaxUnresponsiveDrops() {
    long value = maxUnresponsiveDrops;
    maxUnresponsiveDrops = 0;
    logGauge(GcpMetricsConstants.METRIC_MAX_UNRESPONSIVE_DROPPED_CALLS, value);
    return value;
  }

  private long reportScaleUp() {
    long value = scaleUpCount;
    logCumulative(GcpMetricsConstants.METRIC_CHANNEL_POOL_SCALING + "_up", value);
    return value;
  }

  private long reportScaleDown() {
    long value = scaleDownCount;
    logCumulative(GcpMetricsConstants.METRIC_CHANNEL_POOL_SCALING + "_down", value);
    return value;
  }

  private void incReadyChannels(boolean connected) {
    if (connected) {
      numChannelConnect.incrementAndGet();
    }
    final int newReady = readyChannels.incrementAndGet();
    if (maxReadyChannels < newReady) {
      maxReadyChannels = newReady;
    }
  }

  private void decReadyChannels(boolean disconnected) {
    if (disconnected) {
      numChannelDisconnect.incrementAndGet();
    }
    final int newReady = readyChannels.decrementAndGet();
    if (minReadyChannels > newReady) {
      minReadyChannels = newReady;
    }
  }

  private void saveReadinessTime(long readinessNanos) {
    long readinessTimeUs = readinessNanos / 1000;
    if (minReadinessTime == 0 || readinessTimeUs < minReadinessTime) {
      minReadinessTime = readinessTimeUs;
    }
    if (readinessTimeUs > maxReadinessTime) {
      maxReadinessTime = readinessTimeUs;
    }
    totalReadinessTime.addAndGet(readinessTimeUs);
    readinessTimeOccurrences.incrementAndGet();
  }

  private void recordUnresponsiveDetection(long nanos, long dropCount) {
    unresponsiveDetectionCount.incrementAndGet();
    final long ms = nanos / 1000000;
    if (minUnresponsiveMs == 0 || minUnresponsiveMs > ms) {
      minUnresponsiveMs = ms;
    }
    if (maxUnresponsiveMs < ms) {
      maxUnresponsiveMs = ms;
    }
    if (minUnresponsiveDrops == 0 || minUnresponsiveDrops > dropCount) {
      minUnresponsiveDrops = dropCount;
    }
    if (maxUnresponsiveDrops < dropCount) {
      maxUnresponsiveDrops = dropCount;
    }
  }

  @Override
  public void notifyWhenStateChanged(ConnectivityState source, Runnable callback) {
    if (getState(false).equals(source)) {
      synchronized (this) {
        stateChangeCallbacks.add(callback);
      }
      return;
    }

    try {
      stateNotificationExecutor.execute(callback);
    } catch (RejectedExecutionException e) {
      // Ignore exceptions on shutdown.
      logger.fine(log("State notification change task rejected: %s", e.getMessage()));
    }
  }

  /**
   * ChannelStateMonitor subscribes to channel's state changes and informs {@link GcpManagedChannel}
   * on any new state. This monitor allows to detect when a channel is not ready and temporarily
   * route requests via another ready channel if the option is enabled.
   */
  private class ChannelStateMonitor implements Runnable {
    private final ChannelRef channelRef;
    private final ManagedChannel channel;
    private ConnectivityState currentState;
    private long connectingStartNanos;
    private long connectedSinceNanos;

    private ChannelStateMonitor(ManagedChannel channel, ChannelRef channelRef) {
      this.channelRef = channelRef;
      this.channel = channel;
      run();
    }

    public long getConnectedSinceNanos() {
      return connectedSinceNanos;
    }

    public ConnectivityState getCurrentState() {
      return currentState;
    }

    @Override
    public void run() {
      if (channel == null) {
        return;
      }

      // Is the channel in the pool?
      boolean isActive = channelRefs.contains(this.channelRef);

      // Keep minSize channels always connected.
      boolean requestConnection =
          channelRefs.size() < minSize
              || channelRefs.stream()
                  .mapToInt(ChannelRef::getId)
                  .sorted()
                  .limit(minSize)
                  .anyMatch(id -> (id == channelRef.getId()));

      ConnectivityState newState = channel.getState(requestConnection);
      if (logger.isLoggable(Level.FINER)) {
        logger.finer(
            log(
                "Channel %d state change detected: %s -> %s",
                channelRef.getId(), currentState, newState));
      }
      if (newState == ConnectivityState.READY && currentState != ConnectivityState.READY) {
        connectedSinceNanos = System.nanoTime();
        if (isActive) {
          incReadyChannels(true);
          if (connectingStartNanos > 0) {
            saveReadinessTime(System.nanoTime() - connectingStartNanos);
          }
        }
        connectingStartNanos = 0;
      }
      if (isActive
          && newState != ConnectivityState.READY
          && currentState == ConnectivityState.READY) {
        decReadyChannels(true);
      }
      if (newState == ConnectivityState.CONNECTING
          && currentState != ConnectivityState.CONNECTING) {
        connectingStartNanos = System.nanoTime();
      }
      if (newState != ConnectivityState.READY) {
        connectedSinceNanos = 0;
      }
      currentState = newState;

      processChannelStateChange(channelRef.getId(), newState);
      if (isActive) {
        executeStateChangeCallbacks();
      }

      // Resubscribe.
      if (newState != ConnectivityState.SHUTDOWN) {
        channel.notifyWhenStateChanged(newState, this);
      }
    }
  }

  private synchronized void executeStateChangeCallbacks() {
    List<Runnable> callbacksToTrigger = stateChangeCallbacks;
    stateChangeCallbacks = new LinkedList<>();
    try {
      callbacksToTrigger.forEach(stateNotificationExecutor::execute);
    } catch (RejectedExecutionException e) {
      // Ignore exceptions on shutdown.
      logger.fine(log("State notification change task rejected: %s", e.getMessage()));
    }
  }

  @VisibleForTesting
  void processChannelStateChange(int channelId, ConnectivityState state) {
    if (!fallbackEnabled) {
      return;
    }
    if (state == ConnectivityState.READY || state == ConnectivityState.IDLE) {
      // Ready
      fallbackMap.remove(channelId);
      return;
    }
    // Not ready
    fallbackMap.putIfAbsent(channelId, new ConcurrentHashMap<>());
  }

  public int getMaxSize() {
    return maxSize;
  }

  public int getMinSize() {
    return minSize;
  }

  public int getNumberOfChannels() {
    return channelRefs.size();
  }

  public int getStreamsLowWatermark() {
    return maxConcurrentStreamsLowWatermark;
  }

  public int getMinActiveStreams() {
    return channelRefs.stream().mapToInt(ChannelRef::getActiveStreamsCount).min().orElse(0);
  }

  public int getMaxActiveStreams() {
    return channelRefs.stream().mapToInt(ChannelRef::getActiveStreamsCount).max().orElse(0);
  }

  /**
   * Returns a {@link ChannelRef} from the pool for a binding call. If round-robin on bind is
   * enabled, uses {@link #getChannelRefRoundRobin()} otherwise {@link #getChannelRef(String)}
   *
   * @return {@link ChannelRef} channel to use for a call.
   */
  protected ChannelRef getChannelRefForBind() {
    ChannelRef channelRef;
    if (options.getChannelPoolOptions() != null
        && options.getChannelPoolOptions().isUseRoundRobinOnBind()) {
      channelRef = getChannelRefRoundRobin();
      if (logger.isLoggable(Level.FINEST)) {
        logger.finest(
            log("Channel %d picked for bind operation using round-robin.", channelRef.getId()));
      }
    } else {
      channelRef = getChannelRef(null);
      if (logger.isLoggable(Level.FINEST)) {
        logger.finest(log("Channel %d picked for bind operation.", channelRef.getId()));
      }
    }
    return channelRef;
  }

  /**
   * Returns a {@link ChannelRef} from the pool in round-robin manner. Creates a new channel in the
   * pool until the pool reaches its max size.
   *
   * @return {@link ChannelRef}
   */
  protected synchronized ChannelRef getChannelRefRoundRobin() {
    if (!isDynamicScalingEnabled && channelRefs.size() < maxSize) {
      return createNewChannel();
    }
    bindingIndex++;
    if (bindingIndex >= channelRefs.size()) {
      bindingIndex = 0;
    }
    return channelRefs.get(bindingIndex);
  }

  /**
   * Pick a {@link ChannelRef} (and create a new one if necessary). If notReadyFallbackEnabled is
   * true in the {@link GcpResiliencyOptions} then instead of a channel in a non-READY state another
   * channel in the READY state and having fewer than maximum allowed number of active streams will
   * be provided if available. Subsequent calls with the same affinity key will provide the same
   * fallback channel as long as the fallback channel is in the READY state.
   *
   * @param key affinity key. If it is specified, pick the ChannelRef bound with the affinity key.
   *     Otherwise pick the one with the smallest number of streams.
   */
  protected ChannelRef getChannelRef(@Nullable String key) {
    if (key == null || key.isEmpty()) {
      return pickLeastBusyChannel(/* forFallback= */ false);
    }
    ChannelRef mappedChannel = affinityKeyToChannelRef.get(key);
    affinityKeyLastUsed.put(key, System.nanoTime());
    if (mappedChannel == null) {
      ChannelRef channelRef = pickLeastBusyChannel(/*forFallback= */ false);
      bind(channelRef, Collections.singletonList(key));
      return channelRef;
    }
    if (!fallbackEnabled) {
      return mappedChannel;
    }
    // Look up if the channelRef is not ready.
    Map<String, Integer> tempMap = fallbackMap.get(mappedChannel.getId());
    if (tempMap == null) {
      // Channel is ready.
      return mappedChannel;
    }
    // Channel is not ready. Look up if the affinity key mapped to another channel.
    Integer channelId = tempMap.get(key);
    if (channelId != null && !fallbackMap.containsKey(channelId)) {
      // Fallback channel is ready.
      if (logger.isLoggable(Level.FINEST)) {
        logger.finest(log("Using fallback channel: %d -> %d", mappedChannel.getId(), channelId));
      }
      fallbacksSucceeded.incrementAndGet();
      return channelRefs.get(channelId);
    }
    // No temp mapping for this key or fallback channel is also broken.
    ChannelRef channelRef = pickLeastBusyChannel(/* forFallback= */ true);
    if (!fallbackMap.containsKey(channelRef.getId())
        && channelRef.getActiveStreamsCount() < DEFAULT_MAX_STREAM) {
      // Got a ready and not an overloaded channel.
      if (channelRef.getId() != mappedChannel.getId()) {
        if (logger.isLoggable(Level.FINEST)) {
          logger.finest(
              log("Setting fallback channel: %d -> %d", mappedChannel.getId(), channelRef.getId()));
        }
        fallbacksSucceeded.incrementAndGet();
        tempMap.put(key, channelRef.getId());
      }
      return channelRef;
    }
    if (logger.isLoggable(Level.FINEST)) {
      logger.finest(log("Failed to find fallback for channel %d", mappedChannel.getId()));
    }
    fallbacksFailed.incrementAndGet();
    if (channelId != null) {
      // Stick with previous mapping if fallback has failed.
      return channelRefs.get(channelId);
    }
    return mappedChannel;
  }

  // Create a new channel and add it to channelRefs.
  // If we have a ready channel not in the pool that we wait for completing its RPCs,
  // then re-use that channel instead.
  @VisibleForTesting
  ChannelRef createNewChannel() {
    Optional<ChannelRef> reusedChannelRef = pickChannelForReuse();
    if (reusedChannelRef.isPresent()) {
      ChannelRef chRef = reusedChannelRef.get();
      channelRefs.add(chRef);
      removedChannelRefs.remove(chRef);
      logger.finer(log("Channel %d reused.", chRef.getId()));
      incReadyChannels(false);
      maxChannels = Math.max(maxChannels, channelRefs.size());
      return chRef;
    }

    ChannelRef channelRef = new ChannelRef(delegateChannelBuilder.build());
    channelRefs.add(channelRef);
    logger.finer(log("Channel %d created.", channelRef.getId()));
    maxChannels = Math.max(maxChannels, channelRefs.size());
    return channelRef;
  }

  private Optional<ChannelRef> pickChannelForReuse() {
    // Pick the most recently connected, if any.
    Optional<ChannelRef> chRef =
        removedChannelRefs.stream().max(Comparator.comparing(ChannelRef::getConnectedSinceNanos));

    // Make sure it is ready, because connectedSinceNanos may be 0.
    if (chRef.isPresent() && chRef.get().getState() != ConnectivityState.READY) {
      return Optional.empty();
    }

    return chRef;
  }

  // Returns first newly created channel or null if there are already some channels in the pool.
  @Nullable
  private ChannelRef createFirstChannel() {
    if (!channelRefs.isEmpty()) {
      return null;
    }
    synchronized (this) {
      if (channelRefs.isEmpty()) {
        return createNewChannel();
      }
    }
    return null;
  }

  // Creates new channel if maxSize is not reached.
  // Returns new channel or null.
  @Nullable
  private ChannelRef tryCreateNewChannel() {
    if (channelRefs.size() >= maxSize) {
      return null;
    }
    synchronized (this) {
      if (channelRefs.size() < maxSize) {
        return createNewChannel();
      }
    }
    return null;
  }

  private boolean shouldScaleUp(int minStreams, int totalStreams) {
    if (channelRefs.size() >= maxSize) {
      // Pool is full.
      return false;
    }

    if (!isDynamicScalingEnabled && minStreams >= maxConcurrentStreamsLowWatermark) {
      return true;
    }
    logger.info(log(
      "Checking for scale up. Total streams: %d, channels: %d. Average RPC per channel: %d. Scale up: %s",
      totalStreams,
      channelRefs.size(),
      totalStreams / channelRefs.size(),
      (isDynamicScalingEnabled && (totalStreams / channelRefs.size()) >= maxRpcPerChannel)
    ));
    return (isDynamicScalingEnabled && (totalStreams / channelRefs.size()) >= maxRpcPerChannel);
  }

  /**
   * Pick a {@link ChannelRef} (and create a new one if necessary). If notReadyFallbackEnabled is
   * true in the {@link GcpResiliencyOptions} then instead of a channel in a non-READY state another
   * channel in the READY state and having fewer than maximum allowed number of active streams will
   * be provided if available.
   */
  private ChannelRef pickLeastBusyChannel(boolean forFallback) {
    ChannelRef first = createFirstChannel();
    if (first != null) {
      return first;
    }

    // Pick the least busy channel and the least busy ready and not overloaded channel (this could
    // be the same channel or different or no channel).
    ChannelRef channelCandidate = channelRefs.get(0);
    int minStreams = channelCandidate.getActiveStreamsCount();
    ChannelRef readyCandidate = null;
    int readyMinStreams = Integer.MAX_VALUE;

    int totalStreams = 0;

    for (ChannelRef channelRef : channelRefs) {
      int cnt = channelRef.getActiveStreamsCount();
      totalStreams += cnt;
      if (cnt < minStreams) {
        minStreams = cnt;
        channelCandidate = channelRef;
      }
      if (cnt < readyMinStreams
          && !fallbackMap.containsKey(channelRef.getId())
          && cnt < DEFAULT_MAX_STREAM) {
        readyMinStreams = cnt;
        readyCandidate = channelRef;
      }
    }

    if (!fallbackEnabled) {
      // Check if we need to scale up.
      if (shouldScaleUp(minStreams, totalStreams)) {
        ChannelRef newChannel = tryCreateNewChannel();
        if (newChannel != null) {
          scaleUpCount++;
          return newChannel;
        }
      }
      return channelCandidate;
    }

    // Check if we need to scale up.
    if (shouldScaleUp(readyMinStreams, totalStreams)) {
      ChannelRef newChannel = tryCreateNewChannel();
      if (newChannel != null) {
        scaleUpCount++;
        if (!forFallback && readyCandidate == null) {
          if (logger.isLoggable(Level.FINEST)) {
            logger.finest(log("Fallback to newly created channel %d", newChannel.getId()));
          }
          fallbacksSucceeded.incrementAndGet();
        }
        return newChannel;
      }
    }

    if (readyCandidate != null) {
      if (!forFallback && readyCandidate.getId() != channelCandidate.getId()) {
        if (logger.isLoggable(Level.FINEST)) {
          logger.finest(
              log(
                  "Picking fallback channel: %d -> %d",
                  channelCandidate.getId(), readyCandidate.getId()));
        }
        fallbacksSucceeded.incrementAndGet();
      }
      return readyCandidate;
    }

    if (!forFallback) {
      if (logger.isLoggable(Level.FINEST)) {
        logger.finest(log("Failed to find fallback for channel %d", channelCandidate.getId()));
      }
      fallbacksFailed.incrementAndGet();
    }
    return channelCandidate;
  }

  @Override
  public String authority() {
    if (!channelRefs.isEmpty()) {
      return channelRefs.get(0).getChannel().authority();
    }
    final ManagedChannel channel = delegateChannelBuilder.build();
    final String authority = channel.authority();
    channel.shutdownNow();
    return authority;
  }

  /**
   * Manage the channelpool using GcpClientCall().
   *
   * <p>If method-affinity is specified, we will use the GcpClientCall to fetch the affinitykey and
   * bind/unbind the channel, otherwise we just need the SimpleGcpClientCall to keep track of the
   * number of streams in each channel.
   */
  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(
      MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions) {
    if (callOptions.getOption(DISABLE_AFFINITY_KEY)
        || DISABLE_AFFINITY_CTX_KEY.get(Context.current())) {
      if (logger.isLoggable(Level.FINEST)) {
        logger.finest(log("Channel affinity is disabled via context or call options."));
      }
      return new GcpClientCall.SimpleGcpClientCall<>(
          getChannelRef(null), methodDescriptor, callOptions);
    }

    AffinityConfig affinity = methodToAffinity.get(methodDescriptor.getFullMethodName());
    String key = keyFromOptsCtx(callOptions);
    if (affinity != null && key == null) {
      return new GcpClientCall<>(this, methodDescriptor, callOptions, affinity);
    }

    return new GcpClientCall.SimpleGcpClientCall<>(
        getChannelRef(key), methodDescriptor, callOptions);
  }

  @Nullable
  private String keyFromOptsCtx(CallOptions callOptions) {
    String key = callOptions.getOption(AFFINITY_KEY);
    if (key != null) {
      if (logger.isLoggable(Level.FINEST)) {
        logger.finest(log("Affinity key \"%s\" set manually via call options.", key));
      }
      return key;
    }

    key = AFFINITY_CTX_KEY.get(Context.current());
    if (key != null && logger.isLoggable(Level.FINEST)) {
      logger.finest(log("Affinity key \"%s\" set manually via context.", key));
    }
    return key;
  }

  @Override
  public ManagedChannel shutdownNow() {
    logger.finer(log("Shutdown now started."));
    for (ChannelRef channelRef : channelRefs) {
      if (!channelRef.getChannel().isTerminated()) {
        channelRef.getChannel().shutdownNow();
      }
    }
    for (ChannelRef channelRef : removedChannelRefs) {
      if (!channelRef.getChannel().isTerminated()) {
        channelRef.getChannel().shutdownNow();
      }
    }
    if (backgroundService != null && !backgroundService.isTerminated()) {
      backgroundService.shutdownNow();
    }
    if (!stateNotificationExecutor.isTerminated()) {
      stateNotificationExecutor.shutdownNow();
    }
    return this;
  }

  @Override
  public ManagedChannel shutdown() {
    logger.finer(log("Shutdown started."));
    for (ChannelRef channelRef : channelRefs) {
      channelRef.getChannel().shutdown();
    }
    for (ChannelRef channelRef : removedChannelRefs) {
      channelRef.getChannel().shutdown();
    }
    if (backgroundService != null) {
      backgroundService.shutdown();
    }
    stateNotificationExecutor.shutdown();
    return this;
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    long endTimeNanos = System.nanoTime() + unit.toNanos(timeout);
    List<ChannelRef> allChannelRefs = new ArrayList<>(channelRefs);
    allChannelRefs.addAll(removedChannelRefs);
    for (ChannelRef channelRef : allChannelRefs) {
      if (channelRef.getChannel().isTerminated()) {
        continue;
      }
      long awaitTimeNanos = endTimeNanos - System.nanoTime();
      if (awaitTimeNanos <= 0) {
        break;
      }
      channelRef.getChannel().awaitTermination(awaitTimeNanos, NANOSECONDS);
    }
    long awaitTimeNanos = endTimeNanos - System.nanoTime();
    if (backgroundService != null && awaitTimeNanos > 0) {
      //noinspection ResultOfMethodCallIgnored
      backgroundService.awaitTermination(awaitTimeNanos, NANOSECONDS);
    }
    awaitTimeNanos = endTimeNanos - System.nanoTime();
    if (awaitTimeNanos > 0) {
      //noinspection ResultOfMethodCallIgnored
      stateNotificationExecutor.awaitTermination(awaitTimeNanos, NANOSECONDS);
    }
    return isTerminated();
  }

  @Override
  public boolean isShutdown() {
    List<ChannelRef> allChannelRefs = new ArrayList<>(channelRefs);
    allChannelRefs.addAll(removedChannelRefs);
    for (ChannelRef channelRef : allChannelRefs) {
      if (!channelRef.getChannel().isShutdown()) {
        return false;
      }
    }
    if (backgroundService != null && !backgroundService.isShutdown()) {
      return false;
    }
    return stateNotificationExecutor.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    List<ChannelRef> allChannelRefs = new ArrayList<>(channelRefs);
    allChannelRefs.addAll(removedChannelRefs);
    for (ChannelRef channelRef : allChannelRefs) {
      if (!channelRef.getChannel().isTerminated()) {
        return false;
      }
    }
    if (backgroundService != null && !backgroundService.isTerminated()) {
      return false;
    }
    return stateNotificationExecutor.isTerminated();
  }

  /** Get the current connectivity state of the channel pool. */
  @Override
  public ConnectivityState getState(boolean requestConnection) {
    if (requestConnection && getNumberOfChannels() == 0) {
      createFirstChannel();
    }
    int ready = 0;
    int idle = 0;
    int connecting = 0;
    int transientFailure = 0;
    int shutdown = 0;
    for (ChannelRef channelRef : channelRefs) {
      ConnectivityState cur = channelRef.getChannel().getState(requestConnection);
      switch (cur) {
        case READY:
          ready++;
          break;
        case SHUTDOWN:
          shutdown++;
          break;
        case TRANSIENT_FAILURE:
          transientFailure++;
          break;
        case CONNECTING:
          connecting++;
          break;
        case IDLE:
          idle++;
          break;
      }
    }

    if (ready > 0) {
      return ConnectivityState.READY;
    } else if (connecting > 0) {
      return ConnectivityState.CONNECTING;
    } else if (transientFailure > 0) {
      return ConnectivityState.TRANSIENT_FAILURE;
    } else if (idle > 0) {
      return ConnectivityState.IDLE;
    } else if (shutdown > 0) {
      return ConnectivityState.SHUTDOWN;
    }
    // When no channels are created yet it is also IDLE.
    return ConnectivityState.IDLE;
  }

  /**
   * Bind channel with affinity key.
   *
   * <p>One channel can be mapped to more than one keys. But one key can only be mapped to one
   * channel.
   */
  protected void bind(ChannelRef channelRef, List<String> affinityKeys) {
    if (channelRef == null || affinityKeys == null) {
      return;
    }
    if (logger.isLoggable(Level.FINEST)) {
      logger.finest(
          log(
              "Binding %d key(s) to channel %d: [%s]",
              affinityKeys.size(), channelRef.getId(), String.join(", ", affinityKeys)));
    }
    for (String affinityKey : affinityKeys) {
      while (affinityKeyToChannelRef.putIfAbsent(affinityKey, channelRef) != null) {
        unbind(Collections.singletonList(affinityKey));
      }
      affinityKeyLastUsed.put(affinityKey, System.nanoTime());
      channelRef.affinityCountIncr();
    }
  }

  /** Unbind channel with affinity key. */
  protected void unbind(List<String> affinityKeys) {
    if (affinityKeys == null) {
      return;
    }
    for (String affinityKey : affinityKeys) {
      ChannelRef channelRef = affinityKeyToChannelRef.remove(affinityKey);
      affinityKeyLastUsed.remove(affinityKey);
      if (channelRef != null) {
        channelRef.affinityCountDecr();
        if (logger.isLoggable(Level.FINEST)) {
          logger.finest(log("Unbinding key %s from channel %d.", affinityKey, channelRef.getId()));
        }
      } else {
        if (logger.isLoggable(Level.FINEST)) {
          logger.finest(log("Unbinding key %s but it wasn't bound.", affinityKey));
        }
      }
    }
  }

  /** Load parameters from ApiConfig. */
  private void loadApiConfig(ApiConfig apiConfig) {
    if (apiConfig == null) {
      return;
    }
    // Get the channelPool parameters
    if (apiConfig.getChannelPool().getMaxSize() > 0) {
      maxSize = apiConfig.getChannelPool().getMaxSize();
    }
    final int lowWatermark = apiConfig.getChannelPool().getMaxConcurrentStreamsLowWatermark();
    if (lowWatermark >= 0 && lowWatermark <= DEFAULT_MAX_STREAM) {
      this.maxConcurrentStreamsLowWatermark = lowWatermark;
    }
    // Get method parameters.
    for (MethodConfig method : apiConfig.getMethodList()) {
      if (method.getAffinity().equals(AffinityConfig.getDefaultInstance())) {
        continue;
      }
      for (String methodName : method.getNameList()) {
        methodToAffinity.put(methodName, method.getAffinity());
      }
    }
  }

  /**
   * Get the affinity key from the request message.
   *
   * <p>The message can be written in the format of:
   *
   * <p>session1: "the-key-we-want" \n transaction_id: "not-useful" \n transaction { \n session2:
   * "another session"} \n}
   *
   * <p>If the (affinity) name is "session1", it will return "the-key-we-want".
   *
   * <p>If you want to get the key "another session" in the nested message, the name should be
   * "session1.session2".
   */
  @VisibleForTesting
  static List<String> getKeysFromMessage(MessageOrBuilder msg, String name) {
    // The field names in a nested message name are splitted by '.'.
    int currentLength = name.indexOf('.');
    String currentName = name;
    if (currentLength != -1) {
      currentName = name.substring(0, currentLength);
    }

    List<String> keys = new ArrayList<>();
    Map<FieldDescriptor, Object> obs = msg.getAllFields();
    for (Map.Entry<FieldDescriptor, Object> entry : obs.entrySet()) {
      if (entry.getKey().getName().equals(currentName)) {
        if (currentLength == -1 && entry.getValue() instanceof String) {
          // Value of the current field.
          keys.add(entry.getValue().toString());
        } else if (currentLength != -1 && entry.getValue() instanceof MessageOrBuilder) {
          // One nested MessageOrBuilder.
          keys.addAll(
              getKeysFromMessage(
                  (MessageOrBuilder) entry.getValue(), name.substring(currentLength + 1)));
        } else if (currentLength != -1 && entry.getValue() instanceof List) {
          // Repeated nested MessageOrBuilder.
          List<?> list = (List<?>) entry.getValue();
          if (!list.isEmpty() && list.get(0) instanceof MessageOrBuilder) {
            for (Object item : list) {
              keys.addAll(
                  getKeysFromMessage((MessageOrBuilder) item, name.substring(currentLength + 1)));
            }
          }
        }
      }
    }
    return keys;
  }

  /**
   * Fetch the affinity key from the message.
   *
   * @param message the <reqT> or <respT> prototype message.
   * @param isReq indicates if the message is a request message.
   */
  @Nullable
  protected <ReqT, RespT> List<String> checkKeys(
      Object message, boolean isReq, MethodDescriptor<ReqT, RespT> methodDescriptor) {
    if (!(message instanceof MessageOrBuilder)) {
      return null;
    }

    AffinityConfig affinity = methodToAffinity.get(methodDescriptor.getFullMethodName());
    if (affinity != null) {
      AffinityConfig.Command cmd = affinity.getCommand();
      String keyName = affinity.getAffinityKey();
      List<String> keys = getKeysFromMessage((MessageOrBuilder) message, keyName);
      if (isReq && (cmd == AffinityConfig.Command.UNBIND || cmd == AffinityConfig.Command.BOUND)) {
        if (keys.size() > 1) {
          throw new IllegalStateException("Duplicate affinity key in the request message");
        }
        return keys;
      }
      if (!isReq && cmd == AffinityConfig.Command.BIND) {
        return keys;
      }
    }
    return null;
  }

  /**
   * A wrapper of real grpc channel, it provides helper functions to calculate affinity counts and
   * active streams count.
   */
  protected class ChannelRef {

    private final ManagedChannel delegate;
    private final int channelId;
    private final AtomicInteger affinityCount;
    // activeStreamsCount are mutated from the GcpClientCall concurrently using the
    // `activeStreamsCountIncr()` and `activeStreamsCountDecr()` methods.
    private final AtomicInteger activeStreamsCount;
    private long lastResponseNanos = System.nanoTime();
    private final AtomicInteger deadlineExceededCount = new AtomicInteger();
    private final AtomicLong okCalls = new AtomicLong();
    private final AtomicLong errCalls = new AtomicLong();
    private final ChannelStateMonitor channelStateMonitor;

    protected ChannelRef(ManagedChannel channel) {
      this(channel, 0, 0);
    }

    protected ChannelRef(ManagedChannel channel, int affinityCount, int activeStreamsCount) {
      this.delegate = channel;
      this.channelId = nextChannelId.getAndIncrement();
      this.affinityCount = new AtomicInteger(affinityCount);
      this.activeStreamsCount = new AtomicInteger(activeStreamsCount);
      channelStateMonitor = new ChannelStateMonitor(channel, this);
    }

    protected long getConnectedSinceNanos() {
      return channelStateMonitor.getConnectedSinceNanos();
    }

    protected ConnectivityState getState() {
      return channelStateMonitor.getCurrentState();
    }

    protected ManagedChannel getChannel() {
      return delegate;
    }

    protected int getId() {
      return channelId;
    }

    protected void affinityCountIncr() {
      int count = affinityCount.incrementAndGet();
      maxAffinity.getAndUpdate(currentMax -> Math.max(currentMax, count));
      totalAffinityCount.incrementAndGet();
    }

    protected void affinityCountDecr() {
      int count = affinityCount.decrementAndGet();
      minAffinity.getAndUpdate(currentMin -> Math.min(currentMin, count));
      totalAffinityCount.decrementAndGet();
    }

    protected void resetAffinityCount() {
      affinityCount.set(0);
    }

    protected void activeStreamsCountIncr() {
      int actStreams = activeStreamsCount.incrementAndGet();
      if (maxActiveStreams < actStreams) {
        maxActiveStreams = actStreams;
      }
      int totalActStreams = totalActiveStreams.incrementAndGet();
      if (maxTotalActiveStreams < totalActStreams) {
        maxTotalActiveStreams = totalActStreams;
      }
      if (maxTotalActiveStreamsForScaleDown < totalActStreams) {
        maxTotalActiveStreamsForScaleDown = totalActStreams;
      }
    }

    protected void activeStreamsCountDecr(long startNanos, Status status, boolean fromClientSide) {
      int actStreams = activeStreamsCount.decrementAndGet();
      if (minActiveStreams > actStreams) {
        minActiveStreams = actStreams;
      }
      int totalActStreams = totalActiveStreams.decrementAndGet();
      if (minTotalActiveStreams > totalActStreams) {
        minTotalActiveStreams = totalActStreams;
      }
      if (status.isOk()) {
        okCalls.incrementAndGet();
        totalOkCalls.incrementAndGet();
      } else {
        errCalls.incrementAndGet();
        totalErrCalls.incrementAndGet();
      }
      if (unresponsiveDetectionEnabled) {
        detectUnresponsiveConnection(startNanos, status, fromClientSide);
      }
    }

    protected void messageReceived() {
      lastResponseNanos = System.nanoTime();
      deadlineExceededCount.set(0);
    }

    protected int getAffinityCount() {
      return affinityCount.get();
    }

    protected int getActiveStreamsCount() {
      return activeStreamsCount.get();
    }

    protected long getAndResetOkCalls() {
      return okCalls.getAndSet(0);
    }

    protected long getAndResetErrCalls() {
      return errCalls.getAndSet(0);
    }

    private void detectUnresponsiveConnection(
        long startNanos, Status status, boolean fromClientSide) {
      if (status.getCode().equals(Code.DEADLINE_EXCEEDED)) {
        if (startNanos < lastResponseNanos) {
          // Skip deadline exceeded from past calls.
          return;
        }
        if (deadlineExceededCount.incrementAndGet() >= unresponsiveDropCount
            && msSinceLastResponse() >= unresponsiveMs) {
          maybeReconnectUnresponsive();
        }
        return;
      }
      if (!fromClientSide) {
        // If not a deadline exceeded and not coming from the client side then reset time and count.
        lastResponseNanos = System.nanoTime();
        deadlineExceededCount.set(0);
      }
    }

    private long msSinceLastResponse() {
      return (System.nanoTime() - lastResponseNanos) / 1000000;
    }

    private synchronized void maybeReconnectUnresponsive() {
      final long msSinceLastResponse = msSinceLastResponse();
      if (deadlineExceededCount.get() >= unresponsiveDropCount
          && msSinceLastResponse >= unresponsiveMs) {
        recordUnresponsiveDetection(
            System.nanoTime() - lastResponseNanos, deadlineExceededCount.get());
        logger.finer(
            log(
                "Channel %d connection is unresponsive for %d ms and %d deadline exceeded calls. "
                    + "Forcing channel to idle state.",
                channelId, msSinceLastResponse, deadlineExceededCount.get()));
        delegate.enterIdle();
        lastResponseNanos = System.nanoTime();
        deadlineExceededCount.set(0);
      }
    }
  }
}
