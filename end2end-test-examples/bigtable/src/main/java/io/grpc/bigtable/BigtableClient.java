package io.grpc.bigtable;

import static io.grpc.bigtable.Args.PROJECT_ID;
import static io.grpc.bigtable.Args.INSTANCE_ID;
import static io.grpc.bigtable.Args.CFE_DATA_ENDPOINT;
import static io.grpc.bigtable.Args.DP_DATA_ENDPOINT;
import static io.grpc.bigtable.Args.CFE_ADMIN_ENDPOINT;
import static io.grpc.bigtable.Args.DP_ADMIN_ENDPOINT;
import static io.grpc.bigtable.Args.DP_IPV6_PREFIX;
import static io.grpc.bigtable.Args.DP_IPV4_PREFIX;

import java.io.IOException;
import java.lang.NullPointerException;
import java.util.logging.Logger;
import java.util.List;
import com.google.cloud.bigtable.data.v2.BigtableDataClient;
import com.google.cloud.bigtable.data.v2.BigtableDataSettings;
import com.google.cloud.bigtable.data.v2.models.Query;
import com.google.cloud.bigtable.data.v2.models.Row;
import com.google.cloud.bigtable.data.v2.models.RowMutation;
import com.google.cloud.bigtable.data.v2.models.RowCell;
import com.google.cloud.bigtable.admin.v2.BigtableTableAdminClient;
import com.google.cloud.bigtable.admin.v2.BigtableTableAdminSettings;
import com.google.cloud.bigtable.admin.v2.models.CreateTableRequest;
import com.google.cloud.bigtable.admin.v2.models.ColumnFamily;
import com.google.cloud.bigtable.admin.v2.models.ModifyColumnFamiliesRequest;
import com.google.cloud.bigtable.admin.v2.models.GCRules;
import com.google.cloud.bigtable.data.v2.models.Filters;
import org.HdrHistogram.Histogram;

public class BigtableClient {
	private static final Logger logger = Logger.getLogger(BigtableClient.class.getName());
	private final Args args;
	private final BigtableDataClient dataClient;
	private final BigtableTableAdminClient tableAdminClient;
	private final String tableId = "it-table";
	private final String rowKey = "bigtable-test-data-row-1";
	private final String familyId = "cf";
	private final String qualifierId;

	public BigtableClient(Args args) throws IOException {
		this.args = args;

		//this.tableAdminClient = customEndpointTableAdminClient();
		//this.dataClient = customEndpointDataClient();

		this.tableAdminClient = BigtableTableAdminClient.create(PROJECT_ID, INSTANCE_ID);
		this.dataClient = BigtableDataClient.create(PROJECT_ID, INSTANCE_ID);
		//this.dataClient = Util.peerIpDataClient(PROJECT_ID, INSTANCE_ID);

		this.qualifierId = "col_" + String.valueOf(args.reqSize) + "KB"; 
	}

	/**
	 * Setup test: check if table exists, check column family exists, prepare data for read test
	 */
	public void Setup() {
		// Check table
		if (tableAdminClient.exists(tableId)) {
			logger.info(String.format("Check table: %s exists", tableId));
		} else {
			logger.info(String.format("Create table: %s", tableId));
			tableAdminClient.createTable(CreateTableRequest.of(tableId));
		}
		// check column family
		List<ColumnFamily> columnFamilies = tableAdminClient.getTable(tableId).getColumnFamilies();
		boolean hasColumnFamily = false;
		for (ColumnFamily cf : columnFamilies) {
			if (familyId.equals(cf.getId())) {
				hasColumnFamily = true;
			}
		}
		if (hasColumnFamily) {
			logger.info(String.format("Check column family: %s exists", familyId));
		} else {
			tableAdminClient.modifyFamilies(
					ModifyColumnFamiliesRequest.of(tableId)
																		 .addFamily(familyId));
			logger.info(String.format("Create column family: %s", familyId));
		}
		// Check cell exists for read
		if (args.method.equals("read")) {
			checkReadReady();
		}
	}

	/**
	 * Test read query for one time based on Args and store latency in histogram
	 */
	public long testRead(Histogram histogram) {
		Filters.Filter filter = Filters.FILTERS.chain()
                                   .filter(Filters.FILTERS.family().exactMatch(familyId))
                                   .filter(Filters.FILTERS.qualifier().exactMatch(qualifierId));
		long start = System.currentTimeMillis();
		List<RowCell> rowCells = dataClient.readRow(tableId, rowKey, filter).getCells();
		String cellValue = rowCells.get(0).getValue().toStringUtf8();
		long latency = System.currentTimeMillis() - start;
		histogram.recordValue(latency);
		return cellValue.length();
	}
	
