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
  final int warmups;
  final String cookie;
  final String host;
  final String host2;
  final int port;
  final String service_path;
  final String access_token;
  final String bkt, obj;
  final boolean dp;
  final boolean rr;
  final boolean td;
  final int size;
  final int buffSize;
  final String method;
  final boolean conscrypt;
  final boolean conscrypt_notm;
  final String client;
  final String latencyFilename;
  final int threads;
  final int flowControlWindow;
  final boolean checksum;
  final boolean verboseLog;
  final boolean verboseResult;
  final int zeroCopy; // 0=auto, 1=on, -1=off

  Args(String[] args) throws ArgumentParserException {
    ArgumentParser parser =
        ArgumentParsers.newFor("GCS client test")
            .build()
            .defaultHelp(true)
            .description("GCS client java binary");

    parser.addArgument("--calls").type(Integer.class).setDefault(1);
    parser.addArgument("--warmups").type(Integer.class).setDefault(0);
    parser.addArgument("--cookie").type(String.class).setDefault("");
    parser.addArgument("--host").type(String.class).setDefault(DEFAULT_HOST);
    parser.addArgument("--host2").type(String.class).setDefault("");
    parser.addArgument("--port").type(Integer.class).setDefault(PORT);
    parser.addArgument("--service_path").type(String.class).setDefault("storage/v1/");
    parser.addArgument("--access_token").type(String.class).setDefault("");
    parser.addArgument("--bkt").type(String.class).setDefault("gcs-grpc-team-weiranf");
    parser.addArgument("--obj").type(String.class).setDefault("a");
    parser.addArgument("--dp").type(Boolean.class).setDefault(false);
    parser.addArgument("--rr").type(Boolean.class).setDefault(false);
    parser.addArgument("--td").type(Boolean.class).setDefault(false);
    parser.addArgument("--size").type(Integer.class).setDefault(0);
    parser.addArgument("--buffSize").type(Integer.class).setDefault(0);
    parser.addArgument("--method").type(String.class).setDefault(METHOD_READ);
    parser.addArgument("--conscrypt").type(Boolean.class).setDefault(false);
    parser.addArgument("--conscrypt_notm").type(Boolean.class).setDefault(false);
    parser.addArgument("--client").type(String.class).setDefault(CLIENT_GRPC);
    parser.addArgument("--latencyFilename").type(String.class).setDefault("");
    parser.addArgument("--threads").type(Integer.class).setDefault(1);
    parser.addArgument("--window").type(Integer.class).setDefault(0);
    parser.addArgument("--checksum").type(Boolean.class).setDefault(false);
    parser.addArgument("--verboseLog").type(Boolean.class).setDefault(false);
    parser.addArgument("--verboseResult").type(Boolean.class).setDefault(false);
    parser.addArgument("--zeroCopy").type(Integer.class).setDefault(0);

    Namespace ns = parser.parseArgs(args);

    // Read args
    calls = ns.getInt("calls");
    warmups = ns.getInt("warmups");
    cookie = ns.getString("cookie");
    host = ns.getString("host");
    host2 = ns.getString("host2");
    port = ns.getInt("port");
    service_path = ns.getString("service_path");
    access_token = ns.getString("access_token");
    bkt = ns.getString("bkt");
    obj = ns.getString("obj");
    dp = ns.getBoolean("dp");
    rr = ns.getBoolean("rr");
    td = ns.getBoolean("td");
    size = ns.getInt("size");
    buffSize = ns.getInt("buffSize");
    method = ns.getString("method");
    conscrypt = ns.getBoolean("conscrypt");
    conscrypt_notm = ns.getBoolean("conscrypt_notm");
    client = ns.getString("client");
    latencyFilename = ns.getString("latencyFilename");
    threads = ns.getInt("threads");
    flowControlWindow = ns.getInt("window");
    checksum = ns.getBoolean("checksum");
    verboseLog = ns.getBoolean("verboseLog");
    verboseResult = ns.getBoolean("verboseResult");
    zeroCopy = ns.getInt("zeroCopy");
  }
}
