package csci610.RDDU;

import soot.Unit;
import soot.ValueBox;
import soot.jimple.internal.JimpleLocal;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.scalar.ForwardFlowAnalysis;

import java.util.*;

public class ReachingDefinitionAnalysis extends ForwardFlowAnalysis {
    private HashMap<Unit, HashSet<Definition>> defs;
    public ReachingDefinitionAnalysis(DirectedGraph graph) {
        super(graph);
        defs = new HashMap<>();

        for (Iterator<Unit> i = graph.iterator(); i.hasNext();) {
            Unit u = i.next();
        }
    }

    private static LinkedList<String> getSymbols(List<ValueBox> vbs) {
        LinkedList<String> ret = new LinkedList<>();
        for (ValueBox vb : vbs) {
            if (vb.getValue() instanceof JimpleLocal) {
                String sym = vb.getValue().toString();
                ret.add(sym);
            }
        }
        return ret;
    }

    @Override
    protected void flowThrough(Object o, Object o2, Object a1) {

    }

    @Override
    protected Object newInitialFlow() {
        return null;
    }

    @Override
    protected void merge(Object o, Object a1, Object a2) {

    }

    @Override
    protected void copy(Object o, Object a1) {

    }
}
