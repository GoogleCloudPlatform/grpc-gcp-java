package io.grpc.spanner;

import static com.google.common.base.Preconditions.checkState;

import com.google.api.core.ApiFuture;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.api.gax.retrying.RetrySettings;
import com.google.cloud.spanner.AsyncResultSet;
import com.google.cloud.spanner.AsyncResultSet.CallbackResponse;
import com.google.cloud.spanner.AsyncResultSet.ReadyCallback;
import com.google.cloud.spanner.AsyncRunner.AsyncWork;
import com.google.cloud.spanner.Backup;
import com.google.cloud.spanner.Database;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.spanner.Instance;
import com.google.cloud.spanner.InstanceConfig;
import com.google.cloud.spanner.InstanceConfigId;
import com.google.cloud.spanner.InstanceId;
import com.google.cloud.spanner.InstanceInfo;
import com.google.cloud.spanner.Key;
import com.google.cloud.spanner.KeySet;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerException;
import com.google.cloud.spanner.SpannerExceptionFactory;
import com.google.cloud.spanner.SpannerOptions;
import com.google.cloud.spanner.Statement;
import com.google.cloud.spanner.Struct;
import com.google.cloud.spanner.TimestampBound;
import com.google.cloud.spanner.TransactionContext;
import com.google.common.collect.Iterators;
import com.google.spanner.admin.database.v1.CreateDatabaseMetadata;
import com.google.spanner.admin.instance.v1.CreateInstanceMetadata;
import io.grpc.Status;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.threeten.bp.Duration;

public class SpannerClient {
  private static final Logger logger = Logger.getLogger(SpannerClient.class.getName());
  private static Args args;
  private Spanner spClient;
  private InstanceId instanceId;
  private DatabaseClient dbClient;
  private final String TABLE_NAME = "TestTable";
  private final List<String> ALL_COLUMNS = Arrays.asList("Key", "StringValue");
  private final String READ_KEY = "ReadKey";
  private final KeySet READ_KEY_SET = KeySet.newBuilder().addKey(Key.of("ReadKey")).build();
  private boolean isOwnedInstance;
  final ExecutorService executor = Executors.newCachedThreadPool();
  /** Sequence used to generate unique keys. */
  private static int seq;

  public SpannerClient(Args args) throws IllegalArgumentException, Exception {
    this.args = args;
    SpannerOptions.Builder builder =
        SpannerOptions.newBuilder()
            .setAutoThrottleAdministrativeRequests()
            .setTrackTransactionStarter()
            .setCompressorName("gzip")
            .setHost(args.endpoint)
            .setNumChannels(args.numChannels);

    if (args.gcpProject.isEmpty()) {
      throw new IllegalArgumentException("Please specify a GCP project");
    } else {
      builder.setProjectId(args.gcpProject);
    }
    logger.log(Level.INFO, "GCP project is {0}", args.gcpProject);
    logger.log(Level.INFO, "Test endpoint is {0}", args.endpoint);
    logger.log(Level.INFO, "Number of gRPC channel is {0}", args.numChannels);

    final RetrySettings retrySettings =
        RetrySettings.newBuilder()
            .setInitialRpcTimeout(Duration.ofMillis(args.timeoutMs))
            .setMaxRpcTimeout(Duration.ofMillis(args.timeoutMs))
            .setMaxAttempts(0)
            .setTotalTimeout(Duration.ofMillis(args.timeoutMs))
            .build();

    /*
    builder
        .getSpannerStubSettingsBuilder()
        .applyToAllUnaryMethods(
            new ApiFunction<UnaryCallSettings.Builder<?, ?>, Void>() {
              @Override
              public Void apply(Builder<?, ?> input) {
                input.setRetrySettings(retrySettings);
                return null;
              }
            });
    */

    builder
        .getSpannerStubSettingsBuilder()
        .executeStreamingSqlSettings()
        .setRetryableCodes()
        .setRetrySettings(retrySettings);

    SpannerOptions spannerOptions = builder.build();
    spClient = spannerOptions.getService();

    if (!args.instanceId.isEmpty() && !args.databaseId.isEmpty()) {
      dbClient =
          spClient.getDatabaseClient(
              DatabaseId.of(args.gcpProject, args.instanceId, args.databaseId));
      logger.log(
          Level.INFO,
          "Use existing instance: projects/{0}/instances/{1}/databases/{2}",
          new Object[] {args.gcpProject, args.instanceId, args.databaseId});
    } else {
      instanceId =
          InstanceId.of(
              args.gcpProject,
              String.format("test-instance-%08d", new Random().nextInt(100000000)));
      initializeInstance(instanceId);
      setupDatabase();
    }
  }

