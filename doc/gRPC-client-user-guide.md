# Instructions for create a gRPC client for google cloud services

## Overview

This instruction includes a step by step guide for creating a gRPC 
client to test the google cloud service from an empty linux 
VM, using GCE ubuntu 16.04 TLS instance.

The main steps are followed as steps below: 

- Environment prerequisite
- Install protobuf and gRPC-java-plugin
- Generate client API from .proto files
- Create the client and send/receive RPC.

## Environment Prerequisite

**Java**
```sh
$ [sudo] apt-get install default-jre
$ [sudo] apt-get install openjdk-8-jre
$ [sudo] apt-get install openjdk-8-jdk
```
**Maven**
```sh
$ cd /opt/
$ [sudo] wget http://www-eu.apache.org/dist/maven/maven-3/3.3.9/binaries/apache-maven-3.3.9-bin.tar.gz
$ [sudo] tar -xvzf apache-maven-3.3.9-bin.tar.gz
$ [sudo] mv apache-maven-3.3.9 maven
$ echo "export M2_HOME=/opt/maven" >> ~/.bashrc
$ echo "export PATH=${M2_HOME}/bin:${PATH}" >> ~/.bashrc
$source ~/.bashrc
```
## Install protobuf and gRPC-java-plugin
**Install protoc**
```sh
$ cd $HOME
$ git clone https://github.com/google/protobuf.git
$ cd $HOME/protobuf
$ ./autogen.sh && ./configure && make -j8
$ [sudo] make install
$ [sudo] ldconfig
```
**gRPC-java-plugin**

gRPC-java-plugin is used to generate client library from *.proto files.
```sh
$ cd $HOME
$ git clone https://github.com/grpc/grpc-java.git
$ cd grpc-java/compiler
$ ../gradlew java_pluginExecutable
```
The plugin `protoc-gen-grpc-java` is generated under 
`$HOME/grpc-java/compiler/build/exe/java_plugin/protoc-gen-grpc-java`


## Generate client API from .proto files 
The command using plugin looks like
```sh
$ mkdir $HOME/grpc-java/src/main/java
$ export OUTPUT_FILE=$HOME/project-java/src/main/java
$ protoc --plugin=protoc-gen-grpc-java=compiler/build/exe/java_plugin/protoc-gen-grpc-java \
--grpc-java_out="$OUTPUT_FILE" --java_out="$OUTPUT_FILE" --proto_path="$DIR_OF_PROTO_FILE" \
path/to/your/proto_dependency_directory1/*.proto \
path/to/your/proto_dependency_directory2/*.proto \
path/to/your/proto_service_directory/*.proto
```

