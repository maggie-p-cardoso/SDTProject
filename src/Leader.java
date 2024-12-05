import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class Leader extends UnicastRemoteObject implements SystemInterface {
    private static final String     MULTICAST_ADDRESS = "230.0.0.0";
    private static final int        PORT = 4446;
    private Map<Integer, String>    documentosAtualizados = new HashMap<>();
    private Map<Integer, String>    documentosPendentes = new HashMap<>();
    private Map<String, Long>       ultimoHeartbeat = new HashMap<>();
    private Map<String, Timer>      nodeTimers = new HashMap<>();
    private Set<String>             nodesRegistados = new HashSet<>();
    private Set<String>             ackNodes = new HashSet<>();
    private boolean                 atualizacaoConfirmada = false;
    private int                     totalNodes = 3;


    // Construtor do Leader
    public Leader() throws RemoteException {
        super();

        Timer timerVerificacao = new Timer();
        timerVerificacao.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                verificarNosInativos();
            }
        }, 0, 10000);
       }


    @Override
    public void registarNo(String nodeId) throws RemoteException {
        if (!nodesRegistados.contains(nodeId)) {
            nodesRegistados.add(nodeId);
        }
    }

    // Metodo para remover um nó do sistema
    private void removerNo(String nodeId) {
        nodesRegistados.remove(nodeId);
        nodeTimers.remove(nodeId);
        System.out.println("Nó " + nodeId + " removido do sistema.");
    }

    // Metodo para enviar mensagem de shutdown para um nó
    private void enviarMensagemShutdown(String nodeId) {
        try {
            DatagramSocket socket = new DatagramSocket();
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            String mensagem = "SHUTDOWN: " + nodeId;
            byte[] buffer = mensagem.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
            socket.send(packet);
            System.out.println("Mensagem de shutdown enviada para o nó " + nodeId);
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void enviarMensagem(String mensagem) throws RemoteException {
        String[] parts = mensagem.split(";", 2); // Separar "ID;Conteúdo"
        if (parts.length < 2) {
            System.out.println("Mensagem inválida recebida.");
            return;
        }

        int docId = Integer.parseInt(parts[0]);
        String conteudo = parts[1];

        if (!documentosAtualizados.containsKey(docId)) {
            // Documento ainda não foi atualizado, adiciona aos pendentes
            documentosPendentes.put(docId, conteudo);
            System.out.println("Documento pendente adicionado: ID = " + docId + ", Conteúdo = " + conteudo);

            // Enviar atualização para os nós
            enviarAtualizacaoParaNos();
        } else {
            System.out.println("Documento com ID = " + docId + " já foi atualizado.");
        }
    }

    @Override
    public String listarDocumentosAtualizados() throws RemoteException {
        StringBuilder builder = new StringBuilder();

        documentosAtualizados.forEach((id, conteudo) ->
                builder.append("ID: ").append(id).append(", Conteúdo: ").append(conteudo).append("\n"));
        return builder.toString();
    }

    @Override
    public void verificarHeartbeats(String nodeId) throws RemoteException {
        if (!nodesRegistados.contains(nodeId)) {
            System.out.println("HEARTBEAT recebido de nó não registado: " + nodeId + ". Ignorado.");
            return;
        }

        ultimoHeartbeat.put(nodeId, System.currentTimeMillis()); // Atualiza o último heartbeat

        // Reinicia o timer do nó sempre que receber um heartbeat
        if (nodeTimers.containsKey(nodeId)) {
            nodeTimers.get(nodeId).cancel();
        }

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("Nó " + nodeId + " não respondeu a tempo. A remover...");
                removerNo(nodeId);
                enviarMensagemShutdown(nodeId);
            }
        }, 30000); // Timeout de 30 segundos

        nodeTimers.put(nodeId, timer);
        ackNodes.add(nodeId); // Armazena o nó que enviou o HEARTBEAT

//        // Verifica se todos os nós enviaram HEARTBEAT
        if (ackNodes.size() >= totalNodes) {
            atualizacaoConfirmada = true;
            ackNodes.clear(); // Limpa os ACKs para a próxima atualização
        }
    }


    // Envia atualização para os nós via multicast
    private void enviarAtualizacaoParaNos() {
        try {
            DatagramSocket socket = new DatagramSocket();
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);

            // Mensagem com os documentos pendentes
            StringBuilder mensagem = new StringBuilder("PENDENTES;");
            documentosPendentes.forEach((id, conteudo) ->
                    mensagem.append(id).append(":").append(conteudo).append(";"));

            byte[] buffer = mensagem.toString().getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
            socket.send(packet);
            System.out.println("Líder enviou documentos pendentes via multicast: " + mensagem);
            socket.close();
            atualizacaoConfirmada = false;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Recebe ACKs dos nós via RMI
    @Override
    public synchronized void receberACK(String nodeId) throws RemoteException {
        if (!atualizacaoConfirmada) {
            ackNodes.add(nodeId);
            System.out.println("Líder recebeu ACK do nó " + nodeId);

            // Verificar se a maioria dos nós enviaram ACK
            if (ackNodes.size() > totalNodes / 2) {
                System.out.println("A maioria dos nós confirmaram a atualização. A enviar commit...");
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

            // Atualizar os documentos pendentes como atualizados
            for (Map.Entry<Integer, String> entry : documentosPendentes.entrySet()) {
                documentosAtualizados.put(entry.getKey(), entry.getValue());
            }
            documentosPendentes.clear(); // Limpa a lista de pendentes

            atualizacaoConfirmada = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Envia a lista das atualizações ao novo nó
    @Override
    public String solicitarListaAtualizacoes(String nodeId) throws RemoteException {
        System.out.println("Líder recebeu solicitação de sincronização do nó " + nodeId);

        // Lista com todos os documentos atualizados
        StringBuilder listaAtualizada = new StringBuilder();
        documentosAtualizados.forEach((id, conteudo) ->
                listaAtualizada.append(id).append(":").append(conteudo).append(";"));

        return listaAtualizada.toString();
    }


    public void notificarFalha(String nodeId) throws RemoteException {
        if (nodesRegistados.contains(nodeId)) {
            nodesRegistados.remove(nodeId); // Remove o nó da lista de ativos
            System.out.println("Nó " + nodeId + " foi removido da lista de nós ativos.");
        } else {
            System.out.println("Nó " + nodeId + " já não estava ativo.");
        }
    }


    private void verificarNosInativos() {
        long timeout = 30000; // 30 segundos
        long agora = System.currentTimeMillis();

        for (String nodeId : new HashSet<>(nodesRegistados)) {
            if (ultimoHeartbeat.containsKey(nodeId) && (agora - ultimoHeartbeat.get(nodeId) > timeout)) {
                System.out.println("Nó " + nodeId + " não respondeu a tempo. A remover...");
                removerNo(nodeId);
                enviarMensagemShutdown(nodeId);
            }
        }
    }
}