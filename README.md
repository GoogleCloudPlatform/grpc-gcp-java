# grpc-gcp-java

## Overview

This is a Java test program meant to demonstrate calling all of Firebase's core methods using gRPC.

## Building

The program can be built using Maven and contains a Main class.  You can run mvn install, and then simply execute the resulting .jar file with java -jar.

## Notes

This requires that a Google Credentials service .json file be provided which matches your Google Cloud account. You'll need to set GOOGLE_APPLICATION_CREDENTIALS=/path/to/.json in order to authenticate.