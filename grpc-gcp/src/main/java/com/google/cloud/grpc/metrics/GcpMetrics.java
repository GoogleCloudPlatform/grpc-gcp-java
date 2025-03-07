package com.google.cloud.grpc.metrics;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The GcpMetrics class provides a structured way to define and configure metrics. It defines
 * standard attributes, predefined metrics, metric types, units of measurement, and a flexible
 * configuration system using builders. This framework helps ensure consistency and ease of use when
 * instrumenting GCP API clients.
 */
public class GcpMetrics {
  // Convenient map of all built-in attributes to their names.
  public static final Map<Attr, String> allBuiltInAttributes =
      Collections.unmodifiableMap(
          Stream.of(Attr.values()).collect(Collectors.toMap((Attr x) -> x, Objects::toString)));

  // All predefined metrics.
  public static final Set<MetricConfig> allMetrics =
      Collections.unmodifiableSet(
          new HashSet<>(
              Stream.of(Metric.values())
                  .map(ConfigurableMetric::getDefaultConfig)
                  .collect(Collectors.toSet())));

  /**
   * The Attr enum defines a set of standard attributes that can be associated with metrics. These
   * attributes provide additional context about the metric being recorded. For example, STATUS
   * represents the gRPC status code, METHOD represents the gRPC method name, and so on.
   */
  public enum Attr {
    STATUS("grpc.status"),
    METHOD("grpc.method"),
    METHOD_TYPE("grpcgcp.method_type"),
    TRANSPARENT_RETRY("grpcgcp.tr_retry");

    private final String attr;

    Attr(String attr) {
      this.attr = attr;
    }

    @Override
    public String toString() {
      return attr;
    }
  }

  /**
   * The Metric enum defines a set of predefined metrics that can be recorded. Each metric has a
   * Kind (e.g., COUNTER, HISTOGRAM), an internal Unit (e.g., NANOSECONDS, OCCURENCES), and a
   * default MetricConfig.
   */
  public enum Metric implements ConfigurableMetric {
    START_DELAY(
        Kind.HISTOGRAM,
        Unit.NANOSECONDS,
        (new DoubleMetricConfig.Builder("start_delay")
                .withDescription("Delay by invocating interceptors in the beginning of the call.")
                .withUnit(Unit.SECONDS))
            .build()),
    RESOLUTION_DELAY(
        Kind.HISTOGRAM,
        Unit.NANOSECONDS,
        (new DoubleMetricConfig.Builder("name_resolution_delay")
                .withDescription("Delay caused by name resolution of the target.")
                .withUnit(Unit.SECONDS))
            .build()),
    CONNECTION_DELAY(
        Kind.HISTOGRAM,
        Unit.NANOSECONDS,
        (new DoubleMetricConfig.Builder("connection_delay")
                .withDescription("Delay caused by establishing a connection.")
                .withUnit(Unit.SECONDS))
            .build()),
    SEND_DELAY(
        Kind.HISTOGRAM,
        Unit.NANOSECONDS,
        (new DoubleMetricConfig.Builder("send_delay")
                .withDescription(
                    "Time spent after establishing a connection (or invoking "
                        + "interceptors if the connection is already established) and till sending "
                        + "headers.")
                .withUnit(Unit.SECONDS))
            .build()),
    HEADERS_LATENCY(
        Kind.HISTOGRAM,
        Unit.NANOSECONDS,
        (new DoubleMetricConfig.Builder("headers_latency")
                .withDescription("Time passed between sending and receiving headers.")
                .withUnit(Unit.SECONDS))
            .build()),
    RESPONSE_LATENCY(
        Kind.HISTOGRAM,
        Unit.NANOSECONDS,
        (new DoubleMetricConfig.Builder("response_latency")
                .withDescription(
                    "Time passed between sending headers and receiving complete " + "response.")
                .withUnit(Unit.SECONDS))
            .build()),
    PROCESSING_DELAY(
        Kind.HISTOGRAM,
        Unit.NANOSECONDS,
        (new DoubleMetricConfig.Builder("client_processing_latency")
                .withDescription(
                    "Time spent after receiving complete response and till all "
                        + "interceptors processed onClose.")
                .withUnit(Unit.SECONDS))
            .build()),
    TOTAL_LATENCY(
        Kind.HISTOGRAM,
        Unit.NANOSECONDS,
        (new DoubleMetricConfig.Builder("total_rpc_latency")
                .withDescription("Total time ovserved by GcpMetricsInterceptor.")
                .withUnit(Unit.SECONDS))
            .build());