  /** Create a database. */
  private void setupDatabase() {
    Database db =
        createDatabase(
            "CREATE TABLE TestTable ("
                + "  Key                STRING(MAX) NOT NULL,"
                + "  StringValue        STRING(MAX),"
                + ") PRIMARY KEY (Key)");
    dbClient = spClient.getDatabaseClient(db.getId());
  }

  private static String uniqueString() {
    return String.format("%08d", seq++);
  }

  public void singleRead() {
    Struct row =
        dbClient
            .singleUse(TimestampBound.strong())
            .readRow(TABLE_NAME, Key.of(READ_KEY), ALL_COLUMNS);
    // logger.log(Level.INFO, "read value = {0}", row.getString(1));
  }

  public void singleQuery() {
    ResultSet rs =
        dbClient
            .singleUse()
            .executeQuery(
                Statement.of(
                    "SELECT Key,StringValue FROM "
                        + TABLE_NAME
                        + " WHERE -123  != "
                        + uniqueString()));
    while (rs.next()) {
      // System.out.println("executeSQL: " + rs.getString(0));
      // System.out.println("executeSQL: " + rs.getString(1));
    }
  }

  public ApiFuture<Void> singleReadAsync() {
    final AsyncResultSet asyncResultSet =
        dbClient.singleUse().readAsync(TABLE_NAME, READ_KEY_SET, ALL_COLUMNS);
    final ApiFuture<Void> readFuture =
        asyncResultSet.setCallback(
            executor,
            new ReadyCallback() {
              @Override
              public CallbackResponse cursorReady(AsyncResultSet resultSet) {
                while (true) {
                  switch (resultSet.tryNext()) {
                    case OK:
                      // System.out.println(resultSet.getString("StringValue"));
                      break;
                    case NOT_READY:
                      return CallbackResponse.CONTINUE;
                    case DONE:
                      return CallbackResponse.DONE;
                    default:
                      throw new IllegalStateException();
                  }
                }
              }
            });
    return readFuture;
  }

  public ApiFuture<Void> singleQueryAsync() {
    final AsyncResultSet asyncResultSet =
        dbClient
            .singleUse()
            .executeQueryAsync(
                Statement.of(
                    "SELECT Key,StringValue FROM "
                        + TABLE_NAME
                        + " WHERE -123  != "
                        + uniqueString()));
    final ApiFuture<Void> queryFuture =
        asyncResultSet.setCallback(
            executor,
            new ReadyCallback() {
              @Override
              public CallbackResponse cursorReady(AsyncResultSet resultSet) {
                while (true) {
                  switch (resultSet.tryNext()) {
                    case OK:
                      // System.out.println(resultSet.getString("StringValue"));
                      break;
                    case NOT_READY:
                      return CallbackResponse.CONTINUE;
                    case DONE:
                      return CallbackResponse.DONE;
                    default:
                      throw new IllegalStateException();
                  }
                }
              }
            });
    return queryFuture;
  }

  public void singleWrite() {
    List<Mutation> mutations = new ArrayList<>();
    mutations.add(
        Mutation.newInsertOrUpdateBuilder(TABLE_NAME)
            .set("Key")
            .to(READ_KEY)
            // .to(uniqueString())
            .set("StringValue")
            // .to("uber test")
            .to(generatePayload(60 * 1024))
            .build());
    dbClient.write(mutations);
  }

  public void singleWriteAsync() {
    final ApiFuture<Long> writeFuture =
        dbClient
            .runAsync()
            .runAsync(
                new AsyncWork<Long>() {
                  @Override
                  public ApiFuture<Long> doWorkAsync(TransactionContext txn) {
                    // INVALID_ARGUMENT: UPDATE must have a WHERE clause
                    return txn.executeUpdateAsync(
                        Statement.of("UPDATE " + TABLE_NAME + " SET ReadKey = 'uber test'"));
                  }
                },
                executor);
    // writeFuture.get(10_000, TimeUnit.MILLISECONDS);
  }

