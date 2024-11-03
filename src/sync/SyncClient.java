package sync;

import java.io.PrintWriter;
import java.net.Socket;

public class SyncClient {
    public static void sendUpdate(String nodeAddress, String content) throws Exception {
        try (Socket socket = new Socket(nodeAddress, 8080);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println(content);
            System.out.println("Atualização enviada para " + nodeAddress + ": " + content);
        }
    }
}

