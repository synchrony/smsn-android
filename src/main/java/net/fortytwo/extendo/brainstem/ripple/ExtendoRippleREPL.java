package net.fortytwo.extendo.brainstem.ripple;

import android.util.Log;
import com.illposed.osc.OSCMessage;
import net.fortytwo.extendo.brainstem.Brainstem;
import net.fortytwo.extendo.brainstem.BrainstemAgent;
import net.fortytwo.extendo.brainstem.devices.ChordedKeyer;
import net.fortytwo.extendo.brainstem.devices.TypeatronControl;
import net.fortytwo.extendo.brainstem.ripple.lib.DictionaryGetMapping;
import net.fortytwo.extendo.brainstem.ripple.lib.DictionaryPutMapping;
import net.fortytwo.extendo.brainstem.ripple.lib.MorseMapping;
import net.fortytwo.extendo.brainstem.ripple.lib.SpeakMapping;
import net.fortytwo.extendo.brainstem.ripple.lib.TypeatronDictionaryMapping;
import net.fortytwo.ripple.RippleException;
import net.fortytwo.ripple.libs.stack.Dup;
import net.fortytwo.ripple.libs.stack.Pop;
import net.fortytwo.ripple.libs.string.Concat;
import net.fortytwo.ripple.libs.system.Time;
import net.fortytwo.ripple.model.RippleValue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class ExtendoRippleREPL {
    private final RippleSession session;
    private StringBuilder currentLineOfText;

    private final Map<String, RippleValue> shortcutDictionary = new HashMap<String, RippleValue>();
    private final UserDictionary userDictionary;
    private final TypeatronControl typeatron;

    public ExtendoRippleREPL(final RippleSession session,
                             final TypeatronControl typeatron) throws RippleException {
        this.session = session;
        this.typeatron = typeatron;
        userDictionary = new UserDictionary(typeatron);

        shortcutDictionary.put("DEL", new ControlValue(new DictionaryGetMapping(userDictionary)));
        shortcutDictionary.put("ESC", new ControlValue(new DictionaryPutMapping(userDictionary)));
        // special value: all primitives are available here, through first-class names rather than shortcuts
        shortcutDictionary.put(" ", new ControlValue(new TypeatronDictionaryMapping(typeatron)));

        shortcutDictionary.put("c", new ControlValue(new Concat()));
        shortcutDictionary.put("d", new ControlValue(new Dup()));
        shortcutDictionary.put("m", new ControlValue(new MorseMapping(typeatron)));
        shortcutDictionary.put("p", new ControlValue(new Pop()));
        shortcutDictionary.put("s", new ControlValue(new SpeakMapping(typeatron)));
        shortcutDictionary.put("t", new ControlValue(new Time()));

        newLine();
    }

    private void newLine() {
        currentLineOfText = new StringBuilder();
    }

    public void handle(final String symbol,
                       final ChordedKeyer.Modifier modifier,
                       final ChordedKeyer.Mode mode) throws RippleException {

        Log.i(Brainstem.TAG, "got a symbol: " + symbol + " in mode " + mode + " with modifier " + modifier);
        if (mode.isTextEntryMode()) {
            if (ChordedKeyer.Modifier.Control == modifier) {
                Log.i(Brainstem.TAG, "got a control character");
                RippleValue controlValue = shortcutDictionary.get(symbol);

                if (null != controlValue) {
                    Log.i(Brainstem.TAG, "pushing control value: " + controlValue);
                    session.push(controlValue);
                }
            } else if (ChordedKeyer.Modifier.None == modifier) {
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
        } else if (ChordedKeyer.Mode.Hardware == mode) {
            // TODO: the above is more or less hardware mode; swap this for Emacs mode
        }

        if (Brainstem.RELAY_OSC) {
            BrainstemAgent agent = typeatron.getBrainstem().getAgent();
            if (agent.getFacilitatorConnection().isActive()) {
                OSCMessage m = new OSCMessage("/exo/fctr/tt/symbol");
                m.addArgument(symbol);
                agent.sendOSCMessageToFacilitator(m);
            }
        }
    }
}
