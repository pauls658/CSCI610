package csci610.Project;

import soot.*;
import soot.jimple.toolkits.callgraph.CHATransformer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CGTest {
    public static void main (String[] args) {
        // replace with apk file you want to analyze
        String apkFile = "/home/student/app-release.apk";
        setupAndInvokeSoot(apkFile);
    }

    static void setupAndInvokeSoot(String apkFile) {
        String packName = "wjtp";
        String phaseName = "wjtp.finline";
        String[] sootArgs = {
                "-d", "/home/student/sootOutput", // output directly
                "-w", // analyze in "whole program" mode
                "-p", "cg.test", "enabled:true", // enable phase that constructs call graph
                "-p", phaseName, "enabled:true", // enable our analysis phase
                "-f", "dex", // set output format to output apk file
                //"-keep-line-number",
                //"-keep-offset",
                "-allow-phantom-refs", // soot breaks if this is not enabled usually
                "-process-dir", apkFile, //
                "-src-prec", "apk",
                "-force-android-jar", "/home/student/android.jar"
        };
        setupAndInvokeSootHelper(packName, phaseName, sootArgs);
    }

    static void setupAndInvokeSootHelper(String packName, String phaseName,
                                         String[] sootArgs) {
        // Create the phase and add it to the pack
        Pack pack = PackManager.v().getPack(packName);

        //
        pack.add(new Transform(phaseName, new FunctionInlineInstrumenter()));

        // special call graph generator that gets the correct entry points
        // for an android APK
        pack = PackManager.v().getPack("cg");
        pack.add(new Transform("cg.test", new SceneTransformer() {
            @Override
            protected void internalTransform(String s, Map<String, String> map) {
                // Android does not have a traditional "main" function like
                // normal java programs. Instead it has life cycle methods
                // like "onCreate" or "onPause" etc...
                // You may want to add all 7 life cycle methods. Read up
                // on android apps to decide if you need this or not

                // Set "onCreate" as the entry point to begin call
                // graph analysis
                List<SootMethod> entryPoints = new ArrayList<>();
                for (SootClass sc : Scene.v().getApplicationClasses()) {
                    for (SootMethod sm : sc.getMethods()) {
                        if (sm.getName().equals("onCreate")) {
                            entryPoints.add(sm);
                        }
                    }
                }

                Scene.v().setEntryPoints(entryPoints);
                // Create a call graph based on CHA
                CHATransformer.v().transform();
            }
        }));

        soot.Main.main(sootArgs);
    }
}
