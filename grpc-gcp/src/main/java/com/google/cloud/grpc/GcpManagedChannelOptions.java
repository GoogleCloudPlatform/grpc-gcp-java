/*
 * Copyright 2021 Google LLC
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

import com.google.common.base.Preconditions;
import io.opencensus.metrics.LabelKey;
import io.opencensus.metrics.LabelValue;
import io.opencensus.metrics.MetricRegistry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/** Options for the {@link GcpManagedChannel}. */
public class GcpManagedChannelOptions {
  private static final Logger logger = Logger.getLogger(GcpManagedChannelOptions.class.getName());

  @Nullable private final GcpChannelPoolOptions channelPoolOptions;
  @Nullable private final GcpMetricsOptions metricsOptions;
  @Nullable private final GcpResiliencyOptions resiliencyOptions;

  public GcpManagedChannelOptions() {
    channelPoolOptions = null;
    metricsOptions = null;
    resiliencyOptions = null;
  }

  public GcpManagedChannelOptions(Builder builder) {
    channelPoolOptions = builder.channelPoolOptions;
    metricsOptions = builder.metricsOptions;
    resiliencyOptions = builder.resiliencyOptions;
  }

  @Nullable
  public GcpChannelPoolOptions getChannelPoolOptions() {
    return channelPoolOptions;
  }

  @Nullable
  public GcpMetricsOptions getMetricsOptions() {
    return metricsOptions;
  }

  @Nullable
  public GcpResiliencyOptions getResiliencyOptions() {
    return resiliencyOptions;
  }

  /** Creates a new GcpManagedChannelOptions.Builder. */
  public static Builder newBuilder() {
    return new Builder();
  }

  /** Creates a new GcpManagedChannelOptions.Builder from GcpManagedChannelOptions. */
  public static Builder newBuilder(GcpManagedChannelOptions options) {
    return new Builder(options);
  }

  public static class Builder {
    private GcpChannelPoolOptions channelPoolOptions;
    private GcpMetricsOptions metricsOptions;
    private GcpResiliencyOptions resiliencyOptions;

    public Builder() {}

    public Builder(GcpManagedChannelOptions options) {
      this.channelPoolOptions = options.getChannelPoolOptions();
      this.metricsOptions = options.getMetricsOptions();
      this.resiliencyOptions = options.getResiliencyOptions();
    }

    public GcpManagedChannelOptions build() {
      return new GcpManagedChannelOptions(this);
    }

    /**
     * Sets the channel pool configuration for the {@link GcpManagedChannel}.
     *
     * @param channelPoolOptions a {@link GcpChannelPoolOptions} to use as a channel pool
     *                           configuration.
     */
    public Builder withChannelPoolOptions(GcpChannelPoolOptions channelPoolOptions) {
      this.channelPoolOptions = channelPoolOptions;
      return this;
    }

    /**
     * Sets the metrics configuration for the {@link GcpManagedChannel}.
     *
     * <p>If a {@link MetricRegistry} is provided in {@link GcpMetricsOptions} then the
     * GcpManagedChannel will emit metrics using that registry. The metrics options also allow to
     * set up labels (tags) and a prefix for metrics names. The GcpManagedChannel will add its own
     * label "pool_index" with values "pool-0", "pool-1", etc. for each instance of
     * GcpManagedChannel created.
     *
     * <p>Example usage (e. g. with export to Cloud Monitoring)
     *
     * <pre>
     * // Enable Cloud Monitoring exporter.
     * StackdriverStatsExporter.createAndRegister();
     *
     * // Configure metrics options.
     * GcpMetricsOptions metricsOptions = GcpMetricsOptions.newBuilder(Metrics.getMetricRegistry())
     *     .withNamePrefix("myapp/gcp-pool/")
     *     .build());
     *
     * final GcpManagedChannel pool =
     *     (GcpManagedChannel)
     *         GcpManagedChannelBuilder.forDelegateBuilder(builder)
     *             .withOptions(
     *                 GcpManagedChannelOptions.newBuilder()
     *                     .withMetricsOptions(metricsOptions)
     *                     .build())
     *             .build();
     *
     * // Use the pool that will emit metrics which will be exported to Cloud Monitoring.
     * </pre>
     *
     * @param metricsOptions a {@link GcpMetricsOptions} to use as metrics configuration.
     */
    public Builder withMetricsOptions(GcpMetricsOptions metricsOptions) {
      this.metricsOptions = metricsOptions;
      return this;
    }

