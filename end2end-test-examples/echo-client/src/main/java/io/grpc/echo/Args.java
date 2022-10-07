package io.grpc.echo;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.math3.distribution.PoissonDistribution;

public class Args {
  private static final String DEFAULT_HOST = "staging-grpc-cfe-benchmarks-with-esf.googleapis.com";
  private static final int PORT = 443;

  final int numRpcs;
  final String cookie;
  final boolean header;
  final int warmup;
  final String host;
  final int port;
  final boolean async;
  final int numChannels;
  final boolean insecure;
  final String overrideService;
  final String reqComp;
  final String resComp;
  final int threads;
  final int qps;
  final int reqSize;
  final int resSize;
  final int resType;
  final String token;
  final PoissonDistribution distrib;
  final int interval;
  final int recreateChannelSeconds;
  final int timeout;
  final boolean stream;
  final String logConfig;
  final boolean fineLogs;
  final boolean debugHeader;
  final String logFilename;
  final int logMaxSize;
  final int logMaxFiles;
  final boolean disableConsoleLog;
  final String metricName;
  final String metricTaskPrefix;
  final String metricProbeName;
  final int numMsgs;
  final int msgsInterval;
  final boolean useTd;
  final boolean enableTrace;

  Args(String[] args) throws ArgumentParserException {
    ArgumentParser parser =
        ArgumentParsers.newFor("Echo client test")
            .build()
            .defaultHelp(true)
            .description("Echo client java binary");

    parser.addArgument("--numRpcs").type(Integer.class).setDefault(1);
    parser.addArgument("--cookie").type(String.class).setDefault("");
    parser.addArgument("--header").type(Boolean.class).setDefault(false);
    parser.addArgument("--warmup").type(Integer.class).setDefault(0);
    parser.addArgument("--host").type(String.class).setDefault(DEFAULT_HOST);
    parser.addArgument("--port").type(Integer.class).setDefault(PORT);
    parser.addArgument("--async").type(Boolean.class).setDefault(false);
    parser.addArgument("--numChannels").type(Integer.class).setDefault(1);
    parser.addArgument("--insecure").type(Boolean.class).setDefault(false);
    parser.addArgument("--override").type(String.class).setDefault("");
    parser.addArgument("--reqComp").type(String.class).setDefault("");
    parser.addArgument("--resComp").type(String.class).setDefault("");
    parser.addArgument("--threads").type(Integer.class).setDefault(1);
    parser.addArgument("--qps").type(Integer.class).setDefault(0);
    parser.addArgument("--reqSize").type(Integer.class).setDefault(1);
    parser.addArgument("--resSize").type(Integer.class).setDefault(1);
    parser.addArgument("--resType").type(Integer.class).setDefault(0);
    parser.addArgument("--token").type(String.class).setDefault("");
    parser.addArgument("--interval").type(Integer.class).setDefault(0);
    parser.addArgument("--recreateChannelSeconds").type(Integer.class).setDefault(-1);
    parser.addArgument("--timeout").type(Integer.class).setDefault(3600000);
    parser.addArgument("--stream").type(Boolean.class).setDefault(false);
    parser.addArgument("--logConfig").type(String.class).setDefault("");
    parser.addArgument("--fineLogs").type(Boolean.class).setDefault(false);
    parser.addArgument("--debugHeader").type(Boolean.class).setDefault(false);
    parser.addArgument("--logFilename").type(String.class).setDefault("");
    parser.addArgument("--logMaxSize").type(Integer.class).setDefault(0);
    parser.addArgument("--logMaxFiles").type(Integer.class).setDefault(0);
    parser.addArgument("--disableConsoleLog").type(Boolean.class).setDefault(false);
    parser.addArgument("--metricName").type(String.class).setDefault("");
    parser.addArgument("--metricTaskPrefix").type(String.class).setDefault("");
    parser.addArgument("--metricProbeName").type(String.class).setDefault("");
    parser.addArgument("--numMsgs").type(Integer.class).setDefault(1);
    parser.addArgument("--msgsInterval").type(Integer.class).setDefault(0);
    parser.addArgument("--useTd").type(Boolean.class).setDefault(false);
    parser.addArgument("--enableTrace").type(Boolean.class).setDefault(false);

    Namespace ns = parser.parseArgs(args);

    // Read args
    numRpcs = ns.getInt("numRpcs");
    cookie = ns.getString("cookie");
    header = ns.getBoolean("header");
    warmup = ns.getInt("warmup");
    host = ns.getString("host");
    port = ns.getInt("port");
    async = ns.getBoolean("async");
    numChannels = ns.getInt("numChannels");
    insecure = ns.getBoolean("insecure");
    overrideService = ns.getString("override");
    reqComp = ns.getString("reqComp");
    resComp = ns.getString("resComp");
    threads = ns.getInt("threads");
    qps = ns.getInt("qps");
    reqSize = ns.getInt("reqSize");
    resSize = ns.getInt("resSize");
    resType = ns.getInt("resType");
    token = ns.getString("token");
    interval = ns.getInt("interval");
    recreateChannelSeconds = ns.getInt("recreateChannelSeconds");
    timeout = ns.getInt("timeout");
    stream = ns.getBoolean("stream");
    logConfig = ns.getString("logConfig");
    fineLogs = ns.getBoolean("fineLogs");
    debugHeader = ns.getBoolean("debugHeader");
    logFilename = ns.getString("logFilename");
    logMaxSize = ns.getInt("logMaxSize");
    logMaxFiles = ns.getInt("logMaxFiles");
    disableConsoleLog = ns.getBoolean("disableConsoleLog");
    metricName = ns.getString("metricName");
    metricTaskPrefix = ns.getString("metricTaskPrefix");
    metricProbeName = ns.getString("metricProbeName");
    numMsgs = ns.getInt("numMsgs");
    msgsInterval = ns.getInt("msgsInterval");
    useTd = ns.getBoolean("useTd");
    enableTrace = ns.getBoolean("enableTrace");

    distrib = (qps > 0) ? new PoissonDistribution(1000 / qps) : null;
  }
}
