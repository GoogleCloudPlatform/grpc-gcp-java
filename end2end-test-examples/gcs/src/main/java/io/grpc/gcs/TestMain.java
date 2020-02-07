package io.grpc.gcs;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.logging.Logger;
import org.conscrypt.Conscrypt;
import java.security.Security;

public class TestMain {
  private static final Logger logger = Logger.getLogger(TestMain.class.getName());

  private static void printResult(ArrayList<Long> results) {
    Collections.sort(results);
    int n = results.size();
    System.out.println(
        String.format( "============ Test Results: \n"
                + "\t\tMin\tp5\tp10\tp25\tp50\tp75\tp90\tp99\tMax\n"
                + "  Time(ms)\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d",
            results.get(0),
            results.get((int) (n * 0.05)),
            results.get((int) (n * 0.1)),
            results.get((int) (n * 0.25)),
            results.get((int) (n * 0.50)),
            results.get((int) (n * 0.75)),
            results.get((int) (n * 0.90)),
            results.get((int) (n * 0.99)),
            results.get(n - 1)));
  }

  public static void main(String[] args) throws Exception {
    Args a = new Args(args);
    if (a.conscrypt) {
      Security.insertProviderAt(Conscrypt.newProvider(), 1);
    }
    ArrayList<Long> results = new ArrayList<>();
    if (a.http) {
      System.out.println("Making http call");
      HttpClient client = new HttpClient(a);
      client.startCalls(results);
    } else {
      GrpcClient client = new GrpcClient(a);
      client.startCalls(results);
    }

    printResult(results);
  }
}
