package org.roguewave.grpc.apimethods;

import com.google.firestore.v1beta1.*;
import com.google.firestore.v1beta1.FirestoreGrpc.FirestoreBlockingStub;
import org.roguewave.grpc.util.GRPCFirebaseClientFactory;
import org.roguewave.grpc.util.gfx.DrawDocument;
import org.roguewave.grpc.util.gfx.MakeFieldsMap;
import org.roguewave.grpc.util.gfx.Menu;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;

public class UpdateDocument {

    public void updateDocumentCall() {

        System.out.println("\n:: Updating a Document ::\n");

        FirestoreBlockingStub blockingStub = new GRPCFirebaseClientFactory().createFirebaseClient().getBlockingStub();
        Scanner sc = new Scanner(System.in);

        System.out.print("Enter Document Name: ");
        String docName = sc.next();

        GetDocumentRequest getDocumentRequest = GetDocumentRequest.newBuilder()
                .setName("projects/firestoretestclient/databases/(default)/documents/GrpcTestData/" + docName)
                .build();

        Document doc;

        try {
            doc = blockingStub.getDocument(getDocumentRequest);
        }
        catch (Exception e) {
            System.out.println("Error during call: " + e.getMessage() + e.getCause());
            return;
        }

        HashMap<String,Value> fieldsMap;
        MakeFieldsMap mfm = new MakeFieldsMap();
        fieldsMap = mfm.makeFieldsMap();

        doc = doc.toBuilder().putAllFields(fieldsMap).build();

        Iterator it = fieldsMap.entrySet().iterator();
        DocumentMask docMask = DocumentMask.newBuilder().build();

        while (it.hasNext()) {

            Map.Entry pair = (Map.Entry) it.next();
            docMask = docMask.toBuilder()
                    .addFieldPaths(pair.getKey().toString())
                    .build();

        }

        UpdateDocumentRequest updateDocumentRequest = UpdateDocumentRequest.newBuilder()
                .setDocument(doc)
                .setMask(docMask)
                .build();

        try {
            blockingStub.updateDocument(updateDocumentRequest);
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
