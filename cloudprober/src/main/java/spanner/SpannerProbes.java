package spanner.probes;

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

class ProberException extends Exception {
  public ProberException(String s) {
    super(s);
  }
}

public class SpannerProbes {
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
   * <p>Includes tests against CreateSession, GetSession, ListSessions, and DeleteSession of Spanner
   * stub.
   */
  public static void sessionManagementProber(SpannerGrpc.SpannerBlockingStub stub) {

    Session session = null;

    try {
      session = stub.createSession(CreateSessionRequest.newBuilder().setDatabase(DATABASE).build());

      // Get session.
      Session responseGet =
          stub.getSession(GetSessionRequest.newBuilder().setName(session.getName()).build());
      if (!session.getName().equals(responseGet.getName())) {
        throw new ProberException(
            String.format(
                "Incorrect session name %s, should be %s.",
                responseGet.getName(), session.getName()));
      }

      // List sessions.
      ListSessionsResponse responseList =
          stub.listSessions(ListSessionsRequest.newBuilder().setDatabase(DATABASE).build());
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

    deleteSession(stub, session);
  }

  public static void executeSqlProber(SpannerGrpc.SpannerBlockingStub stub) {
    Session session = null;
    try {
      session = stub.createSession(CreateSessionRequest.newBuilder().setDatabase(DATABASE).build());

      // Probing executeSql call.
      ResultSet response =
          stub.executeSql(
              ExecuteSqlRequest.newBuilder()
                  .setSession(session.getName())
                  .setSql("select * FROM jenny")
                  .build());
      if (response == null) {
        throw new ProberException("Response is null when executing SQL. ");
      }
      if (response.getRowsCount() != 1) {
        throw new ProberException(
            String.format("The number of Responses '%d' is not correct.", response.getRowsCount()));
      }
      if (!response.getRows(0).getValuesList().get(0).getStringValue().equals(TEST_USERNAME)) {
        throw new ProberException(
            "Response value is not correct when executing SQL 'select * from users'. ");
      }

      // Probing streaming executeSql call.
      Iterator<PartialResultSet> responsePartial =
          stub.executeStreamingSql(
              ExecuteSqlRequest.newBuilder()
                  .setSession(session.getName())
                  .setSql("select * FROM jenny")
                  .build());
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

  public static void readProber(SpannerGrpc.SpannerBlockingStub stub) {
    Session session = null;
    try {
      session = stub.createSession(CreateSessionRequest.newBuilder().setDatabase(DATABASE).build());
      KeySet keySet = KeySet.newBuilder().setAll(true).build();

      // Probing read call.
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
      if (response == null) {
        throw new ProberException("Response is null when executing SQL. ");
      }
      if (response.getRowsCount() != 1) {
        throw new ProberException(
            String.format("The number of Responses '%d' is not correct.", response.getRowsCount()));
      }
      if (!response.getRows(0).getValuesList().get(0).getStringValue().equals(TEST_USERNAME)) {
        throw new ProberException(
            "Response value is not correct when executing SQL 'select * from users'. ");
      }

      // Probing streamingRead call.
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

  public static void transactionProber(SpannerGrpc.SpannerBlockingStub stub) {
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
    Transaction txn = stub.beginTransaction(request);

    // Probing commit call.
    stub.commit(
        CommitRequest.newBuilder()
            .setSession(session.getName())
            .setTransactionId(txn.getId())
            .build());

    // Probing rollback call.
    txn = stub.beginTransaction(request);
    stub.rollback(
        RollbackRequest.newBuilder()
            .setSession(session.getName())
            .setTransactionId(txn.getId())
            .build());

    deleteSession(stub, session);
  }

  public static void partitionProber(SpannerGrpc.SpannerBlockingStub stub) {
    Session session =
        stub.createSession(CreateSessionRequest.newBuilder().setDatabase(DATABASE).build());

    // Probing partition query call.
    TransactionOptions options =
        TransactionOptions.newBuilder()
            .setReadOnly(TransactionOptions.ReadOnly.getDefaultInstance())
            .build();
    TransactionSelector selector = TransactionSelector.newBuilder().setBegin(options).build();
    stub.partitionQuery(
        PartitionQueryRequest.newBuilder()
            .setSession(session.getName())
            .setSql("select * FROM jenny")
            .setTransaction(selector)
            .build());

    // Probing partition read call.
    KeySet keySet = KeySet.newBuilder().setAll(true).build();
    stub.partitionRead(
        PartitionReadRequest.newBuilder()
            .setSession(session.getName())
            .setTable("jenny")
            .setTransaction(selector)
            .setKeySet(keySet)
            .addColumns("users")
            .addColumns("firstname")
            .addColumns("lastname")
            .build());

    deleteSession(stub, session);
  }
}
