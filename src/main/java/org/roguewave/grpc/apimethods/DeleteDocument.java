package org.roguewave.grpc.apimethods;

import com.google.firestore.v1beta1.DeleteDocumentRequest;
import com.google.firestore.v1beta1.FirestoreGrpc;
import org.roguewave.grpc.util.GRPCFirebaseClientFactory;
import org.roguewave.grpc.util.gfx.Menu;

import java.util.Scanner;

public class DeleteDocument {

    public void deleteDocumentCall() {

        Scanner sc = new Scanner(System.in);
        System.out.println("\n :: Deleting a Document ::\n");
        System.out.print("Enter Document Name: ");
        String docName = "projects/firestoretestclient/databases/(default)/documents/GrpcTestData/" + sc.next();

        FirestoreGrpc.FirestoreBlockingStub blockingStub = new GRPCFirebaseClientFactory().createFirebaseClient().getBlockingStub();

        DeleteDocumentRequest delReq = DeleteDocumentRequest.newBuilder()
                .setName(docName)
                .build();

        try {
            blockingStub
                    .deleteDocument(delReq);
            System.out.println("Finished call...");
        } catch (Exception e) {
            System.out.println("Error executing blocking stub call: " + (e.getMessage() + "\n" + e.getCause().toString()));
        }

        Menu menu = new Menu();
        menu.draw();

    }

}
