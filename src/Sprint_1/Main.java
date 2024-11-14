package Sprint_1;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Defina o número de nós que você quer criar
        int numNodes = 4;

        // Lista para armazenar e gerenciar as threads dos nós
        List<Thread> nodeThreads = new ArrayList<>();

        for (int i = 1; i <= numNodes; i++) {
            // Cria um ID único para cada nó (Node1, Node2, etc.)
            String nodeId = "Sprint_2.Node" + i;

            // Define o primeiro nó como líder
            boolean isLider = (i == 1);

            // Cria o elemento com o ID e se é líder ou não
            Elemento elemento = new Elemento(nodeId, isLider);

            // Lista de outros nós (inicialmente vazia)
            List<String> otherNodes = new ArrayList<>();

            // Instância de Sprint_1.DistributedNode
            DistributedNode node = new DistributedNode(elemento, "...uma frase qualquer...", otherNodes);

            // Inicia o nó em uma nova thread
            Thread nodeThread = new Thread(node::start);
            nodeThreads.add(nodeThread);
            nodeThread.start();
        }

        // Opcional: Espera que todas as threads terminem (para testes e encerramento do programa)
        for (Thread thread : nodeThreads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
