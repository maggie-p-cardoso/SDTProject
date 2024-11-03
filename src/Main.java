import main.DistributedNode;

public class Main {
    public static void main(String[] args) {
        // Lista de nós para sincronização (endereços IP ou hostnames)
        String[] otherNodes = {"localhost"};

        // Criação do nó líder
        DistributedNode leaderNode = new DistributedNode("Node1", true, "Conteúdo inicial do documento", otherNodes, 8080);

        // Criação de um nó seguidor
        //DistributedNode followerNode = new DistributedNode("Node2", false, "Conteúdo inicial do documento", otherNodes, 8081);

        // Sincronização inicial do documento (para testar o envio de atualizações)
        leaderNode.updateDocument("Atualização de conteúdo pelo líder.");
    }
}
