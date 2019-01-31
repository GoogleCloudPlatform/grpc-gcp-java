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

package com.google.grpc.cloudprober;

import java.util.HashMap;
import java.util.Map;

/** The class to manage latency metrics. */
public class StackdriverUtils {
  private Map<String, Long> metrics;
  private boolean success;
  private String apiName;

  StackdriverUtils(String apiName) {
    metrics = new HashMap<>();
    success = false;
    this.apiName = apiName;
  }

  public void addMetric(String name, long value) {
    metrics.put(name, value);
  }

  public void setSuccess(boolean success) {
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
