package net.fortytwo.extendo.brainstem.ripple.lib;

import net.fortytwo.extendo.brainstem.devices.TypeatronControl;
import net.fortytwo.flow.Sink;
import net.fortytwo.ripple.RippleException;
import net.fortytwo.ripple.model.ModelConnection;
import net.fortytwo.ripple.model.PrimitiveStackMapping;
import net.fortytwo.ripple.model.RippleList;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class GetLightLevelMapping extends PrimitiveStackMapping {
    private final TypeatronControl typeatron;

    public GetLightLevelMapping(final TypeatronControl typeatron) {
        this.typeatron = typeatron;
    }

    public String[] getIdentifiers() {
        return new String[]{
                BrainstemLibrary.NS_2014_04 + "getLightLevel"
        };
    }

    public Parameter[] getParameters() {
        return new Parameter[0];
    }

    public String getComment() {
        return "fetches the current light level from the photoresistor";
    }

    public void apply(RippleList arg, Sink<RippleList> solutions, ModelConnection context) throws RippleException {
        try {
            typeatron.sendPhotoresistorGetCommand();
        } catch (Throwable t) {
            throw new RippleException(t);
        }

        // For now, we decouple the response from the request, pushing the light level onto all stacks in the session.
        // Eventually, it would be best to push the light level only onto *this* stack (arg).

        solutions.put(arg);
    }
}
