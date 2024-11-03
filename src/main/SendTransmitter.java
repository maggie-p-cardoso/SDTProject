package main;
import java.util.List;

public class SendTransmitter extends Thread {
    private final DistributedNode node;

    public SendTransmitter(DistributedNode node) {
        this.node = node;
    }

    @Override
    public void run() {
        while (true) {
            try {
                sendMessages(node.getMessageList());
                node.clearMessages(); // Limpa a lista após o envio
                Thread.sleep(5000); // Pausa de 5 segundos
            } catch (InterruptedException e) {
                System.out.println("Erro no transmissor de mensagens: " + e.getMessage());
            }
        }
    }

    private void sendMessages(List<String> messages) {
        if (!messages.isEmpty()) {
            System.out.println("Envio de mensagens: " + messages);
            // Código para envio das mensagens via multicast
            // Ou sincronização com outros nós, conforme necessário
        }
    }
}
