package Sprint_1;//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

public class Elemento {
    private String nodeId;
    private boolean lider;

    public Elemento(String nodeId, boolean lider) {
        this.nodeId = nodeId;
        this.lider = lider;
    }

    public String getNodeId() {
        return this.nodeId;
    }

    public boolean isLider() {
        return this.lider;
    }

    public void setLider(boolean lider) {
        this.lider = lider;
    }
}
