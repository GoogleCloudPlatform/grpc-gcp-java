package org.roguewave.grpc.apimethods;

import com.google.firestore.v1beta1.*;
import io.grpc.stub.StreamObserver;
import org.roguewave.grpc.util.GRPCFirebaseClientFactory;
import org.roguewave.grpc.util.gfx.DrawDocument;

import java.util.List;
import java.util.Map;

public class ListDocuments {

    public void listDocumentsCall() {

        System.out.println("\n:: Listing all Documents ::\n");

        FirestoreGrpc.FirestoreStub firestoreStub = new GRPCFirebaseClientFactory().createFirebaseClient().getFirestoreStub();

        ListDocumentsRequest ldr = ListDocumentsRequest.newBuilder()
                .setParent("projects/firestoretestclient/databases/(default)/documents")
                .setCollectionId("GrpcTestData")
                .build();

        StreamObserver respStream = new StreamObserver() {
            @Override
            public void onNext(Object resp) {

                ListDocumentsResponse response = (ListDocumentsResponse) resp;
                List<Document> allDocs = response.getDocumentsList();
                DrawDocument dd = new DrawDocument();

                for (Document doc : allDocs) {
                    dd.draw(doc);
                }

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

        try {
            firestoreStub
                    .listDocuments(ldr, respStream);
            System.out.println("Finished call...");
        }
        catch (Exception e) {
            System.out.println("Error executing streaming stub call: " + (e.getMessage() + "\n" +  e.getCause().toString()));
        }

    }
}
