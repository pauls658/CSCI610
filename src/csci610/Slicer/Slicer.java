package csci610.Slicer;

import csci610.CFG.CFG;
import csci610.Graph.Edge;
import csci610.Graph.Graph;
import csci610.Graph.Node;
import csci610.Graph.PostOrderTraverser;
import csci610.RDDU.Definition;
import csci610.RDDU.RDDU;
import fj.Hash;
import soot.PhaseOptions;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.options.Options;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.UnitGraph;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

public class Slicer {

    public static HashMap<Integer, HashSet<Integer>> dominanceFrontiers2(Graph cfg) {
        PostOrderTraverser p = new PostOrderTraverser();
        LinkedList<Node> nodeOrder = p.postOrder(cfg);
        HashMap<Integer, Integer> postOrderNumbers = new HashMap<>();
        int ctr = 0;
        for (Node n : nodeOrder) {
            postOrderNumbers.put(n.getId(), ctr);
            ctr++;
        }

        Collections.reverse(nodeOrder);
        HashMap<Integer, Integer> idom = new HashMap<>();
        for (Node n : nodeOrder) {
             idom.put(n.getId(), null);
        }
        idom.put(cfg.getEntry().getId(), cfg.getEntry().getId());
        nodeOrder.remove(cfg.getEntry());

        boolean changed = true;
        Integer newIdom;
        List<Integer> tmp;
        while (changed) {
            changed = false;
            for (Node n : nodeOrder) {
                tmp = cfg.getPredsById(n.getId()).stream().filter(x -> idom.get(x) != null).collect(Collectors.toList());
                newIdom = tmp.get(0);
                for (Integer pred : tmp.subList(1,tmp.size())) {
                    if (idom.get(pred) != null) {
                        newIdom = intersect(idom, postOrderNumbers, pred, newIdom);
                    }
                }
                if (idom.get(n.getId()) != newIdom) {
                    idom.put(n.getId(), newIdom);
                    changed = true;
                }
            }
        }

        Integer runner;
        HashMap<Integer,HashSet<Integer>> domFrontier = new HashMap<>();
        for (Node n : cfg.getNodes()) {
            domFrontier.put(n.getId(), new HashSet<>());
        }
        for (Node n : cfg.getNodes()) {
            if (cfg.getPreds(n.getId()).size() >= 2) {
                for (Integer pred : cfg.getPredsById(n.getId())) {
                    runner = pred;
                    while (runner != idom.get(n.getId())) {
                        domFrontier.get(runner).add(n.getId());
                        runner = idom.get(runner);
                    }
                }
            }
        }
        return domFrontier;
    }

    private static Integer intersect(HashMap<Integer, Integer> idom, HashMap<Integer, Integer> postOrderNumbers, Integer b1, Integer b2) {
        Integer finger1 = b1;
        Integer finger2 = b2;
        while (finger1 != finger2) {
            while (postOrderNumbers.get(finger1) < postOrderNumbers.get(finger2)) {
                finger1 = idom.get(finger1);
            }
            while (postOrderNumbers.get(finger2) < postOrderNumbers.get(finger1)) {
                finger2 = idom.get(finger2);
            }
        }
        return finger1;
    }

