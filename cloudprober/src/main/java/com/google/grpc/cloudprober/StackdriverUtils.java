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

import com.google.cloud.ServiceOptions;
import com.google.cloud.errorreporting.v1beta1.ReportErrorsServiceClient;
import com.google.devtools.clouderrorreporting.v1beta1.ErrorContext;
import com.google.devtools.clouderrorreporting.v1beta1.ProjectName;
import com.google.devtools.clouderrorreporting.v1beta1.ReportedErrorEvent;
import com.google.devtools.clouderrorreporting.v1beta1.SourceLocation;
import java.util.HashMap;
import java.util.Map;

/** The class to manage latency metrics. */
public class StackdriverUtils {
  private Map<String, Long> metrics = new HashMap<>();;
  private boolean success = false;
  private ReportErrorsServiceClient errClient;
  private String apiName;

  StackdriverUtils(String apiName) {
    this.apiName = apiName;
    try {
      errClient = ReportErrorsServiceClient.create();
    } catch (java.io.IOException e) {
      errClient = null;
      System.err.println("Failed to create the error client.");
    }
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

  /** Report the error to the stackdriver errorlog. */
  public void reportError(String error, String funcName, int line) {
    // Report to the std error.
    System.err.println(error);

    // Report to the stackdriver.
    if (errClient != null) {
      String projectId = ServiceOptions.getDefaultProjectId();
      ProjectName projectName = ProjectName.of(projectId);
      // Custom error events require an error reporting location as well.
      ErrorContext errorContext =
          ErrorContext.newBuilder()
              .setReportLocation(
                  SourceLocation.newBuilder()
                      .setFilePath("Prober.java")
                      .setLineNumber(line)
                      .setFunctionName(funcName)
                      .build())
              .build();

      // Report a custom error event
      ReportedErrorEvent customErrorEvent =
          ReportedErrorEvent.getDefaultInstance()
              .toBuilder()
              .setMessage(error)
              .setContext(errorContext)
              .build();
      // Report an event synchronously, use .reportErrorEventCallable for asynchronous reporting.
      errClient.reportErrorEvent(projectName, customErrorEvent);
    }
  }
}
