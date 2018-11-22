package csci610.Graph;

import soot.jimple.ConditionExpr;

import java.util.Objects;

public class Edge {
    private Node start;
    private Node end;
    private String label;
    private String edgeType;
    private ConditionExpr expr;
    private boolean trueBranch;

    public Edge(Node start, Node end, String label) {
        this.start = start;
        this.end = end;
        this.label = label;
        this.edgeType = "FLOWS_TO";
        expr = null;
    }

    public Edge(Node start, Node end, ConditionExpr expr, Boolean trueBranch) {
        this.start = start;
        this.end = end;
        this.label = "";
        this.edgeType = "FLOWS_TO";
        this.expr = expr;
        this.trueBranch = trueBranch;
    }

    public Edge(Node start, Node end, String label, String edgeType) {
        this.start = start;
        this.end = end;
        this.label = label;
        this.edgeType = edgeType;
        expr = null;
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
    public String getEdgeType() {return edgeType;};

    public ConditionExpr getBranchExpr() {
        return expr;
    }

    public boolean isTrueBranch() {
        return trueBranch;
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
        return Objects.hash(getStartId(), getEndId(), label, edgeType);
    }
}
