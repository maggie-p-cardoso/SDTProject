import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class LeaderMain {
    public static void main(String[] args) {
        try {
            String id = args.length > 0 ? args[0] : "default_leader";
            System.out.println("Líder iniciado com ID: " + id);

            SystemInterface leader = new Leader();
            Registry registry = LocateRegistry.createRegistry(1099); // Use o Registry existente
            registry.rebind("Leader", leader);
            System.out.println("Líder registado no RMI com sucesso!");
        } catch (Exception e) {
            System.err.println("Erro ao iniciar o líder.");
            e.printStackTrace();
        }
    }
}
