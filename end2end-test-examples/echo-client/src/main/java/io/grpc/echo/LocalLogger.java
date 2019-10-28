package io.grpc.echo;

public class LocalLogger {

  static LocalLogger getLogger(String s) {return new LocalLogger();}

  void info(String s) {
    System.out.println(s);
  }

  void warning(String s) {
    System.out.println("*** Warning *** :" + s);
  }
}
