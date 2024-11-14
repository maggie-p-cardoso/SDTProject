package Sprint_1;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DistributedNode {
    private final Elemento elemento; // Usando Sprint_1.Elemento para representar o nó
    private String documentContent;
    private List<String> otherNodes;
    private List<String> messageList;
    private static final String MULTICAST_GROUP = "230.0.0.0";
    private static final int MULTICAST_PORT = 4446;
    private static final int SYNC_PORT = 8080;
    private boolean transmissionStarted = false;
    private boolean firstTransmission = true;

    public DistributedNode(Elemento elemento, String initialContent, List<String> otherNodes) {
        this.elemento = elemento;
        this.documentContent = initialContent;
        this.otherNodes = otherNodes;
        this.messageList = new ArrayList<>();
    }

    public void start() {
        if (elemento.isLider() && !transmissionStarted) {
            sendTransmitter();
            transmissionStarted = true; // Define como true após a primeira execução
        } else {
            recieveMessage(); // Somente os nós que não são líderes se inscrevem para receber
        }
    }

    private void sendTransmitter() {
        System.out.println("Iniciando transmissão no líder: " + elemento.getNodeId());
        new Thread(() -> {
            while (true) {
                try {

                    String transmitterMessage = "Uma frase";
                    messageList.add(transmitterMessage);
                    System.out.println("Thread de transmissão feita pelo no -> " + elemento.getNodeId() + " com a mensagem:(" + transmitterMessage + ")");
                    sendMessage(transmitterMessage);
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void sendMessage(String message) {
        try (MulticastSocket socket = new MulticastSocket()) {
            byte[] buffer = message.getBytes(StandardCharsets.UTF_8);
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, MULTICAST_PORT);
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void recieveMessage() {
        new Thread(() -> {
            try (MulticastSocket socket = new MulticastSocket(MULTICAST_PORT)) {
                InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
                socket.joinGroup(group);
                byte[] buffer = new byte[256];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String received = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    System.out.println(elemento.getNodeId() + " recebeu a mensagem: " + received);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void updateDocument(String newContent) {
        this.documentContent = newContent;
        if (elemento.isLider()) {
            sendMessage(newContent);
        }
    }
}
