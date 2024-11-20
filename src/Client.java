import java.rmi.Naming;

public class Client {
    public static void main(String[] args) {
        try {
            SystemInterface leader = (SystemInterface) Naming.lookup("rmi://localhost/Leader"); // Interface para o cliente comunicar com o leader
            leader.enviarMensagem("Nova atualização do documento: versão 2"); // Invoca a função que está presente no objeto Leader
            System.out.println("Cliente: Mensagem de atualização enviada ao líder.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
