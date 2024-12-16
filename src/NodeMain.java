
 public class NodeMain {
     public static void main(String[] args) {
         try {
             String id = args.length > 0 ? args[0] : "default_node";
             System.out.println("Nó iniciado com ID: " + id);
             Node node = new Node(id);
             node.run();
         } catch (Exception e) {
             e.printStackTrace();
         }
     }
 }
