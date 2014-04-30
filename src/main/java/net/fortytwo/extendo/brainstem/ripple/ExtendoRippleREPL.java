package net.fortytwo.extendo.brainstem.ripple;

import android.util.Log;
import com.illposed.osc.OSCMessage;
import net.fortytwo.extendo.brainstem.Brainstem;
import net.fortytwo.extendo.brainstem.BrainstemAgent;
import net.fortytwo.extendo.brainstem.devices.ChordedKeyer;
import net.fortytwo.extendo.brainstem.devices.TypeatronControl;
import net.fortytwo.extendo.brainstem.ripple.lib.TypeatronDictionaryMapping;
import net.fortytwo.ripple.RippleException;
import net.fortytwo.ripple.model.RDFValue;
import org.openrdf.model.impl.URIImpl;

import java.util.Map;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class ExtendoRippleREPL {
    private final RippleSession session;
    private StringBuilder currentLineOfText;

    private final UserDictionary userDictionary;
    private final ControlValue typeatronDictionary;
    private final TypeatronControl typeatron;

    public ExtendoRippleREPL(final RippleSession session,
                             final TypeatronControl typeatron) throws RippleException {
        this.session = session;
        this.typeatron = typeatron;
        userDictionary = new UserDictionary(typeatron);
        typeatronDictionary = new ControlValue(new TypeatronDictionaryMapping(typeatron, userDictionary, session.getModelConnection()));

        // TODO: temporary, for a demo
        String[][] shortcuts = {
                {"an", "http://fortytwo.net/2014/04/twc#AliNendick"},
                {"av", "http://fortytwo.net/2014/04/twc#AmarViswanathan"},
                {"bm", "http://fortytwo.net/2014/04/twc#BassemMakni"},
                {"bz", "http://fortytwo.net/2014/04/twc#BoliangZhang"},
                {"dy", "http://fortytwo.net/2014/04/twc#DianYu"},
                {"ea", "http://fortytwo.net/2014/04/twc#EricAmeres"},
                {"ep", "http://fortytwo.net/2014/04/twc#EvanPatton"},
                {"hw", "http://fortytwo.net/2014/04/twc#HanWang"},
                {"hl", "http://fortytwo.net/2014/04/twc#HaoLi"},
                {"hh", "http://fortytwo.net/2014/04/twc#HongzhaoHuang"},
                {"jm", "http://fortytwo.net/2014/04/twc#JamesMichaelis"},
                {"jc", "http://fortytwo.net/2014/04/twc#JimMcCusker"},
                {"jz", "http://fortytwo.net/2014/04/twc#JinGuangZheng"},
                {"je", "http://fortytwo.net/2014/04/twc#JohnErickson"},
                {"js", "http://fortytwo.net/2014/04/twc#JoshuaShinavier"},
                {"kc", "http://fortytwo.net/2014/04/twc#KatieChastain"},
                {"kg", "http://fortytwo.net/2014/04/twc#KristineGloria"},
                {"mf", "http://fortytwo.net/2014/04/twc#MattFerrito"},
                {"nr", "http://fortytwo.net/2014/04/twc#NidhiRastogi"},
                {"ql", "http://fortytwo.net/2014/04/twc#QiLi"},
                {"se", "http://fortytwo.net/2014/04/twc#SimonEllis"},
                {"tl", "http://fortytwo.net/2014/04/twc#TimLebo"},
                {"tz", "http://fortytwo.net/2014/04/twc#TongtaoZhang"},
                {"tl", "http://fortytwo.net/2014/04/twc#YueLiu"}};
        for (String[] pair : shortcuts) {
            userDictionary.put(pair[0], new RDFValue(new URIImpl(pair[1])));
        }

        newLine();
    }

    private void newLine() {
        currentLineOfText = new StringBuilder();
    }

    private String getLastSymbol() {
        int n = currentLineOfText.length();
        if (n > 0) {
            char c = currentLineOfText.charAt(n - 1);
            currentLineOfText.deleteCharAt(n - 1);
            return "" + c;
        } else {
            return null;
        }
    }

    public void handle(final String symbol,
                       final ChordedKeyer.Modifier modifier,
                       final ChordedKeyer.Mode mode) throws RippleException {

        Log.i(Brainstem.TAG, "got a symbol: " + symbol + " in mode " + mode + " with modifier " + modifier);
        if (mode.isTextEntryMode()) {
            if (ChordedKeyer.Modifier.Control == modifier) {
                Log.i(Brainstem.TAG, "got a control character");

                if (symbol.equals("")) {
                    if (currentLineOfText.length() > 0) {
                        session.push(session.getModelConnection().valueOf(currentLineOfText.toString()));
                        session.push(typeatronDictionary);
                        newLine();
                    }
                } else if (symbol.equals("u")) {
                    String s = getLastSymbol();
                    if (null != s) {
                        currentLineOfText.append(s.toUpperCase());
                    }
                } else if (symbol.equals("n")) {
                    String s = getLastSymbol();
                    if (null != s) {
                        char c = s.charAt(0);
                        if (c == 'o') {
                            currentLineOfText.append("0");
                        } else if (c >= 'a' && c <= 'i') {
                            currentLineOfText.append((char) (s.charAt(0) - 'a' + '1'));
                        }
                    }
                } else if (symbol.equals("p")) {
                    String s = getLastSymbol();
                    if (null != s) {
                        String p = typeatron.getKeyer().getPunctuationMap().get(s);
                        if (null != p) {
                            currentLineOfText.append(p);
                        }
                    }
                } else {
                    Log.w(Brainstem.TAG, "unknown control value: " + symbol);
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
