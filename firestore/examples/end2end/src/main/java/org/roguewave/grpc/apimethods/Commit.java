package org.roguewave.grpc.apimethods;

import com.google.firestore.v1beta1.CommitRequest;
import com.google.firestore.v1beta1.FirestoreGrpc.FirestoreBlockingStub;
import org.roguewave.grpc.Main;
import org.roguewave.grpc.util.GRPCFirebaseClientFactory;

public class Commit {

    public void commitCall() {

        System.out.println("\n:: Committing Transaction ::\n");

        if (Main.transactionId == null) {

            System.out.println("WARNING:  No current transaction open, run BeginTransaction first...");
            return;

        }
        else {

            System.out.println("Found Transaction ID '" + Main.transactionId.toString() + "'.  Committing....");

        }

        FirestoreBlockingStub blockingStub = new GRPCFirebaseClientFactory().createFirebaseClient().getBlockingStub();

        CommitRequest commitRequest = CommitRequest.newBuilder()
                .setTransaction(Main.transactionId)
                .setDatabase("projects/firestoretestclient/databases/(default)")
                .build();


        try {
            blockingStub.commit(commitRequest);
        }
        catch (Exception e) {
            System.out.println("Error during call: " + e.getMessage() + e.getCause());
            return;
        }

        System.out.println("Success!");

    }



}
