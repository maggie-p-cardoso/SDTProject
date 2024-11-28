import java.rmi.Remote;
import java.rmi.RemoteException;

public interface SystemInterface extends Remote {

    // Envia uma nova mensagem (documento) do cliente para o líder.
    void enviarMensagem(String mensagem) throws RemoteException;

    // Envia um ACK de um nó para o líder, confirmando o recebimento de uma atualização pendente.
    void enviarACK(String nodeId) throws RemoteException;

    // Solicita a lista completa de documentos atualizados do líder.
    String solicitarListaAtualizacoes(String nodeId) throws RemoteException;

    // Verifica se um nó está ativo (heartbeat).
    void verificarHeartbeats(String nodeId) throws RemoteException;

    // Lista todos os documentos que estão atualizados no líder.
    String listarDocumentosAtualizados() throws RemoteException;
}
