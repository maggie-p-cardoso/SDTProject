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

    private List<String> mensagens = new ArrayList<>(); // Histórico de mensagens do nó
    private HashMap<Integer, String> documentosPendentes = new HashMap<>(); // Documentos pendentes no nó
    private HashMap<Integer, String> documentosAtuais = new HashMap<>(); // Documentos confirmados no nó
    private SystemInterface leader; // Referência do objeto Leader, obtido através de RMI

    private volatile boolean ativo = true; // Sinalizador para controlar o estado do nó
    private volatile boolean simularFalha = false; // Sinalizador para simular uma falha no nó

    public Node(String id) {
        this.id = id;
        try {
            leader = (SystemInterface) Naming.lookup("rmi://localhost/Leader");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Sincroniza o nó com o conteúdo mais recente do líder
    private void sincronizarComLider() {
        try {
            String atualizacoes = leader.solicitarListaAtualizacoes(id); // Solicita lista de documentos atuais do líder
            if (atualizacoes != null && !atualizacoes.isEmpty()) {
                String[] docs = atualizacoes.split(";"); // Formato esperado: "ID1:Conteudo1;ID2:Conteudo2;..."
                for (String doc : docs) {
                    String[] docInfo = doc.split(":");
                    if (docInfo.length == 2) {
                        int docId = Integer.parseInt(docInfo[0]);
                        String conteudo = docInfo[1];
                        documentosAtuais.put(docId, conteudo); // Atualiza a lista de documentos atuais
                    }
                }
                // Registra no histórico de mensagens
                mensagens.add("Nó " + id + " sincronizou com o líder às " + System.currentTimeMillis());
                System.out.println("Nó " + id + " sincronizou com o líder.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run() {
        try (MulticastSocket socket = new MulticastSocket(PORT)) {
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            socket.joinGroup(group);

            System.out.println("Nó " + id + " entrou no grupo multicast e está aguardando atualizações...");

            sincronizarComLider(); // Sincroniza com o líder ao inicializar

            while (ativo) {
                if (simularFalha) {
                    System.out.println("Nó " + id + " está simulando falha e atrasará a resposta de heartbeat.");
                    Thread.sleep(35000); // Atraso proposital para simular falha (35 segundos)
                }

                byte[] buffer = new byte[256];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String mensagem = new String(packet.getData(), 0, packet.getLength());

                if (mensagem.startsWith("PENDENTES;")) {
                    processarPendentes(mensagem);
                } else if (mensagem.equals("COMMIT")) {
                    aplicarCommit();
                } else if (mensagem.equals("SHUTDOWN:" + id)) {
                    desligarNo(socket, group);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Processa a mensagem de documentos pendentes enviada pelo líder
    private void processarPendentes(String mensagem) {
        System.out.println("Nó " + id + " recebeu heartbeat com documentos pendentes.");
        mensagens.add("Nó " + id + " recebeu atualização pendente às " + System.currentTimeMillis());

        String[] partes = mensagem.split(";");
        for (int i = 1; i < partes.length; i++) { // Ignora a tag "PENDENTES"
            String[] docInfo = partes[i].split(":");
            if (docInfo.length == 2) {
                int docId = Integer.parseInt(docInfo[0]);
                String conteudo = docInfo[1];
                if (!documentosPendentes.containsKey(docId) && !documentosAtuais.containsKey(docId)) {
                    documentosPendentes.put(docId, conteudo);
                    System.out.println("Nó " + id + " adicionou documento pendente: ID=" + docId + ", Conteúdo=" + conteudo);
                }
            }
        }

        // Envia ACK ao líder para confirmar o recebimento
        try {
            leader.enviarACK(id);
            mensagens.add("Nó " + id + " enviou ACK ao líder.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Aplica o commit, movendo os documentos pendentes para os atuais
    private void aplicarCommit() {
        System.out.println("Nó " + id + " recebeu o COMMIT. Aplicando atualizações...");
        mensagens.add("Nó " + id + " recebeu COMMIT às " + System.currentTimeMillis());

        // Atualiza os documentos pendentes e move para documentos atuais
        for (int docId : documentosPendentes.keySet()) {
            String conteudo = documentosPendentes.get(docId);
            documentosAtuais.put(docId, conteudo); // Move para a lista de documentos atuais
        }

        // Limpa a lista de documentos pendentes
        documentosPendentes.clear();
        mensagens.add("Nó " + id + " aplicou o COMMIT e atualizou seus documentos.");
    }

    // Método para simular uma falha no nó, atrasando sua resposta
    public void simularFalha() {
        this.simularFalha = true;
    }

    // Método para desligar o nó
    private void desligarNo(MulticastSocket socket, InetAddress group) {
        System.out.println("Nó " + id + " está sendo desligado conforme solicitado pelo líder.");
        ativo = false; // Sinaliza para o loop principal encerrar

        try {
            socket.leaveGroup(group); // Sai do grupo multicast
            socket.close(); // Fecha o socket
            System.out.println("Nó " + id + " saiu do grupo multicast e foi desligado.");
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.exit(0); // Encerra o processo do nó
    }
}
