/*
 * Copyright 2018 Google LLC
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

import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableList;
import com.google.spanner.v1.SpannerGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.auth.MoreCallCredentials;
import io.opencensus.common.Scope;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceConfiguration;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceExporter;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.config.TraceConfig;
import io.opencensus.trace.samplers.Samplers;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/** Main method of the cloudprober as an entrypoint to execute probes. */
public class Prober {

  private static final String OAUTH_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
  private static final String SPANNER_TARGET = System.getenv("SPANNER_TARGET");
  private static final String PROJECT_ID = System.getenv("GOOGLE_CLOUD_PROJECT");

  private Prober() {}

  private static boolean[] parseArgs(String[] args) {
    // {spanner, others(firestore, bigtable..)}
    boolean[] vars = new boolean[] {false};
    boolean usage = false;
    for (String arg : args) {
      // Currently, we only support spanner cloudprober. May add new features in the future.
      if (arg.equals("--spanner")) {
        vars[0] = true;
      }
    }
    return vars;
  }

  /**
   * return a global tracer
   */
  private static Tracer getTracer() {
    try {
      createAndRegister();
    }
    catch (IOException e) {
      return null;
    }
    return Tracing.getTracer();
  }

  /**
   * register stackdriver exporter
   */
  private static void createAndRegister() throws IOException {
    StackdriverTraceExporter.createAndRegister(
            StackdriverTraceConfiguration.builder()
                    .setProjectId(PROJECT_ID)
                    .build());
  }

  private static void excuteSpannerProber() throws InterruptedException {

    StackdriverUtils util = new StackdriverUtils("Spanner");
    ManagedChannel channel = ManagedChannelBuilder.forAddress(SPANNER_TARGET, 443).build();
    GoogleCredentials creds = getCreds(util);
    SpannerGrpc.SpannerBlockingStub stub =
        SpannerGrpc.newBlockingStub(channel).withCallCredentials(MoreCallCredentials.from(creds));

    int failureCount = 0;

    Tracer tracer = getTracer();
    if (tracer == null) {
      return;
    }

    // Get the global TraceConfig
    TraceConfig globalTraceConfig = Tracing.getTraceConfig();

    // Update the global TraceConfig to always trace
    globalTraceConfig.updateActiveTraceParams(
            globalTraceConfig.getActiveTraceParams().toBuilder().setSampler(Samplers.alwaysSample()).build());

    // Start tracing
    HashMap<String, AttributeValue> map = new HashMap<>();
    map.put("endpoint", AttributeValue.stringAttributeValue(SPANNER_TARGET));
    try (Scope scope = tracer.spanBuilder("session_management_java").startScopedSpan()) {
      tracer.getCurrentSpan().addAnnotation("endpoint info available", map);
      doOneProber(() -> SpannerProbes.sessionManagementProber(stub), failureCount, util);
    }
    try (Scope scope = tracer.spanBuilder("execute_sql_java").startScopedSpan()) {
      tracer.getCurrentSpan().addAnnotation("endpoint info available", map);
      doOneProber(() -> SpannerProbes.executeSqlProber(stub), failureCount, util);
    }
    try (Scope scope = tracer.spanBuilder("read_java").startScopedSpan()) {
      tracer.getCurrentSpan().addAnnotation("endpoint info available", map);
      doOneProber(() -> SpannerProbes.readProber(stub), failureCount, util);
    }
    try (Scope scope = tracer.spanBuilder("transaction_java").startScopedSpan()) {
      tracer.getCurrentSpan().addAnnotation("endpoint info available", map);
      doOneProber(() -> SpannerProbes.transactionProber(stub), failureCount, util);
    }
    try (Scope scope = tracer.spanBuilder("partition_java").startScopedSpan()) {
      tracer.getCurrentSpan().addAnnotation("endpoint info available", map);
      doOneProber(() -> SpannerProbes.partitionProber(stub), failureCount, util);
    }
    Tracing.getExportComponent().shutdown();
    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);

    if (failureCount == 0) {
      util.setSuccess(true);
    }
    util.outputMetrics();
  }

  private static void doOneProber(OneProber prober, int failureCount, StackdriverUtils util) {
    try {
      prober.probe();
    } catch (Exception e) {
      util.reportError(
          e.getMessage(), "doOneProber", Thread.currentThread().getStackTrace()[2].getLineNumber());
      failureCount++;
    }
  }

  private interface OneProber {
    void probe() throws Exception;
  }

  /** Set the authentication. */
  private static GoogleCredentials getCreds(StackdriverUtils util) throws InterruptedException {
    GoogleCredentials creds;
    try {
      creds = GoogleCredentials.getApplicationDefault();
    } catch (Exception e) {
      util.reportError(
          e.getMessage(),
          "GoogleCredentials",
          Thread.currentThread().getStackTrace()[2].getLineNumber());
      return null;
    }
    ImmutableList<String> requiredScopes = ImmutableList.of(OAUTH_SCOPE);
    creds = creds.createScoped(requiredScopes);
    return creds;
  }

  /** The entrypoint of the cloudprober. */
  public static void main(String[] args) throws InterruptedException {
    boolean[] vars = parseArgs(args);
    if (vars[0]) {
      excuteSpannerProber();
    }
  }
}
