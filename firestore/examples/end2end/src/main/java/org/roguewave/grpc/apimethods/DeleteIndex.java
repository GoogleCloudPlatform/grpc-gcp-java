package org.roguewave.grpc.apimethods;

import com.google.firestore.admin.v1beta1.DeleteIndexRequest;
import com.google.firestore.admin.v1beta1.FirestoreAdminGrpc.FirestoreAdminBlockingStub;
import org.roguewave.grpc.util.GRPCFirebaseAdminClientFactory;

import java.util.Scanner;

public class DeleteIndex {

    public void deleteIndexCall() {

        System.out.println("\n :: Deleting an Index :: \n");

        FirestoreAdminBlockingStub blockingStub = new GRPCFirebaseAdminClientFactory().createFirebaseAdminClient().getBlockingStub();
        Scanner sc = new Scanner(System.in);

        System.out.print("Enter Index Name: ");
        String indexName = "projects/firestoretestclient/databases/(default)/indexes/" + sc.next();


        DeleteIndexRequest deleteIndexRequest = DeleteIndexRequest.newBuilder()
                .setName(indexName)
                .build();

        try {
            blockingStub.deleteIndex(deleteIndexRequest);
        }
        catch (Exception e) {
            System.out.println("Error during call: " + e.getMessage() + e.getCause());
            return;
        }

        System.out.println("Successfully deleted index " + indexName);

    }
}
