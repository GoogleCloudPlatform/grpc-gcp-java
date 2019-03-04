package org.roguewave.grpc.apimethods;

import com.google.firestore.v1beta1.FirestoreGrpc.FirestoreBlockingStub;
import com.google.firestore.v1beta1.RollbackRequest;
import org.roguewave.grpc.Main;
import org.roguewave.grpc.util.GRPCFirebaseClientFactory;
import org.roguewave.grpc.util.gfx.Menu;

public class Rollback {

    public void rollbackCall() {
        System.out.println("\n:: Rolling Back Transaction ::\n");

        if (Main.transactionId == null) {

            System.out.println("WARNING:  No current transaction open, run BeginTransaction first...");
            return;

        }
        else {

            System.out.println("Found Transaction ID '" + Main.transactionId.toString() + "'.  Committing....");

        }

        FirestoreBlockingStub blockingStub = new GRPCFirebaseClientFactory().createFirebaseClient().getBlockingStub();

        RollbackRequest rollbackRequest = RollbackRequest.newBuilder()
                .setTransaction(Main.transactionId)
                .setDatabase("projects/firestoretestclient/databases/(default)")
                .build();


        try {
            blockingStub.rollback(rollbackRequest);
        }
        catch (Exception e) {
            System.out.println("Error during call: " + e.getMessage() + e.getCause());
            return;
        }

        System.out.println("Success!");

        Menu menu = new Menu();
        menu.draw();
    }

}
