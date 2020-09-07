package com.google.cloud;
  
// Java 
import java.io.*;
import java.util.List;
// BigtableDataClient
import com.google.cloud.bigtable.data.v2.BigtableDataClient;
import com.google.cloud.bigtable.data.v2.models.*;
import com.google.cloud.bigtable.data.v2.BigtableDataSettings;
// BigtableTableAdminClient
import static com.google.cloud.bigtable.admin.v2.models.GCRules.GCRULES;
import com.google.cloud.bigtable.admin.v2.BigtableTableAdminClient;
import com.google.cloud.bigtable.admin.v2.models.*;
// Peer IP
import java.net.*;
import io.grpc.*;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.api.gax.grpc.InstantiatingGrpcChannelProvider;
import com.google.api.core.ApiFunction;

public class Client {
	private static final String PROJECT_ID = "directpath-prod-manual-testing";
  private static final String INSTANCE_ID = "blackbox-us-central1-b";
  private static final String CFE_DATA_ENDPOINT = "test-bigtable.sandbox.googleapis.com:443";
  private static final String CFE_ADMIN_ENDPOINT = "test-bigtableadmin.sandbox.googleapis.com:443";
  private static final String DP_DATA_ENDPOINT = "testdirectpath-bigtable.sandbox.googleapis.com:443";
  private static final String DP_ADMIN_ENDPOINT = "test-bigtableadmin.sandbox.googleapis.com:443";
  private static final String DP_IPV6_PREFIX = "2001:4860:8040";
  private static final String DP_IPV4_PREFIX = "34.126";

	// main function
  public static void main( String[] args ) throws IOException {
    boolean isDpEnabled = Boolean.getBoolean("bigtable.attempt-directpath");
    if (isDpEnabled) {
      System.out.println("Access bigtable through directpath.");
      //BigtableDataClient dataClient = BigtableDataClient.create(PROJECT_ID, INSTANCE_ID);
      BigtableDataClient dataClient = Util.peerIpDataClient(PROJECT_ID, INSTANCE_ID, DP_DATA_ENDPOINT);
      testDp(dataClient);
      dataClient.close();
    } else {
      System.out.println("Access bigtable through cfe.");
      BigtableDataClient dataClient = Util.peerIpDataClient(PROJECT_ID, INSTANCE_ID, CFE_DATA_ENDPOINT);
      testCfe(dataClient);
      dataClient.close();
    }
  }

	// Access bigtable through dp: read data
  public static void testDp(BigtableDataClient dataClient) throws IOException {
    String tableId = "it-table";
    Query query = Query.create(tableId);
    for (Row row : dataClient.readRows(query)) {
      System.out.println(row.getKey());
    }
  }

  // Access bigtable instance through cfe: Create table, add column family, add data, read data, delete columns, delete table
  public static void testCfe(BigtableDataClient dataClient) throws IOException {
    String tableId = "directpath-test-table";
    String familyId = "test-family-1";
    String columnId = "test-column-1";
    String rowKey = "com.google.www#20200903";
    String cellValue = "Hello Bigtable!";
    // Create table
    BigtableTableAdminClient tableAdminClient = BigtableTableAdminClient.create(PROJECT_ID, INSTANCE_ID);
    if (!tableAdminClient.exists(tableId)) {
      tableAdminClient.createTable(CreateTableRequest.of(tableId));
    }
    // Create columnFamily
    boolean isFamilyExist = false;
    for (ColumnFamily colFamily : tableAdminClient.getTable(tableId).getColumnFamilies()) {
      if (colFamily.getId().equals(familyId)) {
        isFamilyExist = true;
      }
    }
    if (!isFamilyExist) {
      ModifyColumnFamiliesRequest familyRequest = ModifyColumnFamiliesRequest.of(tableId)
                                                    .addFamily(familyId);
      tableAdminClient.modifyFamilies(familyRequest);
    }
    // Write a single cell
    RowMutation mutation = RowMutation.create(tableId, rowKey)
                                      .setCell(familyId, columnId, cellValue);
    dataClient.mutateRow(mutation);
    // Query a single cell - one cell has multiple timestamp
    // query method 1
    Row row = dataClient.readRow(tableId, rowKey);
    for (RowCell rowCell : row.getCells(familyId, columnId)) {
      System.out.println(rowCell.getValue());
    }
    // query method 2
    Query query = Query.create(tableId);
    for (Row row_ : dataClient.readRows(query)) {
      for (RowCell rowCell : row_.getCells(familyId, columnId)) {
        System.out.println(rowCell.getValue());
      }
    }
    // cleanup
    tableAdminClient.dropAllRows(tableId);
    tableAdminClient.deleteTable(tableId);
    tableAdminClient.close();
  }
}
