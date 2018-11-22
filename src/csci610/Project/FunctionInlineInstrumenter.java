package csci610.Project;

import soot.*;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.jimple.toolkits.callgraph.CHATransformer;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.util.Chain;
import sun.awt.image.ImageWatched;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Collectors;

public class FunctionInlineInstrumenter extends SceneTransformer {
    // Prints out the edges for neo4j
//    @Override
//    protected void internalTransform(String s, Map<String, String> map) {
//        CallGraph cg = Scene.v().getCallGraph();
//        // Id map
//        HashMap<SootMethod,Integer> nodes = new HashMap<>();
//        Integer idCtr = 0;
//        // Get nodes
//        for (Edge e : cg) {
//            if (e.getSrc().method().isJavaLibraryMethod() ||
//            e.getTgt().method().isJavaLibraryMethod()) continue;
//            if (!nodes.containsKey(e.tgt())) {
//                nodes.put(e.tgt(), idCtr);
//                idCtr++;
//            }
//            if (!nodes.containsKey(e.src())) {
//                nodes.put(e.src(), idCtr);
//                idCtr++;
//            }
//        }
//
//        File nodesFile = new File("/home/student/nodes.csv");
//        if (nodesFile.exists()) {
//            nodesFile.delete();
//        }
//        PrintWriter writer = null;
//        try {
//            nodesFile.createNewFile();
//            writer = new PrintWriter(nodesFile);
//        } catch (IOException e) {
//            e.printStackTrace();
//            System.exit(1);
//        }
//        writer.printf(":ID,methodName,:LABEL\n");
//        for (Map.Entry<SootMethod, Integer> e : nodes.entrySet()) {
//            writer.printf("%d,%s,METHOD\n", e.getValue(), e.getKey().getName());
//        }
//        writer.close();
//
//        File edgesFile = new File("/home/student/edges.csv");
//        if (edgesFile.exists()) {
//            edgesFile.delete();
//        }
//        try {
//            edgesFile.createNewFile();
//            writer = new PrintWriter(edgesFile);
//        } catch (IOException e) {
//            e.printStackTrace();
//            System.exit(1);
//        }
//        // Get edges
//        writer.printf(":START_ID,:END_ID,:TYPE\n");
//        for (Edge e : cg) {
//            if (e.getSrc().method().isJavaLibraryMethod() ||
//            e.getTgt().method().isJavaLibraryMethod()) continue;
//
//            writer.printf("%d,%d,CALLS\n", nodes.get(e.src()), nodes.get(e.tgt()));
//        }
//        writer.close();
//    }

    @Override
    protected void internalTransform(String phaseName, Map<String, String> map) {
        CallGraph callGraph = Scene.v().getCallGraph();
        CallGraph userCallGraph = new CallGraph();
        for (Edge e : callGraph) {
            if (e.getSrc().method().isJavaLibraryMethod() ||
            e.getTgt().method().isJavaLibraryMethod()) continue;
            if (e.getSrc().method().getDeclaringClass().getPackageName().startsWith("android.") ||
            e.getTgt().method().getDeclaringClass().getPackageName().startsWith("android.")) continue;

            userCallGraph.addEdge(e);
        }

        Edge e = userCallGraph.iterator().next();

        Chain<Unit> methodUnits = e.getSrc().method().getActiveBody().getUnits();
        InvokeExpr iu = null;
        Stmt s = null;
        for (Unit u : methodUnits) { // TODO: find out can there be more than one JInvoke in a Unit?
            s = (Stmt)u;
            if (s.containsInvokeExpr() && s.getInvokeExpr().getMethod() == e.tgt()) {
                iu = s.getInvokeExpr();
                break;
            }
        }

        inlineFunction(e.src(), s, iu, iu.getMethod());
        methodUnits.remove(s);
    }


    // Note, its possible that stmt == iu
    // Two cases:
    // (1) the stmt is just an invoke stmt
    //      if this
    // (2) the stmt is an assign
    //     we need get the RHS and replace return statements with an assign to it
    //