    /**
     * Sets the resiliency configuration for the {@link GcpManagedChannel}.
     *
     * @param resiliencyOptions a {@link GcpResiliencyOptions} to use as resiliency configuration.
     */
    public Builder withResiliencyOptions(GcpResiliencyOptions resiliencyOptions) {
      this.resiliencyOptions = resiliencyOptions;
      return this;
    }
  }

  /** Channel pool configuration for the GCP managed channel. */
  public static class GcpChannelPoolOptions {
    // The maximum number of channels in the pool.
    private final int maxSize;
    // If every channel in the pool has at least this amount of concurrent streams then a new channel will be created
    // in the pool unless the pool reached its maximum size.
    private final int concurrentStreamsLowWatermark;

    public GcpChannelPoolOptions(Builder builder) {
      maxSize = builder.maxSize;
      concurrentStreamsLowWatermark = builder.concurrentStreamsLowWatermark;
    }

    public int getMaxSize() {
      return maxSize;
    }

    public int getConcurrentStreamsLowWatermark() {
      return concurrentStreamsLowWatermark;
    }

    /** Creates a new GcpChannelPoolOptions.Builder. */
    public static GcpChannelPoolOptions.Builder newBuilder() {
      return new GcpChannelPoolOptions.Builder();
    }

    /** Creates a new GcpChannelPoolOptions.Builder from GcpChannelPoolOptions. */
    public static GcpChannelPoolOptions.Builder newBuilder(GcpChannelPoolOptions options) {
      return new GcpChannelPoolOptions.Builder(options);
    }

    public static class Builder {
      private int maxSize = GcpManagedChannel.DEFAULT_MAX_CHANNEL;
      private int concurrentStreamsLowWatermark = GcpManagedChannel.DEFAULT_MAX_STREAM;

      public Builder() {}

      public Builder(GcpChannelPoolOptions options) {
        this();
        if (options == null) {
          return;
        }
        this.maxSize = options.getMaxSize();
        this.concurrentStreamsLowWatermark = options.getConcurrentStreamsLowWatermark();
      }

      public GcpChannelPoolOptions build() {
        return new GcpChannelPoolOptions(this);
      }

      /**
       * Sets the maximum size of the channel pool.
       *
       * @param maxSize maximum number of channels the pool can have.
       */
      public Builder setMaxSize(int maxSize) {
        Preconditions.checkArgument(maxSize > 0, "Channel pool size must be positive.");
        this.maxSize = maxSize;
        return this;
      }

      /**
       * Sets the concurrent streams low watermark.
       * If every channel in the pool has at least this amount of concurrent streams then a new
       * channel will be created in the pool unless the pool reached its maximum size.
       *
       * @param concurrentStreamsLowWatermark number of streams every channel must reach before adding a new channel
       *                                      to the pool.
       */
      public Builder setConcurrentStreamsLowWatermark(int concurrentStreamsLowWatermark) {
        this.concurrentStreamsLowWatermark = concurrentStreamsLowWatermark;
        return this;
      }
    }
  }

  /** Metrics configuration for the GCP managed channel. */
  public static class GcpMetricsOptions {
    private final MetricRegistry metricRegistry;
    private final List<LabelKey> labelKeys;
    private final List<LabelValue> labelValues;
    private final String namePrefix;

    public GcpMetricsOptions(Builder builder) {
      metricRegistry = builder.metricRegistry;
      labelKeys = builder.labelKeys;
      labelValues = builder.labelValues;
      namePrefix = builder.namePrefix;
    }

    public MetricRegistry getMetricRegistry() {
      return metricRegistry;
    }

    public List<LabelKey> getLabelKeys() {
      return labelKeys;
    }

    public List<LabelValue> getLabelValues() {
      return labelValues;
    }

    public String getNamePrefix() {
      return namePrefix;
    }

    /** Creates a new GcpMetricsOptions.Builder. */
    public static Builder newBuilder() {
      return new Builder();
    }

    /** Creates a new GcpMetricsOptions.Builder from GcpMetricsOptions. */
    public static Builder newBuilder(GcpMetricsOptions options) {
      return new Builder(options);
    }

    public static class Builder {
      private MetricRegistry metricRegistry;
      private List<LabelKey> labelKeys;
      private List<LabelValue> labelValues;
      private String namePrefix;

      /** Constructor for GcpMetricsOptions.Builder. */
      public Builder() {
        labelKeys = new ArrayList<>();
        labelValues = new ArrayList<>();
        namePrefix = "";
      }

      public Builder(GcpMetricsOptions options) {
        this();
        if (options == null) {
          return;
        }
        this.metricRegistry = options.getMetricRegistry();
        this.labelKeys = options.getLabelKeys();
        this.labelValues = options.getLabelValues();
        this.namePrefix = options.getNamePrefix();
      }

