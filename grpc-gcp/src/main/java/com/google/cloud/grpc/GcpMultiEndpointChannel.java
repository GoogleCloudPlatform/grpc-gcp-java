/*
 * Copyright 2022 Google LLC
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

package com.google.cloud.grpc;

import static com.google.cloud.grpc.GcpMetricsConstants.COUNT;
import static com.google.cloud.grpc.GcpMetricsConstants.ENDPOINT_LABEL;
import static com.google.cloud.grpc.GcpMetricsConstants.ENDPOINT_LABEL_DESC;
import static com.google.cloud.grpc.GcpMetricsConstants.METRIC_CURRENT_ENDPOINT;
import static com.google.cloud.grpc.GcpMetricsConstants.METRIC_ENDPOINT_STATE;
import static com.google.cloud.grpc.GcpMetricsConstants.METRIC_ENDPOINT_SWITCH;
import static com.google.cloud.grpc.GcpMetricsConstants.ME_NAME_LABEL;
import static com.google.cloud.grpc.GcpMetricsConstants.ME_NAME_LABEL_DESC;
import static com.google.cloud.grpc.GcpMetricsConstants.STATUS_AVAILABLE;
import static com.google.cloud.grpc.GcpMetricsConstants.STATUS_LABEL;
import static com.google.cloud.grpc.GcpMetricsConstants.STATUS_LABEL_DESC;
import static com.google.cloud.grpc.GcpMetricsConstants.STATUS_UNAVAILABLE;
import static com.google.cloud.grpc.GcpMetricsConstants.TYPE_FALLBACK;
import static com.google.cloud.grpc.GcpMetricsConstants.TYPE_LABEL;
import static com.google.cloud.grpc.GcpMetricsConstants.TYPE_LABEL_DESC;
import static com.google.cloud.grpc.GcpMetricsConstants.TYPE_RECOVER;
import static com.google.cloud.grpc.GcpMetricsConstants.TYPE_REPLACE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.google.cloud.grpc.GcpManagedChannelOptions.GcpChannelPoolOptions;
import com.google.cloud.grpc.GcpManagedChannelOptions.GcpMetricsOptions;
import com.google.cloud.grpc.multiendpoint.MultiEndpoint;
import com.google.cloud.grpc.proto.ApiConfig;
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ClientCall.Listener;
import io.grpc.ConnectivityState;
import io.grpc.Context;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.opencensus.metrics.DerivedLongCumulative;
import io.opencensus.metrics.DerivedLongGauge;
import io.opencensus.metrics.LabelKey;
import io.opencensus.metrics.LabelValue;
import io.opencensus.metrics.MetricOptions;
import io.opencensus.metrics.MetricRegistry;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * The purpose of GcpMultiEndpointChannel is twofold:
 *
 * <ol>
 *   <li>Fallback to an alternative endpoint (host:port) of a gRPC service when the original
 *   endpoint is completely unavailable.
 *   <li>Be able to route an RPC call to a specific group of endpoints.
 * </ol>
 *
 * <p>A group of endpoints is called a {@link MultiEndpoint} and is essentially a list of endpoints
 * where priority is defined by the position in the list with the first endpoint having top
 * priority. A MultiEndpoint tracks endpoints' availability. When a MultiEndpoint is picked for an
 * RPC call, it picks the top priority endpoint that is currently available. More information on
 * the {@link MultiEndpoint} class.
 *
 * <p>GcpMultiEndpointChannel can have one or more MultiEndpoint identified by its name -- arbitrary
 * string provided in the {@link GcpMultiEndpointOptions} when configuring MultiEndpoints. This name
 * can be used to route an RPC call to this MultiEndpoint by setting the {@link #ME_KEY} key value
 * of the RPC {@link CallOptions}.
 *
 * <p>GcpMultiEndpointChannel receives a list of GcpMultiEndpointOptions for initial configuration.
 * An updated configuration can be provided at any time later using
 * {@link GcpMultiEndpointChannel#setMultiEndpoints(List)}. The first item in the
 * GcpMultiEndpointOptions list defines the default MultiEndpoint that will be used when no
 * MultiEndpoint name is provided with an RPC call.
 *
 * <p>Example:
 *
 * <p>Let's assume we have a service with read and write operations and the following backends:
 * <ul>
 *   <li>service.example.com -- the main set of backends supporting all operations</li>
 *   <li>service-fallback.example.com -- read-write replica supporting all operations</li>
 *   <li>ro-service.example.com -- read-only replica supporting only read operations</li>
 * </ul>
 *
 * <p>Example configuration:
 * <ul>
 *   <li>
 *     MultiEndpoint named "default" with endpoints:
 *     <ol>
 *       <li>service.example.com:443</li>
 *       <li>service-fallback.example.com:443</li>
 *     </ol>
 *   </li>
 *   <li>
 *     MultiEndpoint named "read" with endpoints:
 *     <ol>
 *       <li>ro-service.example.com:443</li>
 *       <li>service-fallback.example.com:443</li>
 *       <li>service.example.com:443</li>
 *     </ol>
 *   </li>
 * </ul>
 *
 * <p>With the configuration above GcpMultiEndpointChannel will use the "default" MultiEndpoint by
 * default. It means that RPC calls by default will use the main endpoint and if it is not available
 * then the read-write replica.
 *
 * <p>To offload some read calls to the read-only replica we can specify "read" MultiEndpoint in
 * the CallOptions. Then these calls will use the read-only replica endpoint and if it is not
 * available then the read-write replica and if it is also not available then the main endpoint.
 *
 * <p>GcpMultiEndpointChannel creates a {@link GcpManagedChannel} channel pool for every unique
 * endpoint. For the example above three channel pools will be created.
 */