    /**
     * Inlines a function call. Note that the user should delete the unit containing the
     * function call after this function is executed.
     * @param caller the caller method
     * @param stmt the unit with the function call. the inlined function should be placed
     *             just before this unit. this unit should be deleted by the user after
     *             the call to this function
     * @param iu the function call
     * @param callee the function being called. this function will be inlined into units
     */
    private void inlineFunction(SootMethod caller, Stmt stmt, InvokeExpr iu, SootMethod callee) {
        Chain<Unit> calleeUnits = callee.getActiveBody().getUnits();
        Chain<Unit> callerUnits = caller.getActiveBody().getUnits();
        // callee var -> caller var
        HashMap<Value, Value> varMap = new HashMap<>();
        // callee stmt -> caller stmt
        HashMap<Unit, Unit> stmtMap = new HashMap<>();
        // Units that we need to resolve a target for
        HashMap<Unit, LinkedList<Unit>> unresolvedTargets = new HashMap<>();

        // Dummystatement for targets we can't immediately resolve
        Unit dummyStmt = Jimple.v().newNopStmt();
        callerUnits.insertBefore(dummyStmt, stmt);


        // Final statement for the callee. This will be the target
        // of return statements
        Unit finalStmt = Jimple.v().newNopStmt();
        caller.getActiveBody().getUnits().insertAfter(finalStmt, stmt);
        Unit prevStmt = stmt;
        for (Unit u : calleeUnits) {
            Stmt newStmt = null;
            if (u instanceof JIdentityStmt) {
                JIdentityStmt ji = (JIdentityStmt)u;
                Value lhs, rhs;
                lhs = copyVariable(caller, ji.getLeftOp(), varMap);
                if (ji.getRightOp() instanceof ThisRef) {
                    rhs = ((JVirtualInvokeExpr)iu).getBase();
                } else if (ji.getRightOp() instanceof ParameterRef) {
                    rhs = iu.getArg(((ParameterRef)ji.getRightOp()).getIndex());
                } else {
                    // TODO: Caught exception ref
                    rhs = null;
                }

                newStmt = newAssign(lhs, rhs);
                stmtMap.put(u, newStmt);

            } else if (u instanceof JAssignStmt) {
                JAssignStmt ja = (JAssignStmt)u;
                Value lhs, rhs;
                lhs = copyVariable(caller, ja.getLeftOp(), varMap);
                rhs = copyRValue(caller, ja.getRightOp(), varMap);

                newStmt = newAssign(lhs, rhs);
                stmtMap.put(u, newStmt);

            } else if (u instanceof JReturnStmt) {
                // we only add an assignment if the return value is used
                // i.e. when the calling stmt is an assign stmt
                if (stmt instanceof JAssignStmt) {
                    JReturnStmt jr = (JReturnStmt) u;
                    Value lhs, rhs;
                    // the calling stmt is an assign stmt
                    lhs = ((JAssignStmt) stmt).getLeftOp();
                    rhs = copyVariable(caller, jr.getOp(), varMap);

                    newStmt = newAssign(lhs, rhs);
                    callerUnits.insertAfter(newStmt,prevStmt);
                    stmtMap.put(u, newStmt);
                    prevStmt = newStmt;
                    newStmt = Jimple.v().newGotoStmt(finalStmt);
                } else {
                    newStmt = Jimple.v().newGotoStmt(finalStmt);
                }
            } else if (u instanceof JReturnVoidStmt) {
                newStmt = Jimple.v().newGotoStmt(finalStmt);
                stmtMap.put(u, newStmt);
            } else if (u instanceof JIfStmt) {
                JIfStmt ji = (JIfStmt)u;
                Value expr = copyBinOpExpr(caller, (BinopExpr)ji.getCondition(), varMap);
                Unit target;
                if (stmtMap.containsKey(ji.getTarget())) {
                    target = stmtMap.get(ji.getTarget());
                } else {
                    target = dummyStmt; // temporary
                }
                newStmt = Jimple.v().newIfStmt(expr, target);
                stmtMap.put(u, newStmt);
                if (target == dummyStmt) {
                    if (!unresolvedTargets.containsKey(ji.getTarget()))
                        unresolvedTargets.put(ji.getTarget(), new LinkedList<>());
                    unresolvedTargets.get(ji.getTarget()).add(newStmt);
                }
            } else if (u instanceof JGotoStmt) {
                JGotoStmt jg = (JGotoStmt)u;
                Unit target;
                if (stmtMap.containsKey(jg.getTarget())) {
                    target = stmtMap.get(jg.getTarget());
                } else {
                    target = dummyStmt; // temporary
                }
                newStmt = Jimple.v().newGotoStmt(target);
                stmtMap.put(u, newStmt);

                if (target == dummyStmt) {
                    if (!unresolvedTargets.containsKey(jg.getTarget()))
                        unresolvedTargets.put(jg.getTarget(), new LinkedList<>());
                    unresolvedTargets.get(jg.getTarget()).add(newStmt);
                }
            }


            callerUnits.insertAfter(newStmt, prevStmt);
            prevStmt = newStmt;

            // resolve an targets that we couldn't previously
            if (unresolvedTargets.containsKey(u)) {
                Unit targetUnit = stmtMap.get(u);
                // Get the callee units that point to us
                for (Unit pointsToU : unresolvedTargets.get(u)) {
                        // we processed pointsToU before the target
                    if (pointsToU instanceof JIfStmt) {
                        ((JIfStmt)pointsToU).setTarget(targetUnit);
                    } else if (pointsToU instanceof JGotoStmt) {
                        ((JGotoStmt)pointsToU).setTarget(targetUnit);
                    }
                }
            }

            unresolvedTargets.remove(u);
        }

        assert dummyStmt.getBoxesPointingToThis().size() == 0;
        callerUnits.remove(dummyStmt);
    }

    private void addStmt(Chain<Unit> callerUnits, Stmt newStmt, Unit prevStmt, HashMap<Unit, Unit> stmtMap) {
    }


