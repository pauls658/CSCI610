package csci610.Graph;

import soot.jimple.ConditionExpr;
import sun.awt.image.ImageWatched;

import java.util.*;
import java.util.stream.Collectors;

public class Graph {
    // Lookup table for node by ID
    private HashMap<Integer, Node> nodes;
    // Lookup table for outgoing edges of node
    private HashMap<Integer, HashSet<Edge>> adj;
    private HashMap<Integer, HashSet<Edge>> preds;

    private boolean reversed;

    public Graph() {
        this.adj = new HashMap<>();
        this.preds = new HashMap<>();
        this.nodes = new HashMap<>();
        this.reversed = false;
    }

    public void reverse() {
        HashMap<Integer, HashSet<Edge>> oldAdj = adj;
        adj = new HashMap<>();
        preds = new HashMap<>();
        for (Integer i : oldAdj.keySet()) {
            adj.put(i, new HashSet<>());
            preds.put(i, new HashSet<>());
        }

        for (Map.Entry<Integer, HashSet<Edge>> e : oldAdj.entrySet()) {
            for (Edge edge : e.getValue()) {
                addEdge(edge.getEndId(), edge.getStartId(), edge.getLabel());
            }
        }

        reversed = !reversed;

    }

    public Node getEntry() {
        if (!reversed) {
            return nodes.get(-1);
        } else {
            return nodes.get(-2);
        }
    }

    public Node createNode(int id) {
        Node n = nodes.get(id);
        if (n == null) {
            n = new Node(id);
            nodes.put(id, n);
            adj.put(id, new HashSet<>());
            preds.put(id, new HashSet<>());
        }
        return n;
    }

    public Node getNode(int id) {
        return nodes.get(id);
    }

    public void addEdge(int startId, int endId, String label) {
        if (startId == endId)// && label == null)
            return; // no self loops
        HashSet<Edge> edges = adj.get(startId);
        Node start = createNode(startId);
        Node end = createNode(endId);
        Edge e = new Edge(start, end, label);
        edges.add(e);

        edges = preds.get(endId);
        edges.add(e);
    }

    public void addEdge(int startId, int endId, ConditionExpr expr, boolean trueBranch) {
        if (startId == endId)// && label == null)
            return; // no self loops
        HashSet<Edge> edges = adj.get(startId);
        Node start = createNode(startId);
        Node end = createNode(endId);
        Edge e = new Edge(start, end, expr, trueBranch);
        edges.add(e);

        edges = preds.get(endId);
        edges.add(e);
    }

    public HashSet<Edge> getPreds(int n) {
        return preds.get(n);
    }

    public List<Integer> getPredsById(int n) {
        return preds.get(n).stream().map(e -> e.getStartId()).collect(Collectors.toList());
    }
    public List<Integer> getSuccsById(int n) {
        return adj.get(n).stream().map(e -> e.getEndId()).collect(Collectors.toList());
    }

    public HashSet<Edge> getSuccs(int n) {
        return adj.get(n);
    }

    public LinkedList<Node> getNodes() {
        LinkedList<Node> ret = new LinkedList<>();
        for (Map.Entry<Integer, Node> e : nodes.entrySet()) {
            ret.add(e.getValue());
        }
        return ret;
    }

    public LinkedList<Edge> getEdges() {
        LinkedList<Edge> ret = new LinkedList<>();
        for (Map.Entry<Integer, HashSet<Edge>> e : adj.entrySet()) {
            for (Edge edge : e.getValue()) {
                ret.add(edge);
            }
        }
        return ret;
    }
}
