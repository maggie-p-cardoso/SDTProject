import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class ServerSetup {
    public static void main(String[] args) {
        Registry registry;
        int PORT = 1099;
        int totalNodes = 3;

        try {
            System.out.println("Iniciando o registo RMI na porta " + PORT + "...");

            Leader leader = new Leader();
            registry = LocateRegistry.createRegistry(PORT);
            registry.rebind("Leader", leader);

            System.out.println("Líder registado no RMI com sucesso!");
            System.out.println("Iniciando " + totalNodes + " nós...");

            for (int i = 1; i <= totalNodes; i++) {
                String nodeId = "Node" + i;
                startNodeProcess(nodeId);
                leader.registarNo(nodeId);
                System.out.println("Nó " + nodeId + " registado no lider");
            }
        } catch (Exception e) {
            System.err.println("Erro ao configurar o servidor.");
            e.printStackTrace();
        }
    }

    // Inicialização de cada nó como um processo separado
    private static void startNodeProcess(String nodeId) {
        try {
            String projectPath  = System.getProperty("user.dir");
            String classPath    = projectPath + "/out/production/SDT_pv22458";

            ProcessBuilder processBuilder = new ProcessBuilder(
                    "java", "-cp", classPath, "NodeMain", nodeId);
            processBuilder.inheritIO();
            processBuilder.start();

            System.out.println("Processo do nó " + nodeId + " iniciado.");
        } catch (Exception e) {
            System.err.println("Erro ao iniciar o processo do nó " + nodeId);
            e.printStackTrace();
        }
    }
}