	/**
	 * Test write query for one time based on Args and store latency in histogram
	 */
	public long testWrite(Histogram histogram) {
		String cellValue = generatePayload(args.reqSize * 1024);
		// Set timestamp to 0 so that only the most recent data is kept in the cell
		RowMutation mutation = RowMutation.create(tableId, rowKey)
																			.setCell(familyId, qualifierId, /*timestamp = */ 0, cellValue);
		long start = System.currentTimeMillis();
		dataClient.mutateRow(mutation);
		long latency = System.currentTimeMillis() - start;
		histogram.recordValue(latency);
		return cellValue.length();
	}

	/**
	 * Shutdown bigtable client
	 */
	public void shutdown() throws NullPointerException {
		dataClient.close();
		tableAdminClient.close();
	}


	private String generatePayload(int numBytes) {
    StringBuilder sb = new StringBuilder(numBytes);
    for (int i = 0; i < numBytes; i++) {

      sb.append('x');
    }
    return sb.toString();
  }

	private BigtableDataClient customEndpointDataClient() throws IOException {
		String dataEndPoint;
		if (this.args.dp) {
			dataEndPoint = DP_DATA_ENDPOINT;
		} else {
			dataEndPoint = CFE_DATA_ENDPOINT;
		}
		// System.out.println("dataEndPoint = " + dataEndPoint);
		// System.out.println("adminEndPoint = " + adminEndPoint);
		BigtableDataSettings.Builder settingsBuilder = BigtableDataSettings.newBuilder()
																									   .setProjectId(PROJECT_ID)
																										 .setInstanceId(INSTANCE_ID);
		settingsBuilder.stubSettings()
									 .setEndpoint(dataEndPoint).build();
		return BigtableDataClient.create(settingsBuilder.build());
	} 

	private BigtableTableAdminClient customEndpointTableAdminClient() throws IOException {
		String adminEndPoint;
		if (this.args.dp) {
			adminEndPoint = DP_ADMIN_ENDPOINT;
		} else {
			adminEndPoint = CFE_ADMIN_ENDPOINT;
		}
		BigtableTableAdminSettings.Builder settingsBuilder = BigtableTableAdminSettings.newBuilder()
																												   .setProjectId(PROJECT_ID)
																													 .setInstanceId(INSTANCE_ID);
		settingsBuilder.stubSettings()
									.setEndpoint(adminEndPoint).build();
		return BigtableTableAdminClient.create(settingsBuilder.build());
	}

	private boolean prepareRead() {
		Filters.Filter filter = Filters.FILTERS.chain()
																	 .filter(Filters.FILTERS.family().exactMatch(familyId))
																	 .filter(Filters.FILTERS.qualifier().exactMatch(qualifierId));
		Row row = dataClient.readRow(tableId, rowKey, filter);
		if (row == null) {
			logger.info("No such a cell");
			return false;
		}
		List<RowCell> rowCells = dataClient.readRow(tableId, rowKey, filter).getCells();
		if (rowCells.size() == 0) {
			logger.info("Cell is empty.");
			return false;
		} else if (rowCells.size() == 1) {
			String dummyPayload = generatePayload(args.reqSize * 1024);
			String cellValue = rowCells.get(0).getValue().toStringUtf8();
			// logger.info(String.format("dummyPayload = %s", dummyPayload));
			// logger.info(String.format("cellValue = %s", cellValue));
			return cellValue.equals(dummyPayload);
		} else {
			RowMutation mutation = RowMutation.create(tableId, rowKey)
																				.deleteCells(familyId, qualifierId);
			dataClient.mutateRow(mutation);
			logger.warning(String.format("Found %d versions of cell values. Reset the cell.", rowCells.size()));
			return false;
		}
	}

	private void checkReadReady() {
		if (!prepareRead()) {
			String cellValue = generatePayload(args.reqSize * 1024);
			RowMutation mutation = RowMutation.create(tableId, rowKey)
                                      .setCell(familyId, qualifierId, /*timestamp = */ 0, cellValue);
			dataClient.mutateRow(mutation);
		}
		assert prepareRead() == true : "No data ready for read test";
	}

}



