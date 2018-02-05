package org.roguewave.grpc.apimethods;

import com.google.firestore.v1beta1.*;
import com.google.firestore.v1beta1.FirestoreGrpc.FirestoreBlockingStub;
import org.roguewave.grpc.util.GRPCFirebaseClientFactory;
import org.roguewave.grpc.util.gfx.DrawDocument;
import java.util.Iterator;
import java.util.Scanner;

public class RunQuery {

    public void runQueryCall() {

        System.out.println(":: Running a Query ::");
        FirestoreBlockingStub blockingStub = new GRPCFirebaseClientFactory().createFirebaseClient().getBlockingStub();
        DrawDocument dd = new DrawDocument();

        Scanner sc = new Scanner(System.in);
        System.out.print("Enter field to query: ");
        String queryField = sc.next();

        StructuredQuery.FieldReference fr = StructuredQuery.FieldReference.newBuilder()
                .setFieldPath(queryField)
                .build();

        StructuredQuery.Projection proj = StructuredQuery.Projection.newBuilder()
                .addFields(fr)
                .build();

        StructuredQuery sq = StructuredQuery.newBuilder()
                .setSelect(proj)
                .build();

        RunQueryRequest runQueryRequest = RunQueryRequest.newBuilder()
                .setStructuredQuery(sq)
                .setParent("projects/firestoretestclient/databases/(default)/documents")
                .build();

        Iterator<RunQueryResponse> runQueryResponse;

        try {
             runQueryResponse = blockingStub.runQuery(runQueryRequest);
        }
        catch (Exception e) {
            System.out.println("Error during call: " + e.getMessage() + e.getCause());
            return;
        }

        System.out.println("Result set:\n");

        while (runQueryResponse.hasNext()) {

            RunQueryResponse response = runQueryResponse.next();
            Document doc = response.getDocument();

            dd.draw(doc);

        }

        System.out.println("Done!");

    }

}
