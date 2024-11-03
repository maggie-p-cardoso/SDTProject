package multicast;

import main.DistributedNode;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;

public class MulticastHandler implements Runnable {
    private static final String MULTICAST_GROUP = "230.0.0.0"; // Grupo de multicast arbitrário
    private static final int MULTICAST_PORT = 4446;
    private final DistributedNode node;

    public MulticastHandler(DistributedNode node) {
        this.node = node;
    }

    @Override
    public void run() {
        try (MulticastSocket socket = new MulticastSocket(MULTICAST_PORT)) {
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            socket.joinGroup(group);

            if (node.isLeader()) {
                sendHeartbeats(socket, group);
            } else {
                receiveHeartbeats(socket);
            }

            socket.leaveGroup(group);
        } catch (Exception e) {
            System.out.println("Erro no handler de multicast: " + e.getMessage());
        }
    }

    private void sendHeartbeats(MulticastSocket socket, InetAddress group) {
        while (true) {
            try {
                String message = "HEARTBEAT:" + node.getDocumentContent();
                byte[] buffer = message.getBytes(StandardCharsets.UTF_8);
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, MULTICAST_PORT);
                socket.send(packet);
                System.out.println("Líder enviou heartbeat: " + message);
                Thread.sleep(5000); // Envia heartbeat a cada 5 segundos
            } catch (Exception e) {
                System.out.println("Erro ao enviar heartbeat: " + e.getMessage());
            }
        }
    }

    private void receiveHeartbeats(MulticastSocket socket) {
        while (true) {
            try {
                byte[] buffer = new byte[256];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                System.out.println("Heartbeat recebido: " + message);
            } catch (Exception e) {
                System.out.println("Erro ao receber heartbeat: " + e.getMessage());
            }
        }
    }
}