  private String generatePayload(int numBytes) {
    StringBuilder sb = new StringBuilder(numBytes);
    for (int i = 0; i < numBytes; i++) {
      sb.append('x');
    }
    return sb.toString();
  }

  private Database createDatabase(String... stt) throws SpannerException {
    Iterable<String> statement = Arrays.asList(stt);
    String dbId = String.format("db-id-%08d", new Random().nextInt(100000000));
    try {
      OperationFuture<Database, CreateDatabaseMetadata> op =
          spClient
              .getDatabaseAdminClient()
              .createDatabase(instanceId.getInstance(), dbId, statement);
      Database db = op.get();
      logger.log(Level.INFO, "Created test database {0}", db.getId());
      return db;
    } catch (Exception e) {
      throw SpannerExceptionFactory.newSpannerException(e);
    }
  }

  private void initializeInstance(InstanceId instanceId) {
    InstanceConfig instanceConfig =
        Iterators.get(
            spClient.getInstanceAdminClient().listInstanceConfigs().iterateAll().iterator(),
            32, // us-east1
            null);
    checkState(instanceConfig != null, "No instance configs found");

    InstanceConfigId configId = instanceConfig.getId();
    logger.log(Level.INFO, "Creating instance using config {0}", configId);
    InstanceInfo instance =
        InstanceInfo.newBuilder(instanceId)
            .setNodeCount(1)
            .setDisplayName("Test instance")
            .setInstanceConfigId(configId)
            .build();
    OperationFuture<Instance, CreateInstanceMetadata> op =
        spClient.getInstanceAdminClient().createInstance(instance);
    Instance createdInstance;
    try {
      createdInstance = op.get();
    } catch (Exception e) {
      boolean cancelled = false;
      try {
        // Try to cancel the createInstance operation.
        spClient.getInstanceAdminClient().cancelOperation(op.getName());
        com.google.longrunning.Operation createOperation =
            spClient.getInstanceAdminClient().getOperation(op.getName());
        cancelled =
            createOperation.hasError()
                && createOperation.getError().getCode() == Status.CANCELLED.getCode().value();
        if (cancelled) {
          logger.info("Cancelled the createInstance operation because the operation failed");
        } else {
          logger.info(
              "Tried to cancel the createInstance operation because the operation failed, but the"
                  + " operation could not be cancelled. Current status: "
                  + createOperation.getError().getCode());
        }
      } catch (Throwable t) {
        logger.log(Level.WARNING, "Failed to cancel the createInstance operation", t);
      }
      if (!cancelled) {
        try {
          spClient.getInstanceAdminClient().deleteInstance(instanceId.getInstance());
          logger.info(
              "Deleted the test instance because the createInstance operation failed and"
                  + " cancelling the operation did not succeed");
        } catch (Throwable t) {
          logger.log(Level.WARNING, "Failed to delete the test instance", t);
        }
      }
      throw SpannerExceptionFactory.newSpannerException(e);
    }
    logger.log(Level.INFO, "Created test instance: {0}", createdInstance.getId());
  }

  /** Delete instance and close spanner client, and database will be implicitly dropped. */
  public void cleanUp() {
    if (isOwnedInstance) {
      try {
        logger.log(Level.INFO, "Deleting backups on test instance {0}", instanceId);
        for (Backup backup :
            spClient.getDatabaseAdminClient().listBackups(instanceId.getInstance()).iterateAll()) {
          logger.log(Level.INFO, "Deleting backup {0}", backup.getId());
          backup.delete();
        }
        logger.log(Level.INFO, "Deleting test instance {0}", instanceId);
        spClient.getInstanceAdminClient().deleteInstance(instanceId.getInstance());
      } catch (SpannerException e) {
        logger.log(Level.SEVERE, "Failed to delete test instance " + instanceId, e);
      }
    }
    spClient.close();
    executor.shutdown();
  }
}
