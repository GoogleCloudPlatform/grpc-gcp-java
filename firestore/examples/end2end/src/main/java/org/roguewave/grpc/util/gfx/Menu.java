package org.roguewave.grpc.util.gfx;

import org.roguewave.grpc.util.ChooseMethod;

import java.util.Scanner;

public class Menu {

    public void draw() {

        String selection = "";

        while (! selection.matches("quit")) {

            System.out.println("\n     Google Firestore RPC Menu\n");
            System.out.println("1|batchgetdocuments ......... BatchGetDocuments");
            System.out.println("2|begintransaction  ......... BeginTransaction");
            System.out.println("3|commit .................... Commit");
            System.out.println("4|createdocument ............ CreateDocument");
            System.out.println("5|deletedocument ............ DeleteDocument");
            System.out.println("6|getdocument ............... GetDocument");
            System.out.println("7|listcollectionids ......... ListCollectionIds");
            System.out.println("8|listdocuments ............. ListDocuments");
            System.out.println("9|rollback .................. Rollback");
            System.out.println("10|runquery ................. RunQuery");
            System.out.println("11|updatedocument ........... UpdateDocument");
            System.out.println("12|write .................... Write");
            System.out.println("\n     Firestore Admin RPC's         \n");
            System.out.println("13|createindex .............. CreateIndex");
            System.out.println("14|deleteindex .............. DeleteIndex");
            System.out.println("15|getindex ................. GetIndex");
            System.out.println("16|listindexes .............. ListIndex");
            System.out.print("\n\nEnter an option ('quit' to exit): ");

            Scanner sc = new Scanner(System.in);
            selection = sc.next();

            ChooseMethod cm = new ChooseMethod();

            if (! selection.matches("quit")) {
                cm.choose(selection);
            }
        }

    }

}
