package Sprint_2;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.rmi.Naming;

public class Node implements Runnable {
    private static final String MULTICAST_ADDRESS = "230.0.0.0";
    private static final int PORT = 4446;
    private String id;
    private String conteudoAtual = "Conteúdo inicial do documento";
    private String conteudoPendente; // Conteúdo pendente que será aplicado após o commit
    private SystemInterface leader;

    public Node(String id) {
        this.id = id;
        try {
            leader = (SystemInterface) Naming.lookup("rmi://localhost/Sprint_2.Leader");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try (MulticastSocket socket = new MulticastSocket(PORT)) {
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            socket.joinGroup(group);
            System.out.println("Nó " + id + " entrou no grupo multicast e está aguardando atualizações...");

            while (true) {
                byte[] buffer = new byte[256];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String mensagem = new String(packet.getData(), 0, packet.getLength());
                if (mensagem.equals("COMMIT")) {
                    // Recebeu o commit, aplica o conteúdo pendente
                    System.out.println("Nó " + id + " recebeu o commit. Aplicando atualização...");
                    conteudoAtual = conteudoPendente;
                    System.out.println("Nó " + id + " - Conteúdo atualizado para: " + conteudoAtual);

                    // Envia ACK final para o líder, se necessário
                    leader.enviarACK(id);
                } else {
                    // Recebeu a atualização, mas aguarda o commit
                    System.out.println("Nó " + id + " recebeu atualização: " + mensagem);
                    System.out.println("Nó " + id + " - Conteúdo antes da atualização: " + conteudoAtual);
                    conteudoPendente = mensagem; // Guarda a atualização pendente

                    // Envia ACK para o líder indicando que recebeu a atualização
                    leader.enviarACK(id);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
