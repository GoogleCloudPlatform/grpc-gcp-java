package org.roguewave.grpc.apimethods;

import com.google.firestore.v1beta1.BatchGetDocumentsRequest;
import com.google.firestore.v1beta1.BatchGetDocumentsResponse;
import com.google.firestore.v1beta1.Document;
import com.google.firestore.v1beta1.FirestoreGrpc;

import io.grpc.stub.StreamObserver;
import org.roguewave.grpc.util.GRPCFirebaseClientFactory;
import org.roguewave.grpc.util.gfx.DrawDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class BatchGetDocuments {

    public void batchGetDocumentsCall() {

        List<String> docList = new ArrayList<String>();
        System.out.println("\n :: Batch Retrieve Documents :: \n");
        Scanner sc = new Scanner(System.in);
        String input = "initial";
        FirestoreGrpc.FirestoreStub firestoreStub = new GRPCFirebaseClientFactory().createFirebaseClient().getFirestoreStub();
        DrawDocument dd = new DrawDocument();

        while (! input.matches("DONE")) {
            System.out.print("Enter Document Id (Enter DONE when finished): ");
            input = sc.next();
            if (! input.matches("DONE")) {
                docList.add("projects/firestoretestclient/databases/(default)/documents/GrpcTestData/" + input);
            }
        }

        BatchGetDocumentsRequest batchGetDocsRequest = BatchGetDocumentsRequest.newBuilder()
                .setDatabase("projects/firestoretestclient/databases/(default)")
                .addAllDocuments(docList)
                .build();

        StreamObserver respStream = new StreamObserver() {
            @Override
            public void onNext(Object resp) {

                BatchGetDocumentsResponse response = (BatchGetDocumentsResponse) resp;
                Document doc = response.getFound();
                dd.draw(doc);

            }

            @Override
            public void onError(Throwable throwable) {
                System.out.println("Error During Call: " + throwable.getMessage());
            }

            @Override
            public void onCompleted() {
                System.out.println("finished!");

            }

        };

        firestoreStub.batchGetDocuments(batchGetDocsRequest, respStream);


    }

}
