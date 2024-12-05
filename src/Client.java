import java.rmi.Naming;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) {
        try {
            SystemInterface leader = (SystemInterface) Naming.lookup("rmi://localhost/Leader");

            Scanner scanner = new Scanner(System.in);

            while (true) {
                limparTela();

                System.out.println("Bem vindo. Escolha uma opção:");
                System.out.println("1 - Enviar uma nova atualização ao líder");
                System.out.println("2 - Listar documentos atualizados no líder");
                System.out.println("3 - Sair");
                System.out.print("Opção: ");

                int opcao = scanner.nextInt();
                scanner.nextLine();

                switch (opcao) {
                    case 1:
                        limparTela();
                        System.out.print("Digite o ID do documento: ");
                        int docId = scanner.nextInt();
                        scanner.nextLine();

                        System.out.print("Digite o conteúdo do documento: ");
                        String conteudo = scanner.nextLine();

                        String mensagem = docId + ";" + conteudo;
                        leader.enviarMensagem(mensagem);
                        System.out.println("\nMensagem enviada ao líder: " + mensagem);
                        pausar();
                        break;

                    case 2:
                        limparTela();
                        System.out.println("Obtendo lista de documentos atualizados do líder...");
                        String documentos = leader.listarDocumentosAtualizados();
                        System.out.println("Documentos atualizados:\n" + documentos);
                        pausar();
                        break;

                    case 3:
                        System.out.println("Encerrando...");
                        scanner.close();
                        return;

                    default:
                        System.out.println("Opção inválida! Tente novamente.");
                        pausar();
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void limparTela() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception e) {
            System.out.println("\n".repeat(50));
        }
    }


    public static void pausar() {
        System.out.println("\nPressione Enter para continuar...");
        Scanner scanner = new Scanner(System.in);
        scanner.nextLine();
    }
}