public class GcpMultiEndpointChannel extends ManagedChannel {

  private static final Logger logger = Logger.getLogger(GcpMultiEndpointChannel.class.getName());
  public static final CallOptions.Key<String> ME_KEY = CallOptions.Key.create("MultiEndpoint");
  public static final Context.Key<String> ME_CONTEXT_KEY = Context.key("MultiEndpoint");
  private final LabelKey endpointKey = LabelKey.create(ENDPOINT_LABEL, ENDPOINT_LABEL_DESC);
  private final Map<String, MultiEndpoint> multiEndpoints = new ConcurrentHashMap<>();
  private MultiEndpoint defaultMultiEndpoint;
  private final ApiConfig apiConfig;
  private final GcpManagedChannelOptions gcpManagedChannelOptions;
  private DerivedLongGauge endpointStateMetric;
  private DerivedLongCumulative endpointSwitchMetric;
  private DerivedLongGauge currentEndpointMetric;

  private final Map<String, CurrentEndpointWatcher> currentEndpointWatchers =
      new ConcurrentHashMap<>();

  private final Map<String, GcpManagedChannel> pools = new ConcurrentHashMap<>();

  @GuardedBy("this")
  private final Set<String> currentEndpoints = new HashSet<>();

  private final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);

  /**
   * Constructor for {@link GcpMultiEndpointChannel}.
   *
   * @param meOptions list of MultiEndpoint configurations.
   * @param apiConfig the ApiConfig object for configuring GcpManagedChannel.
   * @param gcpManagedChannelOptions the options for GcpManagedChannel.
   */
  public GcpMultiEndpointChannel(
      List<GcpMultiEndpointOptions> meOptions,
      ApiConfig apiConfig,
      GcpManagedChannelOptions gcpManagedChannelOptions) {
    this.apiConfig = apiConfig;
    this.gcpManagedChannelOptions = gcpManagedChannelOptions;
    createMetrics();
    setMultiEndpoints(meOptions);
  }

  private class EndpointStateMonitor implements Runnable {

    private final ManagedChannel channel;
    private final String endpoint;
    private ConnectivityState currentState;

    private EndpointStateMonitor(ManagedChannel channel, String endpoint) {
      this.endpoint = endpoint;
      this.channel = channel;
      setUpMetrics();
      run();
    }

    private void setUpMetrics() {
      if (endpointStateMetric == null) {
        return;
      }

      endpointStateMetric.createTimeSeries(
          Arrays.asList(
              LabelValue.create(endpoint),
              LabelValue.create(STATUS_AVAILABLE)
          ),
          this,
          EndpointStateMonitor::reportAvailable
          );
      endpointStateMetric.createTimeSeries(
          Arrays.asList(
              LabelValue.create(endpoint),
              LabelValue.create(STATUS_UNAVAILABLE)
          ),
          this,
          EndpointStateMonitor::reportUnavailable
      );
    }

    private void removeMetrics() {
      if (endpointStateMetric == null) {
        return;
      }

      endpointStateMetric.removeTimeSeries(Arrays.asList(
          LabelValue.create(endpoint),
          LabelValue.create(STATUS_AVAILABLE)
      ));
      endpointStateMetric.removeTimeSeries(Arrays.asList(
          LabelValue.create(endpoint),
          LabelValue.create(STATUS_UNAVAILABLE)
      ));
    }

    private long reportAvailable() {
      return ConnectivityState.READY.equals(currentState) ? 1 : 0;
    }

    private long reportUnavailable() {
      return ConnectivityState.READY.equals(currentState) ? 0 : 1;
    }

    @Override
    public void run() {
      if (channel == null) {
        removeMetrics();
        return;
      }
      currentState = checkPoolState(channel, endpoint);
      if (currentState != ConnectivityState.SHUTDOWN) {
        channel.notifyWhenStateChanged(currentState, this);
      } else {
        removeMetrics();
      }
    }
  }

  // Checks and returns channel pool state. Also notifies all MultiEndpoints of the pool state.
  private ConnectivityState checkPoolState(ManagedChannel channel, String endpoint) {
    ConnectivityState state = channel.getState(false);
    // Update endpoint state in all multiendpoints.
    for (MultiEndpoint me : multiEndpoints.values()) {
      me.setEndpointAvailable(endpoint, state.equals(ConnectivityState.READY));
    }
    return state;
  }

  private GcpManagedChannelOptions prepareGcpManagedChannelConfig(
      GcpManagedChannelOptions gcpOptions, String endpoint) {
    final GcpMetricsOptions.Builder metricsOptions = GcpMetricsOptions.newBuilder(
        gcpOptions.getMetricsOptions()
    );

    final List<LabelKey> labelKeys = new ArrayList<>(metricsOptions.build().getLabelKeys());
    final List<LabelValue> labelValues = new ArrayList<>(metricsOptions.build().getLabelValues());

    labelKeys.add(endpointKey);
    labelValues.add(LabelValue.create(endpoint));

    // Make sure the pool will have at least 1 channel always connected. If maximum size > 1 then we
    // want at least 2 channels or square root of maximum channels whichever is larger.
    // Do not override if minSize is already specified as > 0.
    final GcpChannelPoolOptions.Builder poolOptions = GcpChannelPoolOptions.newBuilder(
        gcpOptions.getChannelPoolOptions()
    );
    if (poolOptions.build().getMinSize() < 1) {
      int minSize = Math.min(2, poolOptions.build().getMaxSize());
      minSize = Math.max(minSize, ((int) Math.sqrt(poolOptions.build().getMaxSize())));
      poolOptions.setMinSize(minSize);
    }

    return GcpManagedChannelOptions.newBuilder(gcpOptions)
        .withChannelPoolOptions(poolOptions.build())
        .withMetricsOptions(metricsOptions.withLabels(labelKeys, labelValues).build())
        .build();
  }

  private ManagedChannelBuilder<?> channelBuilderForEndpoint(String endpoint) {
    String serviceAddress;
    // Assume https by default.
    int port = 443;
    try {
      URL url = new URL(endpoint);
      serviceAddress = url.getHost();
      port = url.getPort() < 0 ? url.getDefaultPort() : url.getPort();
    } catch (MalformedURLException ex) {
      // When no protocol is specified, fallback to plain host:port parsing.
      int colon = endpoint.lastIndexOf(':');
      if (colon < 0) {
        serviceAddress = endpoint;
      } else {
        serviceAddress = endpoint.substring(0, colon);
        port = Integer.parseInt(endpoint.substring(colon + 1));
      }
    }
    return ManagedChannelBuilder.forAddress(serviceAddress, port);
  }

  private static class CurrentEndpointWatcher {
    private final MultiEndpoint me;
    private final String endpoint;

    public CurrentEndpointWatcher(MultiEndpoint me, String endpoint) {
      this.me = me;
      this.endpoint = endpoint;
    }

    public long getMetricValue() {
      return endpoint.equals(me.getCurrentId()) ? 1 : 0;
    }
  }

  private void setUpMetricsForMultiEndpoint(GcpMultiEndpointOptions options, MultiEndpoint me) {
    String name = options.getName();
    List<String> endpoints = options.getEndpoints();
    endpointSwitchMetric.createTimeSeries(
        Arrays.asList(
            LabelValue.create(name),
            LabelValue.create(TYPE_FALLBACK)
        ),
        me,
        MultiEndpoint::getFallbackCnt
    );
    endpointSwitchMetric.createTimeSeries(
        Arrays.asList(
            LabelValue.create(name),
            LabelValue.create(TYPE_RECOVER)
        ),
        me,
        MultiEndpoint::getRecoverCnt
    );
    endpointSwitchMetric.createTimeSeries(
        Arrays.asList(
            LabelValue.create(name),
            LabelValue.create(TYPE_REPLACE)
        ),
        me,
        MultiEndpoint::getReplaceCnt
    );
    for (String e : endpoints) {
      CurrentEndpointWatcher watcher = new CurrentEndpointWatcher(me, e);
      currentEndpointWatchers.put(name + ":" + e, watcher);
      currentEndpointMetric.createTimeSeries(
          Arrays.asList(
              LabelValue.create(name),
              LabelValue.create(e)
          ),
          watcher,
          CurrentEndpointWatcher::getMetricValue
      );
    }
  }

  private void updateMetricsForMultiEndpoint(GcpMultiEndpointOptions options, MultiEndpoint me) {
    Set<String> newEndpoints = new HashSet<>(options.getEndpoints());
    Set<String> existingEndpoints = new HashSet<>(me.getEndpoints());
    for (String e : existingEndpoints) {
      if (!newEndpoints.contains(e)) {
        currentEndpointMetric.removeTimeSeries(
            Arrays.asList(
                LabelValue.create(options.getName()),
                LabelValue.create(e)
            )
        );
        currentEndpointWatchers.remove(options.getName() + ":" + e);
      }
    }
    for (String e : newEndpoints) {
      if (!existingEndpoints.contains(e)) {
        CurrentEndpointWatcher watcher = new CurrentEndpointWatcher(me, e);
        currentEndpointWatchers.put(options.getName() + ":" + e, watcher);
        currentEndpointMetric.createTimeSeries(
            Arrays.asList(
                LabelValue.create(options.getName()),
                LabelValue.create(e)
            ),
            watcher,
            CurrentEndpointWatcher::getMetricValue
        );
      }
    }
  }

  private void removeMetricsForMultiEndpoint(String name, MultiEndpoint me) {
    endpointSwitchMetric.removeTimeSeries(
        Arrays.asList(
            LabelValue.create(name),
            LabelValue.create(TYPE_FALLBACK)
        )
    );
    endpointSwitchMetric.removeTimeSeries(
        Arrays.asList(
            LabelValue.create(name),
            LabelValue.create(TYPE_RECOVER)
        )
    );
    endpointSwitchMetric.removeTimeSeries(
        Arrays.asList(
            LabelValue.create(name),
            LabelValue.create(TYPE_REPLACE)
        )
    );
    for (String e : me.getEndpoints()) {
      currentEndpointMetric.removeTimeSeries(
          Arrays.asList(
              LabelValue.create(name),
              LabelValue.create(e)
          )
      );
      currentEndpointWatchers.remove(name + ":" + e);
    }
  }

  /**
   * Update the list of MultiEndpoint configurations.
   *
   * <p>MultiEndpoints are matched with the current ones by name.
   * <ul>
   * <li>If a current MultiEndpoint is missing in the updated list, the MultiEndpoint will be
   * removed.
   * <li>A new MultiEndpoint will be created for every new name in the list.
   * <li>For an existing MultiEndpoint only its endpoints will be updated (no recovery timeout
   * change).
   * </ul>
   *
   * <p>Endpoints are matched by the endpoint address (usually in the form of address:port).
   * <ul>
   * <li>If an existing endpoint is not used by any MultiEndpoint in the updated list, then the
   * channel poll for this endpoint will be shutdown.
   * <li>A channel pool will be created for every new endpoint.
   * <li>For an existing endpoint nothing will change (the channel pool will not be re-created, thus
   * no channel credentials change, nor channel configurator change).
   * </ul>
   */
  public synchronized void setMultiEndpoints(List<GcpMultiEndpointOptions> meOptions) {
    Preconditions.checkNotNull(meOptions);
    Preconditions.checkArgument(!meOptions.isEmpty(), "MultiEndpoints list is empty");
    Set<String> currentMultiEndpoints = new HashSet<>();

    // Must have all multiendpoints before initializing the pools so that all multiendpoints
    // can get status update of every pool.
    meOptions.forEach(options -> {
      currentMultiEndpoints.add(options.getName());
      // Create or update MultiEndpoint
      MultiEndpoint existingMe = multiEndpoints.get(options.getName());
      if (existingMe != null) {
        updateMetricsForMultiEndpoint(options, existingMe);
        existingMe.setEndpoints(options.getEndpoints());
      } else {
        MultiEndpoint me = new MultiEndpoint.Builder(options.getEndpoints())
            .withRecoveryTimeout(options.getRecoveryTimeout())
            .withSwitchingDelay(options.getSwitchingDelay())
            .build();
        setUpMetricsForMultiEndpoint(options, me);
        multiEndpoints.put(options.getName(), me);
      }
    });

    final Set<String> existingPools = new HashSet<>(pools.keySet());
    currentEndpoints.clear();
    // TODO: Support the same endpoint in different MultiEndpoint to use different channel
    //       credentials.
    // TODO: Support different endpoints in the same MultiEndpoint to use different channel
    //       credentials.
    meOptions.forEach(options -> {
      // Create missing pools
      options.getEndpoints().forEach(endpoint -> {
        currentEndpoints.add(endpoint);
        pools.computeIfAbsent(endpoint, e -> {
          ManagedChannelBuilder<?> managedChannelBuilder;
          if (options.getChannelCredentials() != null) {
            managedChannelBuilder = Grpc.newChannelBuilder(e, options.getChannelCredentials());
          } else {
            managedChannelBuilder = channelBuilderForEndpoint(e);
          }
          if (options.getChannelConfigurator() != null) {
            managedChannelBuilder = options.getChannelConfigurator().apply(managedChannelBuilder);
          }

          GcpManagedChannel channel = new GcpManagedChannel(
              managedChannelBuilder,
              apiConfig,
              // Add endpoint to metric labels.
              prepareGcpManagedChannelConfig(gcpManagedChannelOptions, e));
          // Start monitoring the pool state.
          new EndpointStateMonitor(channel, e);
          return channel;
        });
      });
    });
    existingPools.retainAll(currentEndpoints);
    existingPools.forEach(e -> {
      // Communicate current state to MultiEndpoints.
      checkPoolState(pools.get(e), e);
    });
    defaultMultiEndpoint = multiEndpoints.get(meOptions.get(0).getName());

    // Remove obsolete multiendpoints.
    Iterator<String> iter = multiEndpoints.keySet().iterator();
    while (iter.hasNext()) {
      String name = iter.next();
      if (currentMultiEndpoints.contains(name)) {
        continue;
      }
      removeMetricsForMultiEndpoint(name, multiEndpoints.get(name));
      iter.remove();
    }

    // Shutdown and remove the pools not present in options.
    final Set<String> poolsToRemove = new HashSet<>(pools.keySet());
    poolsToRemove.removeIf(currentEndpoints::contains);
    if (!poolsToRemove.isEmpty()) {
      // Get max switching delay.
      Optional<Duration> maxDelay = meOptions.stream()
          .map(GcpMultiEndpointOptions::getSwitchingDelay)
          .max(Comparator.naturalOrder());
      if (maxDelay.isPresent() && maxDelay.get().toMillis() > 0) {
        executor.schedule(
            () -> maybeCleanupPools(poolsToRemove),
            maxDelay.get().toMillis(),
            MILLISECONDS
        );
      } else {
        maybeCleanupPools(poolsToRemove);
      }
    }
  }

  private synchronized void maybeCleanupPools(Set<String> endpoints) {
    for (String endpoint : endpoints) {
      if (currentEndpoints.contains(endpoint)) {
        continue;
      }
      pools.get(endpoint).shutdown();
      pools.remove(endpoint);
    }
  }

  private void createMetrics() {
    if (gcpManagedChannelOptions.getMetricsOptions() == null) {
      return;
    }

    MetricRegistry metricRegistry = gcpManagedChannelOptions.getMetricsOptions().getMetricRegistry();
    if (metricRegistry == null) {
      return;
    }

    if (endpointStateMetric != null) {
      return;
    }

    String prefix = gcpManagedChannelOptions.getMetricsOptions().getNamePrefix();

    final List<LabelKey> endpointStateKeys = Arrays.asList(
        LabelKey.create(ENDPOINT_LABEL, ENDPOINT_LABEL_DESC),
        LabelKey.create(STATUS_LABEL, STATUS_LABEL_DESC)
    );

    endpointStateMetric =
        metricRegistry.addDerivedLongGauge(
            prefix + METRIC_ENDPOINT_STATE,
            createMetricOptions(
                "Reports 1 when endpoint is in the status.",
                endpointStateKeys,
                COUNT
            )
        );

    final List<LabelKey> endpointSwitchKeys = Arrays.asList(
        LabelKey.create(ME_NAME_LABEL, ME_NAME_LABEL_DESC),
        LabelKey.create(TYPE_LABEL, TYPE_LABEL_DESC)
    );

    endpointSwitchMetric =
        metricRegistry.addDerivedLongCumulative(
            prefix + METRIC_ENDPOINT_SWITCH,
            createMetricOptions(
                "Reports occurrences of changes of current endpoint for a multi-endpoint with " +
                    "the name, specifying change type.",
                endpointSwitchKeys,
                COUNT
            )
        );

    final List<LabelKey> currentEndpointKeys = Arrays.asList(
        LabelKey.create(ME_NAME_LABEL, ME_NAME_LABEL_DESC),
        LabelKey.create(ENDPOINT_LABEL, ENDPOINT_LABEL_DESC)
    );

    currentEndpointMetric =
        metricRegistry.addDerivedLongGauge(
            prefix + METRIC_CURRENT_ENDPOINT,
            createMetricOptions(
                "Reports 1 when an endpoint is current for multi-endpoint with the name.",
                currentEndpointKeys,
                COUNT
            )
        );
  }

  private MetricOptions createMetricOptions(
      String description, List<LabelKey> labelKeys, String unit) {
    return MetricOptions.builder()
        .setDescription(description)
        .setLabelKeys(labelKeys)
        .setUnit(unit)
        .build();
  }

  /**
   * Initiates an orderly shutdown in which preexisting calls continue but new calls are immediately
   * cancelled.
   *
   * @return this
   * @since 1.0.0
   */
  @Override
  @CanIgnoreReturnValue
  public ManagedChannel shutdown() {
    pools.values().forEach(GcpManagedChannel::shutdown);
    return this;
  }

  /**
   * Returns whether the channel is shutdown. Shutdown channels immediately cancel any new calls,
   * but may still have some calls being processed.
   *
   * @see #shutdown()
   * @see #isTerminated()
   * @since 1.0.0
   */
  @Override
  public boolean isShutdown() {
    return pools.values().stream().allMatch(GcpManagedChannel::isShutdown);
  }

  /**
   * Returns whether the channel is terminated. Terminated channels have no running calls and
   * relevant resources released (like TCP connections).
   *
   * @see #isShutdown()
   * @since 1.0.0
   */
  @Override
  public boolean isTerminated() {
    return pools.values().stream().allMatch(GcpManagedChannel::isTerminated);
  }

  /**
   * Initiates a forceful shutdown in which preexisting and new calls are cancelled. Although
   * forceful, the shutdown process is still not instantaneous; {@link #isTerminated()} will likely
   * return {@code false} immediately after this method returns.
   *
   * @return this
   * @since 1.0.0
   */
  @Override
  @CanIgnoreReturnValue
  public ManagedChannel shutdownNow() {
    pools.values().forEach(GcpManagedChannel::shutdownNow);
    return this;
  }

  /**
   * Waits for the channel to become terminated, giving up if the timeout is reached.
   *
   * @return whether the channel is terminated, as would be done by {@link #isTerminated()}.
   * @since 1.0.0
   */
  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    long endTimeNanos = System.nanoTime() + unit.toNanos(timeout);
    for (GcpManagedChannel gcpManagedChannel : pools.values()) {
      if (gcpManagedChannel.isTerminated()) {
        continue;
      }
      long awaitTimeNanos = endTimeNanos - System.nanoTime();
      if (awaitTimeNanos <= 0) {
        break;
      }
      gcpManagedChannel.awaitTermination(awaitTimeNanos, NANOSECONDS);
    }
    return isTerminated();
  }

  /**
   * Check the value of {@link #ME_KEY} key in the {@link CallOptions} and if found use
   * the MultiEndpoint with the same name for this call.
   *
   * <p>Create a {@link ClientCall} to the remote operation specified by the given {@link
   * MethodDescriptor}. The returned {@link ClientCall} does not trigger any remote behavior until
   * {@link ClientCall#start(Listener, Metadata)} is invoked.
   *
   * @param methodDescriptor describes the name and parameter types of the operation to call.
   * @param callOptions runtime options to be applied to this call.
   * @return a {@link ClientCall} bound to the specified method.
   * @since 1.0.0
   */
  @Override
  public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
      MethodDescriptor<RequestT, ResponseT> methodDescriptor, CallOptions callOptions) {
    String multiEndpointKey = callOptions.getOption(ME_KEY);
    if (multiEndpointKey == null) {
      multiEndpointKey = ME_CONTEXT_KEY.get(Context.current());
    }
    MultiEndpoint me = defaultMultiEndpoint;
    if (multiEndpointKey != null) {
      me = multiEndpoints.getOrDefault(multiEndpointKey, defaultMultiEndpoint);
    }
    return pools.get(me.getCurrentId()).newCall(methodDescriptor, callOptions);
  }

  /**
   * The authority of the current endpoint of the default MultiEndpoint. Typically, this is in the
   * format {@code host:port}.
   *
   * To get the authority of the current endpoint of another MultiEndpoint use {@link
   * #authorityFor(String)} method.
   *
   * This may return different values over time because MultiEndpoint may switch between endpoints.
   *
   * @since 1.0.0
   */
  @Override
  public String authority() {
    return pools.get(defaultMultiEndpoint.getCurrentId()).authority();
  }

  /**
   * The authority of the current endpoint of the specified MultiEndpoint. Typically, this is in the
   * format {@code host:port}.
   *
   * This may return different values over time because MultiEndpoint may switch between endpoints.
   */
  public String authorityFor(String multiEndpointName) {
    MultiEndpoint multiEndpoint = multiEndpoints.get(multiEndpointName);
    if (multiEndpoint == null) {
      return null;
    }
    return pools.get(multiEndpoint.getCurrentId()).authority();
  }
}
