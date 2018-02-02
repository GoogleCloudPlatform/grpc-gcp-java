package org.roguewave.grpc.apimethods;

import com.google.firestore.v1beta1.BeginTransactionRequest;
import com.google.firestore.v1beta1.BeginTransactionResponse;
import com.google.firestore.v1beta1.FirestoreGrpc.FirestoreBlockingStub;
import com.google.firestore.v1beta1.TransactionOptions;
import org.roguewave.grpc.Main;
import org.roguewave.grpc.util.GRPCFirebaseClientFactory;

public class BeginTransaction {

    public void beginTransactionCall() {

        FirestoreBlockingStub blockingStub = new GRPCFirebaseClientFactory().createFirebaseClient().getBlockingStub();

        TransactionOptions tOpts = TransactionOptions.newBuilder().build();

        BeginTransactionRequest beginTransactionRequest = BeginTransactionRequest.newBuilder()
                .setDatabase("projects/firestoretestclient/databases/(default)")
                .setOptions(tOpts)
                .build();

        BeginTransactionResponse response;

        try {
            response = blockingStub.beginTransaction(beginTransactionRequest);
        }
        catch (Exception e) {
            System.out.println("Error during call: " + e.getMessage() + e.getCause());
            return;
        }

        System.out.println("Began Transaction: " + response.getTransaction().toString());
        Main.transactionId = response.getTransaction();
    }
}
