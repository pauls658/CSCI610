package csci610.CFG;

import csci610.DOTExporter.DOTExporter;
import csci610.Graph.Graph;
import csci610.Graph.Node;
import soot.*;
import soot.jimple.ConditionExpr;
import soot.jimple.internal.*;
import soot.options.Options;
import soot.tagkit.Tag;
import soot.util.Chain;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;

public class CFG {

    public static Graph CFGfromSootMethod(SootMethod sm) {
        Graph ret = new Graph();

        Node entryNode = ret.createNode(-1);
        entryNode.makeEntryNode();
        Node exitNode = ret.createNode(-2);
        exitNode.makeExitNode();

        Body b = sm.retrieveActiveBody();
        Chain<Unit> units = b.getUnits();
        Object[] unitArray = units.toArray();

        ////////////////////////////////////////////////////////////////////////////
        // First pass: figure out which switch statements were "String" switches, //
        // if we have any iterators, and any if's with multiple conditions.       //
        ////////////////////////////////////////////////////////////////////////////

        // string switchs and for iters
        // Lineno -> index of lookup stmt in units
        HashMap<Integer, Integer> seenLinenos = new HashMap<>();
        HashMap<Integer, Integer> stringSwitchStmts = new HashMap<>();
        // Set of lineno's that have a lengthof stmt
        HashSet<Integer> lengthofStmt = new HashSet<>();
        // index in units[] -> actual lineno
        int indexCtr = 0, maxLineNo = -1;
        for (Unit u : units) {
            if (u instanceof JLookupSwitchStmt || u instanceof JTableSwitchStmt) { // string switch
                Integer firstStmt = seenLinenos.get(u.getJavaSourceStartLineNumber());
                if (firstStmt != null){
                    stringSwitchStmts.put(firstStmt, indexCtr);
                } else {
                    seenLinenos.put(u.getJavaSourceStartLineNumber(), indexCtr);
                }
            } else if (u instanceof JAssignStmt) { // potential iterator
                JAssignStmt ju = (JAssignStmt)u;
                if (ju.getRightOp() instanceof JLengthExpr) {
                    lengthofStmt.add(ju.getJavaSourceStartLineNumber());
                }
            }
            if (u.getJavaSourceStartLineNumber() > maxLineNo)
                maxLineNo = u.getJavaSourceStartLineNumber();
            indexCtr += 1;
        }

        // multiple if conditions
        HashMap<Integer, Integer> linenoTranslation = new HashMap<>();
        int lastLinenoOfIf = -1;
        indexCtr = 0;
        for (Unit u : units) {
            if (units.getLast() == u) continue; //  the last unit will have lineno
            if (u instanceof JIfStmt) {
                // check if we should rewrite first
                if (lastLinenoOfIf != -1 && (lastLinenoOfIf > u.getJavaSourceStartLineNumber() || u.getJavaSourceStartLineNumber() >= maxLineNo)) {
                    // we have previously seen an if, and this line number is before the last if, or the line number doesn't make sense
                    linenoTranslation.put(indexCtr, lastLinenoOfIf);
                } else {
                    // otherwise, this is an if statement, and the line number makes sense
                    lastLinenoOfIf = u.getJavaSourceStartLineNumber();
                }
            } else if (lastLinenoOfIf != -1 && (lastLinenoOfIf > u.getJavaSourceStartLineNumber() || u.getJavaSourceStartLineNumber() >= maxLineNo)) {
                // this is not an if statement, but the line number doesn't make sense
                linenoTranslation.put(indexCtr, lastLinenoOfIf);
            } else {
                // This is not an if, and the line number makes sense
                lastLinenoOfIf = -1;
            }
            indexCtr += 1;
        }
        //////////////////////////////////////////////////////////
        // Second pass: Get the cases for the switch statements //
        //////////////////////////////////////////////////////////

        // index of switchstmt in units -> in order list of string labels
        HashMap<Integer, LinkedList<String>> labelsForSwitchStmt = new HashMap<>();
        for (Map.Entry<Integer, Integer> e : stringSwitchStmts.entrySet()) {
            LinkedList<String> labels = new LinkedList<String>();
            labelsForSwitchStmt.put(e.getValue(), labels);
            int numTargets = 0;
            if (unitArray[e.getKey()] instanceof JTableSwitchStmt) {
                numTargets = ((JTableSwitchStmt)unitArray[e.getKey()]).getTargetCount();
            } else {
                numTargets = ((JLookupSwitchStmt)unitArray[e.getKey()]).getTargetCount();
            }
            for (int i = 0; i < numTargets; i++) {
                int equalsLoc = e.getKey() + 1 + i*4;
                Unit targetU = (Unit) unitArray[equalsLoc];
                JAssignStmt ja = (JAssignStmt) targetU;
                JVirtualInvokeExpr jv = (JVirtualInvokeExpr)ja.getRightOp();
                String arg = jv.getArg(0).toString();
                labels.add(arg.replace('"', '\''));
            }
        }

        Node prev = entryNode;
        Node cur;
        Unit tmp;
        String cond = null;
        ConditionExpr prevExpr = null;
        Unit u;
        boolean isStringSwitch = false;

        int lastJumpLineno = -1;
        boolean isLengthOf = false;
        for (Integer i = 0; i < units.size(); i++) {
            isStringSwitch = false;
            if (stringSwitchStmts.get(i) != null) {
                i = stringSwitchStmts.get(i); // Skip the first switch stmt of a string switch
                isStringSwitch = true;
            }

            u = (Unit) units.toArray()[i];

            // Fake code!
            if (u.getJavaSourceStartLineNumber() <= 0) {
                if (u instanceof JIdentityStmt)
                    entryNode.addUnit(u); // this holds the symbols for the func args
                continue;
            }

            cur = ret.createNode(u.getJavaSourceStartLineNumber());

            if (isLengthOf && u.getJavaSourceStartLineNumber() > lastJumpLineno) {
                // the previous statement had a jump, and the linenumber of the jump
                // is less than the next instruction. This is what happens when we have
                // a for-iter
                cur.addUnit(u);
                isLengthOf = false;
                continue;
            }
            isLengthOf = false;

            // this is an if statement with multiple conditions
            if (linenoTranslation.containsKey(i)) {
                cur = ret.createNode(linenoTranslation.get(i));
                cur.addUnit(u);
            }

            if (isStringSwitch)
                cur.setStringSwitch(true);
            cur.addUnit(u);
            if (prev != null) {
                // Connect this node with the previous node
                // this is not a branch, so the label is null
                if (cond != null)
                    cond = "!" + cond;
                ret.addEdge(prev.getId(), cur.getId(), prevExpr, false);
            }

            cond = null;
            prevExpr = null;
            // check if we branch to anywhere
            if (u instanceof JReturnVoidStmt || u instanceof JReturnStmt || u instanceof JRetStmt) {
                // returns go directly to exit
                ret.addEdge(cur.getId(), exitNode.getId(), null);
            } else if (u instanceof JIfStmt) {
                JIfStmt ju = (JIfStmt) u;
                ConditionExpr expr = (ConditionExpr)ju.getCondition();
                cond = expr.getSymbol().trim();
                tmp = ju.getTargetBox().getUnit();
                //ret.addEdge(cur.getId(), tmp.getJavaSourceStartLineNumber(), cond);
                ret.addEdge(cur.getId(), tmp.getJavaSourceStartLineNumber(), expr, true);
                prevExpr = expr;
                lastJumpLineno = tmp.getJavaSourceStartLineNumber();
                if  (lastJumpLineno > cur.getId()) {
                    isLengthOf = lengthofStmt.contains(ju.getJavaSourceStartLineNumber());
                }
            } else if (u instanceof JGotoStmt) {
                JGotoStmt ju = (JGotoStmt)u;
                tmp = ju.getTargetBox().getUnit();
                ret.addEdge(cur.getId(), tmp.getJavaSourceStartLineNumber(), null);
            } else if (u instanceof JTableSwitchStmt) {
                JTableSwitchStmt ju = (JTableSwitchStmt)u;
                int j = 0;
                for (Unit ut : ju.getTargets()) {
                    if (ut == ju.getDefaultTarget()) {
                        if (!isStringSwitch) j+= 1;
                        continue;
                    }
                    String label = "== ";
                    if (isStringSwitch) {
                        label += labelsForSwitchStmt.get(i).get(j);
                    } else {
                        label += Integer.toString(j + ju.getLowIndex());
                    }
                    ret.addEdge(cur.getId(), ut.getJavaSourceStartLineNumber(), label);
                    j += 1;
                }
                tmp = ju.getDefaultTarget();
                if (tmp != null)
                    ret.addEdge(cur.getId(), tmp.getJavaSourceStartLineNumber(), "default");
            } else if (u instanceof JLookupSwitchStmt) {
                JLookupSwitchStmt ju = (JLookupSwitchStmt)u;
                int j = 0;
                for (Unit ut : ju.getTargets()) {
                    if (ut == ju.getDefaultTarget()) continue;
                    String label = "== ";
                    if (isStringSwitch) {
                        label += labelsForSwitchStmt.get(i).get(j);
                    } else {
                        label += Integer.toString(ju.getLookupValue(j));
                    }
                    ret.addEdge(cur.getId(), ut.getJavaSourceStartLineNumber(), label);
                    j += 1;
                }
                tmp = ju.getDefaultTarget();
                if (tmp != null)
                    ret.addEdge(cur.getId(), tmp.getJavaSourceStartLineNumber(), "default");

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
        String className = "Program1";
        String outGraphName = "/home/student/out.dotty";
        String sootCP = "/home/student/cs610/bin";

        // Set soot settings
        String sootClassPath = Scene.v().getSootClassPath() + File.pathSeparator + sootCP;
        Scene.v().setSootClassPath(sootClassPath);
        Options.v().set_keep_line_number(true);
        Options.v().setPhaseOption("jb", "use-original-names");
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
