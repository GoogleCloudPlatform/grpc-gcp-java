package io.grpc.gcs;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class Args {
  public static final String METHOD_READ = "read";
  public static final String METHOD_RANDOM = "random";
  public static final String METHOD_WRITE = "write";

  public static final String CLIENT_GRPC = "grpc";
  public static final String CLIENT_YOSHI = "yoshi";
  public static final String CLIENT_GCSIO_HTTP = "gcsio-http";
  public static final String CLIENT_GCSIO_GRPC = "gcsio-grpc";

  public static final String DEFAULT_HOST = "storage.googleapis.com";
  private static final int PORT = 443;

  final int calls;
  final String cookie;
  final String host;
  final int port;
  final String bkt, obj;
  final boolean dp;
  final int size;
  final int buffSize;
  final String method;
  final boolean conscrypt;
  final String client;
  final String latencyFilename;
  final int threads;

  Args(String[] args) throws ArgumentParserException {
    ArgumentParser parser =
        ArgumentParsers.newFor("GCS client test")
            .build()
            .defaultHelp(true)
            .description("GCS client java binary");

    parser.addArgument("--calls").type(Integer.class).setDefault(1);
    parser.addArgument("--cookie").type(String.class).setDefault("");
    parser.addArgument("--host").type(String.class).setDefault(DEFAULT_HOST);
    parser.addArgument("--port").type(Integer.class).setDefault(PORT);
    parser.addArgument("--bkt").type(String.class).setDefault("gcs-grpc-team-weiranf");
    parser.addArgument("--obj").type(String.class).setDefault("a");
    parser.addArgument("--dp").type(Boolean.class).setDefault(false);
    parser.addArgument("--size").type(Integer.class).setDefault(0);
    parser.addArgument("--buffSize").type(Integer.class).setDefault(0);
    parser.addArgument("--method").type(String.class).setDefault(METHOD_READ);
    parser.addArgument("--conscrypt").type(Boolean.class).setDefault(false);
    parser.addArgument("--client").type(String.class).setDefault(CLIENT_GRPC);
    parser.addArgument("--latencyFilename").type(String.class).setDefault("latency_results");
    parser.addArgument("--threads").type(Integer.class).setDefault(0);

    Namespace ns = parser.parseArgs(args);

    // Read args
    calls = ns.getInt("calls");
    cookie = ns.getString("cookie");
    host = ns.getString("host");
    port = ns.getInt("port");
    bkt = ns.getString("bkt");
    obj = ns.getString("obj");
    dp = ns.getBoolean("dp");
    size = ns.getInt("size");
    buffSize = ns.getInt("buffSize");
    method = ns.getString("method");
    conscrypt = ns.getBoolean("conscrypt");
    client = ns.getString("client");
    latencyFilename = ns.getString("latencyFilename");
    threads = ns.getInt("threads");
  }
}
