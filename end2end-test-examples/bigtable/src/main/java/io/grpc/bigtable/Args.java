package io.grpc.bigtable;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class Args {
  static final String PROJECT_ID = "directpath-prod-manual-testing";
  static final String INSTANCE_ID = "blackbox-us-central1-b";
  static final String CFE_DATA_ENDPOINT = "test-bigtable.sandbox.googleapis.com:443";
  static final String CFE_ADMIN_ENDPOINT = "test-bigtableadmin.sandbox.googleapis.com:443";
  static final String DP_DATA_ENDPOINT = "testdirectpath-bigtable.sandbox.googleapis.com:443";
  static final String DP_ADMIN_ENDPOINT = "test-bigtableadmin.sandbox.googleapis.com:443";
  static final String DP_IPV6_PREFIX = "2001:4860:8040";
  static final String DP_IPV4_PREFIX = "34.126";

	final String method;
	final int numClient;
	final int reqSize;
	final int numCall;
	final Boolean dp = Boolean.getBoolean("bigtable.attempt-directpath");

	Args(String[] args) throws ArgumentParserException {
		ArgumentParser parser =
        ArgumentParsers.newFor("BigTable client test")
            .build()
            .defaultHelp(true)
            .description("BigTable client java binary");

		parser.addArgument("--method").type(String.class).setDefault("read");
		parser.addArgument("--numClient").type(Integer.class).setDefault(1);
		parser.addArgument("--reqSize").type(Integer.class).setDefault(1);
		parser.addArgument("--numCall").type(Integer.class).setDefault(1);

		Namespace ns = parser.parseArgs(args);

		// Read args
		method = ns.getString("method");
		numClient = ns.getInt("numClient");
		reqSize = ns.getInt("reqSize");
		numCall = ns.getInt("numCall");
	}
}
