package Sprint_2;

public class NodeMain {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Uso: java NodeMain <NodeID>");
            return;
        }

        String nodeId = args[0];
        Node node = new Node(nodeId);

        // Executar o nó em uma thread dentro do processo para manter compatibilidade
        Thread nodeThread = new Thread(node);
        nodeThread.start();
    }
}
