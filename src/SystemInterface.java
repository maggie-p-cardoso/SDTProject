import java.rmi.Remote;
import java.rmi.RemoteException;

public interface SystemInterface extends Remote {

    /**
     * Envia uma nova mensagem (documento) do cliente para o líder.
     * Formato esperado: "ID;Conteúdo"
     */
    void enviarMensagem(String mensagem) throws RemoteException; // Cliente para líder

    /**
     * Envia um ACK de um nó para o líder, confirmando o recebimento de uma atualização pendente.
     */
    void enviarACK(String nodeId) throws RemoteException; // Nós para líder

    /**
     * Solicita a lista completa de documentos atualizados do líder.
     * Retorna uma String no formato: "ID1:Conteudo1;ID2:Conteudo2;..."
     */
    String solicitarListaAtualizacoes(String nodeId) throws RemoteException; // Nós para líder

    /**
     * Verifica se um nó está ativo (heartbeat).
     * Pode ser usado para monitorar a conexão entre o líder e os nós.
     */
    void verificarHeartbeats(String nodeId) throws RemoteException; // Nós para líder

    /**
     * Lista todos os documentos que estão atualizados no líder.
     * Retorna uma descrição detalhada dos documentos armazenados.
     */
    String listarDocumentosAtualizados() throws RemoteException; // Nós ou cliente para líder
}
