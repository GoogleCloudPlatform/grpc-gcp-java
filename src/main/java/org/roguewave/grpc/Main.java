package org.roguewave.grpc;
import com.google.protobuf.ByteString;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.roguewave.grpc.util.gfx.Menu;

public class Main  {

    public static ByteString transactionId;

    public static void main(String[] args) {

        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);

        Menu m = new Menu();
        m.draw();

    }

}

