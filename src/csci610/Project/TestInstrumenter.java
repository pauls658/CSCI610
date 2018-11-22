package csci610.Project;

import soot.Body;
import soot.BodyTransformer;
import soot.util.Chain;

import java.util.Map;

public class TestInstrumenter extends BodyTransformer {
    @Override
    protected void internalTransform(Body body, String s, Map<String, String> map) {
        Chain units = body.getUnits();
        units.remove(units.toArray()[1]);
    }
}
