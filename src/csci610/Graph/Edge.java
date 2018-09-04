package csci610.Graph;

import java.util.Objects;

public class Edge {
    private Node start;
    private Node end;
    private String label;

    public Edge(Node start, Node end, String label) {
        this.start = start;
        this.end = end;
        this.label = label;
    }

    public Node getStart() {
        return start;
    }
    public Node getEnd() {
        return end;
    }
    public int getStartId() {
        return start.getId();
    }
    public int getEndId() {
        return end.getId();
    }
    public String getLabel() {
        return label;
    }

    @Override
    public boolean equals(Object o) {
        Edge other = (Edge) o;
        return getStartId() == other.getStartId() &&
                getEndId() == other.getEndId() &&
                label == other.label;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getStartId(), getEndId(), label);
    }
}
