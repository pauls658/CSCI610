package csci610.RDDU;

import java.util.Objects;

public class Definition {
    public String var;
    public int node;

    public Definition(String var, int node) {
        this.var = var;
        this.node = node;
    }

    @Override
    public boolean equals(Object o) {
        Definition other = (Definition)o;
        return var.equals(other.var) && node == other.node;
    }

    @Override
    public int hashCode() {
        return Objects.hash(var, node);
    }

    public String toString() {
        return "<" + var + ", " + Integer.toString(node) + ">";
    }
}
