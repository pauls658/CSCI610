package csci610.Graph;

public class Node  {
    private int id;
    private String type;

    public Node(int id) {
        this.id = id;
        this.type = "node";
    }

    public int getId() {
        return id;
    }

    public void makeEntryNode() {
        this.type = "entry";
    }

    public void makeExitNode() {
        this.type = "exit";
    }

    public String getNodeEnc() {
        if (this.type.equals("entry") || this.type.equals("exit"))
            return type;
        else
            return Integer.toString(this.id);
    }
}
