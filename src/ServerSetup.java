import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class ServerSetup {
    public static void main(String[] args) {
        try {
            // Configuração do líder e registro no RMI
            Registry registry = LocateRegistry.createRegistry(1099); // Cria o registo RMI com a porta 1099
            Leader leader = new Leader(); // Criação de uma instância do Leader, permitindo implementar os metodos que o cliente e o nó vão usar
            registry.rebind("Leader", leader); // Regista o objeto Leader no registro RMI

            // Iniciar os nós como processos
            startNodeProcess("Node1");
            startNodeProcess("Node2");
            startNodeProcess("Node3");

            System.out.println("Servidor configurado e nós iniciados como processos separados.");
            System.out.println("");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Iniciação de cada nó como um processo separado
    private static void startNodeProcess(String nodeId) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "java", "-cp", "out/production/SDT_pv22458", "NodeMain", nodeId);
            processBuilder.inheritIO(); // Permite visualizar a saída do processo no terminal
            processBuilder.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
