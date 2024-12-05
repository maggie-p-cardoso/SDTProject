public class NodeMain {
    public static void main(String[] args) {
        String  nodeId      = args[0];
        Node    node        = new Node(nodeId);
        Thread  nodeThread  = new Thread(node);

        if (args.length != 1) {
            System.out.println("Uso: java NodeMain <NodeID>");
            return;
        }

        nodeThread.start();
    }
}