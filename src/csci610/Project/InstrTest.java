package csci610.Project;

import soot.*;
import soot.jimple.toolkits.callgraph.CHATransformer;
import soot.jimple.toolkits.callgraph.CallGraph;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InstrTest {
    public static void main(String[] args) {
        String sootCP = System.getProperty("user.dir");
        String sootClassPath = Scene.v().getSootClassPath() + File.pathSeparator + sootCP;
        Scene.v().setSootClassPath(sootClassPath);

        String[] sootArgs = {
                "-d", "/home/student/sootOutput",
                "-w",
                "-p", "cg.test", "enabled:true",
                //"-p", "wjtp.test", "enabled:true",
                //"-p", phaseName, "enabled:true",
                //"-f", "n",
                //"-keep-line-number",
                //"-keep-offset",
                "-allow-phantom-refs",
                //"-process-multiple-dex",
                //"-process-dir", apkFile,
                //"-src-prec", "apk",
                //"-force-android-jar", "/home/student/android.jar"
                "Test"
        };

        Pack jtp = PackManager.v().getPack("cg");
        jtp.add(new Transform("cg.test", new SceneTransformer() {
            @Override
            protected void internalTransform(String s, Map<String, String> map) {
                List<SootMethod> entryPoints = new ArrayList<>();
                for (SootClass sc : Scene.v().getApplicationClasses()) {
                    if (!sc.getName().endsWith("Test")) continue;
                    for (SootMethod sm : sc.getMethods()) {
                        if (sm.getName().equals("main")) {
                            entryPoints.add(sm);
                        }
                    }
                }

                Scene.v().setEntryPoints(entryPoints);
                CHATransformer.v().transform();
                CallGraph cg = Scene.v().getCallGraph();
            }
        }));

        soot.Main.main(sootArgs);
    }
}
