package org.roguewave.grpc.util;
import org.roguewave.grpc.apimethods.*;

public class ChooseMethod {

    public void choose(String menuInput) {

        switch (menuInput) {
            case "batchgetdocuments":
            case "1":
                BatchGetDocuments bgd = new BatchGetDocuments();
                bgd.batchGetDocumentsCall();
                break;
            case "begintransaction":
            case "2":
                BeginTransaction bt = new BeginTransaction();
                bt.beginTransactionCall();
                break;
            case "commit":
            case "3":
                Commit c = new Commit();
                c.commitCall();
                break;
            case "createdocument":
            case "4":
                CreateDocument cd = new CreateDocument();
                cd.createDocumentCall();
                break;
            case "deletedocument":
            case "5":
                DeleteDocument dd = new DeleteDocument();
                dd.deleteDocumentCall();
                break;
            case "getdocument":
            case "6":
                GetDocument gd = new GetDocument();
                gd.getDocumentCall();
                break;
            case "listcollectionids":
            case "7":
                ListCollectionIds lci = new ListCollectionIds();
                lci.listCollectionIdsCall();
                break;
            case "listdocuments":
            case "8":
                ListDocuments ld = new ListDocuments();
                ld.listDocumentsCall();
                break;
            case "rollback":
            case "9":
                Rollback r = new Rollback();
                r.rollbackCall();
                break;
            case "runquery":
            case "10":
                RunQuery rq = new RunQuery();
                rq.runQueryCall();
                break;
            case "updatedocument":
            case "11":
                UpdateDocument ud = new UpdateDocument();
                ud.updateDocumentCall();
                break;
            case "write":
            case "12":
                //TODO Call write
                break;
            case "createindex":
            case "13":
                CreateIndex ci = new CreateIndex();
                ci.createIndexCall();
                break;
            case "deleteindex":
            case "14":
                DeleteIndex di = new DeleteIndex();
                di.deleteIndexCall();
                break;
            case "getindex":
            case "15":
                GetIndex gi = new GetIndex();
                gi.getIndexCall();
                break;
            case "listindexes":
            case "16":
                ListIndexes li = new ListIndexes();
                li.listIndexesCall();
                break;
        }

    }

}
