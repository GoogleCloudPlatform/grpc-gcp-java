package org.roguewave.grpc.apimethods;

import com.google.firestore.admin.v1beta1.FirestoreAdminGrpc.FirestoreAdminBlockingStub;
import com.google.firestore.admin.v1beta1.Index;
import com.google.firestore.admin.v1beta1.ListIndexesRequest;
import com.google.firestore.admin.v1beta1.ListIndexesResponse;
import org.roguewave.grpc.util.GRPCFirebaseAdminClientFactory;
import org.roguewave.grpc.util.gfx.DrawIndex;
import org.roguewave.grpc.util.gfx.Menu;

import java.util.List;

public class ListIndexes {

    public void listIndexesCall() {

        System.out.println(":: Listing All Indexes ::");

        FirestoreAdminBlockingStub blockingStub = new GRPCFirebaseAdminClientFactory().createFirebaseAdminClient().getBlockingStub();

        ListIndexesRequest listIndexesRequest = ListIndexesRequest.newBuilder()
                .setParent("projects/firestoretestclient/databases/(default)")
                .build();

        ListIndexesResponse response;

        try {
            response = blockingStub.listIndexes(listIndexesRequest);
        }
        catch (Exception e) {
            System.out.println("Error during call: " + e.getMessage() + e.getCause());
            return;
        }

        List<Index> indexList = response.getIndexesList();

        DrawIndex di = new DrawIndex();

        for (Index index : indexList) {
            di.drawIndex(index);
        }

        Menu menu = new Menu();
        menu.draw();


    }


}
