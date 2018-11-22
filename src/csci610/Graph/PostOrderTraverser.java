package csci610.Graph;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;

public class PostOrderTraverser {
    private LinkedList<Node> nodeOrder;
    private HashSet<Integer> visited;
    private Graph g;

    public PostOrderTraverser() {
        this.nodeOrder = new LinkedList<>();
        this.visited = new HashSet<>();
    }

    public LinkedList<Node> postOrder(Graph g) {
        nodeOrder.clear();
        visited.clear();
        this.g = g;
        visit(g.getEntry());
        return nodeOrder;
    }

    private void visit(Node n) {
        visited.add(n.getId());
        for (Edge e : g.getSuccs(n.getId())) {
            Node s = e.getEnd();
            if (visited.contains(s.getId())) continue;
            visit(s);
        }
        nodeOrder.add(n);
    }
}

