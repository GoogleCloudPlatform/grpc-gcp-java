package org.roguewave.grpc.apimethods;

import com.google.firestore.v1beta1.Document;
import com.google.firestore.v1beta1.FirestoreGrpc;
import com.google.firestore.v1beta1.GetDocumentRequest;
import org.roguewave.grpc.util.GRPCFirebaseClientFactory;
import org.roguewave.grpc.util.gfx.DrawDocument;
import org.roguewave.grpc.util.gfx.Menu;

import java.util.Scanner;

public class GetDocument {

    public void getDocumentCall() {

        System.out.println("\n:: Getting a Document ::\n");
        FirestoreGrpc.FirestoreBlockingStub blockingStub = new GRPCFirebaseClientFactory().createFirebaseClient().getBlockingStub();

        Scanner sc = new Scanner(System.in);

        System.out.print("Enter document name: ");
        String docName = sc.next();

        GetDocumentRequest getDocumentRequest = GetDocumentRequest.newBuilder()
                .setName("projects/firestoretestclient/databases/(default)/documents/GrpcTestData/" + docName)
                .build();

        try {
            Document doc = blockingStub.getDocument(getDocumentRequest);
            DrawDocument dd = new DrawDocument();
            dd.draw(doc);
        }
        catch (Exception e) {
            System.out.println("Error during call: " + e.getMessage() + e.getCause());
            return;
        }

        Menu menu = new Menu();
        menu.draw();

    }

}
