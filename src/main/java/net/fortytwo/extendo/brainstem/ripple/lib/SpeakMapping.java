package net.fortytwo.extendo.brainstem.ripple.lib;

import android.util.Log;
import net.fortytwo.extendo.brainstem.Brainstem;
import net.fortytwo.extendo.brainstem.devices.TypeatronControl;
import net.fortytwo.flow.Sink;
import net.fortytwo.ripple.RippleException;
import net.fortytwo.ripple.model.ModelConnection;
import net.fortytwo.ripple.model.PrimitiveStackMapping;
import net.fortytwo.ripple.model.RippleList;

/**
* @author Joshua Shinavier (http://fortytwo.net)
*/
public class SpeakMapping extends PrimitiveStackMapping {
    private final TypeatronControl typeatron;

    public SpeakMapping(final TypeatronControl typeatron) {
        this.typeatron = typeatron;
    }

    public String[] getIdentifiers() {
        return new String[]{
                BrainstemLibrary.NS_2014_04 + "speak"
        };
    }

    public Parameter[] getParameters() {
        return new Parameter[]{new Parameter("text", "the text to be spoken", true)};
    }

    public String getComment() {
        return "speaks a line of text";
    }

    public void apply(final RippleList arg,
                      final Sink<RippleList> solutions,
                      final ModelConnection context) throws RippleException {
        Log.i(Brainstem.TAG, "executing the speak mapping");

        String text = arg.getFirst().toString();

        try {
            typeatron.speak(text);
        } catch (Throwable t) {
            throw new RippleException(t);
        }

        solutions.put(arg.getRest());
    }
}
