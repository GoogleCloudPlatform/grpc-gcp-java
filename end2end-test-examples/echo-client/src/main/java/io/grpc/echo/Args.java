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
  final boolean enableTracer;
  final String cookie;
  final boolean header;
  final int warmup;
  final String host;
  final int port;
  final boolean async;
  final int numChannels;
  final boolean insecure;
  final String overrideService;
  final String compression;
  final int threads;
  final int qps;
  final int reqSize;
  final int rspSize;
  final PoissonDistribution distrib;

  Args(String[] args) throws ArgumentParserException {
    ArgumentParser parser =
        ArgumentParsers.newFor("Echo client test")
            .build()
            .defaultHelp(true)
            .description("Echo client java binary");

    parser.addArgument("--numRpcs").type(Integer.class).setDefault(1);
    parser.addArgument("--tracer").type(Boolean.class).setDefault(false);
    parser.addArgument("--cookie").type(String.class).setDefault("");
    parser.addArgument("--header").type(Boolean.class).setDefault(false);
    parser.addArgument("--warmup").type(Integer.class).setDefault(5);
    parser.addArgument("--host").type(String.class).setDefault(DEFAULT_HOST);
    parser.addArgument("--port").type(Integer.class).setDefault(PORT);
    parser.addArgument("--async").type(Boolean.class).setDefault(false);
    parser.addArgument("--numChannels").type(Integer.class).setDefault(1);
    parser.addArgument("--insecure").type(Boolean.class).setDefault(false);
    parser.addArgument("--override").type(String.class).setDefault("");
    parser.addArgument("--compression").type(String.class).setDefault("");
    parser.addArgument("--threads").type(Integer.class).setDefault(1);
    parser.addArgument("--qps").type(Integer.class).setDefault(0);
    parser.addArgument("--reqSize").type(Integer.class).setDefault(1);
    parser.addArgument("--rspSize").type(Integer.class).setDefault(1);

    Namespace ns = parser.parseArgs(args);

    // Read args
    numRpcs = ns.getInt("numRpcs");
    enableTracer = ns.getBoolean("tracer");
    cookie = ns.getString("cookie");
    header = ns.getBoolean("header");
    warmup = ns.getInt("warmup");
    host = ns.getString("host");
    port = ns.getInt("port");
    async = ns.getBoolean("async");
    numChannels = ns.getInt("numChannels");
    insecure = ns.getBoolean("insecure");
    overrideService = ns.getString("override");
    compression = ns.getString("compression");
    threads = ns.getInt("threads");
    qps = ns.getInt("qps");
    reqSize = ns.getInt("reqSize");
    rspSize = ns.getInt("rspSize");
    distrib = (qps > 0) ? new PoissonDistribution(1000/qps) : null;
  }
}
