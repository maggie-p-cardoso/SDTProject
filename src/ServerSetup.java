import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class ServerSetup {
    public static void main(String[] args) {
        try {
            // Configuração do líder e registro no RMI
            System.out.println("Inicializando o registro RMI na porta 1099...");
            Registry registry = LocateRegistry.createRegistry(1099); // Cria o registro RMI com a porta 1099
            Leader leader = new Leader(); // Instancia o objeto Leader
            registry.rebind("Leader", leader); // Registra o objeto Leader no registro RMI
            System.out.println("Líder registrado no RMI com sucesso!");

            // Número de nós a iniciar
            int totalNodes = 3;
            System.out.println("Iniciando " + totalNodes + " nós...");

            for (int i = 1; i <= totalNodes; i++) {
                String nodeId = "Node" + i;
                startNodeProcess(nodeId);
            }

            System.out.println("Servidor configurado com sucesso e nós iniciados como processos separados.");
        } catch (Exception e) {
            System.err.println("Erro ao configurar o servidor:");
            e.printStackTrace();
        }
    }

    // Iniciação de cada nó como um processo separado
    private static void startNodeProcess(String nodeId) {
        try {
            // Caminho do projeto para garantir que o comando de execução funcione em qualquer ambiente
            String projectPath = System.getProperty("user.dir");
            String classPath = projectPath + "/out/production/SDT_pv22458";

            // Comando para iniciar o nó
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "java", "-cp", classPath, "NodeMain", nodeId);
            processBuilder.inheritIO(); // Permite visualizar a saída do processo no terminal
            processBuilder.start();

            System.out.println("Processo do nó " + nodeId + " iniciado.");
        } catch (Exception e) {
            System.err.println("Erro ao iniciar o processo do nó " + nodeId + ":");
            e.printStackTrace();
        }
    }
}
