package sync;

import main.DistributedNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class SyncServer implements Runnable {
    private final int port;
    private final DistributedNode node;

    public SyncServer(int port, DistributedNode node) {
        this.port = port;
        this.node = node;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            System.out.println("Servidor de sincronização iniciado na porta " + port);

            while (true) {
                try (Socket clientSocket = serverSocket.accept()) {
                    System.out.println("Conexão recebida de " + clientSocket.getInetAddress().getHostAddress());

                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    String newContent = in.readLine();

                    if(newContent != null) {
                        node.updateDocument(newContent);
                        System.out.println("Documento atualizado: " + newContent);
                    } else {
                        System.out.println("Sem atualizações de documento");
                    }
                } catch (IOException e) {
                    System.out.println("Erro ao processar cliente: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.out.println("Erro no servidor de sincronização: " + e.getMessage());
        }
    }
}
