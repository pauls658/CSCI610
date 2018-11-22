package csci610.Project;

import soot.*;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.util.Chain;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class NikolaAnalysis extends SceneTransformer {


    @Override
    protected void internalTransform(String phaseName, Map<String, String> map) {
        // Retrieve the call graph
        CallGraph callGraph = Scene.v().getCallGraph();
        // remove edges we don't care about
        CallGraph userCallGraph = new CallGraph();
        for (Edge e : callGraph) {
            // remove java library methods
            if (e.getSrc().method().isJavaLibraryMethod() ||
            e.getTgt().method().isJavaLibraryMethod()) continue;

            // remove android library methods
            if (e.getSrc().method().getDeclaringClass().getPackageName().startsWith("android.") ||
            e.getTgt().method().getDeclaringClass().getPackageName().startsWith("android.")) continue;

            userCallGraph.addEdge(e);
        }

        // now you can analyze the call graph however you want.
        // the nodes in the call graph will contain references
        // to the soot methods
    }

}
