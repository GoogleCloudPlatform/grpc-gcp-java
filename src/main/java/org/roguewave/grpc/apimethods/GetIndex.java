package org.roguewave.grpc.apimethods;

import com.google.firestore.admin.v1beta1.FirestoreAdminGrpc.FirestoreAdminBlockingStub;
import com.google.firestore.admin.v1beta1.GetIndexRequest;
import com.google.firestore.admin.v1beta1.Index;
import org.roguewave.grpc.util.GRPCFirebaseAdminClientFactory;
import org.roguewave.grpc.util.gfx.DrawIndex;
import org.roguewave.grpc.util.gfx.Menu;

import java.util.Scanner;

public class GetIndex {

    public void getIndexCall() {

        System.out.println("\n :: Getting an Index :: \n");

        FirestoreAdminBlockingStub blockingStub = new GRPCFirebaseAdminClientFactory().createFirebaseAdminClient().getBlockingStub();
        Scanner sc = new Scanner(System.in);

        System.out.print("Enter Index Name: ");
        String indexName = "projects/firestoretestclient/databases/(default)/indexes/" + sc.next();


        GetIndexRequest getIndexRequest = GetIndexRequest.newBuilder()
                .setName(indexName)
                .build();

        Index index;

        try {
            index = blockingStub.getIndex(getIndexRequest);
        }
        catch (Exception e) {
            System.out.println("Error during call: " + e.getMessage() + e.getCause());
            return;
        }

        DrawIndex di = new DrawIndex();
        di.drawIndex(index);

        Menu menu = new Menu();
        menu.draw();

    }

}
