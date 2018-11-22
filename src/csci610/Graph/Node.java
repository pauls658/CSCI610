package csci610.Graph;

import soot.Unit;
import soot.jimple.internal.JIfStmt;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;

public class Node  {
    private int id;
    private String type;
    private HashSet<String> defs;
    private HashSet<String> uses;
    private boolean isStringSwitch = false;
    private LinkedList<Unit> units = new LinkedList<>();

    // For-loop detection
    private boolean hasIf = false;

    public Node(int id) {
        this.id = id;
        this.type = "node";
        this.defs = new HashSet<>();
        this.uses = new HashSet<>();
    }

    public void addUnit(Unit u) {
        if (u instanceof JIfStmt) hasIf = true;
        this.units.add(u);
    }

    public boolean isForLoop() {
        return hasIf && loopVar() != null;
    }

    public String loopVar() {
        String loopVar = null;
        for (String def : defs) {
            if (uses.contains(def)) {
                loopVar = def;
                break;
            }
        }
        return loopVar;
    }

    public LinkedList<Unit> getUnits() {
        return units;
    }

    public void setStringSwitch(boolean b) {
        this.isStringSwitch = b;
    }

    public boolean isStringSwitch() {
        return isStringSwitch;
    }

    public void addDef(String def) {
        this.defs.add(def);
    }

    public void addUse(String use) {
        this.uses.add(use);
    }

    public void addDef(Collection<String> def) {
        this.defs.addAll(def);
    }

    public void addUse(Collection<String> use) {
        this.uses.addAll(use);
    }

    public HashSet<String> getDefs() {
        return defs;
    }

    public HashSet<String> getUses() {
        return uses;
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

    public String toString() {
        String ret = getNodeEnc();
        if (isForLoop()) {
            ret += " (loop header)";
        }
        return ret;
    }

    public String getNodeEnc() {
        if (this.type.equals("entry") || this.type.equals("exit"))
            return type;
        else
            return Integer.toString(this.id);
    }

}
