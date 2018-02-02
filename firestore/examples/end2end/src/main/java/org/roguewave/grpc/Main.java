package org.roguewave.grpc;
import com.google.protobuf.ByteString;
import org.roguewave.grpc.util.gfx.Menu;

public class Main  {

    public static ByteString transactionId;

    public static void main(String[] args) {

        Menu m = new Menu();
        m.draw();

    }

}

