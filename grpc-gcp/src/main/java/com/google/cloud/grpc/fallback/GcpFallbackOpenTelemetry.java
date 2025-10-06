/*
 * Copyright 2025 Google LLC
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

package com.google.cloud.grpc.fallback;

import static com.google.cloud.grpc.GrpcGcpUtil.IMPLEMENTATION_VERSION;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * The entrypoint for OpenTelemetry metrics functionality in gRPC-GCP Fallback channel.
 *
 * <p>GcpFallbackOpenTelemetry uses {@link io.opentelemetry.api.OpenTelemetry} APIs for
 * instrumentation. When no SDK is explicitly added no telemetry data will be collected. See {@code
 * io.opentelemetry.sdk.OpenTelemetrySdk} for information on how to construct the SDK.
 */
public final class GcpFallbackOpenTelemetry {
  static final String INSTRUMENTATION_SCOPE = "grpc-gcp";
  static final String METRIC_PREFIX = "eef";

  static final String CURRENT_CHANNEL_METRIC = "current_channel";
  static final String FALLBACK_COUNT_METRIC = "fallback_count";
  static final String CALL_STATUS_METRIC = "call_status";
  static final String ERROR_RATIO_METRIC = "error_ratio";
  static final String PROBE_RESULT_METRIC = "probe_result";
  static final String CHANNEL_DOWNTIME_METRIC = "channel_downtime";

  static final AttributeKey<String> CHANNEL_NAME = AttributeKey.stringKey("channel_name");
  static final AttributeKey<String> FROM_CHANNEL_NAME = AttributeKey.stringKey("from_channel_name");
  static final AttributeKey<String> TO_CHANNEL_NAME = AttributeKey.stringKey("to_channel_name");
  static final AttributeKey<String> STATUS_CODE = AttributeKey.stringKey("status_code");
  // static final AttributeKey<Boolean> PROBE_SUCCESS = AttributeKey.booleanKey("success");
  static final AttributeKey<String> PROBE_RESULT = AttributeKey.stringKey("result");

  private final OpenTelemetry openTelemetrySdk;
  private final MeterProvider meterProvider;
  private final Meter meter;
  private final Map<String, Boolean> enableMetrics;
  private final boolean disableDefault;
  private final OpenTelemetryMetricsResource resource;
  private final OpenTelemetryMetricsModule openTelemetryMetricsModule;

  public static Builder newBuilder() {
    return new Builder();
  }

  private GcpFallbackOpenTelemetry(Builder builder) {
    this.openTelemetrySdk = checkNotNull(builder.openTelemetrySdk, "openTelemetrySdk");
    this.meterProvider = checkNotNull(openTelemetrySdk.getMeterProvider(), "meterProvider");
    this.meter =
        this.meterProvider
            .meterBuilder(INSTRUMENTATION_SCOPE)
            .setInstrumentationVersion(IMPLEMENTATION_VERSION)
            .build();
    this.enableMetrics = ImmutableMap.copyOf(builder.enableMetrics);
    this.disableDefault = builder.disableAll;
    this.resource = createMetricInstruments(meter, enableMetrics, disableDefault);
    this.openTelemetryMetricsModule = new OpenTelemetryMetricsModule(resource);
  }

  /** Builder for configuring {@link GcpFallbackOpenTelemetry}. */
  public static class Builder {
    private OpenTelemetry openTelemetrySdk = OpenTelemetry.noop();
    private final Map<String, Boolean> enableMetrics = new HashMap<>();
    private boolean disableAll;

    private Builder() {}

    /**
     * Sets the {@link io.opentelemetry.api.OpenTelemetry} entrypoint to use. This can be used to
     * configure OpenTelemetry by returning the instance created by a {@code
     * io.opentelemetry.sdk.OpenTelemetrySdkBuilder}.
     */
    public Builder withSdk(OpenTelemetry sdk) {
      this.openTelemetrySdk = sdk;
      return this;
    }

