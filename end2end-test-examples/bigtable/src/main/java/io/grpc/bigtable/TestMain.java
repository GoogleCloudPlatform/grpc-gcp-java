package io.grpc.bigtable;

import java.io.IOException;
import java.util.logging.Logger;
import org.HdrHistogram.Histogram;

/**
 * BigTable stress test client
 */
public class TestMain {
  private static final Logger logger = Logger.getLogger(TestMain.class.getName());

	public static void printResult(Args argsObj, long totalLatencyMS, long totalPayloadKB, Histogram histogram) {
		System.out.println(String.format(
					"TEST over %s DONE."
					+ "\n=================================="
					+ "\n%d total %s requests sent. Payload per request = %dKB."
					+ "\nTotal payload = %dKB. Total latency = %ds. Per sec payload = %.2fKB." 
					+ "\n\t\tMin"
					+ "\tp50"
					+ "\tp90"
					+ "\tp99"
					+ "\tp99.9"
					+ "\tMAX"
					+ "\n  Time(ms)\t%d\t%d\t%d\t%d\t%d\t%d",
					argsObj.dp ? "DirectPath" : "CFE",
					histogram.getTotalCount(), argsObj.method, argsObj.reqSize, 
					totalPayloadKB, totalLatencyMS, totalPayloadKB / (totalLatencyMS / 1000.),
					histogram.getMinValue(),
          histogram.getValueAtPercentile(50),
          histogram.getValueAtPercentile(90),
          histogram.getValueAtPercentile(99),
          histogram.getValueAtPercentile(99.9),
          histogram.getValueAtPercentile(100)
		));
	}
	
  public static void main(String[] args) throws Exception {
		Args argsObj = new Args(args);
		if (argsObj.dp) {
			logger.info("Begin test over DirectPath");
		} else {
			logger.info("Begin test over CFE");
		}
		BigtableClient bigtableClient = new BigtableClient(argsObj);
		bigtableClient.Setup();
		Histogram histogram = new Histogram(/*highestTrackavleValue = */ 60000000L,
																				/*decimal resolution*/ 1);
		long totalStart = System.currentTimeMillis();
		long totalPayloadKB = 0;
		for (int i = 0; i < argsObj.numCall; ++i) {
			if (argsObj.method.equals("read")) {
				totalPayloadKB += bigtableClient.testRead(histogram);
			}	else if (argsObj.method.equals("write")) {
				totalPayloadKB += bigtableClient.testWrite(histogram);
			} else {
				logger.warning(String.format("--method must be read or write, input is %s.", argsObj.method));
			}
		}
		long totalLatencyMS = System.currentTimeMillis() - totalStart;
		printResult(argsObj, totalLatencyMS, totalPayloadKB, histogram);
		bigtableClient.shutdown();

		// move this part to BigtableClient
		/*
    if (arg.dp) {
      //BigtableDataClient dataClient = BigtableDataClient.create(PROJECT_ID, INSTANCE_ID);
      BigtableDataClient dataClient = Util.peerIpDataClient(PROJECT_ID, INSTANCE_ID, DP_DATA_ENDPOINT);
      testDp(dataClient);
      dataClient.close();
    } else {
      //BigtableDataClient dataClient = BigtableDataClient.create(PROJECT_ID, INSTANCE_ID);
      BigtableDataClient dataClient = Util.peerIpDataClient(PROJECT_ID, INSTANCE_ID, CFE_DATA_ENDPOINT);
      testCfe(dataClient);
      dataClient.close();
    }
		*/
  }
}
