package csci610.Graph;

import sun.awt.image.ImageWatched;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;

public class Graph {
    // Lookup table for node by ID
    private HashMap<Integer, Node> nodes;
    // Lookup table for outgoing edges of node
    private HashMap<Integer, HashSet<Edge>> adj;

    public Graph() {
        this.adj = new HashMap<>();
        this.nodes = new HashMap<>();
    }

    public Node createNode(int id) {
        Node n = nodes.get(id);
        if (n == null) {
            n = new Node(id);
            nodes.put(id, n);
            adj.put(id, new HashSet<>());
        }
        return n;
    }

    public void addEdge(int startId, int endId, String label) {
        if (startId == endId)// && label == null)
            return; // no self loops
        HashSet<Edge> edges = adj.get(startId);
        Node start = createNode(startId);
        Node end = createNode(endId);
        edges.add(new Edge(start, end, label));
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
