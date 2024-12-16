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
    private Map<String, Long>       ultimoACK = new HashMap<>();
    private Map<String, Timer>      nodeTimers = new HashMap<>();
    private Set<String>             nodesRegistados = new HashSet<>();
    private Set<String>             ackNodes = new HashSet<>();
    private boolean                 atualizacaoConfirmada = false;
    private int                     totalNodes = 3;


    // Construtor do Leader
    public Leader() throws RemoteException {
        super();

        Timer heartbeatTimer = new Timer();
        heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                enviarHeartbeatParaNos();
            }
        }, 0, 5000);
    }

    private void enviarHeartbeatParaNos() {
        try {
            DatagramSocket socket = new DatagramSocket();
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);

            String mensagem = "HEARTBEAT";
            byte[] buffer = mensagem.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
            socket.send(packet);

            System.out.println("Líder enviou heartbeat para os nós.");
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public void registarNo(String nodeId) throws RemoteException {
        if (!nodesRegistados.contains(nodeId)) {
            nodesRegistados.add(nodeId);
            atualizarTotalNodes();
            System.out.println("Nó " + nodeId + " registrado no líder com sucesso.");
        }else {
            System.out.println("Nó já registrado: " + nodeId);
        }
    }

    public synchronized void atualizarTotalNodes() {
        totalNodes = nodesRegistados.size();

    }
    // Metodo para remover um nó do sistema
    private void removerNo(String nodeId) {
        nodesRegistados.remove(nodeId);
        //nodeTimers.remove(nodeId);
        Timer timer = nodeTimers.remove(nodeId);
        if (timer != null) {
            timer.cancel();
        }
        System.out.println("Nó " + nodeId + " removido do sistema.");
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
    public void processarACK(String nodeId) throws RemoteException {
        if (!nodesRegistados.contains(nodeId)) {
            return;
        }

        ultimoACK.put(nodeId, System.currentTimeMillis()); // Atualiza o último ack

        // Reinicia o timer do nó sempre que receber um ack
        if (nodeTimers.containsKey(nodeId)) {
            nodeTimers.get(nodeId).cancel();
        }

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
           @Override
           public void run() {
               System.out.println("Nó " + nodeId + " não respondeu a tempo. A remover...");
               removerNo(nodeId);
            }
        }, 30000);

        nodeTimers.put(nodeId, timer);
        ackNodes.add(nodeId); // Armazena o nó que enviou o ACK

        // Verifica se todos os nós enviaram ACK
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


}