package com.google.grpc.cloudprober;

import java.util.HashMap;
import java.util.Map;

/** The class to manage latency metrics. */
public class StackdriverUtils {
  private Map<String, Long> metrics;
  private Boolean success;
  private String apiName;

  StackdriverUtils(String apiName) {
    metrics = new HashMap<>();
    success = false;
    this.apiName = apiName;
  }

  public void addMetric(String name, Long value) {
    metrics.put(name, value);
  }

  public void setSuccess(Boolean success) {
    this.success = success;
  }

  public void addMetricsDict(Map<String, Long> dict) {
    for (Map.Entry<String, Long> entry : dict.entrySet()) {
      metrics.put(entry.getKey(), entry.getValue());
    }
  }

  public void outputMetrics() {
    if (success) {
      System.out.println(String.format("%s_success 1", apiName));
    } else {
      System.out.println(String.format("%s_success 0", apiName));
    }
    for (Map.Entry<String, Long> entry : metrics.entrySet()) {
      System.out.println(String.format("%s %d", entry.getKey(), entry.getValue()));
    }
  }
}
