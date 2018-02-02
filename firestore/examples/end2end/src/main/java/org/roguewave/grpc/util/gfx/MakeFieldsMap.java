package org.roguewave.grpc.util.gfx;

import com.google.firestore.v1beta1.Value;

import java.util.HashMap;
import java.util.Scanner;

public class MakeFieldsMap {

    public HashMap<String,Value> makeFieldsMap () {

        String fieldName = "initial";
        String fieldValue = "initial";
        Scanner sc = new Scanner(System.in);
        HashMap<String,Value> inputFields = new HashMap<>();

        while (! fieldName.matches("DONE")) {

            System.out.print("Field Name (Enter DONE to quit): ");
            fieldName = sc.next();

            if (! fieldName.matches("DONE")) {

                System.out.print("Field Value: ");
                fieldValue = sc.next();

                Value fsValue = Value.newBuilder()
                        .setStringValue(fieldValue)
                        .build();

                inputFields.put(fieldName,fsValue);

            }

        }

        return inputFields;

    }


}