    private Value copyBinOpExpr(SootMethod caller, BinopExpr condition, HashMap<Value, Value> varMap) {
        // the ops can only be immediates, i.e. a local or a constant
        Value op1 = copyVariable(caller, condition.getOp1(), varMap);
        Value op2 = copyVariable(caller, condition.getOp2(), varMap);
        if (condition instanceof JAddExpr) {
            return Jimple.v().newAddExpr(op1, op2);
        } else if (condition instanceof JAndExpr) {
            return Jimple.v().newAndExpr(op1, op2);
        } else if (condition instanceof JCmpExpr) {
            return Jimple.v().newCmpExpr(op1, op2);
        } else if (condition instanceof JCmpgExpr) {
            return Jimple.v().newCmpgExpr(op1, op2);
        } else if (condition instanceof JCmplExpr) {
            return Jimple.v().newCmplExpr(op1, op2);
        } else if (condition instanceof JDivExpr) {
            return Jimple.v().newDivExpr(op1, op2);
        } else if (condition instanceof JEqExpr) {
            return Jimple.v().newEqExpr(op1, op2);
        } else if (condition instanceof JGeExpr) {
            return Jimple.v().newGeExpr(op1, op2);
        } else if (condition instanceof JGtExpr) {
            return Jimple.v().newGtExpr(op1, op2);
        } else if (condition instanceof JLeExpr) {
            return Jimple.v().newLeExpr(op1, op2);
        } else if (condition instanceof JLtExpr) {
            return Jimple.v().newLtExpr(op1, op2);
        } else if (condition instanceof JMulExpr) {
            return Jimple.v().newMulExpr(op1, op2);
        } else if (condition instanceof JNeExpr) {
            return Jimple.v().newNeExpr(op1, op2);
        } else if (condition instanceof JOrExpr) {
            return Jimple.v().newOrExpr(op1, op2);
        } else if (condition instanceof JRemExpr) {
            return Jimple.v().newRemExpr(op1, op2);
        } else if (condition instanceof JShlExpr) {
            return Jimple.v().newShlExpr(op1, op2);
        } else if (condition instanceof JShrExpr) {
            return Jimple.v().newShrExpr(op1, op2);
        } else if (condition instanceof JSubExpr) {
            return Jimple.v().newSubExpr(op1, op2);
        } else if (condition instanceof JUshrExpr) {
            return Jimple.v().newUshrExpr(op1, op2);
        } else if (condition instanceof JXorExpr) {
            return Jimple.v().newXorExpr(op1, op2);
        } else {
            return null;
        }
    }


    private int varCtr = 0;
    private String varPrefix = "BPBPV";
    /**
     * Copies a variable. A variable is a: array ref, instance field ref, static field ref, local, or constant.
     * In the jimple grammar, this includes: immediate and variable
     * @param caller caller method
     * @param v the value in of the callee. we need to convert this to a local of the caller
     * @param varMap the variable map
     */
    private Value copyVariable(SootMethod caller, Value v, HashMap<Value, Value> varMap) {
        if (v instanceof JimpleLocal) {
            if (varMap.containsKey(v)) {
                return varMap.get(v);
            } else {
                Local newV = Jimple.v().newLocal(varPrefix + String.valueOf(varCtr), v.getType());
                caller.getActiveBody().getLocals().add(newV);
                varCtr++;
                varMap.put(v, newV);
                return newV;
            }
        } else if (v instanceof Constant) {
            return v; // I think this will work lol...
        } else {
            return null;
        }
    }

    private Value copyRValue(SootMethod caller, Value rightOp, HashMap<Value, Value> varMap) {
        if (rightOp instanceof InvokeExpr) {
            return copyInvokeExpr(caller, (InvokeExpr)rightOp, varMap);
        } else {
            return null;
        }
    }

    /**
     * Copies an invoke expr. Note that all the necessary variables should have been defined at
     * this point.
     * @param caller The caller of the method we are inlining.
     * @param rightOp The invoke expr to copy
     * @param varMap The current variable mapping
     * @return
     */
    private Value copyInvokeExpr(SootMethod caller, InvokeExpr rightOp, HashMap<Value, Value> varMap) {
        LinkedList<Value> mappedArgList = new LinkedList<>();
        for (Value arg : rightOp.getArgs()) {
            mappedArgList.add(varMap.get(arg));
        }

        if (rightOp instanceof JVirtualInvokeExpr) {
            Local baseVar = (Local) ((JVirtualInvokeExpr) rightOp).getBase();
            Local mappedBaseVar = (Local) varMap.get(baseVar);
            return (JVirtualInvokeExpr)
                    Jimple.v().newVirtualInvokeExpr(
                            mappedBaseVar,
                            rightOp.getMethodRef(),
                            mappedArgList);

        }

        return null;
    }

    private AssignStmt newAssign(Value lhs, Value rhs) {
        return Jimple.v().newAssignStmt(lhs, rhs);
    }

}
