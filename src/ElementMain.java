public class ElementMain {
    public static void main(String[] args) {
        try {
            String id = args[0]; // ID do elemento passado como argumento
            String role = args[1]; // Papel: "leader" ou "node"

            if (role.equalsIgnoreCase("leader")) {
                System.out.println("Elemento " + id + " com papel de ->Líder.");
                LeaderMain.main(new String[]{id}); // Chama a lógica do líder
            } else if (role.equalsIgnoreCase("node")) {
                System.out.println("Elemento " + id + " com papel de ->Nó.");
                NodeMain.main(new String[]{id}); // Chama a lógica do nó
            } else {
                System.out.println("Papel desconhecido para o elemento " + id);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
