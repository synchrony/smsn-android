package net.fortytwo.extendo.brainstem.ripple.lib;

import android.util.Log;
import net.fortytwo.extendo.brainstem.Brainstem;
import net.fortytwo.extendo.brainstem.devices.TypeatronControl;
import net.fortytwo.flow.Sink;
import net.fortytwo.ripple.RippleException;
import net.fortytwo.ripple.model.ModelConnection;
import net.fortytwo.ripple.model.PrimitiveStackMapping;
import net.fortytwo.ripple.model.RDFValue;
import net.fortytwo.ripple.model.RippleList;
import org.openrdf.model.URI;
import org.openrdf.model.Value;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class LaserPointerMapping extends PrimitiveStackMapping {
    private final TypeatronControl typeatron;

    public LaserPointerMapping(final TypeatronControl typeatron) {
        this.typeatron = typeatron;
    }

    public String[] getIdentifiers() {
        return new String[]{
                BrainstemLibrary.NS_2014_04 + "point"
        };
    }

    public Parameter[] getParameters() {
        return new Parameter[]{new Parameter("thingPointedTo", "the thing pointed to or referenced", true)};
    }

    public String getComment() {
        return "points to or indicates an item";
    }

    public void apply(final RippleList arg,
                      final Sink<RippleList> solutions,
                      final ModelConnection context) throws RippleException {
        Log.i(Brainstem.TAG, "executing the laser pointer mapping");

        Value thingPointedTo = arg.getFirst().toRDF(context).sesameValue();

        if (thingPointedTo instanceof URI) {
            try {
                typeatron.pointTo((URI) thingPointedTo);
            } catch (Throwable t) {
                throw new RippleException(t);
            }

            // keep the thing pointed to on the stack
            solutions.put(arg);
        }
    }
}
