package main;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class SendTransmitter extends Thread {
    private final DistributedNode node;

    public SendTransmitter(DistributedNode node) {
        this.node = node;
    }

    @Override
    public void run() {
        while (true) {
            try {
                sendMessages(node.getMessageList());
                node.clearMessages(); // Limpa a lista após o envio
                Thread.sleep(5000); // Pausa de 5 segundos
            } catch (InterruptedException e) {
                System.out.println("Erro no transmissor de mensagens: " + e.getMessage());
            }
        }
    }

    private void sendMessages(List<String> messages) {
        if (!messages.isEmpty()) {
            System.out.println("Envio de mensagens: " + messages);
            try (MulticastSocket socket = new MulticastSocket()) {
                InetAddress group = InetAddress.getByName("230.0.0.0");
                for (String message : messages) {
                    byte[] buffer = message.getBytes(StandardCharsets.UTF_8);
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, 4446);
                    socket.send(packet);
                    System.out.println("Mensagem enviada via multicast: " + message);
                }
            } catch (Exception e) {
                System.out.println("Erro ao enviar mensagens via multicast: " + e.getMessage());
                System.out.println("... " + e.getMessage());
            }
        }
    }
}