    private final Kind kind;
    private final Unit internalUnit;
    private final MetricConfig templateConfig;
    private MetricConfig defaultConfig = null;

    Metric(Kind kind, Unit internalUnit, MetricConfig templateConfig) {
      this.kind = kind;
      this.internalUnit = internalUnit;
      this.templateConfig = templateConfig;
    }

    public MetricConfig getDefaultConfig() {
      if (defaultConfig == null) {
        defaultConfig =
            templateConfig
                .toBuilder()
                .withMetric(this)
                .withBuiltInAttributes(allBuiltInAttributes)
                .build();
      }
      return defaultConfig;
    }

    public Kind getKind() {
      return kind;
    }

    public Unit getInternalUnit() {
      return internalUnit;
    }
  }

  public interface ConfigurableMetric {
    MetricConfig getDefaultConfig();

    Kind getKind();

    Unit getInternalUnit();
  }

  // Kind of a metric.
  public enum Kind {
    COUNTER,
    // Has buckets.
    HISTOGRAM,
    // UPDOWNCOUNTER,
    // GAUGE
  }

  public enum Unit {
    NANOSECONDS("ns", 1),
    MICROSECONDS("us", 1_000),
    MILLISECONDS("ms", 1_000_000),
    SECONDS("s", 1_000_000_000),
    OCCURENCES("1");

    private final String unit;
    private final long baseFactor;

    Unit(String unit, long baseFactor) {
      this.unit = unit;
      this.baseFactor = baseFactor;
    }

    Unit(String unit) {
      this.unit = unit;
      this.baseFactor = 1;
    }

    @Override
    public String toString() {
      return unit;
    }

    public double convert(double value, Unit to) {
      return value * ((double) this.baseFactor / to.baseFactor);
    }

    public long convert(long value, Unit to) {
      return Math.round(value * ((double) this.baseFactor / to.baseFactor));
    }
  }

  /**
   * The MetricConfig abstract class represents the configuration of a metric. There are two
   * concrete subclasses: LongMetricConfig and DoubleMetricConfig, which represent configurations
   * for metrics with long and double values, respectively. These subclasses also allow specifying
   * buckets for histograms.
   */
  public abstract static class MetricConfig {
    protected final ConfigurableMetric metric;
    protected final String name;
    protected final String description;
    protected final String unit;
    protected final Unit reportedUnit;
    protected final Map<String, String> attributes;
    protected final Map<Attr, String> builtInAttributes;

    private MetricConfig(Builder<?> builder) {
      this.metric = builder.metric;
      this.name = builder.name;
      this.description = builder.description;
      this.unit = builder.unit;
      this.reportedUnit = builder.reportedUnit;
      this.attributes = builder.attributes;
      this.builtInAttributes = builder.builtInAttributes;
    }

    protected abstract Builder<?> toBuilder();

    public abstract DoubleMetricConfig.Builder toDoubleBuilder();

    public abstract LongMetricConfig.Builder toLongBuilder();

    public abstract static class Builder<T extends Builder<T>> {
      // These are the defaults for a metric config.
      protected ConfigurableMetric metric;
      protected String name;
      protected String description = "";
      protected String unit = "1";
      protected Unit reportedUnit = Unit.OCCURENCES;
      // Attributes allowed. A map from what internal attribute name is to how it should be
      // reported.
      protected Map<String, String> attributes = Collections.unmodifiableMap(new HashMap<>());
      // Built-in attributes.
      protected Map<Attr, String> builtInAttributes = Collections.unmodifiableMap(new HashMap<>());

      public abstract MetricConfig build();

      protected abstract T self();

      private Builder(String name) {
        this.withName(name);
      }

      protected T withMetric(ConfigurableMetric metric) {
        this.metric = metric;
        return self();
      }

      public T withName(String name) {
        Preconditions.checkNotNull(name);
        Preconditions.checkArgument(!name.isEmpty());
        this.name = name;
        return self();
      }

