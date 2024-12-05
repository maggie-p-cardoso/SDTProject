import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.rmi.Naming;
import java.util.*;

public class Node implements Runnable {
    private List<String>                mensagens           = new ArrayList<>();
    private HashMap<Integer, String>    documentosPendentes = new HashMap<>();
    private HashMap<Integer, String>    documentosAtuais    = new HashMap<>();
    private static final String         MULTICAST_ADDRESS   = "230.0.0.0";
    private static final int            PORT                = 4446;
    private volatile boolean            simularFalha        = false;
    private boolean                     joinedGroup         = false;
    private boolean                     ativo;
    private Timer                       heartbeatTimer;
    private String                      id;
    private SystemInterface             leader;


    //Construtor do Node
    public Node(String id) {
        this.id = id;
        this.ativo = true;
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
                // Regista no histórico de mensagens
                mensagens.add("Nó " + id + " sincronizou com o líder às " + System.currentTimeMillis());
                System.out.println("Nó " + id + " sincronizou com o líder.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void enviarHeartbeat() {
        try {
            if (ativo) {
                leader.verificarHeartbeats(id);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void run() {
        try (MulticastSocket socket = new MulticastSocket(PORT)) {
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            socket.joinGroup(group);
            joinedGroup = true; // Entrada do nó no grupo

            System.out.println("Nó " + id + " entrou no grupo multicast e está a aguardar atualizações...");

            sincronizarComLider(); // Sincroniza com o líder ao inicializar

            // Inicia o temporizador para enviar heartbeats ao líder
            heartbeatTimer = new Timer();
            heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    enviarHeartbeat();
                }
            }, 0, 10000); // Envia heartbeat a cada 10 segundos

            while (ativo) {
                try {
                    if (simularFalha) {
                        System.out.println("Nó " + id + " está a simular falha e atrasará a resposta ao HEARTBEAT.");
                        Thread.sleep(35000); // Atraso propositado para simular falha (35 segundos)
                        continue; // Ignora processamento de mensagens
                    }

                    byte[] buffer = new byte[256];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                    // Define um timeout para garantir que o loop verifica o estado ativo periodicamente
                    socket.setSoTimeout(1000); // Timeout de 1 segundo
                    socket.receive(packet);

                    String mensagem = new String(packet.getData(), 0, packet.getLength());

                    if (mensagem.startsWith("PENDENTES;")) {
                        processarPendentes(mensagem);
                    } else if (mensagem.equals("COMMIT")) {
                        aplicarCommit();
                    } else if (mensagem.equals("SHUTDOWN:" + id)) {
                        desligarNo(socket, group);
                    }
                } catch (java.net.SocketTimeoutException e) {
                    // Timeout é esperado, apenas permite verificar o estado 'ativo'
                }
            }
            socket.leaveGroup(group);
            socket.close();
            System.out.println("Nó " + id + " foi desligado e saiu do grupo multicast.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void processarPendentes(String mensagem) {
        if(ativo) {
            System.out.println("Nó " + id + " recebeu HEARTBEAT com documentos pendentes.");
            mensagens.add("Nó " + id + " recebeu atualização pendente às " + System.currentTimeMillis());

            String[] partes = mensagem.split(";");
            for (int i = 1; i < partes.length; i++) { // Ignora a tag "PENDENTES"
                String[] docInfo = partes[i].split(":");
                if (docInfo.length == 2) {
                    int docId = Integer.parseInt(docInfo[0]);
                    String conteudo = docInfo[1];
                    if (!documentosPendentes.containsKey(docId) && !documentosAtuais.containsKey(docId)) {
                        documentosPendentes.put(docId, conteudo);
                        System.out.println("Nó " + id + " adicionou documento pendente: ID = " + docId + ", Conteúdo = " + conteudo);
                    }
                }
            }

            // Envia ACK ao líder para confirmar o recebimento
            try {
                leader.receberACK(id);
                mensagens.add("Nó " + id + " enviou ACK ao líder.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Aplica o commit, movendo os documentos pendentes para os atuais
    private void aplicarCommit() {
        if (!ativo) {
            System.out.println("Nó " + id + " não está ativo. A ignorar COMMIT...");
            return;
        }
        else {
            System.out.println("Nó " + id + " recebeu o COMMIT. Aplicando atualizações...");
            mensagens.add("Nó " + id + " recebeu COMMIT às " + System.currentTimeMillis());

            // Atualiza os documentos pendentes e move para documentos atuais
            for (int docId : documentosPendentes.keySet()) {
                String conteudo = documentosPendentes.get(docId);
                documentosAtuais.put(docId, conteudo);
            }

            // Limpa a lista de documentos pendentes
            documentosPendentes.clear();
            mensagens.add("Nó " + id + " aplicou o COMMIT e atualizou os seus documentos.");
        }
    }

    //Metodo para desligar o nó
    private void desligarNo(MulticastSocket socket, InetAddress group) {
        System.out.println("Nó " + id + " desligado conforme solicitado pelo líder.");
        ativo = false;

        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
        }

        try {
            // Notifica o líder sobre o estado de falha
            leader.notificarFalha(id);
            System.out.println("Nó " + id + " notificou o líder sobre a sua falha.");
        } catch (Exception e) {
            System.err.println("Falha ao notificar o líder sobre o desligamento do nó " + id);
            e.printStackTrace();
        }

        // Sai do grupo multicast, se necessário
        try {
            if (joinedGroup) {
                socket.leaveGroup(group);
                joinedGroup = false;
                System.out.println("Nó " + id + " saiu do grupo multicast.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}