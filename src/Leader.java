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
    private static final String MULTICAST_ADDRESS = "230.0.0.0"; // Endereço IP do multicast para enviar mensagens para os nós
    private static final int PORT = 4446; // Porta para qual as mensagens serão enviadas
    private Map<Integer, String> documentosAtualizados = new HashMap<>(); // ID -> Documento Atualizado
    private Map<Integer, String> documentosPendentes = new HashMap<>();   // ID -> Documento Pendente
    private Set<String> ackNodes = new HashSet<>(); // Armazena os ids dos nós que vão enviar ACKS
    private int totalNodes = 3; // Número total de nós
    private boolean atualizacaoConfirmada = false; // Variável que controla a confirmação dos nós
    private Map<String, Timer> nodeTimers = new HashMap<>(); // Armazena temporizadores para cada nó
    private Set<String> nodesRegistrados = new HashSet<>(); // Conjunto de nós registrados

    // Construtor do Leader
    public Leader() throws RemoteException {
        super();
        documentosAtualizados.put(1, "documento inicial");
        System.out.println("Líder inicializado com o documento ID=1, Conteúdo='documento inicial'.");
    }

    @Override
    public void simularFalhaNode(String nodeId) throws RemoteException {
        System.out.println("Líder: Solicitada simulação de falha no nó: " + nodeId);
        if (nodesRegistrados.contains(nodeId)) {
            System.out.println("Simulando falha no nó: " + nodeId);
            // Reinicia o temporizador do nó para simular uma falha atrasando o envio do heartbeat
            if (nodeTimers.containsKey(nodeId)) {
                nodeTimers.get(nodeId).cancel();
            }
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    System.out.println("Nó " + nodeId + " não respondeu a tempo. Removendo-o.");
                    removerNo(nodeId);
                    enviarMensagemShutdown(nodeId);
                }
            }, 35000); // Timeout de 35 segundos para simular a falha

            nodeTimers.put(nodeId, timer);
        } else {
            System.out.println("Nó " + nodeId + " não encontrado.");
        }
    }

    @Override
    public void registrarNo(String nodeId) throws RemoteException {
        if (!nodesRegistrados.contains(nodeId)) {
            nodesRegistrados.add(nodeId);
            System.out.println("Líder: Nó registrado com sucesso: " + nodeId);
        } else {
            System.out.println("Líder: O nó " + nodeId + " já está registrado.");
        }
    }


    // Método para remover um nó do sistema
    private void removerNo(String nodeId) {
        nodesRegistrados.remove(nodeId);
        nodeTimers.remove(nodeId);
        System.out.println("Líder: Nó removido do sistema: " + nodeId);
    }

    // Método para enviar mensagem de desligamento para um nó
    private void enviarMensagemShutdown(String nodeId) {
        System.out.println("Líder: Enviando mensagem de desligamento para o nó: " + nodeId);
        // Lógica para enviar a mensagem de desligamento
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
            System.out.println("Documento pendente adicionado: ID=" + docId + ", Conteúdo=" + conteudo);

            // Enviar atualização para os nós
            enviarAtualizacaoParaNos();
        } else {
            System.out.println("Documento com ID=" + docId + " já foi atualizado.");
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
        if (!nodesRegistrados.contains(nodeId)) {
            System.out.println("Heartbeat recebido de nó não registrado: " + nodeId + ". Ignorando.");
            return;
        }

        System.out.println("Líder recebeu HEARTBEAT do nó: " + nodeId);

        // Reinicia o timer do nó sempre que receber um heartbeat
        if (nodeTimers.containsKey(nodeId)) {
            nodeTimers.get(nodeId).cancel();
        }

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("Nó " + nodeId + " não respondeu a tempo. Removendo-o.");
                removerNo(nodeId);
                enviarMensagemShutdown(nodeId);
            }
        }, 30000); // Timeout de 30 segundos

        nodeTimers.put(nodeId, timer);
        ackNodes.add(nodeId); // Armazena o nó que enviou o HEARTBEAT

        // Verifica se todos os nós enviaram HEARTBEAT
        if (ackNodes.size() >= totalNodes) {
            System.out.println("Todos os nós enviaram HEARTBEAT. Atualização confirmada.");
            atualizacaoConfirmada = true;
            ackNodes.clear(); // Limpa os ACKs para a próxima rodada de atualizações
        } else {
            System.out.println("Aguardando mais HEARTBEATs. Total recebido: " + ackNodes.size());
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

}