      public T withDescription(String description) {
        Preconditions.checkNotNull(description);
        this.description = description;
        return self();
      }

      public T withUnit(String unit) {
        Preconditions.checkNotNull(unit);
        this.unit = unit;
        return self();
      }

      public T withUnit(Unit unit) {
        Preconditions.checkNotNull(unit);
        this.reportedUnit = unit;
        this.unit = unit.toString();
        return self();
      }

      public T withAttributes(Map<String, String> attributes) {
        Preconditions.checkNotNull(attributes);
        this.attributes = Collections.unmodifiableMap(new HashMap<>(attributes));
        return self();
      }

      public T withBuiltInAttributes(Map<Attr, String> builtInAttributes) {
        Preconditions.checkNotNull(builtInAttributes);
        this.builtInAttributes = Collections.unmodifiableMap(new HashMap<>(builtInAttributes));
        return self();
      }
    }
  }

  public static class LongMetricConfig extends MetricConfig {
    private final List<Long> buckets;

    private LongMetricConfig(Builder builder) {
      super(builder);
      this.buckets = builder.buckets;
    }

    @Override
    protected Builder toBuilder() {
      return new Builder(name)
          .withMetric(metric)
          .withDescription(description)
          .withUnit(unit)
          .withAttributes(attributes)
          .withBuiltInAttributes(builtInAttributes)
          .withBuckets(buckets);
    }

    @Override
    public Builder toLongBuilder() {
      return toBuilder();
    }

    @Override
    public DoubleMetricConfig.Builder toDoubleBuilder() {
      return new DoubleMetricConfig.Builder(name)
          .withMetric(metric)
          .withDescription(description)
          .withUnit(unit)
          .withAttributes(attributes)
          .withBuiltInAttributes(builtInAttributes)
          .withBuckets(buckets.stream().map(Long::doubleValue).collect(Collectors.toList()));
    }

    public static class Builder extends MetricConfig.Builder<Builder> {
      private List<Long> buckets = new ArrayList<>();

      private Builder(String name) {
        super(name);
      }

      public Builder withBuckets(List<Long> buckets) {
        Preconditions.checkNotNull(buckets);
        if (!buckets.isEmpty() && metric != null) {
          Preconditions.checkArgument(metric.getKind() == Kind.HISTOGRAM);
        }
        this.buckets = buckets;
        return this;
      }

      @Override
      public LongMetricConfig build() {
        return new LongMetricConfig(this);
      }

      @Override
      protected Builder self() {
        return this;
      }
    }
  }

  public static class DoubleMetricConfig extends MetricConfig {
    private final List<Double> buckets;

    public List<Double> getBuckets() {
      return buckets;
    }

    private DoubleMetricConfig(Builder builder) {
      super(builder);
      this.buckets = builder.buckets;
    }

    @Override
    protected Builder toBuilder() {
      return new Builder(name)
          .withMetric(metric)
          .withDescription(description)
          .withUnit(unit)
          .withAttributes(attributes)
          .withBuiltInAttributes(builtInAttributes)
          .withBuckets(buckets);
    }

    @Override
    public Builder toDoubleBuilder() {
      return toBuilder();
    }

    @Override
    public LongMetricConfig.Builder toLongBuilder() {
      return new LongMetricConfig.Builder(name)
          .withMetric(metric)
          .withDescription(description)
          .withUnit(unit)
          .withAttributes(attributes)
          .withBuiltInAttributes(builtInAttributes)
          .withBuckets(buckets.stream().map(Double::longValue).collect(Collectors.toList()));
    }

    public static class Builder extends MetricConfig.Builder<Builder> {
      private List<Double> buckets = new ArrayList<>();

      private Builder(String name) {
        super(name);
      }

      public Builder withBuckets(List<Double> buckets) {
        Preconditions.checkNotNull(buckets);
        if (!buckets.isEmpty() && metric != null) {
          Preconditions.checkArgument(metric.getKind() == Kind.HISTOGRAM);
        }
        this.buckets = buckets;
        return this;
      }

      @Override
      public DoubleMetricConfig build() {
        return new DoubleMetricConfig(this);
      }

      @Override
      protected Builder self() {
        return this;
      }
    }
  }
}
