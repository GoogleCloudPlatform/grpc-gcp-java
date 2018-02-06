package org.roguewave.grpc.apimethods;

import com.google.firestore.v1beta1.*;
import com.google.firestore.v1beta1.FirestoreGrpc.FirestoreBlockingStub;
import com.google.firestore.v1beta1.FirestoreGrpc.FirestoreStub;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import org.roguewave.grpc.util.GRPCFirebaseClientFactory;
import java.util.Scanner;

public class Write {

    public static ByteString streamToken;
    public static String streamId = "";

    public void writeCall() {

        System.out.println(":: Starting Write Stream ::");
        FirestoreBlockingStub blockingStub = new GRPCFirebaseClientFactory().createFirebaseClient().getBlockingStub();
        FirestoreStub firestoreStub = new GRPCFirebaseClientFactory().createFirebaseClient().getFirestoreStub();
        Scanner sc = new Scanner(System.in);

        System.out.print("Enter document name: ");
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

        // Retrieve initial stream token and stream id

        WriteRequest writeRequest = WriteRequest.newBuilder()

                .setDatabase("projects/firestoretestclient/databases/(default)")
                .build();

        StreamObserver<WriteResponse> writeResponseStreamObserver = new StreamObserver<WriteResponse>() {
            @Override
            public void onNext(WriteResponse writeResponse) {
                Write.streamToken = writeResponse.getStreamToken();
                Write.streamId = writeResponse.getStreamId();
            }

            @Override
            public void onError(Throwable throwable) {
                System.out.println(throwable.getMessage() + throwable.getCause());
            }

            @Override
            public void onCompleted() {

            }
        };

        StreamObserver<WriteRequest> writeRequestStreamObserver = firestoreStub.write(writeResponseStreamObserver);

        writeRequestStreamObserver.onNext(writeRequest);

        String fieldName = "";
        String fieldValue = "";

        // Begin streaming write loop

        while (! fieldName.matches("DONE")) {

            System.out.print("Field Name (Enter DONE to quit): ");
            fieldName = sc.next();

            if (! fieldName.matches("DONE")) {

                System.out.print("Field Value: ");
                fieldValue = sc.next();

                Value fsValue = Value.newBuilder()
                        .setStringValue(fieldValue)
                        .build();

                doc = doc.toBuilder()
                        .putFields(fieldName, fsValue)
                        .build();

                DocumentMask docMask = DocumentMask.newBuilder()
                        .addFieldPaths(fieldName)
                        .build();

                com.google.firestore.v1beta1.Write currentWrite = com.google.firestore.v1beta1.Write.newBuilder()
                        .setUpdate(doc)
                        .setUpdateMask(docMask)
                        .build();

                WriteRequest currentWriteRequest = WriteRequest.newBuilder()
                        .setDatabase("projects/firestoretestclient/databases/(default)")
                        .setStreamToken(Write.streamToken)
                        .setStreamId(Write.streamId)
                        .addWrites(currentWrite)
                        .build();

                writeRequestStreamObserver.onNext(currentWriteRequest);

            }

        }

        writeRequestStreamObserver.onCompleted();

        System.out.println("Finished streaming writes!");

    }

}
