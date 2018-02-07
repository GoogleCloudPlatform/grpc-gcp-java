package org.roguewave.grpc.apimethods;

import com.google.firestore.v1beta1.Document;
import com.google.firestore.v1beta1.FirestoreGrpc.FirestoreBlockingStub;
import com.google.firestore.v1beta1.ListDocumentsRequest;
import com.google.firestore.v1beta1.ListDocumentsResponse;
import io.grpc.stub.StreamObserver;
import org.roguewave.grpc.util.GRPCFirebaseClientFactory;
import org.roguewave.grpc.util.gfx.DrawDocument;
import org.roguewave.grpc.util.gfx.Menu;

import java.util.List;

public class ListDocuments {

    public void listDocumentsCall() {

        System.out.println("\n:: Listing all Documents ::\n");

        FirestoreBlockingStub blockingStub = new GRPCFirebaseClientFactory().createFirebaseClient().getBlockingStub();

        ListDocumentsRequest ldr = ListDocumentsRequest.newBuilder()
                .setParent("projects/firestoretestclient/databases/(default)/documents")
                .setCollectionId("GrpcTestData")
                .build();

        try {

            ListDocumentsResponse listDocumentsResponse = blockingStub.listDocuments(ldr);
            List<Document> allDocs = listDocumentsResponse.getDocumentsList();
            DrawDocument dd = new DrawDocument();

            for (Document doc : allDocs) {
                dd.draw(doc);
            }

            Menu menu = new Menu();
            menu.draw();
            System.out.println("Finished call...");
        }
        catch (Exception e) {
            System.out.println("Error executing streaming stub call: " + (e.getMessage() + "\n" +  e.getCause().toString()));
        }

    }
}
