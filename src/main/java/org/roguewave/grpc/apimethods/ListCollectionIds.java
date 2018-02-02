package org.roguewave.grpc.apimethods;

import com.google.firestore.v1beta1.FirestoreGrpc;
import com.google.firestore.v1beta1.ListCollectionIdsRequest;
import com.google.firestore.v1beta1.ListCollectionIdsResponse;
import com.google.protobuf.ProtocolStringList;
import org.roguewave.grpc.util.GRPCFirebaseClientFactory;

import java.util.Iterator;

public class ListCollectionIds {

    public void listCollectionIdsCall() {

        System.out.println("\n :: Listing all Collection Ids ::\n");

        FirestoreGrpc.FirestoreBlockingStub blockingStub = new GRPCFirebaseClientFactory().createFirebaseClient().getBlockingStub();

        ListCollectionIdsRequest listCollectionIdsRequest = ListCollectionIdsRequest.newBuilder()
                .setParent("projects/firestoretestclient/databases/(default)")
                .build();

        ListCollectionIdsResponse response;

        try {
            response = blockingStub.listCollectionIds(listCollectionIdsRequest);
        }
        catch (Exception e) {
            System.out.println("Error during call: " + e.getMessage() + e.getCause());
            return;
        }

        ProtocolStringList psl = response.getCollectionIdsList();
        Iterator<String> collectionIdIter = psl.iterator();

        while (collectionIdIter.hasNext()) {
            System.out.println(collectionIdIter.next());
        }

    }
}
