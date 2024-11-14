package Sprint_2;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class ServerSetup {
    public static void main(String[] args) {
        try {
            Registry registry = LocateRegistry.createRegistry(1099);
            Leader leader = new Leader();
            registry.rebind("Sprint_2.Leader", leader);

            Thread node1 = new Thread(new Node("Node1"));
            Thread node2 = new Thread(new Node("Node2"));
            Thread node3 = new Thread(new Node("Node3"));

            node1.start();
            node2.start();
            node3.start();

            System.out.println("Servidor configurado e pronto para enviar atualizações.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
