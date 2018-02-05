package org.roguewave.grpc.apimethods;

import com.google.firestore.admin.v1beta1.CreateIndexRequest;
import com.google.firestore.admin.v1beta1.FirestoreAdminGrpc.FirestoreAdminBlockingStub;
import com.google.firestore.admin.v1beta1.Index;
import com.google.firestore.admin.v1beta1.IndexField;
import com.google.firestore.v1beta1.FirestoreGrpc;
import com.google.longrunning.Operation;
import org.roguewave.grpc.util.GRPCFirebaseAdminClientFactory;

import java.util.ArrayList;
import java.util.Scanner;

public class CreateIndex {

    public void createIndexCall() {

        System.out.println(":: Creating New Index ::");

        FirestoreAdminBlockingStub blockingStub = new GRPCFirebaseAdminClientFactory().createFirebaseAdminClient().getBlockingStub();

        String indexField = "initial";
        String indexMode = "initial";
        Scanner sc = new Scanner(System.in);
        ArrayList<IndexField> allIndexes = new ArrayList<>();

        while (!indexField.matches("DONE")) {

            System.out.print("Index Field Name: ");
            indexField = sc.next();

            if (!indexField.matches("DONE")) {

                System.out.print("Mode (*ASCENDING*/DESCENDING - DONE to finish): ");
                indexMode = sc.next();

                if ((! indexMode.matches("ASCENDING")) && (! indexMode.matches("DESCENDING"))) {
                    System.out.println("Not Recognized, setting to default ASCENDING");
                    indexMode = "ASCENDING";
                }

                IndexField iff = IndexField.newBuilder()
                        .setFieldPath(indexField)
                        .setMode((indexMode.matches("ASCENDING") ? IndexField.Mode.ASCENDING : IndexField.Mode.DESCENDING))
                        .build();

                allIndexes.add(iff);

            }
        }

        Index newIndex = Index.newBuilder()
                .setCollectionId("GrpcTestData")
                .addAllFields(allIndexes)
                .build();

        CreateIndexRequest createIndexRequest = CreateIndexRequest.newBuilder()
                .setParent("projects/firestoretestclient/databases/(default)")
                .setIndex(newIndex)
                .build();

        try {
            blockingStub.createIndex(createIndexRequest);
        } catch (Exception e) {
            System.out.println("Error during call: " + e.getMessage() + e.getCause());
        }

        System.out.println("Successfully created new index!");

    }

}
