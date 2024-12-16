import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class ServerSetup {
    private static Map<String, Integer> votos = new HashMap<>();
    private static boolean liderEleito = false;
    private static Map<String, Process> nodeProcesses = new HashMap<>();

    private static volatile boolean serverReady = false;
    private static int n_nos=3;
    private static synchronized void registarVoto(String voto) {
        if (liderEleito) return;

        votos.put(voto, votos.getOrDefault(voto, 0) + 1);
        System.out.println("Voto registado: " + voto);

        if (votos.size() > n_nos/2) { // Supondo 3 nós no sistema
            elegerNovoLider();
        }
    }

    private static void elegerNovoLider() {
        liderEleito = true;

        // Determina o novo líder
        String novoLider = votos.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .get().getKey();

        System.out.println("Novo líder eleito: " + novoLider);

        // Extrai o nome do nó (ex: "Nó Element2")
        String nodeName = novoLider.split(" ")[1];
        reiniciarComoLeader(nodeName);
    }

    private static void reiniciarComoLeader(String nodeName) {
        try {
            // Encerra o processo atual do nó, se existir
            Process process = nodeProcesses.get(nodeName);
            if (process != null) {
                process.destroy();
                System.out.println("Processo do nó " + nodeName + " encerrado.");
            }
            String projectPath = System.getProperty("user.dir");
            String classPath = projectPath + "/out/production/SDT_pv22458";

            ProcessBuilder processBuilder = new ProcessBuilder(
                    "java", "-cp", classPath, "LeaderMain", nodeName);
            processBuilder.inheritIO();
            processBuilder.start();

            System.out.println("Processo de " + nodeName + " reiniciado como líder com sucesso.");
        } catch (Exception e) {
            System.err.println("Erro ao reiniciar " + nodeName + " como LeaderMain.");
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {
        Thread listenerThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(5000)) {
                System.out.println("ServerSetup escutando na porta 5000...");
                serverReady = true; // Indica que o servidor está pronto

                while (!liderEleito) {
                    try (Socket clientSocket = serverSocket.accept();
                         BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {

                        String voto = in.readLine();
                        registarVoto(voto);
                    } catch (IOException e) {
                        System.err.println("Erro ao processar conexão de um nó.");
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                System.err.println("Erro ao iniciar o ServerSocket.");
                e.printStackTrace();
            }
        });

        listenerThread.start();
        while (!serverReady) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        try {
            startElementProcess("Element1", "leader");
            System.out.println("Líder iniciado com sucesso!");

            for (int i = 2; i <= 4; i++) {
                startElementProcess("Element" + i, "node");
            }
        } catch (Exception e) {
            System.err.println("Erro ao iniciar os elementos.");
            e.printStackTrace();
        }
    }

    private static void startElementProcess(String id, String role) {
        try {
            String projectPath = System.getProperty("user.dir");
            String classPath = projectPath + "/out/production/SDT_pv22458";

            ProcessBuilder processBuilder = new ProcessBuilder(
                    "java", "-cp", classPath, "ElementMain", id, role);
            processBuilder.inheritIO();
            Process process = processBuilder.start();
            nodeProcesses.put(id, process);
            System.out.println("Processo do elemento " + id + " iniciado como " + role + ".");
        } catch (Exception e) {
            System.err.println("Erro ao iniciar o processo do elemento " + id);
            e.printStackTrace();
        }
    }
}
