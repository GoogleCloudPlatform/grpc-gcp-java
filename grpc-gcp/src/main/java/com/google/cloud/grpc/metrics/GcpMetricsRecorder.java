package com.google.cloud.grpc.metrics;

import com.google.cloud.grpc.metrics.GcpMetrics.ConfigurableMetric;
import java.util.Map;

public interface GcpMetricsRecorder {
  void record(ConfigurableMetric metric, long value, Map<String, String> attributes);

  void record(ConfigurableMetric metric, double value, Map<String, String> attributes);

  void recordAll(Map<ConfigurableMetric, Number> recs, Map<String, String> attributes);
}
