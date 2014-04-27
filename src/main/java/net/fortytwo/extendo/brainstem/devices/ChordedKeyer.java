package net.fortytwo.extendo.brainstem.devices;

import net.fortytwo.ripple.RippleException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class ChordedKeyer {

    private byte[] lastInput;

    public enum Mode {
        Text, Numeric, Hardware, Mash;

        public boolean isTextEntryMode() {
            return this != Hardware && this != Mash;
        }
    }

    public enum Modifier {Control, None}

    private class StateNode {
        /**
         * A symbol emitted when this state is reached
         */
        public String symbol;

        /**
         * A mode entered when this state is reached
         */
        public Mode mode;

        /**
         * A modifier applied when this state is reached
         */
        public Modifier modifier;

        public StateNode[] nextNodes = new StateNode[5];
    }

    private Map<Mode, StateNode> rootStates;

    private Mode currentMode;
    private StateNode currentButtonState;
    private int totalButtonsCurrentlyPressed;

    private final EventHandler eventHandler;

    public ChordedKeyer(final EventHandler eventHandler) throws IOException {
        this.eventHandler = eventHandler;
        initializeChords();
    }

    public void nextInputState(final byte[] state) {
        System.out.println("lastInput: " + new String(lastInput));
        for (int i = 0; i < 5; i++) {
            // Generally, at most one button should change per time step
            // However, if two buttons change state, it is an arbitrary choice w.r.t. which one changed first
            if (state[i] != lastInput[i]) {
                System.out.println("state[" + i + "] differs");
                if ('1' == state[i]) {
                    buttonPressed(i);
                } else {
                    buttonReleased(i);
                }
            }
        }

        System.arraycopy(state, 0, lastInput, 0, 5);
    }

    private void addChord(final Mode inputMode,
                          final String sequence,
                          final Mode outputMode,
                          final Modifier outputModifier,
                          final String outputSymbol) {
        StateNode cur = rootStates.get(inputMode);
        int l = sequence.length();
        for (int j = 0; j < l; j++) {
            int index = sequence.charAt(j) - 49;
            StateNode next = cur.nextNodes[index];
            if (null == next) {
                next = new StateNode();
                cur.nextNodes[index] = next;
            }

            cur = next;
        }

        if (null != outputSymbol) {
            if (null != cur.symbol && !cur.symbol.equals(outputSymbol)) {
                throw new IllegalStateException("conflicting symbols for sequence " + sequence);
            }
            cur.symbol = outputSymbol;
        }

        if (null != outputMode) {
            if (null != cur.mode && cur.mode != outputMode) {
                throw new IllegalArgumentException("conflicting output modes for sequence " + sequence);
            }
            cur.mode = outputMode;
        }

        if (null != outputModifier) {
            if (null != cur.modifier && cur.modifier != outputModifier) {
                throw new IllegalArgumentException("conflicting output modifiers for sequence " + sequence);
            }

            cur.modifier = outputModifier;
        }

        if (null != cur.mode && null != cur.symbol) {
            throw new IllegalStateException("sequence has been assigned both an output symbol and an output mode: " + sequence);
        } //else if (null != cur.modifier && Modifier.None != cur.modifier && (null != cur.mode || null != cur.symbol)) {
        //  throw new IllegalStateException("sequence has output modifier and also output symbol or mode");
        //}
    }

    private void initializeChords() throws IOException {
        // TODO: we shouldn't assume the device powers up with no buttons pressed, although this is likely
        totalButtonsCurrentlyPressed = 0;
        lastInput = "00000".getBytes();

        rootStates = new HashMap<Mode, StateNode>();
        for (Mode m : Mode.values()) {
            rootStates.put(m, new StateNode());
        }

        currentMode = Mode.Text;
        currentButtonState = rootStates.get(currentMode);

        // control-ESC codes for dictionary put
        addChord(Mode.Text, "1221", null, Modifier.Control, "ESC");
        // control-DEL codes for dictionary get
        addChord(Mode.Text, "1212", null, Modifier.Control, "DEL");
        //addChord(Mode.Text, "1221", null, Modifier.Control, null);
        //addChord(Mode.Text, "1212", Mode.Hardware, null, null);
        // 1331 unassigned
        addChord(Mode.Text, "1313", Mode.Numeric, null, null);
        // 1441 unassigned
        addChord(Mode.Text, "1414", Mode.Text, null, null);  // a no-op
        addChord(Mode.Text, "1551", Mode.Mash, Modifier.None, null);
        // 1515 unassigned

        // return to default mode from anywhere other than mash mode
        for (Mode m : Mode.values()) {
            if (m != Mode.Mash) {
                addChord(m, "123321", Mode.Text, Modifier.None, null);
            }
        }

        // return from mash mode
        addChord(Mode.Mash, "1234554321", Mode.Text, Modifier.None, null);

        // space, newline, delete, escape available in both of the text-entry modes
        for (Mode m : new Mode[]{Mode.Text, Mode.Numeric}) {
            // control-space codes for the Typeatron dictionary operator
            addChord(m, "11", null, Modifier.Control, " ");

            addChord(m, "22", null, null, " ");
            //addChord("22", null, null, "SPACE", m);
            addChord(m, "33", null, null, "\n");
            //addChord("33", null, null, "RET", m);
            addChord(m, "44", null, null, "DEL");
            addChord(m, "55", null, null, "ESC");
        }

        InputStream in = TypeatronControl.class.getResourceAsStream("typeatron-letters-and-punctuation.csv");
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String line;
            while (null != (line = br.readLine())) {
                line = line.trim();
                if (line.length() > 0) {
                    String[] a = line.split(",");
                    String chord = a[0];
                    String letter = a[1];

                    addChord(Mode.Text, chord, null, null, letter);
                    addChord(Mode.Text, findControlChord(chord), null, Modifier.Control, letter);
                    addChord(Mode.Text, findUppercaseChord(chord), null, null, letter.toUpperCase());

                    if (a.length > 2) {
                        String punc = a[2].replaceAll("COMMA", ",");
                        addChord(Mode.Text, findPunctuationChord(chord), null, null, punc);
                    }
                }
            }
        } finally {
            in.close();
        }

        /*
        addChord(Mode.Text, "2112", null, null, "a");
        addChord(Mode.Text, "213312", null, Modifier.Control, "a");
        addChord(Mode.Text, "214412", null, null, "A");
        addChord(Mode.Text, "215512", null, null, "'");

        // 2121 unassigned

        addChord(Mode.Text, "2332", null, null, "e");
        addChord(Mode.Text, "231132", null, Modifier.Control, "e");
        addChord(Mode.Text, "234432", null, null, "E");
        addChord(Mode.Text, "235532", null, null, "=");

        addChord(Mode.Text, "2323", null, null, "w");
        addChord(Mode.Text, "231123", null, Modifier.Control, "w");
        addChord(Mode.Text, "234423", null, null, "W");
        addChord(Mode.Text, "235523", null, null, "@");

        addChord(Mode.Text, "2442", null, null, "i");
        addChord(Mode.Text, "241142", null, Modifier.Control, "i");
        addChord(Mode.Text, "243342", null, null, "I");
        addChord(Mode.Text, "245542", null, null, ":");

        addChord(Mode.Text, "2424", null, null, "y");
        addChord(Mode.Text, "241124", null, Modifier.Control, "y");
        addChord(Mode.Text, "243324", null, null, "Y");
        addChord(Mode.Text, "245524", null, null, "&");

        addChord(Mode.Text, "2552", null, null, "o");
        addChord(Mode.Text, "251152", null, Modifier.Control, "o");
        addChord(Mode.Text, "253352", null, null, "O");
        // there is no punctuation associated with "o"

        addChord(Mode.Text, "2525", null, null, "u");
        addChord(Mode.Text, "251125", null, Modifier.Control, "u");
        addChord(Mode.Text, "253325", null, null, "U");
        addChord(Mode.Text, "254425", null, null, "_");

        addChord(Mode.Text, "3113", null, null, "p");
        addChord(Mode.Text, "312213", null, Modifier.Control, "p");
        addChord(Mode.Text, "314413", null, null, "P");
        addChord(Mode.Text, "315513", null, null, "+");

        addChord(Mode.Text, "3131", null, null, "b");
        addChord(Mode.Text, "312231", null, Modifier.Control, "b");
        addChord(Mode.Text, "314431", null, null, "B");
        addChord(Mode.Text, "315531", null, null, "\\");

        addChord(Mode.Text, "3223", null, null, "t");
        addChord(Mode.Text, "321123", null, Modifier.Control, "t");
        addChord(Mode.Text, "324423", null, null, "T");
        addChord(Mode.Text, "325523", null, null, "~");

        addChord(Mode.Text, "3232", null, null, "d");
        addChord(Mode.Text, "321132", null, Modifier.Control, "d");
        addChord(Mode.Text, "324432", null, null, "D");
        addChord(Mode.Text, "325532", null, null, "$");

        addChord(Mode.Text, "3443", null, null, "k");
        addChord(Mode.Text, "341143", null, Modifier.Control, "k");
        addChord(Mode.Text, "342243", null, null, "K");
        addChord(Mode.Text, "345543", null, null, "*");

        addChord(Mode.Text, "3434", null, null, "g");
        addChord(Mode.Text, "341134", null, Modifier.Control, "g");
        addChord(Mode.Text, "342234", null, null, "G");
        addChord(Mode.Text, "345534", null, null, "`");

        addChord(Mode.Text, "3553", null, null, "q");
        addChord(Mode.Text, "351153", null, Modifier.Control, "q");
        addChord(Mode.Text, "352253", null, null, "Q");
        addChord(Mode.Text, "354453", null, null, "?");

        // 3535 unassigned

        addChord(Mode.Text, "4114", null, null, "f");
        addChord(Mode.Text, "412214", null, Modifier.Control, "f");
        addChord(Mode.Text, "413314", null, null, "F");
        addChord(Mode.Text, "415514", null, null, ".");

        addChord(Mode.Text, "4141", null, null, "v");
        addChord(Mode.Text, "412241", null, Modifier.Control, "v");
        addChord(Mode.Text, "413341", null, null, "V");
        addChord(Mode.Text, "415541", null, null, "|");

        addChord(Mode.Text, "4224", null, null, "c");
        addChord(Mode.Text, "421124", null, Modifier.Control, "c");
        addChord(Mode.Text, "423324", null, null, "C");
        addChord(Mode.Text, "425524", null, null, ",");

        addChord(Mode.Text, "4242", null, null, "j");
        addChord(Mode.Text, "421142", null, Modifier.Control, "j");
        addChord(Mode.Text, "423342", null, null, "J");
        addChord(Mode.Text, "425542", null, null, ";");

        addChord(Mode.Text, "4334", null, null, "s");
        addChord(Mode.Text, "431134", null, Modifier.Control, "s");
        addChord(Mode.Text, "432234", null, null, "S");
        addChord(Mode.Text, "435534", null, null, "/");

        addChord(Mode.Text, "4343", null, null, "z");
        addChord(Mode.Text, "431143", null, Modifier.Control, "z");
        addChord(Mode.Text, "432243", null, null, "Z");
        addChord(Mode.Text, "435543", null, null, "%");

        addChord(Mode.Text, "4554", null, null, "h");
        addChord(Mode.Text, "451154", null, Modifier.Control, "h");
        addChord(Mode.Text, "452254", null, null, "H");
        addChord(Mode.Text, "453354", null, null, "^");

        addChord(Mode.Text, "4545", null, null, "x");
        addChord(Mode.Text, "451145", null, Modifier.Control, "x");
        addChord(Mode.Text, "452245", null, null, "X");
        addChord(Mode.Text, "453345", null, null, "!");

        addChord(Mode.Text, "5115", null, null, "m");
        addChord(Mode.Text, "512215", null, Modifier.Control, "m");
        addChord(Mode.Text, "513315", null, null, "M");
        addChord(Mode.Text, "514415", null, null, "-");

        // 5151 unassigned

        addChord(Mode.Text, "5225", null, null, "n");
        addChord(Mode.Text, "521125", null, Modifier.Control, "n");
        addChord(Mode.Text, "523325", null, null, "N");
        addChord(Mode.Text, "524425", null, null, "#");

        // 5252 unassigned

        addChord(Mode.Text, "5335", null, null, "l");
        addChord(Mode.Text, "531135", null, Modifier.Control, "l");
        addChord(Mode.Text, "532235", null, null, "L");
        addChord(Mode.Text, "534435", null, null, "\"");

        // 5353 unassigned

        addChord(Mode.Text, "5445", null, null, "r");
        addChord(Mode.Text, "541145", null, Modifier.Control, "r");
        addChord(Mode.Text, "542245", null, null, "R");
        // no punctuation associated with "r" (this slot is reserved for right-handed quotes)

        // 5454 unassigned
        */
    }

    private char findUnusedKey(final String chord,
                               int index) {
        boolean[] used = new boolean[5];
        for (byte b : chord.getBytes()) {
            used[b - 49] = true;
        }

        for (int i = 0; i < 5; i++) {
            if (!used[i]) {
                if (0 == index) {
                    return (char) (i + 49);
                } else {
                    index--;
                }
            }
        }

        throw new IllegalArgumentException("index too high");
    }

    private String findControlChord(final String chord) {
        char key = findUnusedKey(chord, 0);
        return chord.substring(0, 2) + key + key + chord.substring(2);
    }

    private String findUppercaseChord(final String chord) {
        char key = findUnusedKey(chord, 1);
        return chord.substring(0, 2) + key + key + chord.substring(2);
    }

    private String findPunctuationChord(final String chord) {
        char key = findUnusedKey(chord, 2);
        return chord.substring(0, 2) + key + key + chord.substring(2);
    }

    // buttonIndex: 0 (thumb) through 4 (pinky)
    private void buttonEvent(int buttonIndex) {
        System.out.println("buttonEvent(" + buttonIndex + ")"); System.out.flush();
        if (null != currentButtonState) {
            currentButtonState = currentButtonState.nextNodes[buttonIndex];
        }
    }

    private void buttonPressed(int buttonIndex) {
        System.out.println("buttonPressed(" + buttonIndex + ")"); System.out.flush();
        totalButtonsCurrentlyPressed++;

        buttonEvent(buttonIndex);
    }

    private void buttonReleased(int buttonIndex) {
        System.out.println("buttonReleased(" + buttonIndex + ")"); System.out.flush();
        totalButtonsCurrentlyPressed--;

        buttonEvent(buttonIndex);

        // at present, events are triggered when the last key of a sequence is released
        if (0 == totalButtonsCurrentlyPressed) {
            if (null != currentButtonState) {
                String symbol = currentButtonState.symbol;
                if (null != symbol) {
                    Modifier modifier = currentButtonState.modifier;
                    if (null == modifier) {
                        modifier = Modifier.None;
                    }

                    eventHandler.handle(currentButtonState.mode, symbol, modifier);
                } else {
                    Mode mode = currentButtonState.mode;
                    // this sets the mode for *subsequent* key events
                    if (null != mode) {
                        currentMode = mode;

                        eventHandler.handle(mode, null, currentButtonState.modifier);
                    }
                }
            }

            currentButtonState = rootStates.get(currentMode);
        }
    }

    public interface EventHandler {
        void handle(Mode mode, String symbol, Modifier modifier);
    }
}
