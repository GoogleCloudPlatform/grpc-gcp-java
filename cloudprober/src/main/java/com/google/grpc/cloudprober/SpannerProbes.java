package com.google.grpc.cloudprober;

import com.google.spanner.v1.BeginTransactionRequest;
import com.google.spanner.v1.CommitRequest;
import com.google.spanner.v1.CreateSessionRequest;
import com.google.spanner.v1.DeleteSessionRequest;
import com.google.spanner.v1.ExecuteSqlRequest;
import com.google.spanner.v1.GetSessionRequest;
import com.google.spanner.v1.KeySet;
import com.google.spanner.v1.ListSessionsRequest;
import com.google.spanner.v1.ListSessionsResponse;
import com.google.spanner.v1.PartialResultSet;
import com.google.spanner.v1.PartitionQueryRequest;
import com.google.spanner.v1.PartitionReadRequest;
import com.google.spanner.v1.ReadRequest;
import com.google.spanner.v1.ResultSet;
import com.google.spanner.v1.RollbackRequest;
import com.google.spanner.v1.Session;
import com.google.spanner.v1.SpannerGrpc;
import com.google.spanner.v1.Transaction;
import com.google.spanner.v1.TransactionOptions;
import com.google.spanner.v1.TransactionSelector;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;



public class SpannerProbes {

  public static final class ProberException extends Exception {
    ProberException(String s) {
      super(s);
    }
  }


  private static final String DATABASE =
      "projects/cloudprober-test/instances/test-instance/databases/test-db";
  private static final String CLOUD_API_NAME = "Spanner";
  private static final String TEST_USERNAME = "test_username";

  private SpannerProbes() {}

  private static void deleteSession(SpannerGrpc.SpannerBlockingStub stub, Session session) {
    if (session != null) {
      stub.deleteSession(DeleteSessionRequest.newBuilder().setName(session.getName()).build());
    }
  }


  /**
   * Probes to test session related grpc call from Spanner stub.
   *
   * Includes tests against CreateSession, GetSession, ListSessions, and DeleteSession of Spanner
   * stub.
   */
  public static void sessionManagementProber(SpannerGrpc.SpannerBlockingStub stub, Map<String, Long> metrics) {

    Session session = null;
    Long start;

    try {
      start = System.currentTimeMillis();
      session = stub.createSession(CreateSessionRequest.newBuilder().setDatabase(DATABASE).build());
      metrics.put("create_session_latency_ms", (System.currentTimeMillis() - start));

      // Get session.
      start = System.currentTimeMillis();
      Session responseGet =
          stub.getSession(GetSessionRequest.newBuilder().setName(session.getName()).build());
      metrics.put("get_session_latency_ms", (System.currentTimeMillis() - start));

      if (!session.getName().equals(responseGet.getName())) {
        throw new ProberException(
            String.format(
                "Incorrect session name %s, should be %s.",
                responseGet.getName(), session.getName()));
      }

      // List sessions.
      start = System.currentTimeMillis();
      ListSessionsResponse responseList =
          stub.listSessions(ListSessionsRequest.newBuilder().setDatabase(DATABASE).build());
      metrics.put("list_session_latency_ms", (System.currentTimeMillis() - start));

      int inList = 0;
      for (Session s : responseList.getSessionsList()) {
        if (s.getName().equals(session.getName())) {
          inList = 1;
          break;
        }
      }
      if (inList == 0) {
        throw new ProberException(
            String.format(
                "Incorrect session name %s, should be %s.",
                responseGet.getName(), session.getName()));
      }
    } catch (ProberException e) {
      System.out.println(e.getMessage());
    }

    start = System.currentTimeMillis();
    deleteSession(stub, session);
    metrics.put("delete_session_latency_ms", (System.currentTimeMillis() - start));

  }


  /** Probes to test ExecuteSql and ExecuteStreamingSql call from Spanner stub. */
  public static void executeSqlProber(SpannerGrpc.SpannerBlockingStub stub, Map<String, Long> metrics) {
    Session session = null;
    try {
      Long start;
      session = stub.createSession(CreateSessionRequest.newBuilder().setDatabase(DATABASE).build());

      // Probing executeSql call.
      start = System.currentTimeMillis();
      ResultSet response =
          stub.executeSql(
              ExecuteSqlRequest.newBuilder()
                  .setSession(session.getName())
                  .setSql("select * FROM jenny")
                  .build());
      metrics.put("execute_sql_latency_ms", (System.currentTimeMillis() - start));

      if (response == null) {
        throw new ProberException("Response is null when executing SQL. ");
      }
      if (response.getRowsCount() != 1) {
        throw new ProberException(
            String.format("The number of Responses '%d' is not correct.", response.getRowsCount()));
      }
      if (!response.getRows(0).getValuesList().get(0).getStringValue().equals(TEST_USERNAME)) {
        throw new ProberException(
            "Response value is not correct when executing SQL.");
      }

      // Probing streaming executeSql call.
      start = System.currentTimeMillis();
      Iterator<PartialResultSet> responsePartial =
          stub.executeStreamingSql(
              ExecuteSqlRequest.newBuilder()
                  .setSession(session.getName())
                  .setSql("select * FROM jenny")
                  .build());
      metrics.put("execute_streaming_sql_latency_ms", (System.currentTimeMillis() - start));

      if (responsePartial == null) {
        throw new ProberException("Response is null when executing streaming SQL. ");
      }
      if (!responsePartial.next().getValues(0).getStringValue().equals(TEST_USERNAME)) {
        throw new ProberException("Response value is not correct when executing streaming SQL. ");
      }

    } catch (ProberException e) {
      System.out.println(e.getMessage());
    }

    deleteSession(stub, session);
  }


