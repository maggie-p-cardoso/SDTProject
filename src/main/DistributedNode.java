package main;

import multicast.MulticastHandler;
import sync.SyncClient;
import sync.SyncServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class DistributedNode {
    private final String nodeId;
    private final boolean isLeader;
    private String documentContent;
    private final String[] otherNodes;
    private final int port;
    private final List<String> messageList; // Lista de mensagens

    public DistributedNode(String nodeId, boolean isLeader, String initialContent, String[] otherNodes, int port) {
        this.nodeId = nodeId;
        this.isLeader = isLeader;
        this.documentContent = initialContent;
        this.otherNodes = otherNodes;
        this.port = port;
        this.messageList = new ArrayList<>();

        startSyncServer();
        startMulticastHandler();

        if (isLeader) {
            startDataSyncRequest();
            new SendTransmitter(this).start(); // Inicia o transmissor de mensagens
        }
    }

    public void startSyncServer() {
        SyncServer syncServer = new SyncServer(port, this);
        new Thread(syncServer).start();
    }

    public void startMulticastHandler() {
        MulticastHandler multicastHandler = new MulticastHandler(this);
        new Thread(multicastHandler).start();
    }

    private void startDataSyncRequest() {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("Líder " + nodeId + " requisita sincronização de dados.");
                syncWithOtherNodes();
            }
        }, 0, 10000);
    }

    private void syncWithOtherNodes() {
        for (String node : otherNodes) {
            try {
                SyncClient.sendUpdate(node, documentContent);
            } catch (Exception e) {
                System.out.println("Falha ao sincronizar com o nó " + node);
            }
        }
    }

    public synchronized void updateDocument(String newContent) {
        this.documentContent = newContent;
        addMessage("Update: " + newContent); // Adiciona atualização à lista de mensagens
    }

    public synchronized String getDocumentContent() {
        return documentContent;
    }

    public boolean isLeader() {
        return isLeader;
    }

    public void addMessage(String message) {
        synchronized (messageList) {
            messageList.add(message);
        }
    }

    public List<String> getMessageList() {
        synchronized (messageList) {
            return new ArrayList<>(messageList);
        }
    }

    public void clearMessages() {
        synchronized (messageList) {
            messageList.clear();
        }
    }

    public static void main(String[] args) {
        String[] otherNodes = {"localhost"};
        new DistributedNode("Node1", true, "Conteúdo inicial do documento", otherNodes, 8080);
      //  leaderNode.updateDocument("Atualização de conteúdo pelo líder.");
    }
}
