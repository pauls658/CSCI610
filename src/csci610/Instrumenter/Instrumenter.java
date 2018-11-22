package csci610.Instrumenter;

import csci610.CFG.CFG;
import csci610.Graph.Graph;
import polyglot.ast.Assign;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JIdentityStmt;
import soot.jimple.internal.JReturnStmt;
import soot.jimple.internal.JReturnVoidStmt;
import soot.jimple.toolkits.annotation.logic.Loop;
import soot.options.Options;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.LoopNestTree;
import soot.util.JasminOutputStream;

import java.io.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.stream.Collectors;

import static soot.Scene.v;
import static soot.SootClass.SIGNATURES;

public class Instrumenter {
    public static void main(String[] args) throws IOException {
        String className = "Subject3";
        String sootCP = "/home/student/cs610/bin";
        String outDir = "/home/student/IdeaProjects/CSCI610/sootOutput";

        String sootClassPath = v().getSootClassPath() + File.pathSeparator + sootCP;
        v().setSootClassPath(sootClassPath);
        Options.v().set_keep_line_number(true);
        Options.v().setPhaseOption("jb", "use-original-names");
        Options.v().set_output_dir(outDir);
        SootClass sootClass = v().loadClassAndSupport(className);
        v().addBasicClass("java.lang.System", SIGNATURES);
        v().addBasicClass("java.io.PrintStream", SIGNATURES);
        v().loadBasicClasses();
        v().loadNecessaryClasses();
        sootClass.setApplicationClass();

        for (SootMethod m : sootClass.getMethods()) {
            m.retrieveActiveBody();
        }

        SootMethod sm = sootClass.getMethodByName("main");
        Body body = sm.retrieveActiveBody();
        BriefUnitGraph bug = new BriefUnitGraph(body);
        LoopNestTree loopTree = new LoopNestTree(body);
        Graph g = CFG.CFGfromSootMethod(sm);

        addCounterField(sootClass);
        addLoopIncr(sootClass, body, loopTree, g);
        transformAppends(body, bug);


        String filename = SourceLocator.v().getFileNameFor(sootClass, Options.output_format_class);
        File f = new File(filename);
        f.getParentFile().mkdirs();
        if (f.exists()) {
            f.delete();
        }
        f.createNewFile();
        OutputStream o = new JasminOutputStream(new FileOutputStream(f));
        PrintWriter p = new PrintWriter(new OutputStreamWriter(o));
        JasminClass jc = new JasminClass(sootClass);
        jc.print(p);
        p.flush();
        p.close();
        //o.close();
    }

    private static StaticFieldRef loopCtr;
    private static Local systemOutTmp;
    private static Local loopCtrTmp;

    private static void addLoopIncr(SootClass sootClass, Body body, LoopNestTree loopTree, Graph g) {
        UnitPatchingChain units = body.getUnits();

        // First init the loop ctr to 0
        AssignStmt a = Jimple.v().newAssignStmt(loopCtr, LongConstant.v(0));
        Unit insertBeforePoint = units.getFirst();
        while (insertBeforePoint instanceof JIdentityStmt) {
            insertBeforePoint = units.getSuccOf(insertBeforePoint);
        }
        units.insertBefore(a, insertBeforePoint);

        // now figure out the first instruction of each loop body.
        // we will insert the increment just before this point

        // first figure out the lineno of the loop header
        HashSet<Integer> loopHeaderLineNos = new HashSet<>();
        for (Loop l : loopTree) {
            loopHeaderLineNos.add(l.getHead().getJavaSourceStartLineNumber());
        }

        Integer maxLineNo = Collections.max(g.getNodes().stream().map(node -> node.getId()).collect(Collectors.toList()));

        HashSet<Integer> instrLinenos = new HashSet<>();
        for (Integer headerLineno : loopHeaderLineNos) {
            Integer cur = headerLineno + 1;
            while (g.getNode(cur) == null && cur < maxLineNo) cur++;
            instrLinenos.add(cur);
        }

        for (Integer instrPoint : instrLinenos) {
            Unit firstUnit = g.getNode(instrPoint).getUnits().getFirst();
            addIncr(body, firstUnit);
        }

    }

    private static void addIncr(Body body, Unit firstUnit) {
        AssignStmt a1 = Jimple.v().newAssignStmt(loopCtrTmp, loopCtr);
        body.getUnits().insertBefore(a1, firstUnit);

        AssignStmt a2 = Jimple.v().newAssignStmt(loopCtrTmp,
                Jimple.v().newAddExpr(loopCtrTmp, LongConstant.v(1)));
        body.getUnits().insertAfter(a2, a1);
        AssignStmt a3 = Jimple.v().newAssignStmt(loopCtr, loopCtrTmp);
        body.getUnits().insertAfter(a3, a2);
    }

    private static void addCounterField(SootClass sootClass) {
        SootField loopCtrField = new SootField("loopCtr", LongType.v(), Modifier.STATIC);
        sootClass.addField(loopCtrField);
        SootFieldRef tmp = sootClass.getFieldByName("loopCtr").makeRef();
        loopCtr = Jimple.v().newStaticFieldRef(tmp);
        systemOutTmp = Jimple.v().newLocal("systemOutTmp", RefType.v("java.io.PrintStream"));
        loopCtrTmp = Jimple.v().newLocal("loopCtrTmp", loopCtr.getType());

        Body b = sootClass.getMethodByName("main").retrieveActiveBody();
        b.getLocals().add(systemOutTmp);
        b.getLocals().add(loopCtrTmp);
    }

    private static void transformAppends(Body body, BriefUnitGraph bug) {
        UnitPatchingChain units = body.getUnits();
        LinkedList<Unit> appendCalls = new LinkedList<>();
        for (Unit u : units) {
            Stmt s = (Stmt)u;
            if (s.containsInvokeExpr()) {
                InvokeExpr i = s.getInvokeExpr();
                SootMethod sm = i.getMethod();
                if (sm.getName().equals("append") && sm.getDeclaringClass().getName().equals("java.lang.StringBuffer")) {
                    appendCalls.add(u);
                }
            } else if (u instanceof JReturnVoidStmt || u instanceof JReturnStmt) {
                appendCalls.add(u);
            }
        }

        SootFieldRef systemOutField = Scene.v().getField("<java.lang.System: java.io.PrintStream out>").makeRef();
        StaticFieldRef systemOutFieldRef = Jimple.v().newStaticFieldRef(systemOutField);
        SootMethodRef printStringMethod = Scene.v().getMethod("<java.io.PrintStream: void print(java.lang.String)>").makeRef();
        SootMethodRef printLongMethod = Scene.v().getMethod("<java.io.PrintStream: void println(long)>").makeRef();
        for (Unit u : appendCalls) {
            AssignStmt a = Jimple.v().newAssignStmt(systemOutTmp, systemOutFieldRef);
            units.insertBefore(a, u);
            AssignStmt a2 = Jimple.v().newAssignStmt(loopCtrTmp, loopCtr);
            units.insertAfter(a2, a);

            StringConstant s = StringConstant.v("Total number of iterations is: ");
            InvokeStmt i = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(systemOutTmp, printStringMethod, s));
            units.insertAfter(i, a2);
            InvokeStmt i2 = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(systemOutTmp, printLongMethod, loopCtrTmp));
            units.insertAfter(i2, i);

            if (((Stmt)u).containsInvokeExpr()) {
                units.remove(u);
            }
        }

        body.validate();
    }
}