      public GcpMetricsOptions build() {
        return new GcpMetricsOptions(this);
      }

      public Builder withMetricRegistry(MetricRegistry registry) {
        this.metricRegistry = registry;
        return this;
      }

      /**
       * Sets label keys and values to report with the metrics. The size of keys and values lists
       * must match. Otherwise the labels will not be applied.
       *
       * @param labelKeys a list of {@link LabelKey}.
       * @param labelValues a list of {@link LabelValue}.
       */
      public Builder withLabels(List<LabelKey> labelKeys, List<LabelValue> labelValues) {
        if (labelKeys == null || labelValues == null || labelKeys.size() != labelValues.size()) {
          logger.warning("Unable to set label keys and values - size mismatch or null.");
          return this;
        }
        this.labelKeys = labelKeys;
        this.labelValues = labelValues;
        return this;
      }

      /**
       * Sets the prefix for all metric names reported by GcpManagedChannel.
       *
       * @param namePrefix the prefix for metrics names.
       */
      public Builder withNamePrefix(String namePrefix) {
        this.namePrefix = namePrefix;
        return this;
      }
    }
  }

  /** Resiliency configuration for the GCP managed channel. */
  public static class GcpResiliencyOptions {
    private final boolean notReadyFallbackEnabled;
    private final boolean unresponsiveDetectionEnabled;
    private final int unresponsiveDetectionMs;
    private final int unresponsiveDetectionDroppedCount;
    private final List<GcpMultiEndpointOptions> multiEndpoints;

    public static class GcpMultiEndpointOptions {
      private final String name;
      private final List<String> endpoints;
      private final Duration recoveryTimeout;

      public static String DEFAULT_NAME = "default";

      public GcpMultiEndpointOptions(Builder builder) {
        this.name = builder.name;
        this.endpoints = builder.endpoints;
        this.recoveryTimeout = builder.recoveryTimeout;
      }

      public static Builder newBuilder(List<String> endpoints) {
        return new Builder(endpoints);
      }

      public static Builder newBuilder(GcpMultiEndpointOptions options) {
        return new Builder(options);
      }

      public String getName() {
        return name;
      }

      public List<String> getEndpoints() {
        return endpoints;
      }

      public Duration getRecoveryTimeout() {
        return recoveryTimeout;
      }

      public static class Builder {
        private String name = GcpMultiEndpointOptions.DEFAULT_NAME;
        private List<String> endpoints;
        private Duration recoveryTimeout = Duration.ZERO;

        public Builder(List<String> endpoints) {
          setEndpoints(endpoints);
        }

        public Builder(GcpMultiEndpointOptions options) {
          this.name = options.getName();
          this.endpoints = options.getEndpoints();
          this.recoveryTimeout = options.getRecoveryTimeout();
        }

        public GcpMultiEndpointOptions build() {
          return new GcpMultiEndpointOptions(this);
        }

        private void setEndpoints(List<String> endpoints) {
          Preconditions.checkNotNull(endpoints);
          Preconditions.checkArgument(
                  !endpoints.isEmpty(), "At least one endpoint must be specified.");
          Preconditions.checkArgument(
                  endpoints.stream().noneMatch(s -> s.trim().isEmpty()), "No empty endpoints allowed.");
          this.endpoints = endpoints;
        }

        public Builder withName(String name) {
          this.name = name;
          return this;
        }

        public Builder withEndpoints(List<String> endpoints) {
          this.setEndpoints(endpoints);
          return this;
        }

        public Builder withRecoveryTimeout(Duration recoveryTimeout) {
          this.recoveryTimeout = recoveryTimeout;
          return this;
        }
      }
    }

    public GcpResiliencyOptions(Builder builder) {
      notReadyFallbackEnabled = builder.notReadyFallbackEnabled;
      unresponsiveDetectionEnabled = builder.unresponsiveDetectionEnabled;
      unresponsiveDetectionMs = builder.unresponsiveDetectionMs;
      unresponsiveDetectionDroppedCount = builder.unresponsiveDetectionDroppedCount;
      multiEndpoints = builder.multiEndpoints;
    }

    /** Creates a new GcpResiliencyOptions.Builder. */
    public static Builder newBuilder() {
      return new Builder();
    }

    /** Creates a new GcpResiliencyOptions.Builder from GcpResiliencyOptions. */
    public static Builder newBuilder(GcpResiliencyOptions options) {
      return new Builder(options);
    }

    public boolean isNotReadyFallbackEnabled() {
      return notReadyFallbackEnabled;
    }

    public boolean isUnresponsiveDetectionEnabled() {
      return unresponsiveDetectionEnabled;
    }

