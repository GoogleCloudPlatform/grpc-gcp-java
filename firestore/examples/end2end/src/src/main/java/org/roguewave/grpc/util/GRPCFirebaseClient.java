package org.roguewave.grpc.util;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableList;
import com.google.firestore.v1beta1.*;
import io.grpc.*;
import com.google.firestore.v1beta1.FirestoreGrpc.FirestoreStub;
import com.google.firestore.v1beta1.FirestoreGrpc.FirestoreBlockingStub;
import io.grpc.auth.MoreCallCredentials;

public class GRPCFirebaseClient {

    public FirestoreStub firestoreStub;
    public FirestoreBlockingStub blockingStub;
    static final ImmutableList<String> REQURIED_SCOPES = ImmutableList.of( "https://www.googleapis.com/auth/cloud-platform", "https://www.googleapis.com/auth/datastore");

    public GRPCFirebaseClient() {
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

        firestoreStub = FirestoreGrpc.newStub(channel).withCallCredentials(MoreCallCredentials.from(creds));
        blockingStub = FirestoreGrpc.newBlockingStub(channel).withCallCredentials(MoreCallCredentials.from(creds));
    }

    public FirestoreStub getFirestoreStub() {

        return firestoreStub;

    }

    public FirestoreBlockingStub getBlockingStub() {

        return blockingStub;

    }



}
