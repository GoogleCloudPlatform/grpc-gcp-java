package io.grpc.echo;

import static io.opencensus.exporter.trace.stackdriver.StackdriverExporter.createAndRegister;

import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.config.TraceConfig;
import io.opencensus.trace.samplers.Samplers;
import java.io.IOException;

class TracerManager {
  private static final LocalLogger logger = LocalLogger.getLogger(TracerManager.class.getName());

  final Tracer tracer;

  TracerManager() {
    try {
      createAndRegister();
    } catch (IOException e) {
      logger.warning("Cannot get tracer");
    }
    tracer = Tracing.getTracer();
    if (tracer == null) {
      logger.warning("Cannot get tracer");
      return;
    }
    TraceConfig globalTraceConfig = Tracing.getTraceConfig();
    globalTraceConfig.updateActiveTraceParams(
        globalTraceConfig.getActiveTraceParams().toBuilder()
            .setSampler(Samplers.alwaysSample())
            .build());
  }

  Tracer getTracer() {
    return tracer;
  }
}