    public static HashMap<Integer, HashSet<Integer>> dominanceFrontiers(Graph cfg) {
        PostOrderTraverser p = new PostOrderTraverser();
        LinkedList<Node> nodeOrder = p.postOrder(cfg);

        HashMap<Integer, HashSet<Integer>> dominators = new HashMap<>();
        HashSet<Integer> init;
        for (Node n : nodeOrder) {
            init = new HashSet<>();
            for (Node n1 : nodeOrder)
                init.add(n1.getId());
             dominators.put(n.getId(), init);
        }
        dominators.get(cfg.getEntry().getId()).clear();
        dominators.get(cfg.getEntry().getId()).add(cfg.getEntry().getId());
        nodeOrder.remove(cfg.getEntry());

        HashSet<Integer> newDoms = new HashSet<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Node n : nodeOrder) {
                newDoms.clear();

                // Compute the intersection of all of our predecessors
                List<Integer> preds = cfg.getPredsById(n.getId());
                newDoms.addAll( dominators.get(preds.get(0)));
                for (Integer pred : preds) {
                    newDoms.retainAll( dominators.get(pred));
                }
                newDoms.add(n.getId());

                // See if things changed
                if (!newDoms.equals( dominators.get(n.getId()))) {
                    changed = true;
                     dominators.get(n.getId()).clear();
                     dominators.get(n.getId()).addAll(newDoms);
                }
            }
        }

        HashSet<Integer> tmp = new HashSet<>();
        HashMap<Integer, Integer> idom = new HashMap<>();

        HashMap<Integer, HashSet<Integer>> domFrontier = new HashMap<>();
        for (Node n : nodeOrder) {
            if (cfg.getPreds(n.getId()).size() >= 2) {
                for (Edge e : cfg.getPreds(n.getId())) {
                    Node pred = e.getStart();
                }
            }
        }

        return  dominators;
    }

    public static void main(String[] args) throws IOException {
//        String sootCP = "/home/student/cs610/bin";
//        String className = "Program1";
//        String outFile = "/home/student/out.txt";
//        String srcLineno = "31";
//        String varName = "cars";

        String sootCP = args[0];
        String className = args[1];
        String outFile = args[2];
        String srcLineno = args[3];
        String varName = args[4];

        // Set soot settings
        String sootClassPath = Scene.v().getSootClassPath() + File.pathSeparator + sootCP;
        Scene.v().setSootClassPath(sootClassPath);
        Options.v().set_keep_line_number(true);
        Options.v().setPhaseOption("jb", "use-original-names");
//        Options.v().setPhaseOption("jb", "preserve-source-annotations");
//        Options.v().setPhaseOption("jb.a", "enabled:false");
//        Options.v().setPhaseOption("jb.ule", "enabled:false");
//        Options.v().setPhaseOption("jb.ls", "enabled:false");
//        Options.v().setPhaseOption("jb.cp", "enabled:false");
//        Options.v().setPhaseOption("jb.ulp", "enabled:false");
//        Options.v().setPhaseOption("wjop", "off");
//        Options.v().setPhaseOption("jop", "off");
//        PhaseOptions.v().setPhaseOption("jb.a", "only-stack-locals:false");
//        PhaseOptions.v().setPhaseOption("jb.a", "off");
        SootClass sootClass = Scene.v().loadClassAndSupport(className);
        Scene.v().loadNecessaryClasses();
        sootClass.setApplicationClass();

        SootMethod sm = sootClass.getMethodByName("main");
        Graph cfg = CFG.CFGfromSootMethod(sm);
        HashMap<Integer, HashSet<Definition>> dataDeps = RDDU.getDataDeps(cfg);
        cfg.reverse();
        HashMap<Integer, HashSet<Integer>> domFrontier = dominanceFrontiers2(cfg);
        HashSet<Integer> slice = computeSlice(domFrontier, dataDeps, Integer.parseInt(srcLineno), varName);

        String tmp = "";
        for (Integer i : slice) {
            if (i == -1)
                tmp += "entry\t";
            else if (i == -2)
                tmp += "exit\t";
            else
                tmp += i.toString() + "\t";
        }

        File out = new File(outFile);
        if (out.exists()) {
            out.delete();
        }
        out.createNewFile();
        PrintWriter p = new PrintWriter(out);
        p.write(tmp);
        p.close();
    }

    private static HashSet<Integer> computeSlice(HashMap<Integer, HashSet<Integer>> ctrlDeps, HashMap<Integer, HashSet<Definition>> dataDeps, Integer lineno, String varName) {
        HashSet<Integer> slice = new HashSet<>();
        LinkedList<Integer> work = new LinkedList<>();

        slice.add(lineno);
        for (Definition d : dataDeps.get(lineno)) {
            if (d.var.equals(varName))
                work.add(d.node);
        }
        for (Integer c : ctrlDeps.get(lineno)) {
            work.add(c);
        }

        Integer cur;
        while (!work.isEmpty()) {
            cur = work.pop();
            if (slice.contains(cur)) continue;
            slice.add(cur);

            work.addAll(ctrlDeps.get(cur));
            work.addAll(dataDeps.get(cur).stream().map(d -> d.node).collect(Collectors.toList()));
        }

        return slice;
    }
}
