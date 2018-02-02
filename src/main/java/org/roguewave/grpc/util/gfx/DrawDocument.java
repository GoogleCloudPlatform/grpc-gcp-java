package org.roguewave.grpc.util.gfx;

import com.google.firestore.v1beta1.Document;
import com.google.firestore.v1beta1.Value;

import java.util.Map;

public class DrawDocument {

    public void draw(Document doc) {
        System.out.println("Document Name: " + doc.getName());
        System.out.println("  Fields: ");
        for (Map.Entry<String, Value> fieldsMap : doc.getFieldsMap().entrySet()) {
            System.out.println("    " + fieldsMap.getKey() + " : " + fieldsMap.getValue().getStringValue());
        }
        System.out.println("\n");
    }

}
