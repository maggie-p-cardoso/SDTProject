import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.rmi.Naming;
import java.util.*;

public class Node implements Runnable {
    private List<String>                mensagens           = new ArrayList<>();
    private HashMap<Integer, String>    documentosPendentes = new HashMap<>();
    private HashMap<Integer, String>    documentosAtuais    = new HashMap<>();
    private static final String         MULTICAST_ADDRESS   = "230.0.0.0";
    private static final int            PORT                = 4446;
    private boolean                     joinedGroup         = false;
    private boolean                     ativo;
    private Timer                       nodeTimer;
    private String                      id;
    private SystemInterface             leader;


    //Construtor do Node
    public Node(String id) {
        this.id = id;
        this.ativo = true;
        try {
            leader = (SystemInterface) Naming.lookup("rmi://localhost/Leader");
            leader.registarNo(id);
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
                System.out.println("Nó " + id + " sincronizou com o líder.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void confirmarAtividade() {
        try {
            if (ativo) {
                leader.processarACK(id);
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

            // Inicia o temporizador para enviar acks ao líder
            nodeTimer = new Timer();
            nodeTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    confirmarAtividade();
                }
            }, 0, 10000);

            while (ativo) {
                try {

                    byte[] buffer = new byte[256];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                    // Define um timeout para garantir que o loop verifica o estado ativo periodicamente
                    socket.setSoTimeout(1000); // Timeout de 1 segundo
                    socket.receive(packet);
                    String mensagem = new String(packet.getData(), 0, packet.getLength());

                    if (mensagem.equals("HEARTBEAT")) {
                        System.out.println("Nó " + id + " recebeu heartbeat do líder.");
                    } else if (mensagem.startsWith("PENDENTES;")) {
                        processarPendentes(mensagem);
                    } else if (mensagem.equals("COMMIT")) {
                        aplicarCommit();
                    }
                } catch (SocketTimeoutException e) {
                    // Timeout é esperado, apenas permite verificar o estado 'ativo'
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void processarPendentes(String mensagem) {
        if(ativo) {
            System.out.println("Nó " + id + " recebeu documentos pendentes.");
            mensagens.add("Nó " + id + " recebeu atualização pendente às " + System.currentTimeMillis());

            String[] partes = mensagem.split(";");
            for (int i = 1; i < partes.length; i++) { // Ignora a tab "PENDENTES"
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
            enviarACK();
        }
    }


    // Envia ACK ao líder
    private void enviarACK() {
        if (!ativo) {
            System.out.println("Nó " + id + " está a simular falha. Não enviará ACK.");
            return;
        }

        try {
            leader.receberACK(id);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Aplica o commit, movendo os documentos pendentes para os atuais
    private void aplicarCommit() {
        if (!ativo) {
            System.out.println("Nó " + id + " não está ativo. Ignorando COMMIT...");
            return;
        }

        System.out.println("Nó " + id + " recebeu o COMMIT. Aplicando atualizações...");
        mensagens.add("Nó " + id + " recebeu COMMIT às " + System.currentTimeMillis());

        documentosPendentes.forEach(documentosAtuais::put);
        documentosPendentes.clear();

        mensagens.add("Nó " + id + " aplicou o COMMIT e atualizou os seus documentos.");
    }
}