    /**
     * Enables the specified metrics for collection and export. By default, all metrics are enabled.
     */
    public Builder enableMetrics(Collection<String> enableMetrics) {
      for (String metric : enableMetrics) {
        this.enableMetrics.put(metric, true);
      }
      return this;
    }

    /** Disables the specified metrics from being collected and exported. */
    public Builder disableMetrics(Collection<String> disableMetrics) {
      for (String metric : disableMetrics) {
        this.enableMetrics.put(metric, false);
      }
      return this;
    }

    /** Disable all metrics. Any desired metric must be explicitly enabled after this. */
    public Builder disableAllMetrics() {
      this.enableMetrics.clear();
      this.disableAll = true;
      return this;
    }

    /**
     * Returns a new {@link GcpFallbackOpenTelemetry} built with the configuration of this {@link
     * Builder}.
     */
    public GcpFallbackOpenTelemetry build() {
      return new GcpFallbackOpenTelemetry(this);
    }
  }

  static boolean isMetricEnabled(
      String metricName, Map<String, Boolean> enableMetrics, boolean disableDefault) {
    Boolean explicitlyEnabled = enableMetrics.get(metricName);
    if (explicitlyEnabled != null) {
      return explicitlyEnabled;
    }
    return OpenTelemetryMetricsModule.DEFAULT_METRICS_SET.contains(metricName) && !disableDefault;
  }

  static OpenTelemetryMetricsResource createMetricInstruments(
      Meter meter, Map<String, Boolean> enableMetrics, boolean disableDefault) {
    OpenTelemetryMetricsResource.Builder builder = OpenTelemetryMetricsResource.builder();

    if (isMetricEnabled(CURRENT_CHANNEL_METRIC, enableMetrics, disableDefault)) {
      builder.currentChannelGauge(
          meter
              .gaugeBuilder(String.format("%s.%s", METRIC_PREFIX, CURRENT_CHANNEL_METRIC))
              .ofLongs()
              .setUnit("{channel}")
              .setDescription("1 for currently active channel, 0 otherwise.")
              .build());
    }

    if (isMetricEnabled(FALLBACK_COUNT_METRIC, enableMetrics, disableDefault)) {
      builder.fallbackCounter(
          meter
              .counterBuilder(String.format("%s.%s", METRIC_PREFIX, FALLBACK_COUNT_METRIC))
              .setUnit("{occurrence}")
              .setDescription("Number of fallbacks occurred from one channel to another.")
              .build());
    }

    if (isMetricEnabled(CALL_STATUS_METRIC, enableMetrics, disableDefault)) {
      builder.callStatusCounter(
          meter
              .counterBuilder(String.format("%s.%s", METRIC_PREFIX, CALL_STATUS_METRIC))
              .setUnit("{call}")
              .setDescription("Number of calls with a status and channel.")
              .build());
    }

    if (isMetricEnabled(ERROR_RATIO_METRIC, enableMetrics, disableDefault)) {
      builder.errorRatioGauge(
          meter
              .gaugeBuilder(String.format("%s.%s", METRIC_PREFIX, ERROR_RATIO_METRIC))
              .setUnit("1")
              .setDescription("Ratio of failed calls to total calls for a channel.")
              .build());
    }

    if (isMetricEnabled(PROBE_RESULT_METRIC, enableMetrics, disableDefault)) {
      builder.probeResultCounter(
          meter
              .counterBuilder(String.format("%s.%s", METRIC_PREFIX, PROBE_RESULT_METRIC))
              .setUnit("{result}")
              .setDescription("Results of probing functions execution.")
              .build());
    }

    if (isMetricEnabled(CHANNEL_DOWNTIME_METRIC, enableMetrics, disableDefault)) {
      builder.channelDowntimeGauge(
          meter
              .gaugeBuilder(String.format("%s.%s", METRIC_PREFIX, CHANNEL_DOWNTIME_METRIC))
              .ofLongs()
              .setUnit("s")
              .setDescription("How many consecutive seconds probing fails for the channel.")
              .build());
    }

    return builder.build();
  }

  OpenTelemetryMetricsModule getModule() {
    return openTelemetryMetricsModule;
  }
}
