import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class Leader extends UnicastRemoteObject implements SystemInterface {
    private static final String MULTICAST_ADDRESS = "230.0.0.0"; // Endereço IP do multicast para enviar mensagens para os nós
    private static final int PORT = 4446; // Porta para qual as mensagens serão enviadas
    private Map<Integer, String> documentosAtualizados = new HashMap<>(); // ID -> Documento Atualizado
    private Map<Integer, String> documentosPendentes = new HashMap<>();   // ID -> Documento Pendente

    private Set<String> ackNodes = new HashSet<>(); // Armazena os ids dos nós que vão enviar ACKS
    private Map<String, Long> lastHeartbeatTimes = new HashMap<>(); // Armazenar o último tempo de heartbeat de cada nó
    private int totalNodes = 3; // Número total de nós
    private boolean atualizacaoConfirmada = false; // Variável que controla a confirmação dos nós
    private static final long MAX_INACTIVITY_TIME = 10000; // 10 segundos de inatividade máxima

    // Construtor do Leader
    public Leader() throws RemoteException {
        super();
        documentosAtualizados.put(1, "documento inicial");
        System.out.println("Líder inicializado com o documento ID=1, Conteúdo='documento inicial'.");
    }

    // Cliente solicita uma atualização via RMI
    @Override
    public synchronized void enviarMensagem(String mensagem) throws RemoteException {
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
            System.out.println("Documento pendente adicionado: ID=" + docId + ", Conteúdo=" + conteudo);

            // Enviar atualização para os nós
            enviarAtualizacaoParaNos();
        } else {
            System.out.println("Documento com ID=" + docId + " já foi atualizado.");
        }
    }

    @Override
    public void verificarHeartbeats(String nodeId) throws RemoteException {
        long currentTime = System.currentTimeMillis();
        lastHeartbeatTimes.put(nodeId, currentTime); // Atualiza o último tempo de heartbeat do nó

        System.out.println("Líder recebeu HEARTBEAT do nó: " + nodeId);

        // Verifica se o nó está ativo ou não
        removerNodosInativos();

        ackNodes.add(nodeId);

        if (ackNodes.size() >= totalNodes) {
            System.out.println("Todos os nós enviaram HEARTBEAT. Atualização confirmada.");
            atualizacaoConfirmada = true;
            ackNodes.clear(); // Limpa os ACKs para a próxima rodada de atualizações
        } else {
            System.out.println("Aguardando mais HEARTBEATs. Total recebido: " + ackNodes.size());
        }
    }

    // Método para remover nós inativos
    private void removerNodosInativos() {
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> iterator = lastHeartbeatTimes.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            String nodeId = entry.getKey();
            long lastHeartbeatTime = entry.getValue();

            // Se o nó estiver inativo há mais de MAX_INACTIVITY_TIME (ex: 10 segundos), removê-lo
            if (currentTime - lastHeartbeatTime > MAX_INACTIVITY_TIME) {
                System.out.println("Nó " + nodeId + " removido por inatividade.");
                iterator.remove(); // Remove o nó do mapa de heartbeats
                ackNodes.remove(nodeId); // Remove o nó do conjunto de ACKs também
            }
        }
    }

    // Envia atualização para os nós via multicast
    private void enviarAtualizacaoParaNos() {
        try {
            DatagramSocket socket = new DatagramSocket();
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);

            // Montar a mensagem com os documentos pendentes
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
    public synchronized void enviarACK(String nodeId) throws RemoteException {
        if (!atualizacaoConfirmada) {
            ackNodes.add(nodeId);
            System.out.println("Líder recebeu ACK do nó: " + nodeId);

            // Verificar se a maioria dos nós enviaram ACK
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

            // Atualizar os documentos pendentes como atualizados
            for (Map.Entry<Integer, String> entry : documentosPendentes.entrySet()) {
                documentosAtualizados.put(entry.getKey(), entry.getValue());
                System.out.println("Documento atualizado: ID=" + entry.getKey() + ", Conteúdo=" + entry.getValue());
            }
            documentosPendentes.clear(); // Limpa a lista de pendentes

            atualizacaoConfirmada = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Envia a lista das atualizações ao novo nó que se juntou
    @Override
    public String solicitarListaAtualizacoes(String nodeId) throws RemoteException {
        System.out.println("Líder recebeu solicitação de sincronização do nó: " + nodeId);

        // Construir uma lista com todos os documentos atualizados
        StringBuilder listaAtualizada = new StringBuilder();
        documentosAtualizados.forEach((id, conteudo) ->
                listaAtualizada.append(id).append(":").append(conteudo).append(";"));

        return listaAtualizada.toString();
    }

    @Override
    public synchronized String listarDocumentosAtualizados() throws RemoteException {
        StringBuilder builder = new StringBuilder();

        documentosAtualizados.forEach((id, conteudo) ->
                builder.append("ID: ").append(id).append(", Conteúdo: ").append(conteudo).append("\n"));
        return builder.toString();
    }
}
