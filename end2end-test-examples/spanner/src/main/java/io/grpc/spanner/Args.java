package io.grpc.spanner;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class Args {
  static final Boolean ATTEMPT_DIRECTPATH = false;

  final String gcpProject;
  final String instanceId;
  final String databaseId;
  final String endpoint;
  final int numChannels;
  final int numRpcs;
  final int intervalMs;
  final int tcpkillMs;
  final int timeoutMs;
  final String operation;
  final int payloadKb;
  final String logConfig;
  final Boolean fineLogs;
  final String logFilename;
  final int logMaxSize;
  final int logMaxFiles;
  final Boolean disableConsoleLog;

  Args(String[] args) throws ArgumentParserException {
    ArgumentParser parser =
        ArgumentParsers.newFor("Spanner E2E client")
            .build()
            .defaultHelp(true)
            .description("Spanner E2E client java binary");
    parser.addArgument("--gcpProject").type(String.class).setDefault("");
    parser.addArgument("--instanceId").type(String.class).setDefault("");
    parser.addArgument("--databaseId").type(String.class).setDefault("");
    parser
        .addArgument("--endpoint")
        .type(String.class)
        .setDefault("https://spanner.googleapis.com:443");
    parser.addArgument("--numChannels").type(Integer.class).setDefault(4);
    parser.addArgument("--numRpcs").type(Integer.class).setDefault(1);
    parser.addArgument("--intervalMs").type(Integer.class).setDefault(0);
    parser.addArgument("--tcpkillMs").type(Integer.class).setDefault(0);
    parser.addArgument("--timeoutMs").type(Integer.class).setDefault(1000);
    parser.addArgument("--operation").type(String.class).setDefault("read");
    parser.addArgument("--payloadKb").type(Integer.class).setDefault(1);
    parser.addArgument("--fineLogs").type(Boolean.class).setDefault(false);
    parser.addArgument("--logFilename").type(String.class).setDefault("");
    parser.addArgument("--logConfig").type(String.class).setDefault("");
    parser.addArgument("--logMaxSize").type(Integer.class).setDefault(0);
    parser.addArgument("--logMaxFiles").type(Integer.class).setDefault(0);
    parser.addArgument("--disableConsoleLog").type(Boolean.class).setDefault(false);

    Namespace ns = parser.parseArgs(args);

    gcpProject = ns.getString("gcpProject");
    instanceId = ns.getString("instanceId");
    databaseId = ns.getString("databaseId");
    endpoint = ns.getString("endpoint");
    numChannels = ns.getInt("numChannels");
    numRpcs = ns.getInt("numRpcs");
    intervalMs = ns.getInt("intervalMs");
    tcpkillMs = ns.getInt("tcpkillMs");
    timeoutMs = ns.getInt("timeoutMs");
    operation = ns.getString("operation");
    payloadKb = ns.getInt("payloadKb");
    fineLogs = ns.getBoolean("fineLogs");
    logConfig = ns.getString("logConfig");
    logFilename = ns.getString("logFilename");
    logMaxSize = ns.getInt("logMaxSize");
    logMaxFiles = ns.getInt("logMaxFiles");
    disableConsoleLog = ns.getBoolean("disableConsoleLog");
  }
}