Since most of cloud services already publish proto files under 
[googleapis github repo](https://github.com/googleapis/googleapis), another way
to generate client API is to use it's Makefile. The side effect is that is will generate
lots of unused services which slow the compile. You can delete the generated directories not neede.
For example, for Firestore service, the directories needed under `src/main/java/com/google/` are
`api`, `firestore`, `longrunning`, `rpc`, `type`. You can delete all other directories. Then mvn 
will only compile ~300 files instead of ~2,000 files.

The `Makefile` will help you generate the client API as
well as all the dependencies. The command will simply be:
```sh
$ cd $HOME
$ mkdir project-java
$ git clone https://github.com/googleapis/googleapis.git
$ cd googleapis
$ make LANGUAGE=java OUTPUT=/home/ddyihai/project-java/ FLAGS="--proto_path=./ --plugin=protoc-gen-grpc-java=$HOME/grpc-java/compiler/build/exe/java_plugin/protoc-gen-grpc-java --grpc-java_out=$HOME/project-java/src/main/java --java_out=$HOME/project-java/src/main/java"
```

The client API library is generated under `$HOME/project-java/src/main/java/com/google`.
Take [`Firestore`](https://github.com/googleapis/googleapis/blob/master/google/firestore/v1beta1/firestore.proto)
as example, the Client API is under 
`$HOME/src/main/java/com/google/firestore/v1beta1` depends on your 
package namespace inside .proto file. An easy way to find your client is 
```sh
$ cd $HOME/project-java
$ find ./ -name [service_name: eg, firestore, cluster_service]*
```

## Create the client and send/receive RPC.
Now it's time to use the client API to send and receive RPCs.

**Set credential file**

This is important otherwise your RPC response will be a permission error.
``` sh
$ vim $HOME/key.json
## Paste you credential file downloaded from your cloud project
## which you can find in APIs&Services => credentials => create credentials
## => Service account key => your credentials
$ export GOOGLE_APPLICATION_CREDENTIALS=$HOME/key.json
```

**Create pom.xml**

```sh
$ vim $HOME/project-java/pom.xml
```
The file looks like:
```
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>org.grpc</groupId>
    <artifactId>grpc-java</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>GRPC Java Example Application</name>
    <description>An example Java application implenting all of the GRPC function calls.</description>
    <dependencies>

        <!-- https://mvnrepository.com/artifact/io.grpc/grpc-all -->
        <dependency>
            <groupId>io.grpc</groupId>
            <artifactId>grpc-all</artifactId>
            <version>1.9.0</version>
        </dependency>

        <dependency>
            <groupId>io.grpc</groupId>
            <artifactId>grpc-netty</artifactId>
            <version>1.9.0</version>
        </dependency>

        <dependency>
            <groupId>io.netty</groupId>
            <artifactId>netty-all</artifactId>
            <version>4.1.20.Final</version>
        </dependency>
        <dependency>
            <groupId>io.netty</groupId>
            <artifactId>netty-tcnative-boringssl-static</artifactId>
            <version>2.0.7.Final</version>
        </dependency>
        <dependency>
            <groupId>io.netty</groupId>
            <artifactId>netty-codec-http2</artifactId>
            <version>4.1.20.Final</version>
        </dependency>
        
        <!-- https://mvnrepository.com/artifact/com.google.auth/google-auth-library-oauth2-http -->
        <dependency>
            <groupId>com.google.auth</groupId>
            <artifactId>google-auth-library-oauth2-http</artifactId>
            <version>0.9.0</version>
        </dependency>
        <!-- https://mvnrepository.com/artifact/com.google.auth/google-auth-library-parent -->
        <dependency>
            <groupId>com.google.auth</groupId>
            <artifactId>google-auth-library-parent</artifactId>
            <version>0.9.0</version>
            <type>pom</type>
        </dependency>
        <dependency>
            <groupId>com.google.api</groupId>
            <artifactId>api-common</artifactId>
            <version>1.2.0</version>
        </dependency>

    </dependencies>
    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <configuration>
                        <source>1.8</source>
                        <target>1.8</target>
                    </configuration>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-assembly-plugin</artifactId>
                    <version>2.4</version>
                    <configuration>
                        <descriptorRefs>
                            <descriptorRef>jar-with-dependencies</descriptorRef>
                        </descriptorRefs>
                        <archive>
                            <manifest>
                                <mainClass>org.grpc.Main</mainClass>
                            </manifest>
                        </archive>
                    </configuration>
                    <executions>
                        <execution>
                            <id>make-assembly</id>
                            <phase>package</phase>
                            <goals>
                                <goal>single</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

**Implement Service Client**

Take a unary-unary RPC `listDocument` from `FirestoreClient` as example.
Create a file name `Main.java`.
```sh
$ vim $HOME/project-java/src/main/java/org/grpc/Main.java
```
- Import library
```
package org.grpc;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableList;
import com.google.firestore.v1beta1.*;
import io.grpc.*;
import com.google.firestore.v1beta1.FirestoreGrpc.FirestoreBlockingStub;
import io.grpc.auth.MoreCallCredentials;

import com.google.firestore.v1beta1.Document;
import com.google.firestore.v1beta1.ListDocumentsRequest;
import com.google.firestore.v1beta1.ListDocumentsResponse;

import java.util.List;
```
- Set Google Auth. Please see the referece for 
[authenticate with Google using an Oauth2 token](https://grpc.io/docs/guides/auth.html#authenticate-with-google-4)
for the use of 'googleauth' library.
Inside Main class:
```
public class Main  {
    public static void main(String[] args) {
```
Create channel with credentials:
```
GoogleCredentials creds;
try {
  creds = GoogleCredentials.getApplicationDefault();
}
catch (Exception e) {
  System.out.println(e.getMessage());
  return;
}
ImmutableList<String> REQURIED_SCOPES = ImmutableList.of( "https://www.googleapis.com/auth/cloud-platform", "https://www.googleapis.com/auth/datastore");
creds = creds.createScoped(REQURIED_SCOPES);
ManagedChannel channel = ManagedChannelBuilder.forAddress("firestore.googleapis.com", 443)
    .build();
```
- Create Client

```
FirestoreBlockingStub blockingStub = FirestoreGrpc.newBlockingStub(channel).withCallCredentials(MoreCallCredentials.from(creds));
```
- Make and receive RPC call
```
ListDocumentsRequest ldr = ListDocumentsRequest.newBuilder()
  .setParent("projects/ddyihai-firestore/databases/(default)/documents")
  .build();
try {
  ListDocumentsResponse listDocumentsResponse = blockingStub.listDocuments(ldr);
  List<Document> allDocs = listDocumentsResponse.getDocumentsList();
  for (Document doc : allDocs) {
    System.out.println(doc);
  }
  System.out.println("Finished call...");
}
catch (Exception e) {
  System.out.println("Error executing streaming stub call: " + (e.getMessage() + "\n" +  e.getCause().toString()));
}
                
```
- Build and run
```sh
$ mvn clean compile assembly:single
$ java -jar target/grpc-java-0.0.1-SNAPSHOT-jar-with-dependencies.jar
```

For different kinds of RPC(unary-unary, unary-stream, stream-unary, stream-stream),
please check [grpc.io Java part](https://grpc.io/docs/tutorials/basic/java.html#simple-rpc)
for reference.


