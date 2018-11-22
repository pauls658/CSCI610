package csci610.SymEx;

import com.microsoft.z3.*;
import com.microsoft.z3.Context;
import csci610.CFG.CFG;
import csci610.Graph.Edge;
import csci610.Graph.Graph;
import csci610.Graph.Node;
import csci610.RDDU.RDDU;
import fj.P;
import soot.*;
import soot.jimple.ConditionExpr;
import soot.jimple.IntConstant;
import soot.jimple.Jimple;
import soot.jimple.internal.*;
import soot.options.Options;
import soot.toolkits.graph.BriefUnitGraph;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class SymEx {

    private static HashMap<Unit, Integer> lineMap;
    private static boolean isFeasible2(BriefUnitGraph bug, int lineno, HashMap<Unit, Integer> linenoMap) {
        lineMap = linenoMap;
        Context c = new Context();

        // initialize the var map
        HashMap<Value, ArithExpr> varMap = new HashMap<>();
        Unit cur = bug.getHeads().get(0);
        while (cur instanceof JIdentityStmt) {
            JIdentityStmt ji = (JIdentityStmt)cur;
            IntExpr expr = c.mkIntConst(ji.getLeftOp().toString());
            varMap.put(ji.getLeftOp(), expr);
            cur = bug.getSuccsOf(cur).get(0);
        }

        Solver s = c.mkSolver();
        HashSet<Unit> visited = new HashSet<>();

        return isFeasible2(bug, lineno, cur, visited, varMap, s, c);
    }

    private static boolean isFeasible2(BriefUnitGraph bug, int lineno, Unit cur, HashSet<Unit> visited, HashMap<Value, ArithExpr> prevVarMap, Solver s, Context c) {
        if (visited.contains(cur) || s.check() == Status.UNSATISFIABLE) {
            return false;
        }
        if (lineno == lineMap.get(cur)) {
            return true;
        }

        HashMap<Value, ArithExpr> varMap = new HashMap<>(); // copy the var map
        for (Map.Entry<Value, ArithExpr> e : prevVarMap.entrySet()) {
            varMap.put(e.getKey(), e.getValue());
        }

        updateVarMap2(cur, varMap, c);

        if (bug.getSuccsOf(cur).size() > 1) {
            // branch node
            if (cur instanceof JIfStmt) {
                visited.add(cur);
                JIfStmt ji = (JIfStmt)cur;
                BoolExpr branchCond;

                for (Unit succ : bug.getSuccsOf(cur)) {
                    if (succ != ji.getTarget()) {
                        branchCond = c.mkNot(sootBoolExprtoZ3((ConditionExpr)ji.getCondition(), varMap, c));
                    } else {
                        branchCond = sootBoolExprtoZ3((ConditionExpr)ji.getCondition(), varMap, c);
                    }
                    s.push();
                    s.add(branchCond);
                    boolean res = isFeasible2(bug, lineno, succ, visited, varMap, s, c);
                    s.pop();
                    if (res) return true;
                }

                visited.remove(cur);
            } else if (cur instanceof JTableSwitchStmt) {
                JTableSwitchStmt jt = (JTableSwitchStmt)cur;
                visited.add(cur);
                int j = 0;
                Value switchVar = jt.getKey();
                BoolExpr defaultExpr = c.mkTrue();
                for (Unit succ : jt.getTargets()) {
                    if (succ == jt.getDefaultTarget()) {
                        j++;
                        continue; // do this last
                    }
                    IntNum switchVal = c.mkInt(j + jt.getLowIndex());
                    s.push();
                    BoolExpr caseExpr = makeZ3CaseExpr(switchVar, switchVal, varMap, c);
                    s.add(caseExpr);
                    boolean res = isFeasible2(bug, lineno, succ, visited, varMap, s, c);
                    s.pop();
                    if (res) return true;

                    defaultExpr = c.mkAnd(defaultExpr, c.mkNot(caseExpr));
                    j++;
                }

                // now do the default case
                s.push();
                s.add(defaultExpr);
                boolean res = isFeasible2(bug, lineno, jt.getDefaultTarget(), visited, varMap, s, c);
                if (res) return true;
                s.pop();

                visited.remove(cur);
            } else if (cur instanceof JLookupSwitchStmt) {
                JLookupSwitchStmt jl = (JLookupSwitchStmt)cur;
                visited.add(cur);

                int j = 0;
                Value switchVar = jl.getKey();
                BoolExpr defaultExpr = c.mkTrue();
                for (Unit succ : jl.getTargets()) {
                    if (succ == jl.getDefaultTarget()) {
                        j++;
                        continue; // save for last
                    }
                    IntNum switchVal = c.mkInt(jl.getLookupValue(j));
                    BoolExpr caseExpr = makeZ3CaseExpr(switchVar, switchVal, varMap, c);
                    s.push();
                    s.add(caseExpr);
                    boolean res = isFeasible2(bug, lineno, succ, visited, varMap, s, c);
                    s.pop();
                    if (res) return true;

                    defaultExpr = c.mkAnd(defaultExpr, c.mkNot(caseExpr));
                    j++;
                }

                // now do the default case
                s.push();
                s.add(defaultExpr);
                boolean res = isFeasible2(bug, lineno, jl.getDefaultTarget(), visited, varMap, s, c);
                if (res) return true;
                s.pop();

                visited.remove(cur);
            }

            return false;
        } else if (bug.getSuccsOf(cur).size() == 1) {
            // regular case
            Unit succ = bug.getSuccsOf(cur).get(0);
            visited.add(cur);
            boolean res = isFeasible2(bug, lineno, succ, visited, varMap, s, c);
            visited.remove(cur);
            return res;
        } else {
            // no successors
            return false;
        }
    }

    private static BoolExpr makeZ3CaseExpr(Value switchVar, IntNum switchVal, HashMap<Value, ArithExpr> varMap, Context c) {
        if (varMap.containsKey(switchVar)) {
            return c.mkEq(varMap.get(switchVar), switchVal);
        } else {
            return null;
        }
    }

    public static int varctr = 0;
    public static boolean isFeasible(Graph cfg, int lineno) {
        Context c = new Context();

        // initialize the var map
        HashMap<Value, ArithExpr> varMap = new HashMap<>();
        for (Unit u : cfg.getEntry().getUnits()) {
            if (u instanceof JIdentityStmt) {
                JIdentityStmt ju = (JIdentityStmt)u;
                IntExpr expr = c.mkIntConst(ju.getLeftOp().toString());
                varMap.put(ju.getLeftOp(), expr);
            }
        }

        Solver s = c.mkSolver();
        HashSet<Integer> visited = new HashSet<>();
        visited.add(-1);
        visited.add(-2); // no need to consider the exit
        int cur = cfg.getSuccsById(-1).get(0);

        return isFeasible(cfg, lineno, cur, visited, varMap, s, c);
    }



    private static boolean isFeasible(Graph cfg, int lineno, int cur, HashSet<Integer> visited, HashMap<Value, ArithExpr> prevVarMap, Solver s, Context c) {
        if (visited.contains(cur) || s.check() == Status.UNSATISFIABLE) {
            return false;
        }
        if (cur == lineno) {
            return true;
        }

        HashMap<Value, ArithExpr> varMap = new HashMap<>();
        for (Map.Entry<Value, ArithExpr> e : prevVarMap.entrySet()) {
            varMap.put(e.getKey(), e.getValue());
        }


        updateVarMap(cfg.getNode(cur), varMap, c);

        if (cfg.getSuccs(cur).size() > 1) {
            // branch node
            for (Edge e : cfg.getSuccs(cur)) {
                int succ = e.getEndId();
                visited.add(cur);
                BoolExpr branchCond;
                if (e.isTrueBranch()) {
                    branchCond = sootBoolExprtoZ3(e.getBranchExpr(), varMap, c);
                } else {
                    branchCond = c.mkNot(sootBoolExprtoZ3(e.getBranchExpr(), varMap, c));
                }
                s.push();
                s.add(branchCond);
                boolean res = isFeasible(cfg, lineno, succ, visited, varMap, s, c);
                s.pop();
                visited.remove(cur);

                if (res) // if sat, then stop
                    return true;
                // else try the other branch
            }
            return false;
        } else if (cfg.getSuccs(cur).size() == 1) {
            // regular case
            int succ = cfg.getSuccsById(cur).get(0);
            visited.add(cur);
            boolean res = isFeasible(cfg, lineno, succ, visited, varMap, s, c);
            visited.remove(cur);
            return res;
        } else {
            // no successors
            return false;
        }
    }

    private static BoolExpr sootBoolExprtoZ3(ConditionExpr branchExpr, HashMap<Value, ArithExpr> varMap, Context c) {
        ArithExpr op1 = getZ3Op(branchExpr.getOp1(), varMap, c);
        ArithExpr op2 = getZ3Op(branchExpr.getOp2(), varMap, c);
        if (branchExpr instanceof  JEqExpr) {
            return c.mkEq(op1, op2);
        } else if (branchExpr instanceof JGeExpr) {
            return c.mkGe(op1, op2);
        } else if (branchExpr instanceof JGtExpr) {
            return c.mkGt(op1, op2);
        } else if (branchExpr instanceof JLeExpr) {
            return c.mkLe(op1, op2);
        } else if (branchExpr instanceof JLtExpr) {
            return c.mkLt(op1, op2);
        } else if (branchExpr instanceof JNeExpr) {
            return c.mkNot(c.mkEq(op1, op2));
        } else {
            return null;
        }
    }

    private static void updateVarMap(Node node, HashMap<Value, ArithExpr> varMap, Context c) {
        for (Unit u : node.getUnits()) {
            if (u instanceof JAssignStmt) {
                JAssignStmt ju = (JAssignStmt)u;
                Value LHS = ju.getLeftOp();
                ArithExpr expr = sootExprToZ3Expr(ju.getRightOp(), varMap, c);
                varMap.put(LHS, expr);
            }
        }
    }

    private static void updateVarMap2(Unit u, HashMap<Value, ArithExpr> varMap, Context c) {
        if (u instanceof JAssignStmt) {
            JAssignStmt ju = (JAssignStmt)u;
            Value LHS = ju.getLeftOp();
            ArithExpr expr = sootExprToZ3Expr(ju.getRightOp(), varMap, c);
            varMap.put(LHS, expr);
        }
    }

    private static ArithExpr sootExprToZ3Expr(Value rightOp, HashMap<Value, ArithExpr> varMap, Context c) {
        ArithExpr expr = null;
        if (rightOp instanceof JAddExpr) {
            JAddExpr addExpr = (JAddExpr)rightOp;
            ArithExpr Z3Op1 = getZ3Op(addExpr.getOp1(), varMap, c);
            ArithExpr Z3Op2 = getZ3Op(addExpr.getOp2(), varMap, c);
            expr = c.mkAdd(Z3Op1, Z3Op2);
        } else if (rightOp instanceof JSubExpr) {
            JSubExpr subExpr = (JSubExpr)rightOp;
            ArithExpr Z3Op1 = getZ3Op(subExpr.getOp1(), varMap, c);
            ArithExpr Z3Op2 = getZ3Op(subExpr.getOp2(), varMap, c);
            expr = c.mkSub(Z3Op1, Z3Op2);
        } else {
            expr = getZ3Op(rightOp, varMap, c);
        }
        return expr;
    }

    private static ArithExpr getZ3Op(Value op1, HashMap<Value, ArithExpr> varMap, Context c) {
        if (op1 instanceof IntConstant) {
            return c.mkInt(((IntConstant)op1).value);
        } else if (op1 instanceof JimpleLocal) {
            if (varMap.containsKey(op1)) {
                return varMap.get(op1);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public static void main(String[] args) {
//        String className = "SymEx4";
//        String sootCP = "/home/student/cs610/bin";
//        String methodName = "func";
//        String linenoStr = "16";

        String sootCP = args[0];
        String className = args[1];
        String methodName = args[2];
        String linenoStr = args[3];

        int lineno = Integer.parseInt(linenoStr);

        // Set soot settings
        String sootClassPath = Scene.v().getSootClassPath() + File.pathSeparator + sootCP;
        Scene.v().setSootClassPath(sootClassPath);
        Options.v().set_keep_line_number(true);
        Options.v().setPhaseOption("jb", "use-original-names");
        SootClass sootClass = Scene.v().loadClassAndSupport(className);
        Scene.v().loadNecessaryClasses();
        sootClass.setApplicationClass();

        SootMethod sm = sootClass.getMethodByName(methodName);
        BriefUnitGraph bug = new BriefUnitGraph(sm.retrieveActiveBody());
        Graph cfg = CFG.CFGfromSootMethod(sm);

        HashMap<Unit, Integer> linenoMap = new HashMap<>();
        for (Node n : cfg.getNodes()) {
            for (Unit u : n.getUnits()) {
                linenoMap.put(u, n.getId());
            }
        }

        boolean b;
        try {
            b = isFeasible2(bug, lineno, linenoMap);
            //b = isFeasible(cfg, lineno);
        } catch (Exception e) {
            b = true;
        }

        System.out.println((b ? "Feasible" : "Infeasible"));

        return;
    }

}
