package net.fortytwo.extendo.brainstem.ripple.lib;

import android.util.Log;
import net.fortytwo.extendo.brainstem.Brainstem;
import net.fortytwo.extendo.brainstem.devices.TypeatronControl;
import net.fortytwo.flow.Sink;
import net.fortytwo.ripple.RippleException;
import net.fortytwo.ripple.model.ModelConnection;
import net.fortytwo.ripple.model.PrimitiveStackMapping;
import net.fortytwo.ripple.model.RippleList;
import net.fortytwo.ripple.model.StackMapping;

/**
* @author Joshua Shinavier (http://fortytwo.net)
*/
public class MorseMapping extends PrimitiveStackMapping {
    private final TypeatronControl typeatron;

    public MorseMapping(final TypeatronControl typeatron) {
        this.typeatron = typeatron;
    }

    public String[] getIdentifiers() {
        return new String[]{
                BrainstemLibrary.NS_2014_04 + "playMorse"
        };
    }

    public Parameter[] getParameters() {
        return new Parameter[0];
    }

    public String getComment() {
        return "plays an alphanumeric sequence in Morse code";
    }

    public void apply(final RippleList arg,
                      final Sink<RippleList> solutions,
                      final ModelConnection context) throws RippleException {
        Log.i(Brainstem.TAG, "executing the Morse mapping");

        String morseArg = arg.getFirst().toString();

        try {
            typeatron.sendMorseCommand(morseArg);
        } catch (Throwable t) {
            throw new RippleException(t);
        }

        solutions.put(arg.getRest());
    }
}
