package csci610.RDDU;

import csci610.CFG.CFG;
import csci610.Graph.Edge;
import csci610.Graph.Graph;
import csci610.Graph.Node;
import csci610.Graph.PostOrderTraverser;
import soot.*;
import soot.jimple.internal.JimpleLocal;
import soot.options.Options;
import sun.awt.image.ImageWatched;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class RDDU {

    private static LinkedList<String> getSymbols(List<ValueBox> vbs) {
        LinkedList<String> ret = new LinkedList<>();
        for (ValueBox vb : vbs) {
            if (vb.getValue() instanceof JimpleLocal && !vb.getValue().toString().startsWith("$")) {
                String sym = vb.getValue().toString();
                sym = sym.replaceAll("#.*", "");
                ret.add(sym);
            }
        }
        return ret;
    }

    private static LinkedList<String> getDefsForUnit(Unit u) {
        return getSymbols(u.getDefBoxes());
    }

    private static LinkedList<String> getUsesForUnit(Unit u) {
        return getSymbols(u.getUseBoxes());
    }

    private static HashMap<Integer, HashSet<Definition>> reachingDefs(Graph udg) {
        HashMap<Integer, HashSet<Definition>> in = new HashMap<>();
        HashMap<Integer, HashSet<Definition>> out = new HashMap<>();

        for (Node n : udg.getNodes()) {
            in.put(n.getId(), new HashSet<Definition>());
            out.put(n.getId(), new HashSet<Definition>());
        }

        PostOrderTraverser p = new PostOrderTraverser();
        LinkedList<Node> nodeOrder = p.postOrder(udg);
        Collections.reverse(nodeOrder);
        HashSet<Definition> tmp;
        HashSet<Definition> newOut = new HashSet<Definition>();
        boolean changed = true; int iters = 0;
        while (changed) {
            changed = false;
            for (Node n : nodeOrder) {
                tmp = in.get(n.getId());
                for (Edge e : udg.getPreds(n.getId())) {
                    tmp.addAll(out.get(e.getStartId()));
                }

                newOut.clear();
                newOut.addAll(tmp);
                newOut.removeIf((Definition d) -> n.getDefs().contains(d.var));
                for (String s : n.getDefs()) {
                    newOut.add(new Definition(s, n.getId()));
                }
                if (!newOut.equals(out.get(n.getId()))) {
                    changed = true;
                    out.get(n.getId()).addAll(newOut);
                }
            }
            iters++;
        }
        //System.out.println(iters);

        return in;
    }

    public static void makeDefUseGraph(Graph g) {

        for (Node n : g.getNodes()) {
            if (n.isStringSwitch()) {
                n.getUses().addAll(getUsesForUnit(n.getUnits().get(0)));
            } else {
                for (Unit u : n.getUnits()) {
                    n.addUse(getUsesForUnit(u));
                    n.addDef(getDefsForUnit(u));
                }
            }
        }
    }

    public static HashMap<Integer, HashSet<Definition>> getDataDeps(Graph cfg) {
        makeDefUseGraph(cfg);
        HashMap<Integer, HashSet<Definition>> in = reachingDefs(cfg);
        HashMap<Integer, HashSet<Definition>> dataDeps = new HashMap<>();

        HashSet<Definition> rds;
        String useLoc;
        String defLoc;
        for (Node n : cfg.getNodes()) {
            dataDeps.put(n.getId(), new HashSet<>());
            rds = in.get(n.getId());
            useLoc = n.getNodeEnc();
            for (Definition d : rds) {
                if (n.getUses().contains(d.var)) {
                    // we have a def that reaches here, but we want to ignore when it's a
                    // previous declaration of a loop variable
                    if (n.isForLoop() && // this is a for looop
                        n.loopVar().equals(d.var) && // the reaching def is that same as our loop var
                        d.node < n.getId()) // the def occurs earlier in the program
                        continue;

                    dataDeps.get(n.getId()).add(d);
                }
            }
        }

        return dataDeps;
    }

    private static void writeDUChains(Graph defUseGraph, HashMap<Integer, HashSet<Definition>> in, String outGraphName) throws IOException {
        File dotFile = new File(outGraphName);
        if (dotFile.exists()) {
            dotFile.delete();
        }
        dotFile.createNewFile();
        PrintWriter writer = new PrintWriter(dotFile);


        writer.print("digraph control_flow_graph {\n" +
                "    node [shape = rectangle];\n" +
                "    node [shape = rectangle];\n" +
                "    node [fontname = \"fixed\"];\n" +
                "    edge [fontname = \"fixed\"];\n\n");


        HashSet<Definition> rds;
        String useLoc;
        String defLoc;
        for (Node n : defUseGraph.getNodes()) {
            rds = in.get(n.getId());
            useLoc = n.getNodeEnc();
            for (Definition d : rds) {
                if (n.getUses().contains(d.var)) {
                    defLoc = defUseGraph.getNode(d.node).getNodeEnc();
                    writer.printf("\t\"%s, %s\" -> %s;\n", d.var, defLoc, useLoc);
                }
            }
        }


        writer.print("\n}");
        writer.close();
    }


    private static void writeReachingDefs(Graph defUseGraph, HashMap<Integer, HashSet<Definition>> in, String outReachingDefs) throws IOException {
        File rdFile = new File(outReachingDefs);
        if (rdFile.exists()) {
            rdFile.delete();
        }
        rdFile.createNewFile();
        PrintWriter writer = new PrintWriter(rdFile);

        TreeMap<Integer, HashSet<Definition>> sortedIn = new TreeMap<>(in);

        String defLoc, nLoc;
        for (Map.Entry<Integer, HashSet<Definition>> e : sortedIn.entrySet()) {
            if (e.getKey() < 0) continue; // skip entry and exit
            nLoc = defUseGraph.getNode(e.getKey()).getNodeEnc();
            writer.printf("%s", nLoc);
            for (Definition d : e.getValue()) {
                defLoc = defUseGraph.getNode(d.node).getNodeEnc();
                writer.printf("\t<%s, %s>", d.var, defLoc);
            }
            writer.printf("\n");
        }

        writer.close();
    }

    public static void main(String[] args) throws IOException {
        //String className = args[0];
        //String outGraphName = args[1];
        //String outReachingDefs = args[2];
        //String sootCP = args[3];
        String className = "Example4";
        String outGraphName = "/home/student/out.dotty";
        String sootCP = "/home/student/cs610/bin";
        String outReachingDefs = "/home/student/out.rd.txt";

        // Set soot settings
        String sootClassPath = Scene.v().getSootClassPath() + File.pathSeparator + sootCP;
        Scene.v().setSootClassPath(sootClassPath);
        Options.v().set_keep_line_number(true);
        Options.v().setPhaseOption("jb", "use-original-names");
        SootClass sootClass = Scene.v().loadClassAndSupport(className);
        Scene.v().loadNecessaryClasses();
        sootClass.setApplicationClass();

        SootMethod sm = sootClass.getMethodByName("main");
        Graph defUseGraph = CFG.CFGfromSootMethod(sm);
        makeDefUseGraph(defUseGraph);
        HashMap<Integer, HashSet<Definition>> in = reachingDefs(defUseGraph);
        writeDUChains(defUseGraph, in, outGraphName);
        writeReachingDefs(defUseGraph, in, outReachingDefs);
    }



}
