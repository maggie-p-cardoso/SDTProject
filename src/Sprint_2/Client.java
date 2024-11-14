package Sprint_2;

import java.rmi.Naming;

public class Client {
    public static void main(String[] args) {
        try {
            SystemInterface leader = (SystemInterface) Naming.lookup("rmi://localhost/Sprint_2.Leader");
            leader.enviarMensagem("Nova atualização do documento: versão 2");
            System.out.println("Cliente: Mensagem de atualização enviada ao líder.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}