    public int getUnresponsiveDetectionMs() {
      return unresponsiveDetectionMs;
    }

    public int getUnresponsiveDetectionDroppedCount() {
      return unresponsiveDetectionDroppedCount;
    }

    public List<GcpMultiEndpointOptions> getMultiEndpoints() {
      return multiEndpoints;
    }

    public static class Builder {
      private boolean notReadyFallbackEnabled = false;
      private boolean unresponsiveDetectionEnabled = false;
      private int unresponsiveDetectionMs = 0;
      private int unresponsiveDetectionDroppedCount = 0;
      private List<GcpMultiEndpointOptions> multiEndpoints = new ArrayList<>();

      public Builder() {}

      public Builder(GcpResiliencyOptions options) {
        this.notReadyFallbackEnabled = options.isNotReadyFallbackEnabled();
        this.unresponsiveDetectionEnabled = options.isUnresponsiveDetectionEnabled();
        this.unresponsiveDetectionMs = options.getUnresponsiveDetectionMs();
        this.unresponsiveDetectionDroppedCount = options.getUnresponsiveDetectionDroppedCount();
        this.multiEndpoints = options.getMultiEndpoints();
      }

      public GcpResiliencyOptions build() {
        return new GcpResiliencyOptions(this);
      }

      /**
       * If true, temporarily fallback requests to a ready channel from a channel which is not ready
       * to send a request immediately. The fallback will happen if the pool has another channel in
       * the READY state and that channel has less than maximum allowed concurrent active streams.
       */
      public Builder setNotReadyFallback(boolean enabled) {
        notReadyFallbackEnabled = enabled;
        return this;
      }

      /**
       * Enable unresponsive connection detection.
       *
       * <p>If an RPC channel fails to receive any RPC message from the server for {@code ms}
       * milliseconds and there were {@code numDroppedRequests} calls (started after the last
       * response from the server) that resulted in DEADLINE_EXCEEDED then a graceful reconnection
       * of the channel will be performed.
       *
       * <p>During the reconnection a new subchannel (connection) will be created for new RPCs, and
       * the calls on the old subchannel will still have a chance to complete if the server side
       * responds. When all RPCs on the old subchannel finish the old connection will be closed.
       *
       * <p>The {@code ms} should not be less than the timeout used for the majority of calls. And
       * {@code numDroppedRequests} must be > 0.
       *
       * <p>The logic treats any message from the server almost as a "ping" response. But only calls
       * started after the last response received and ended up in DEADLINE_EXCEEDED count towards
       * {@code numDroppedRequests}. Because of that, it may not detect an unresponsive connection
       * if you have long-running streaming calls only.
       */
      public Builder withUnresponsiveConnectionDetection(int ms, int numDroppedRequests) {
        Preconditions.checkArgument(ms > 0, "ms should be > 0, got %s", ms);
        Preconditions.checkArgument(
            numDroppedRequests > 0, "numDroppedRequests should be > 0, got %s", numDroppedRequests);
        unresponsiveDetectionEnabled = true;
        unresponsiveDetectionMs = ms;
        unresponsiveDetectionDroppedCount = numDroppedRequests;
        return this;
      }

      /** Disable unresponsive connection detection. */
      public Builder disableUnresponsiveConnectionDetection() {
        unresponsiveDetectionEnabled = false;
        return this;
      }

      /**
       * Enable/disable multi-endpoint feature.
       *
       * <p>For each item of the {@link GcpMultiEndpointOptions} list provided a named MultiEndpoint
       * will be created.
       *
       * <p>Any call by default will use the first MultiEndpoint if the name of the desired
       * MultiEndpoint is not specified in {@link io.grpc.CallOptions}.
       *
       * <p>Each MultiEndpoint is a list of endpoints. A channel pool will be created for each
       * endpoint and for each channel in a pool the endpoint from a {@link
       * io.grpc.ManagedChannelBuilder} will be overridden by the endpoint from MultiEndpoint.
       *
       * <p>The first endpoint in the MultiEndpoint list will be used for calls as long as there is
       * a healthy (ready) connection in the pool for this endpoint. If no connection is healthy and
       * {@code recoveryTimeout} has passed then the next endpoint will be used for new calls and so
       * on. If a connection from a previous endpoint recovers then new calls will use the previous
       * endpoint.
       *
       * <p>To disable previously enabled multi-endpoint feature provide an empty list.
       */
      public Builder withMultiEndpoints(List<GcpMultiEndpointOptions> multiEndpoints) {
        Preconditions.checkNotNull(multiEndpoints);
        this.multiEndpoints = multiEndpoints;
        return this;
      }
    }
  }
}
