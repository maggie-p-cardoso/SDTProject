import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.*;
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
    private Timer                       lidervivo;
    private boolean liderAtivo = true;


    private void configurarGestaoDeHeartbeats() {
        lidervivo = new Timer();
        lidervivo.schedule(new TimerTask() {
            @Override
            public void run() {
               // System.out.println("Falha detectada: Líder não enviou heartbeat a tempo!");
                liderFalhou();
            }
        }, 15000); // Timeout de 15 segundos
    }

    private void liderFalhou() {
        System.out.println("Nó " + id + " detetou que o líder falhou.");
        liderAtivo = false;


        String voto = "Nó " + id + " votou: Nó " + id; // Exemplo de voto

        try (Socket socket = new Socket("localhost", 5000); // Conecta ao ServerSetup
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)) {
            out.println(voto);
        } catch (Exception e) {
            System.err.println("Erro ao enviar voto ao ServerSetup.");
            e.printStackTrace();
        }
    }

    public Node(String id) {
        this.id = id;
        this.ativo = true;

        while (leader == null) {
            try {
                leader = (SystemInterface) Naming.lookup("rmi://localhost/Leader");
                System.out.println("Nó " + id + " conectado ao líder com sucesso.");
            } catch (Exception e) {
                System.err.println("Erro ao conectar ao líder. Tentando novamente em 5 segundos...");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }

        verificarLiderPeriodicamente(); // Monitorar reconexão com o líder
    }

    private void verificarLiderPeriodicamente() {
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!liderAtivo || leader == null) {
                    try {
                        leader = (SystemInterface) Naming.lookup("rmi://localhost/Leader");
                        liderAtivo = true;
                        System.out.println("Nó " + id + " conectado ao novo líder com sucesso.");
                        leader.registarNo(id);
                        reiniciarConexaoMulticast();
                    } catch (Exception e) {
                        System.err.println("Erro ao conectar ao líder. Líder ainda está offline.");
                        liderAtivo = false;
                    }
                }
            }
        }, 0, 5000); // Verifica a cada 5 segundos
    }

    private void reiniciarConexaoMulticast() {
        try {
            System.out.println("Nó " + id + " a reinicar conexão multicast...");
            MulticastSocket socket = new MulticastSocket(PORT);
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            socket.joinGroup(group);
            System.out.println("Nó " + id + " entrou no grupo multicast com sucesso.");
        } catch (Exception e) {
            System.err.println("Erro ao reiniciar conexão multicast.");
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
        if (!liderAtivo) {
            System.out.println("Líder está offline. Nó " + id + " não enviará ACK.");
            return;
        }
        try {
            if (leader != null) {
                leader.processarACK(id);
                System.out.println("Nó " + id + " enviou ACK ao líder.");
            }
        } catch (Exception e) {
            liderAtivo = false; // Atualiza o estado do líder como inativo
        }
    }


    public void run() {
        try (MulticastSocket socket = new MulticastSocket(PORT)) {
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            socket.joinGroup(group);
            joinedGroup = true; // Entrada do nó no grupo

            System.out.println("Nó " + id + " entrou no grupo multicast e está a aguardar atualizações...");

            sincronizarComLider(); // Sincroniza com o líder ao inicializar

            configurarGestaoDeHeartbeats(); // Configura o timer de heartbeat

            // Inicia o timer para enviar ACKs ao líder
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

                    socket.receive(packet);

                    String mensagem = new String(packet.getData(), 0, packet.getLength());
                    if (mensagem.equals("HEARTBEAT")) {
                        System.out.println("Nó " + id + " recebeu heartbeat do líder.");
                        liderAtivo = true; // Marca o líder como ativo
                        if (lidervivo != null) {
                            lidervivo.cancel(); // Cancela o timer atual
                            configurarGestaoDeHeartbeats(); // Reinicia o timer
                        }
                    } else if (mensagem.startsWith("PENDENTES;")) {
                        System.out.println("Nó " + id + " recebeu mensagens PENDENTES do líder.");
                        processarPendentes(mensagem);
                    } else if (mensagem.equals("COMMIT")) {
                        System.out.println("Nó " + id + " recebeu COMMIT do líder.");
                        aplicarCommit();
                    }
                } catch (SocketTimeoutException e) {
                    if (!liderAtivo) {
                        System.out.println("Líder ainda está offline. Nó " + id + " está a aguardar...");
                    }
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