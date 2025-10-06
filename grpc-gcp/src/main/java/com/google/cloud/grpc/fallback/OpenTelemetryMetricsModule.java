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

import static com.google.cloud.grpc.fallback.GcpFallbackOpenTelemetry.CHANNEL_NAME;
import static com.google.cloud.grpc.fallback.GcpFallbackOpenTelemetry.FROM_CHANNEL_NAME;
import static com.google.cloud.grpc.fallback.GcpFallbackOpenTelemetry.PROBE_RESULT;
import static com.google.cloud.grpc.fallback.GcpFallbackOpenTelemetry.STATUS_CODE;
import static com.google.cloud.grpc.fallback.GcpFallbackOpenTelemetry.TO_CHANNEL_NAME;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
import io.grpc.Status.Code;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongGauge;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

final class OpenTelemetryMetricsModule {
  private static final Logger logger = Logger.getLogger(OpenTelemetryMetricsModule.class.getName());
  static final ImmutableSet<String> DEFAULT_METRICS_SET =
      ImmutableSet.of(
          "current_channel",
          "fallback_count",
          "call_status",
          "error_ratio",
          "probe_result",
          "channel_downtime");
  private final OpenTelemetryMetricsResource resource;
  private Map<String, Long> firstFailure = new ConcurrentHashMap<>();

  OpenTelemetryMetricsModule(OpenTelemetryMetricsResource resource) {
    this.resource = checkNotNull(resource, "resource");
  }

  void reportErrorRate(String channelName, float errorRate) {
    DoubleGauge errorRatioGauge = resource.errorRatioGauge();

    if (errorRatioGauge != null) {
      Attributes attributes = Attributes.of(CHANNEL_NAME, channelName);
      errorRatioGauge.set(errorRate, attributes);
    }
  }

  void reportStatus(String channelName, Code statusCode) {
    LongCounter callStatusCounter = resource.callStatusCounter();
    if (callStatusCounter == null) {
      return;
    }

    Attributes attributes =
        Attributes.of(CHANNEL_NAME, channelName, STATUS_CODE, statusCode.toString());

    callStatusCounter.add(1, attributes);
  }

  void reportProbeResult(String channelName, String result) {
    if (result == null) {
      return;
    }

    LongCounter probeResultCounter = resource.probeResultCounter();
    if (probeResultCounter != null) {

      Attributes attributes =
          Attributes.of(
              CHANNEL_NAME, channelName,
              PROBE_RESULT, result);

      probeResultCounter.add(1, attributes);
    }

    LongGauge downtimeGauge = resource.channelDowntimeGauge();
    if (downtimeGauge == null) {
      return;
    }

    Attributes attributes = Attributes.of(CHANNEL_NAME, channelName);

    if (result.isEmpty()) {
      firstFailure.remove(channelName);
      downtimeGauge.set(0, attributes);
    } else {
      firstFailure.putIfAbsent(channelName, System.nanoTime());
      downtimeGauge.set(
          (System.nanoTime() - firstFailure.get(channelName)) / 1_000_000_000, attributes);
    }
  }

  void reportCurrentChannel(String channelName, boolean current) {
    LongGauge channelGauge = resource.currentChannelGauge();
    if (channelGauge == null) {
      return;
    }

    Attributes attributes = Attributes.of(CHANNEL_NAME, channelName);

    if (current) {
      channelGauge.set(1, attributes);
    } else {
      channelGauge.set(0, attributes);
    }
  }

  void reportFallback(String fromChannelName, String toChannelName) {
    LongCounter fallbackCounter = resource.fallbackCounter();
    if (fallbackCounter == null) {
      return;
    }

    Attributes attributes =
        Attributes.of(
            FROM_CHANNEL_NAME, fromChannelName,
            TO_CHANNEL_NAME, toChannelName);

    fallbackCounter.add(1, attributes);
  }
}