  /** Probe to test Read and StreamingRead grpc call from Spanner stub. */
  public static void readProber(SpannerGrpc.SpannerBlockingStub stub, Map<String, Long> metrics) {
    Session session = null;
    try {
      Long start;
      session = stub.createSession(CreateSessionRequest.newBuilder().setDatabase(DATABASE).build());
      KeySet keySet = KeySet.newBuilder().setAll(true).build();

      // Probing read call.
      start = System.currentTimeMillis();
      ResultSet response =
          stub.read(
              ReadRequest.newBuilder()
                  .setSession(session.getName())
                  .setTable("jenny")
                  .setKeySet(keySet)
                  .addColumns("users")
                  .addColumns("firstname")
                  .addColumns("lastname")
                  .build());
      metrics.put("read_latency_ms", (System.currentTimeMillis() - start));

      if (response == null) {
        throw new ProberException("Response is null when executing SQL. ");
      }
      if (response.getRowsCount() != 1) {
        throw new ProberException(
            String.format("The number of Responses '%d' is not correct.", response.getRowsCount()));
      }
      if (!response.getRows(0).getValuesList().get(0).getStringValue().equals(TEST_USERNAME)) {
        throw new ProberException(
            "Response value is not correct when executing Reader.");
      }

      // Probing streamingRead call.
      start = System.currentTimeMillis();
      Iterator<PartialResultSet> responsePartial =
          stub.streamingRead(
              ReadRequest.newBuilder()
                  .setSession(session.getName())
                  .setTable("jenny")
                  .setKeySet(keySet)
                  .addColumns("users")
                  .addColumns("firstname")
                  .addColumns("lastname")
                  .build());
      metrics.put("streaming_read_latency_ms", (System.currentTimeMillis() - start));

      if (responsePartial == null) {
        throw new ProberException("Response is null when executing streaming SQL. ");
      }
      if (!responsePartial.next().getValues(0).getStringValue().equals(TEST_USERNAME)) {
        throw new ProberException("Response value is not correct when executing streaming Reader. ");
      }

    } catch (ProberException e) {
      System.out.println(e.getMessage());
    }

    deleteSession(stub, session);
  }


  /** Probe to test BeginTransaction, Commit and Rollback grpc from Spanner stub. */
  public static void transactionProber(SpannerGrpc.SpannerBlockingStub stub, Map<String, Long> metrics) {
    Long start;
    Session session =
        stub.createSession(CreateSessionRequest.newBuilder().setDatabase(DATABASE).build());

    // Probing begin transaction call.
    TransactionOptions options =
        TransactionOptions.newBuilder()
            .setReadWrite(TransactionOptions.ReadWrite.getDefaultInstance())
            .build();
    BeginTransactionRequest request =
        BeginTransactionRequest.newBuilder()
            .setSession(session.getName())
            .setOptions(options)
            .build();
    start = System.currentTimeMillis();
    Transaction txn = stub.beginTransaction(request);
    metrics.put("begin_transaction__latency_ms", (System.currentTimeMillis() - start));

    // Probing commit call.
    start = System.currentTimeMillis();
    stub.commit(
        CommitRequest.newBuilder()
            .setSession(session.getName())
            .setTransactionId(txn.getId())
            .build());
    metrics.put("commit__latency_ms", (System.currentTimeMillis() - start));

    // Probing rollback call.
    txn = stub.beginTransaction(request);
    start = System.currentTimeMillis();
    stub.rollback(
        RollbackRequest.newBuilder()
            .setSession(session.getName())
            .setTransactionId(txn.getId())
            .build());
    metrics.put("rollback__latency_ms", (System.currentTimeMillis() - start));

    deleteSession(stub, session);
  }


  /** Probe to test PartitionQuery and PartitionRead grpc call from Spanner stub. */
  public static void partitionProber(SpannerGrpc.SpannerBlockingStub stub, Map<String, Long> metrics) {
    Long start;
    Session session =
        stub.createSession(CreateSessionRequest.newBuilder().setDatabase(DATABASE).build());

    // Probing partition query call.
    TransactionOptions options =
        TransactionOptions.newBuilder()
            .setReadOnly(TransactionOptions.ReadOnly.getDefaultInstance())
            .build();
    TransactionSelector selector = TransactionSelector.newBuilder().setBegin(options).build();
    start = System.currentTimeMillis();
    stub.partitionQuery(
        PartitionQueryRequest.newBuilder()
            .setSession(session.getName())
            .setSql("select * FROM jenny")
            .setTransaction(selector)
            .build());
    metrics.put("partition_query__latency_ms", (System.currentTimeMillis() - start));

    // Probing partition read call.
    start = System.currentTimeMillis();
    stub.partitionRead(
        PartitionReadRequest.newBuilder()
            .setSession(session.getName())
            .setTable("jenny")
            .setTransaction(selector)
            .setKeySet(KeySet.newBuilder().setAll(true).build())
            .addColumns("users")
            .addColumns("firstname")
            .addColumns("lastname")
            .build());
    metrics.put("partition_read__latency_ms", (System.currentTimeMillis() - start));

    deleteSession(stub, session);
  }
}
