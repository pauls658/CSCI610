package csci610.CFG;

import csci610.DOTExporter.DOTExporter;
import csci610.Graph.Graph;
import csci610.Graph.Node;
import soot.*;
import soot.jimple.ConditionExpr;
import soot.jimple.internal.*;
import soot.options.Options;
import soot.util.Chain;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

public class CFG {


    public static Graph CFGfromSootMethod(SootMethod sm) {
        Graph ret = new Graph();

        Node entryNode = ret.createNode(-1);
        entryNode.makeEntryNode();
        Node exitNode = ret.createNode(-2);
        exitNode.makeExitNode();

        Body b = sm.retrieveActiveBody();
        Chain<Unit> units = b.getUnits();

        Node prev = entryNode;
        Node cur;
        Unit tmp;
        String cond = null;
        for (Unit u : units) {
            // Fake code!
            if (u.getJavaSourceStartLineNumber() <= 0) continue;

            cur = ret.createNode(u.getJavaSourceStartLineNumber());
            if (prev != null) {
                // Connect this node with the previous node
                // this is not a branch, so the label is null
                if (cond != null)
                    cond = "!" + cond;
                ret.addEdge(prev.getId(), cur.getId(), cond);
            }

            cond = null;
            // check if we branch to anywhere
            if (u instanceof JReturnVoidStmt || u instanceof JReturnStmt || u instanceof JRetStmt) {
                // returns go directly to exit
                ret.addEdge(cur.getId(), exitNode.getId(), null);
            } else if (u instanceof JIfStmt) {
                JIfStmt ju = (JIfStmt) u;
                ConditionExpr expr = (ConditionExpr)ju.getCondition();
                cond = expr.getSymbol().trim();
                tmp = ju.getTargetBox().getUnit();
                ret.addEdge(cur.getId(), tmp.getJavaSourceStartLineNumber(), cond);
            } else if (u instanceof JGotoStmt) {
                JGotoStmt ju = (JGotoStmt)u;
                tmp = ju.getTargetBox().getUnit();
                ret.addEdge(cur.getId(), tmp.getJavaSourceStartLineNumber(), null);
            } else if (u instanceof JTableSwitchStmt) {
                JTableSwitchStmt ju = (JTableSwitchStmt)u;
                for (Unit ut : ju.getTargets()) {
                    ret.addEdge(cur.getId(), ut.getJavaSourceStartLineNumber(), null);
                }
                tmp = ju.getDefaultTarget();
                if (tmp != null)
                    ret.addEdge(cur.getId(), tmp.getJavaSourceStartLineNumber(), null);
            } else if (u instanceof JLookupSwitchStmt) {
                 JLookupSwitchStmt ju = (JLookupSwitchStmt)u;
                 for (Unit ut : ju.getTargets()) {
                    ret.addEdge(cur.getId(), ut.getJavaSourceStartLineNumber(), null);
                }
                tmp = ju.getDefaultTarget();
                if (tmp != null)
                    ret.addEdge(cur.getId(), tmp.getJavaSourceStartLineNumber(), null);
            } else if (u instanceof JThrowStmt) {
                // TODO? how should we handle this
            }

            if (u.fallsThrough()) {
                // make the next edge
                prev = cur;
            } else {
                prev = null;
            }
        }

        // Connect the last instruction to the CFG exit?
        if (prev != null) {
            ret.addEdge(prev.getId(), exitNode.getId(), null);
        }

        return ret;
    }

    public static void main(String[] args) throws IOException {
        //String className = args[0];
        //String outGraphName = args[1];
        //String sootCP = args[2];
        String className = "Test2";
        String outGraphName = "/home/student/out.dotty";
        String sootCP = "/home/student/cs610/bin";

        // Set soot settings
        String sootClassPath = Scene.v().getSootClassPath() + File.pathSeparator + sootCP;
        Scene.v().setSootClassPath(sootClassPath);
        Options.v().set_keep_line_number(true);
        SootClass sootClass = Scene.v().loadClassAndSupport(className);
        Scene.v().loadNecessaryClasses();
        sootClass.setApplicationClass();

        SootMethod sm = sootClass.getMethodByName("main");
        Graph g = CFGfromSootMethod(sm);

        File dotFile = new File(outGraphName);
        if (dotFile.exists()) {
            dotFile.delete();
        }
        dotFile.createNewFile();
        DOTExporter d = new DOTExporter(new PrintWriter(dotFile));
        d.writeGraph(g);
        d.close();
    }

}
