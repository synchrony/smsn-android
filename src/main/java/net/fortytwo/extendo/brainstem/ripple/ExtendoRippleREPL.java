package net.fortytwo.extendo.brainstem.ripple;

import android.util.Log;
import net.fortytwo.extendo.brainstem.Brainstem;
import net.fortytwo.extendo.brainstem.devices.TypeatronControl;
import net.fortytwo.extendo.brainstem.ripple.lib.GetLightLevelMapping;
import net.fortytwo.extendo.brainstem.ripple.lib.MorseMapping;
import net.fortytwo.extendo.brainstem.ripple.lib.VibrateMapping;
import net.fortytwo.ripple.RippleException;
import net.fortytwo.ripple.libs.stack.Pop;
import net.fortytwo.ripple.model.RippleValue;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class ExtendoRippleREPL {
    private final RippleSession session;
    private StringBuilder currentLineOfText;

    private final RippleValue morse;
    private final RippleValue vibro;
    private final RippleValue photo;
    private final RippleValue pop;

    public ExtendoRippleREPL(final RippleSession session,
                             final TypeatronControl typeatron) throws RippleException {
        this.session = session;
        morse = new ControlValue(new MorseMapping(typeatron));
        vibro = new ControlValue(new VibrateMapping(typeatron));
        photo = new ControlValue(new GetLightLevelMapping(typeatron));
        pop = new ControlValue(new Pop());

        newLine();
    }

    private void newLine() {
        currentLineOfText = new StringBuilder();
    }

    public void handle(final String symbol,
                       final TypeatronControl.Modifier modifier,
                       final TypeatronControl.Mode mode) throws RippleException {
        Log.i(Brainstem.TAG, "got a symbol: " + symbol + " in mode " + mode + " with modifier " + modifier);
        if (mode.isTextEntryMode()) {
            if (TypeatronControl.Modifier.Control == modifier) {
                Log.i(Brainstem.TAG, "got a control character");
                if (symbol.equals("l")) {
                    Log.i(Brainstem.TAG, "pushing light level get command");
                    session.push(photo);
                } else if (symbol.equals("m")) {
                    Log.i(Brainstem.TAG, "pushing the Morse operator");
                    session.push(morse);
                } else if (symbol.equals("p")) {
                    Log.i(Brainstem.TAG, "push the pop mapping");
                    session.push(pop);
                } else if (symbol.equals("v")) {
                    Log.i(Brainstem.TAG, "pushing the sendVibrateCommand operator");
                    session.push(vibro);
                }
            } else if (TypeatronControl.Modifier.None == modifier) {
                if (symbol.equals("\n")) {
                    if (currentLineOfText.length() > 0) {
                        session.push(session.getModelConnection().valueOf(currentLineOfText.toString()));
                        newLine();
                    }
                } else if (symbol.equals("DEL")) {
                    if (currentLineOfText.length() > 0) {
                        currentLineOfText.deleteCharAt(currentLineOfText.length() - 1);
                    }
                } else if (symbol.equals("ESC")) {
                    newLine();
                } else {
                    currentLineOfText.append(symbol);
                }
            } else {
                throw new IllegalStateException("unexpected modifier: " + modifier);
            }
        } else if (TypeatronControl.Mode.Hardware == mode) {
            // TODO: the above is more or less hardware mode; swap this for Emacs mode
        }
    }

}
