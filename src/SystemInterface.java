import java.rmi.Remote;
import java.rmi.RemoteException;

public interface SystemInterface extends Remote {
    void enviarMensagem(String mensagem) throws RemoteException; // Cliente para líder
    void enviarACK(String nodeId) throws RemoteException;        // Nós para líder
}
