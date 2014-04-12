package net.fortytwo.extendo.brainstem.devices;

import android.util.Log;
import com.illposed.osc.OSCMessage;
import net.fortytwo.extendo.brain.BrainModeClient;
import net.fortytwo.extendo.brainstem.Brainstem;
import net.fortytwo.extendo.brainstem.bluetooth.BluetoothDeviceControl;
import net.fortytwo.extendo.brainstem.osc.OSCDispatcher;
import net.fortytwo.extendo.brainstem.osc.OSCMessageHandler;
import net.fortytwo.extendo.brainstem.ripple.ExtendoRippleREPL;
import net.fortytwo.extendo.brainstem.ripple.RippleSession;
import net.fortytwo.ripple.RippleException;
import net.fortytwo.ripple.model.ModelConnection;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class TypeatronControl extends BluetoothDeviceControl {

    // outbound addresses
    private static final String
            EXO_TT_MODE = "/exo/tt/mode",
            EXO_TT_MORSE = "/exo/tt/morse",
            EXO_TT_PHOTO_GET = "/exo/tt/photo/get",
            EXO_TT_VIBR = "/exo/tt/vibr";

    // inbound addresses
    private static final String
            EXO_TT_ERROR = "/exo/tt/error",
            EXO_TT_INFO = "/exo/tt/info",
            EXO_TT_KEYS = "/exo/tt/keys",
            EXO_TT_PHOTO_DATA = "/exo/tt/photo/data",
            EXO_TT_PING_REPLY = "/exo/tt/ping/reply";

    private final Brainstem brainstem;

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

    private final BrainModeClientWrapper brainModeWrapper;
    private final RippleSession rippleSession;
    private final ExtendoRippleREPL rippleREPL;

    public TypeatronControl(final String address,
                            final OSCDispatcher oscDispatcher,
                            final Brainstem brainstem) throws DeviceInitializationException {
        super(address, oscDispatcher);

        this.brainstem = brainstem;
        try {
            rippleSession = new RippleSession();
            rippleREPL = new ExtendoRippleREPL(rippleSession, this);
        } catch (RippleException e) {
            throw new DeviceInitializationException(e);
        }

        setupParser();

        oscDispatcher.register(EXO_TT_ERROR, new OSCMessageHandler() {
            public void handle(OSCMessage message) {
                Object[] args = message.getArguments();
                if (1 == args.length) {
                    brainstem.getToaster().makeText("error message from Typeatron: " + args[0]);
                    Log.e(Brainstem.TAG, "error message from Typeatron " + address + ": " + args[0]);
                } else {
                    Log.e(Brainstem.TAG, "wrong number of arguments in Typeatron error message");
                }
            }
        });

        oscDispatcher.register(EXO_TT_INFO, new OSCMessageHandler() {
            public void handle(OSCMessage message) {
                Object[] args = message.getArguments();
                if (1 == args.length) {
                    //brainstem.getToaster().makeText("\ninfo message from Typeatron: " + args[0]);
                    Log.i(Brainstem.TAG, "info message from Typeatron " + address + ": " + args[0]);
                } else {
                    Log.e(Brainstem.TAG, "wrong number of arguments in Typeatron info message");
                }
            }
        });

        oscDispatcher.register(EXO_TT_KEYS, new OSCMessageHandler() {
            public void handle(final OSCMessage message) {
                Object[] args = message.getArguments();
                if (1 == args.length) {
                    try {
                        inputReceived(((String) args[0]).getBytes());
                    } catch (Exception e) {
                        Log.e(Brainstem.TAG, "failed to relay Typeatron input");
                        e.printStackTrace(System.err);
                    }
                    //brainstem.getToaster().makeText("Typeatron keys: " + args[0] + " (" + totalButtonsCurrentlyPressed + " pressed)");
                } else {
                    brainstem.getToaster().makeText("Typeatron control error (wrong # of args)");
                }
            }
        });

        oscDispatcher.register(EXO_TT_PHOTO_DATA, new OSCMessageHandler() {
            public void handle(OSCMessage message) {
                Object[] args = message.getArguments();
                if (7 != args.length) {
                    throw new IllegalStateException("photoresistor observation has unexpected number of arguments ("
                            + args.length + "): " + message);
                }

                // workaround for unavailable Xerces dependency: make startTime and endTime into xsd:long instead of xsd:dateTime
                long startTime = ((Date) args[0]).getTime();
                long endTime = ((Date) args[1]).getTime();

                Integer numberOfMeasurements = (Integer) args[2];
                Float minValue = (Float) args[3];
                Float maxValue = (Float) args[4];
                Float mean = (Float) args[5];
                Float variance = (Float) args[6];

                ModelConnection mc = rippleSession.getModelConnection();
                try {
                    rippleSession.push(mc.valueOf(startTime),
                            mc.valueOf(endTime),
                            mc.valueOf(numberOfMeasurements),
                            mc.valueOf(minValue),
                            mc.valueOf(maxValue),
                            mc.valueOf(variance),
                            mc.valueOf(mean));
                } catch (RippleException e) {
                    Log.e(Brainstem.TAG, "Ripple error while pushing photoresistor observation: " + e.getMessage());
                    e.printStackTrace(System.err);
                }
            }
        });

        oscDispatcher.register(EXO_TT_PING_REPLY, new OSCMessageHandler() {
            public void handle(OSCMessage message) {
                // note: argument is ignored for now; in future, it could be used to synchronize clocks

                // we assume this reply is a response to the latest ping
                long delay = System.currentTimeMillis() - latestPing;

                brainstem.getToaster().makeText("ping reply received from Typeatron " + address + " in " + delay + "ms");
                Log.i(Brainstem.TAG, "ping reply received from Typeatron " + address + " in " + delay + "ms");
            }
        });

        // TODO: temporary... assume Emacs is available, even if we can't detect it...
        boolean forceEmacsAvailable = true;  // emacsAvailable

        try {
            brainModeWrapper = forceEmacsAvailable ? new BrainModeClientWrapper() : null;
        } catch (IOException e) {
            throw new DeviceInitializationException(e);
        }
    }

    @Override
    protected void onConnect() {
        doPing();
    }

    private class BrainModeClientWrapper {
        private boolean isAlive;
        private final PipedOutputStream source;

        public BrainModeClientWrapper() throws IOException {
            source = new PipedOutputStream();

            PipedInputStream sink = new PipedInputStream(source);

            final BrainModeClient client = new BrainModeClient(sink);
            // since /usr/bin may not be in PATH
            client.setExecutable("/usr/bin/emacsclient");

            new Thread(new Runnable() {
                public void run() {
                    isAlive = true;

                    try {
                        client.run();
                    } catch (BrainModeClient.ExecutionException e) {
                        Log.e(Brainstem.TAG, "Brain-mode client giving up due to execution exception: " + e.getMessage());
                    } catch (Throwable t) {
                        Log.e(Brainstem.TAG, "Brain-mode client thread died with error: " + t.getMessage());
                        t.printStackTrace(System.err);
                    } finally {
                        isAlive = false;
                    }
                }
            }).start();
        }

        public void write(final String symbol) throws IOException {
            Log.i(Brainstem.TAG, (isAlive ? "" : "NOT ") + "writing '" + symbol + "' to Emacs...");
            source.write(symbol.getBytes());
        }
    }

    // feedback to the Typeatron whenever mode changes
    public void sendModeInfo() {
        OSCMessage m = new OSCMessage(EXO_TT_MODE);
        m.addArgument(currentMode.name());
        send(m);
    }

    public void sendMorseCommand(final String text) {
        OSCMessage m = new OSCMessage(EXO_TT_MORSE);
        m.addArgument(text);
        send(m);
    }

    public void sendPhotoresistorGetCommand() {
        OSCMessage m = new OSCMessage(EXO_TT_PHOTO_GET);
        send(m);
    }

    public void speak(final String text) {
        brainstem.getSpeaker().speak(text);
    }

    /**
     * @param time the duration of the signal in millseconds (valid values range from 1 to 60000)
     */
    public void sendVibrateCommand(final int time) {
        if (time < 0 || time > 60000) {
            throw new IllegalArgumentException("vibration interval too short or too long: " + time);
        }

        OSCMessage m = new OSCMessage(EXO_TT_VIBR);
        m.addArgument(time);
        send(m);
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

    private void setupParser() {
        // TODO: we shouldn't assume the device powers up with no buttons pressed, although this is likely
        totalButtonsCurrentlyPressed = 0;
        lastInput = "00000".getBytes();

        rootStates = new HashMap<Mode, StateNode>();
        for (Mode m : Mode.values()) {
            rootStates.put(m, new StateNode());
        }

        currentMode = Mode.Text;
        currentButtonState = rootStates.get(currentMode);

        // TODO: is mode switching so important?  Perhaps we can use these chords for something else
        // mode entry from default mode
        addChord(Mode.Text, "1221", null, Modifier.Control, null);
        addChord(Mode.Text, "1212", Mode.Hardware, null, null);
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
    }

    // buttonIndex: 0 (thumb) through 4 (pinky)
    private void buttonEvent(int buttonIndex) {
        if (null != currentButtonState) {
            currentButtonState = currentButtonState.nextNodes[buttonIndex];
        }
    }

    private void buttonPressed(int buttonIndex) {
        totalButtonsCurrentlyPressed++;

        buttonEvent(buttonIndex);
    }

    private String modifySymbol(final String symbol,
                                final Modifier modifier) {
        switch (modifier) {
            case Control:
                return symbol.length() == 1 ? "<C-" + symbol + ">" : "<" + symbol + ">";
            case None:
                return symbol.length() == 1 ? symbol : "<" + symbol + ">";
            default:
                throw new IllegalStateException();
        }
    }

    private void buttonReleased(int buttonIndex) throws IOException {
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
                    Log.i(Brainstem.TAG, "using modifier: " + modifier);

                    try {
                        rippleREPL.handle(symbol, modifier, currentMode);
                    } catch (RippleException e) {
                        brainstem.getTexter().setText(e.getMessage());
                        brainstem.getToaster().makeText("Ripple error: " + e.getMessage());
                        Log.w(Brainstem.TAG, "Ripple error: " + e.getMessage());
                        e.printStackTrace(System.err);
                    }

                    String mod = modifySymbol(symbol, modifier);
                    //toaster.makeText("typed: " + mod);
                    if (null != brainModeWrapper) {
                        brainModeWrapper.write(mod);
                    }
                } else {
                    Mode mode = currentButtonState.mode;
                    // this sets the mode for *subsequent* key events
                    if (null != mode) {
                        currentMode = mode;

                        sendModeInfo();

                        Log.i(Brainstem.TAG, "entered mode: " + mode);
                    }
                }
            }

            currentButtonState = rootStates.get(currentMode);
        }
    }

    private void inputReceived(byte[] input) throws IOException, RippleException {
        for (int i = 0; i < 5; i++) {
            // Generally, at most one button should change per time step
            // However, if two buttons change state, it is an arbitrary choice w.r.t. which one changed first
            if (input[i] != lastInput[i]) {
                if ('1' == input[i]) {
                    buttonPressed(i);
                } else {
                    buttonReleased(i);
                }
            }
        }

        lastInput = input;
    }

}
