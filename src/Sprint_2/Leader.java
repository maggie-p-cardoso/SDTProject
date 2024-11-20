package Sprint_2;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashSet;
import java.util.Set;

public class Leader extends UnicastRemoteObject implements SystemInterface {
    private static final String MULTICAST_ADDRESS = "230.0.0.0"; // Endereço IP do multicast para enviar mensagens para os nós
    private static final int PORT = 4446; // Porta para qual as mensagens serão enviadas
    private String conteudoAtual = "Conteúdo inicial do documento";
    private Set<String> ackNodes = new HashSet<>(); // Armazena os ids dos nós que vão enviar ACKS
    private int totalNodes = 3; // Número total de nós
    private boolean atualizacaoConfirmada = false; // Variável que controla a confirmação dos nós

    // Construtor do Leader
    public Leader() throws RemoteException {
        super();
    }

    // Verifica se recebeu o heartbeat dos nós
    public void verificarHeartbeats(String nodeId) throws RemoteException {
        if (!atualizacaoConfirmada) {
            System.out.println("Líder recebeu HEARTBEAT do nó: " + nodeId);
            ackNodes.add(nodeId); // Armazena os nós que enviaram resposta

            // Verificar se todos os nós enviaram HEARTBEAT
            if (ackNodes.size() >= totalNodes) {
                System.out.println("Todos os nós responderam ao HEARTBEAT");

                atualizacaoConfirmada = true;
                ackNodes.clear(); // Limpa os ACKs
            }
        } else {
            System.out.println("Aguardando a resposta dos heartbeats dos outros nós.");
        }
    }

    // Cliente solicita uma atualização via RMI
    @Override
    public void enviarMensagem(String mensagem) throws RemoteException {
        if (!atualizacaoConfirmada) {
            System.out.println("Líder recebeu mensagem do cliente: " + mensagem);
            enviarAtualizacaoParaNos(mensagem);
            atualizacaoConfirmada = false; // Iniciar nova atualização
            ackNodes.clear(); // Limpa o conjunto de ACKs recebidos
        } else {
            System.out.println("Uma atualização já está em progresso. Aguarde a finalização.");
        }
    }

    // Envia atualização para os nós via multicast
    private void enviarAtualizacaoParaNos(String mensagem) {
        try {
            DatagramSocket socket = new DatagramSocket();
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            byte[] buffer = mensagem.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
            socket.send(packet);
            System.out.println("Líder enviou atualização via multicast: " + mensagem);
            socket.close();
            atualizacaoConfirmada = false;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Recebe ACKs dos nós via RMI
    @Override
    public synchronized void enviarACK(String nodeId) throws RemoteException {
        // Verificar se já recebemos todos os ACKs para evitar duplicação
        if (!atualizacaoConfirmada) {
            ackNodes.add(nodeId);
            System.out.println("Líder recebeu ACK do nó: " + nodeId);

            // Verificar se todos os nós enviaram o ACK
            if (ackNodes.size() > totalNodes / 2) {
                System.out.println("A maioria dos nós confirmaram a atualização. Enviando commit...");
                enviarCommitParaNos();
                atualizacaoConfirmada = true; // Marcar atualização como confirmada
                ackNodes.clear(); // Limpar os ACKs para a próxima atualização
            }
        }
    }

    // Envia o commit via multicast para que os nós apliquem a atualização
    private void enviarCommitParaNos() {
        try {
            DatagramSocket socket = new DatagramSocket();
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            String commitMessage = "COMMIT";
            byte[] buffer = commitMessage.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
            socket.send(packet);
            System.out.println("Líder enviou o commit via multicast.");
           atualizacaoConfirmada = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Envia a lista das atualizações ao novo nó que se juntou
    @Override
    public String solicitarListaAtualizacoes(String nodeId) throws  RemoteException {
        System.out.println("Lider recebeu solicitação da sincronização do nó:" + nodeId);
        return  conteudoAtual;
    }
}
