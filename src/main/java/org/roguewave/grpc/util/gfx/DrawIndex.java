package org.roguewave.grpc.util.gfx;

import com.google.firestore.admin.v1beta1.Index;
import com.google.firestore.admin.v1beta1.IndexField;

import java.util.List;

public class DrawIndex {

    public void drawIndex(Index index) {

        System.out.println("Index Name: " + index.getName() + "\nIndex State: " + index.getState());

        List<IndexField> indexFieldList = index.getFieldsList();
        for (IndexField field : indexFieldList) {

            System.out.println("   Field: " + field.getFieldPath() + "   Mode: " + field.getMode().toString());

        }


    }

}
