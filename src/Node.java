import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.rmi.Naming;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Node implements Runnable {
    private static final String MULTICAST_ADDRESS = "230.0.0.0"; // Endereço multicast
    private static final int PORT = 4446; // Porta do endereço multicast
    private String id; // ID único de cada nó
    private List<String> mensagens = new ArrayList<>(); // Lista de mensagens
    private String conteudoAtual = "Conteúdo inicial do documento";
    private String conteudoPendente; // Conteúdo pendente que será aplicado após o commit
    private SystemInterface leader; // Referência do objeto Leader, obtido através de RMI
    private HashMap<String, String> mensagensNaoConfirmadas = new HashMap<>();
    private boolean atualizacaoConfirmada = false;


    public Node(String id) {
        this.id = id;
        try {
            leader = (SystemInterface) Naming.lookup("rmi://localhost/Leader");
            // startHeartbeatReceiver(); // Inicia recepção de heartbeats
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Sincroniza o nó com o conteúdo mais recente do Leader
    private void sincronizarcomLider() {
        try {
            String atualizacoesPendentes = leader.solicitarListaAtualizacoes(id); // Usa o metodo remoto SolicitarListaAtualizações para obter as atualizações mais recentes
            if (atualizacoesPendentes != null && !atualizacoesPendentes.isEmpty()) {
                conteudoAtual = atualizacoesPendentes;
                System.out.println("Nó " + id + " " + "sincronizou com as informações mais recentes: " + conteudoAtual);
            }

        } catch (Exception e ) {
            e.printStackTrace();
        }
    }


    public void run() {
        try (MulticastSocket socket = new MulticastSocket(PORT)) {
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            socket.joinGroup(group);

            System.out.println("Nó " + id + " entrou no grupo multicast e está aguardando atualizações...");

            sincronizarcomLider(); // Sincroniza com o leader

            while (true) {
                byte[] buffer = new byte[256];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String mensagem = new String(packet.getData(), 0, packet.getLength());

                // Faz a verificação para saber se recebeu o heartbeat do lider
                if (!atualizacaoConfirmada) {
                    System.out.println("Nó " + id + " recebeu HEARTBEAT do líder.");
                    try {
                        if (id.equals("Node3")) {
                            Thread.sleep(5000);
                        }
                        leader.verificarHeartbeats(id);
                        System.out.println("Nó " + id + " enviou resposta do HEARTBEAT");
                    } catch (Exception e) {
                        System.err.println("Erro ao enviar resposta do HEARTBEAT para o líder: " + e.getMessage());
                    }
                }

                // Faz o commit
                if (mensagem.equals("COMMIT")) {
                    // Recebeu o commit, aplica o conteúdo pendente
                    System.out.println("Nó " + id + " recebeu o commit. Aplicando atualização...");
                    conteudoAtual = conteudoPendente;
                    mensagens.add(conteudoPendente); // Adiciona a nova mensagem à lista
                    System.out.println("Nó " + id + " - Conteúdo atualizado para: " + conteudoAtual);
                    mensagensNaoConfirmadas.put(conteudoPendente, "Confirmada");
                    imprimirMensagensNaoConfirmadas(); //

                } else {

                    // Recebeu a atualização, mas aguarda o commit
                    System.out.println("Nó " + id + " recebeu atualização: " + mensagem);
                    System.out.println("Nó " + id + " - Conteúdo antes da atualização: " + conteudoAtual);
                    conteudoPendente = mensagem; // Guarda a atualização pendente
                    mensagensNaoConfirmadas.put(mensagem, "Pendente");
                    imprimirMensagensNaoConfirmadas();

                    // Envia ACK para o líder indicando que recebeu a atualização
                    leader.enviarACK(id);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Serve de auxilio para verificar o estado das mensagens (Com commit ou estão pendentes)
    private void imprimirMensagensNaoConfirmadas() {
        System.out.println("Estado atual das mensagens não confirmadas para o nó:" + id + ";");
        for (String mensagem : mensagensNaoConfirmadas.keySet()) {
            System.out.println("Mensagem: " + mensagem + " - Status: " + mensagensNaoConfirmadas.get(mensagem));
        }
    }
}
