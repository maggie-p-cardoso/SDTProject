import java.rmi.Remote;
import java.rmi.RemoteException;

public interface SystemInterface extends Remote {
    void enviarMensagem(String mensagem) throws RemoteException;

    void receberACK(String nodeId) throws RemoteException;

    String solicitarListaAtualizacoes(String nodeId) throws RemoteException;

    void processarACK(String nodeId) throws RemoteException;

    String listarDocumentosAtualizados() throws RemoteException;

    void registarNo(String nodeId) throws RemoteException;
}
