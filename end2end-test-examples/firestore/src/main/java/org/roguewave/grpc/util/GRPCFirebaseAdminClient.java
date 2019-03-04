package org.roguewave.grpc.util;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableList;
import com.google.firestore.admin.v1beta1.FirestoreAdminGrpc;
import com.google.firestore.admin.v1beta1.FirestoreAdminGrpc.FirestoreAdminStub;
import com.google.firestore.admin.v1beta1.FirestoreAdminGrpc.FirestoreAdminBlockingStub;
import io.grpc.*;
import io.grpc.auth.MoreCallCredentials;

public class GRPCFirebaseAdminClient {

    public FirestoreAdminStub firestoreAdminStub;
    public FirestoreAdminBlockingStub blockingAdminStub;
    static final ImmutableList<String> REQURIED_SCOPES = ImmutableList.of( "https://www.googleapis.com/auth/cloud-platform", "https://www.googleapis.com/auth/datastore");

    public GRPCFirebaseAdminClient() {
        GoogleCredentials creds;

        try {
            creds = GoogleCredentials.getApplicationDefault();
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            return;
        }

        creds = creds.createScoped(REQURIED_SCOPES);

        ManagedChannel channel = ManagedChannelBuilder.forAddress("firestore.googleapis.com", 443)
                .build();

        firestoreAdminStub = FirestoreAdminGrpc.newStub(channel).withCallCredentials(MoreCallCredentials.from(creds));
        blockingAdminStub = FirestoreAdminGrpc.newBlockingStub(channel).withCallCredentials(MoreCallCredentials.from(creds));
    }

    public FirestoreAdminStub getFirestoreStub() {

        return firestoreAdminStub;

    }

    public FirestoreAdminBlockingStub getBlockingStub() {

        return blockingAdminStub;

    }



}