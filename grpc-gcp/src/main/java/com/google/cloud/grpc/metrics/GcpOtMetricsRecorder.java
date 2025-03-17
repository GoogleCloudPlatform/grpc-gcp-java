package com.google.cloud.grpc.metrics;

import com.google.cloud.grpc.metrics.GcpMetrics.ConfigurableMetric;
import com.google.cloud.grpc.metrics.GcpMetrics.DoubleMetricConfig;
import com.google.cloud.grpc.metrics.GcpMetrics.MetricConfig;
import com.google.common.base.Preconditions;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.Context;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class GcpOtMetricsRecorder implements GcpMetricsRecorder {
  private final Map<ConfigurableMetric, MetricWrapper> recordMap = new HashMap<>();
  private final Attributes staticAttributes;
  private final Map<String, AttributeKey<String>> attrsKeyMap;
  private final Map<String, AttributeKey<String>> oTelAttrKeyMap;

  private enum MType {
    DOUBLE_HISTOGRAM,
    LONG_HISTOGRAM,
    DOUBLE_COUNTER,
    LONG_COUNTER
  }

  private class MetricWrapper {
    private final MetricConfig metricConfig;
    private final Map<String, AttributeKey<String>> attrMap;

    private DoubleHistogram dh;
    private LongHistogram lh;
    private DoubleCounter dc;
    private LongCounter lc;

    private MType mType;

    public MetricWrapper(Meter meter, GcpMetrics.MetricConfig metricConfig) {
      this.metricConfig = metricConfig;

      // Prepare allowed attribute keys map for the metric.
      Map<String, AttributeKey<String>> aMap = new HashMap<>();
      // Include commonly allowed attributes.
      aMap.putAll(attrsKeyMap);

      // Include built-in attributes specific to this metric.
      metricConfig.builtInAttributes.forEach(
          (attr, reportAs) -> {
            aMap.put(attr.toString(), oTelAttrKeyMap.get(reportAs));
          });
      // Include attributes specific to this metric.
      metricConfig.attributes.forEach(
          (attr, reportAs) -> {
            aMap.put(attr, oTelAttrKeyMap.get(reportAs));
          });
      this.attrMap = Collections.unmodifiableMap(aMap);

      if (metricConfig instanceof DoubleMetricConfig) {
        switch (metricConfig.metric.getKind()) {
          case HISTOGRAM:
            mType = MType.DOUBLE_HISTOGRAM;
            DoubleHistogramBuilder builder =
                meter
                    .histogramBuilder(metricConfig.name)
                    .setDescription(metricConfig.description)
                    .setUnit(metricConfig.unit);
            if (!((DoubleMetricConfig) metricConfig).getBuckets().isEmpty()) {
              builder.setExplicitBucketBoundariesAdvice(
                  ((DoubleMetricConfig) metricConfig).getBuckets());
            }
            dh = builder.build();

            break;
          case COUNTER:
            mType = MType.DOUBLE_COUNTER;
        }
      } else {
        switch (metricConfig.metric.getKind()) {
          case HISTOGRAM:
            mType = MType.LONG_HISTOGRAM;
            break;
          case COUNTER:
            mType = MType.LONG_COUNTER;
            lc =
                meter
                    .counterBuilder(metricConfig.name)
                    .setDescription(metricConfig.description)
                    .setUnit(metricConfig.unit)
                    .build();
        }
      }
    }

    public void record(long value, Attributes attributes) {
      switch (mType) {
        case LONG_COUNTER:
          lc.add(
              metricConfig.metric.getInternalUnit().convert(value, metricConfig.reportedUnit),
              attributes,
              Context.current());
          break;
        case DOUBLE_COUNTER:
          dc.add(
              metricConfig
                  .metric
                  .getInternalUnit()
                  .convert(((Long) value).doubleValue(), metricConfig.reportedUnit),
              attributes,
              Context.current());
          break;
        case LONG_HISTOGRAM:
          lh.record(
              metricConfig.metric.getInternalUnit().convert(value, metricConfig.reportedUnit),
              attributes,
              Context.current());
          break;
        case DOUBLE_HISTOGRAM:
          dh.record(
              metricConfig
                  .metric
                  .getInternalUnit()
                  .convert(((Long) value).doubleValue(), metricConfig.reportedUnit),
              attributes,
              Context.current());
          break;
      }
    }

    public void record(double value, Attributes attributes) {
      switch (mType) {
        case LONG_COUNTER:
          lc.add(
              ((Double)
                      metricConfig
                          .metric
                          .getInternalUnit()
                          .convert(value, metricConfig.reportedUnit))
                  .longValue(),
              attributes,
              Context.current());
          break;
        case DOUBLE_COUNTER:
          dc.add(
              metricConfig.metric.getInternalUnit().convert(value, metricConfig.reportedUnit),
              attributes,
              Context.current());
          break;
        case LONG_HISTOGRAM:
          lh.record(
              ((Double)
                      metricConfig
                          .metric
                          .getInternalUnit()
                          .convert(value, metricConfig.reportedUnit))
                  .longValue(),
              attributes,
              Context.current());
          break;
        case DOUBLE_HISTOGRAM:
          dh.record(
              metricConfig.metric.getInternalUnit().convert(value, metricConfig.reportedUnit),
              attributes,
              Context.current());
          break;
      }
    }
  }

  public GcpOtMetricsRecorder(OpenTelemetry openTelemetry, GcpMetricsConfig config) {
    Meter meter =
        openTelemetry
            .meterBuilder(config.getMeterName())
            // TODO: move to config.
            .setInstrumentationVersion(
                GcpOtMetricsRecorder.class.getPackage().getImplementationVersion())
            .build();

    Map<String, AttributeKey<String>> oMap = new HashMap<>();
    config
        .getStaticAttributes()
        .keySet()
        .forEach((key) -> oMap.put(key, AttributeKey.stringKey(key)));

    config
        .getEnabledMetrics()
        .forEach(
            metricConfig -> {
              metricConfig
                  .builtInAttributes
                  .values()
                  .forEach((recordAs) -> oMap.put(recordAs, AttributeKey.stringKey(recordAs)));
              metricConfig
                  .attributes
                  .values()
                  .forEach((recordAs) -> oMap.put(recordAs, AttributeKey.stringKey(recordAs)));
            });

    oTelAttrKeyMap = Collections.unmodifiableMap(oMap);

    AttributesBuilder staticAttributesBuilder = Attributes.builder();
    config.getStaticAttributes().forEach(staticAttributesBuilder::put);
    staticAttributes = staticAttributesBuilder.build();

    Map<String, AttributeKey<String>> aMap = new HashMap<>();
    config
        .getCommonBuiltInAttrKeysMap()
        .forEach(
            (attr, reportAs) -> {
              aMap.put(attr.toString(), oTelAttrKeyMap.get(reportAs));
            });
    config
        .getCommonAttrKeysMap()
        .forEach(
            (attr, reportAs) -> {
              aMap.put(attr, oTelAttrKeyMap.get(reportAs));
            });
    attrsKeyMap = Collections.unmodifiableMap(aMap);

    config
        .getEnabledMetrics()
        .forEach(
            metricConfig -> {
              recordMap.put(metricConfig.metric, new MetricWrapper(meter, metricConfig));
            });
  }

  @Override
  public void record(ConfigurableMetric metric, long value, Map<String, String> attributes) {
    MetricWrapper wrapper = recordMap.get(metric);
    if (wrapper == null) {
      return;
    }
    wrapper.record(value, toOtelAttributes(attributes, wrapper));
  }

  @Override
  public void record(ConfigurableMetric metric, double value, Map<String, String> attributes) {
    MetricWrapper wrapper = recordMap.get(metric);
    if (wrapper == null) {
      return;
    }
    wrapper.record(value, toOtelAttributes(attributes, wrapper));
  }

  @Override
  public void recordAll(Map<ConfigurableMetric, Number> recs, Map<String, String> attributes) {
    // Cache for resulting attributes.
    Map<Map<String, AttributeKey<String>>, Attributes> attrCache = new HashMap<>();

    recs.forEach(
        (metric, number) -> {
          MetricWrapper wrapper = recordMap.get(metric);
          if (wrapper != null) {
            attrCache.computeIfAbsent(
                wrapper.attrMap, (attrMap) -> toOtelAttributes(attributes, attrMap));
            Attributes attrs = attrCache.get(wrapper.attrMap);

            if (number instanceof Long) {
              wrapper.record((long) number, attrs);
            }
            if (number instanceof Double) {
              wrapper.record((double) number, attrs);
            }
          }
        });
  }

  private Attributes toOtelAttributes(
      Map<String, String> attributes, Map<String, AttributeKey<String>> attrMap) {
    Preconditions.checkNotNull(attributes);

    AttributesBuilder attributesBuilder = staticAttributes.toBuilder();
    attributes.forEach(
        (key, value) -> {
          if (attrMap.containsKey(key)) {
            attributesBuilder.put(attrMap.get(key), value);
          }
        });

    return attributesBuilder.build();
  }

  private Attributes toOtelAttributes(Map<String, String> attributes, MetricWrapper wrapper) {
    return toOtelAttributes(attributes, wrapper.attrMap);
  }
}
