package com.google.cloud.grpc.metrics;

import com.google.cloud.grpc.metrics.GcpMetrics.Attr;
import com.google.cloud.grpc.metrics.GcpMetrics.MetricConfig;
import com.google.common.base.Preconditions;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GcpMetricsConfig {
  private static final String DEFAULT_METER_NAME = "grpc-gcp";

  private final String meterName;
  private final String metricPrefix;
  private final Map<String, String> staticAttributes;
  // Attrs allowed for all metrics.
  private final Map<String, String> commonAttrKeysMap;
  private final Map<Attr, String> commonBuiltInAttrKeysMap;
  private final Set<MetricConfig> enabledMetrics;

  public static Builder newBuilder() {
    return new Builder();
  }

  public static GcpMetricsConfig defaultConfig() {
    return newBuilder().build();
  }

  private GcpMetricsConfig(Builder builder) {
    meterName = builder.meterName;
    metricPrefix = builder.metricPrefix;
    staticAttributes = builder.staticAttributes;
    commonAttrKeysMap = builder.commonAttrKeysMap;
    commonBuiltInAttrKeysMap = builder.commonBuiltInAttrKeysMap;
    enabledMetrics = builder.enabledMetrics;
  }

  public String getMeterName() {
    return meterName;
  }

  public String getMetricPrefix() {
    return metricPrefix;
  }

  public Map<String, String> getStaticAttributes() {
    return staticAttributes;
  }

  public Map<String, String> getCommonAttrKeysMap() {
    return commonAttrKeysMap;
  }

  public Map<Attr, String> getCommonBuiltInAttrKeysMap() {
    return commonBuiltInAttrKeysMap;
  }

  public Set<MetricConfig> getEnabledMetrics() {
    return enabledMetrics;
  }

  public static class Builder {
    private String meterName = DEFAULT_METER_NAME;
    private String metricPrefix = "";
    private Map<String, String> staticAttributes =
        Collections.unmodifiableMap(Collections.emptyMap());
    // Attrs allowed for all metrics.
    Map<String, String> commonAttrKeysMap = new HashMap<>();
    // All built-in attributes enabled by default.
    Map<Attr, String> commonBuiltInAttrKeysMap = GcpMetrics.allBuiltInAttributes;
    // All metrics enabled by default.
    Set<MetricConfig> enabledMetrics = GcpMetrics.allMetrics;

    public Builder() {}

    public Builder withMeterName(String meterName) {
      Preconditions.checkNotNull(meterName);
      Preconditions.checkArgument(!meterName.isEmpty());
      this.meterName = meterName;
      return this;
    }

    public Builder withMetricPrefix(String metricPrefix) {
      Preconditions.checkNotNull(metricPrefix);
      this.metricPrefix = metricPrefix;
      return this;
    }

    public Builder withStaticAttributes(Map<String, String> staticAttributes) {
      Preconditions.checkNotNull(staticAttributes);
      this.staticAttributes = Collections.unmodifiableMap(new HashMap<>(staticAttributes));
      return this;
    }

    public Builder withCommonAttrKeysMap(Map<String, String> commonAttrKeysMap) {
      Preconditions.checkNotNull(commonAttrKeysMap);
      this.commonAttrKeysMap = Collections.unmodifiableMap(new HashMap<>(commonAttrKeysMap));
      return this;
    }

    public Builder withCommonBuiltInAttrKeysMap(Map<Attr, String> commonBuiltInAttrKeysMap) {
      Preconditions.checkNotNull(commonBuiltInAttrKeysMap);
      this.commonBuiltInAttrKeysMap =
          Collections.unmodifiableMap(new HashMap<>(commonBuiltInAttrKeysMap));
      return this;
    }

    public Builder withEnabledMetrics(Set<MetricConfig> enabledMetrics) {
      Preconditions.checkNotNull(enabledMetrics);
      this.enabledMetrics = Collections.unmodifiableSet(new HashSet<>(enabledMetrics));
      return this;
    }

    public GcpMetricsConfig build() {
      return new GcpMetricsConfig(this);
    }
  }
}
