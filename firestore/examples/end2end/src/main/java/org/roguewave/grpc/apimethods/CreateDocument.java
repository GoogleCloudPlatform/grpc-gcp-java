package org.roguewave.grpc.apimethods;

import com.google.firestore.v1beta1.CreateDocumentRequest;
import com.google.firestore.v1beta1.Document;
import com.google.firestore.v1beta1.FirestoreGrpc.FirestoreBlockingStub;
import com.google.firestore.v1beta1.Value;
import com.google.protobuf.Descriptors;
import org.roguewave.grpc.util.GRPCFirebaseClientFactory;
import org.roguewave.grpc.util.gfx.DrawDocument;
import org.roguewave.grpc.util.gfx.MakeFieldsMap;

import java.util.HashMap;
import java.util.Scanner;

public class CreateDocument {

    public void createDocumentCall() {

        System.out.println("\n:: Creating New Document ::\n");
        FirestoreBlockingStub blockingStub = new GRPCFirebaseClientFactory().createFirebaseClient().getBlockingStub();
        Scanner sc = new Scanner(System.in);

        System.out.print("Enter Document Name: ");
        String docName = sc.next();
        HashMap<String,Value> fieldsMap;

        MakeFieldsMap mfm = new MakeFieldsMap();

        fieldsMap = mfm.makeFieldsMap();

        Document newDoc = Document.newBuilder()
                .putAllFields(fieldsMap)
                .build();

        CreateDocumentRequest createDocumentRequest = CreateDocumentRequest.newBuilder()
                .setDocument(newDoc)
                .setCollectionId("GrpcTestData")
                .setParent("projects/firestoretestclient/databases/(default)/documents")
                .setDocumentId(docName)
                .build();

        Document finishedDoc;
        try {
            finishedDoc = blockingStub.createDocument(createDocumentRequest);
        }
        catch (Exception e) {
            System.out.println("Error during call: " + e.getMessage() + e.getCause());
            return;
        }

        DrawDocument dd = new DrawDocument();
        dd.draw(finishedDoc);


    }

}
