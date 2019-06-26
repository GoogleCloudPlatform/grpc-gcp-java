/*
 * Copyright 2019 Google LLC
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of Google LLC nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package io.grpc.e2e.checksum;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableList;
import com.google.datastore.v1.CommitRequest;
import com.google.datastore.v1.CommitRequest.Mode;
import com.google.datastore.v1.CommitResponse;
import com.google.datastore.v1.DatastoreGrpc;
import com.google.datastore.v1.DatastoreGrpc.DatastoreBlockingStub;
import com.google.datastore.v1.Entity;
import com.google.datastore.v1.Key;
import com.google.datastore.v1.Key.PathElement;
import com.google.datastore.v1.LookupRequest;
import com.google.datastore.v1.LookupResponse;
import com.google.datastore.v1.Mutation;
import com.google.datastore.v1.Value;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.auth.MoreCallCredentials;
import java.io.IOException;
import java.util.Map;

public class DataStoreChecksumClient {

  private static final String DATASTORE_TARGET = "datastore.googleapis.com";
  private static final String OAUTH_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

  private static void simpleLookup(DatastoreBlockingStub stub, String projectId) {
    PathElement pathElement = PathElement.newBuilder().setKind("Person").setName("weiranf").build();
    Key key = Key.newBuilder().addPath(pathElement).build();
    LookupRequest lookupRequest =
        LookupRequest.newBuilder().setProjectId(projectId).addKeys(key).build();
    LookupResponse lookupResponse = stub.lookup(lookupRequest);
    if (lookupResponse.getFoundCount() > 0) {
      System.out.println("ENTITY FOUND:");
      Entity entity = lookupResponse.getFound(0).getEntity();
      Map<String, Value> map = entity.getPropertiesMap();
      for (Map.Entry<String, Value> entry : map.entrySet()) {
        System.out.printf("%s => %s\n", entry.getKey(), entry.getValue().getStringValue());
      }
    } else {
      System.out.println("NO ENTITY FOUND!");
    }
  }

  private static void commitTransaction(DatastoreBlockingStub stub, String projectId) {
    int transactionBytes = 2000;
    String payload = new String(new char[transactionBytes]).replace('\0', 'x');

    Key key =
        Key.newBuilder()
            .addPath(PathElement.newBuilder().setKind("Data").setName("data1").build())
            .build();
    Entity entity =
        Entity.newBuilder()
            .setKey(key)
            .putProperties(
                "2kb",
                Value.newBuilder().setExcludeFromIndexes(true).setStringValue(payload).build())
            .build();
    Mutation mutation = Mutation.newBuilder().setUpdate(entity).build();

    CommitRequest commitRequest =
        CommitRequest.newBuilder()
            .setProjectId(projectId)
            .setMode(Mode.NON_TRANSACTIONAL)
            .addMutations(mutation)
            .build();

    CommitResponse commitResponse = stub.commit(commitRequest);

    System.out.println("index updates: " + commitResponse.getIndexUpdates());
  }

  private static DatastoreBlockingStub createStub(boolean overrideTarget, String target)
      throws IOException {

    ManagedChannelBuilder channelBuilder = ManagedChannelBuilder
        .forAddress(target, 443);
    GrpcRequestMarshallerInterceptor reqMarshallerInterceptor =
        new GrpcRequestMarshallerInterceptor();
    ManagedChannel channel = channelBuilder.intercept(reqMarshallerInterceptor).build();

    DatastoreBlockingStub stub = DatastoreGrpc.newBlockingStub(channel);

    if (!overrideTarget) {
      GoogleCredentials gcreds = GoogleCredentials.getApplicationDefault()
          .createScoped(ImmutableList.of(OAUTH_SCOPE));
      stub = stub.withCallCredentials(MoreCallCredentials.from(gcreds));
    }

    return stub;
  }

  private static void printUsageAndExit() {
    System.err.println("Args Usage: ");
    System.err.println("    <project_id> <rpc_command> [override_target]");
    System.err.println("Examples: ");
    System.err.println("    my-project lookup");
    System.err.println("    my-project commit test-service.sandbox.googleapis.com");
    System.exit(1);
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 2 || args.length > 3) {
      printUsageAndExit();
    }

    boolean overrideTarget = args.length == 3;
    String projectId = args[0];
    String rpcCommand = args[1];
    String target = overrideTarget ? args[2] : DATASTORE_TARGET;

    DatastoreBlockingStub stub = createStub(overrideTarget, target);

    switch (rpcCommand) {
      case "lookup":
        simpleLookup(stub, projectId);
        break;
      case "commit":
        commitTransaction(stub, projectId);
        break;
      default:
        printUsageAndExit();
    }
  }

}
