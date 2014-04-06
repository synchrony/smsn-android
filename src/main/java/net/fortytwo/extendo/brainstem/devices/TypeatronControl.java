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
    private final String
            EXO_TT_MODE = "/exo/tt/mode",
            EXO_TT_MORSE = "/exo/tt/morse",
            EXO_TT_PHOTO_GET = "/exo/tt/photo/get",
            EXO_TT_VIBR = "/exo/tt/vibr";

    // inbound addresses
    private final String
            EXO_TT_ERROR = "/exo/tt/error",
            EXO_TT_INFO = "/exo/tt/info",
            EXO_TT_KEYS = "/exo/tt/keys",
            EXO_TT_PHOTO_DATA = "/exo/tt/photo/data",
            EXO_TT_PING_REPLY = "/exo/tt/ping/reply";


    private final Brainstem brainstem;

    private byte[] lastInput;

    public enum Mode {
        LowercaseText, UppercaseText, Punctuation, Numeric, Hardware, Mash;

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
    private Modifier currentModifier;
    private StateNode currentButtonState;
    private int totalButtonsCurrentlyPressed;

    private final BrainModeClientWrapper brainModeWrapper;
    private final RippleSession rippleSession;
    private final ExtendoRippleREPL rippleREPL;

    private StringBuilder currentLineOfText = new StringBuilder();

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
                    brainstem.getToaster().makeText("\ninfo message from Typeatron: " + args[0]);
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
                    brainstem.getToaster().makeText("Typeatron keys: " + args[0] + " (" + totalButtonsCurrentlyPressed + " pressed)");
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

    private void handleLineOfText(final String text) throws RippleException {
        brainstem.getToaster().makeText("typed: " + text);

        /*
        // TODO: this should be controlled by a primitive
        sendMorseCommand(text);

        RippleSession ripple = brainstem.getRippleSession();
        RippleValue value = ripple.getModelConnection().valueOf(text);
        brainstem.getRippleSession().push(value);
        */
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

    private void addChord(final String sequence,
                          final Mode outputMode,
                          final Modifier outputModifier,
                          final String outputSymbol,
                          final Mode inputMode) {
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
        } else if (null != cur.modifier && Modifier.None != cur.modifier && (null != cur.mode || null != cur.symbol)) {
            throw new IllegalStateException("sequence has output modifier and also output symbol or mode");
        }
    }

    private void setupParser() {
        // TODO: we shouldn't assume the device powers up with no buttons pressed, although this is likely
        totalButtonsCurrentlyPressed = 0;
        lastInput = "00000".getBytes();

        rootStates = new HashMap<Mode, StateNode>();
        for (Mode m : Mode.values()) {
            rootStates.put(m, new StateNode());
        }

        currentMode = Mode.LowercaseText;
        currentModifier = Modifier.None;
        currentButtonState = rootStates.get(currentMode);

        // mode entry from default mode
        addChord("1221", null, Modifier.Control, null, Mode.LowercaseText);
        addChord("1212", Mode.Hardware, null, null, Mode.LowercaseText);
        addChord("1331", Mode.Punctuation, null, null, Mode.LowercaseText);
        addChord("1313", Mode.Numeric, null, null, Mode.LowercaseText);
        addChord("1441", Mode.UppercaseText, null, null, Mode.LowercaseText);
        addChord("1414", Mode.LowercaseText, null, null, Mode.LowercaseText);  // a no-op
        addChord("1551", Mode.Mash, Modifier.None, null, Mode.LowercaseText);
        // 1515 unassigned

        // return to default mode from anywhere other than mash mode
        for (Mode m : Mode.values()) {
            if (m != Mode.Mash) {
                addChord("11", Mode.LowercaseText, Modifier.None, null, m);
            }
        }

        // return from mash mode
        addChord("1234554321", Mode.LowercaseText, Modifier.None, null, Mode.Mash);

        // space, newline, delete, escape available in all of the text-entry modes
        for (Mode m : new Mode[]{Mode.LowercaseText, Mode.UppercaseText, Mode.Punctuation, Mode.Numeric}) {
            addChord("22", null, null, " ", m);
            //addChord("22", null, null, "SPACE", m);
            addChord("33", null, null, "\n", m);
            //addChord("33", null, null, "RET", m);
            addChord("44", null, null, "DEL", m);
            addChord("55", null, null, "ESC", m);
        }

        addChord("2112", null, null, "a", Mode.LowercaseText);
        addChord("2112", null, null, "A", Mode.UppercaseText);
        addChord("2112", null, null, "'", Mode.Punctuation);

        // 2121 unassigned

        addChord("2332", null, null, "e", Mode.LowercaseText);
        addChord("2332", null, null, "E", Mode.UppercaseText);
        addChord("2332", null, null, "=", Mode.Punctuation);

        addChord("2323", null, null, "w", Mode.LowercaseText);
        addChord("2323", null, null, "W", Mode.UppercaseText);
        addChord("2323", null, null, "@", Mode.Punctuation);

        addChord("2442", null, null, "i", Mode.LowercaseText);
        addChord("2442", null, null, "I", Mode.UppercaseText);
        addChord("2442", null, null, ":", Mode.Punctuation);

        addChord("2424", null, null, "y", Mode.LowercaseText);
        addChord("2424", null, null, "Y", Mode.UppercaseText);
        addChord("2424", null, null, "&", Mode.Punctuation);

        addChord("2552", null, null, "o", Mode.LowercaseText);
        addChord("2552", null, null, "O", Mode.UppercaseText);
        // no punctuation associated with "o"

        addChord("2525", null, null, "u", Mode.LowercaseText);
        addChord("2525", null, null, "U", Mode.UppercaseText);
        addChord("2525", null, null, "_", Mode.Punctuation);

        addChord("3113", null, null, "p", Mode.LowercaseText);
        addChord("3113", null, null, "P", Mode.UppercaseText);
        addChord("3113", null, null, "+", Mode.Punctuation);

        addChord("3131", null, null, "b", Mode.LowercaseText);
        addChord("3131", null, null, "B", Mode.UppercaseText);
        addChord("3131", null, null, "\\", Mode.Punctuation);

        addChord("3223", null, null, "t", Mode.LowercaseText);
        addChord("3223", null, null, "T", Mode.UppercaseText);
        addChord("3223", null, null, "~", Mode.Punctuation);

        addChord("3232", null, null, "d", Mode.LowercaseText);
        addChord("3232", null, null, "D", Mode.UppercaseText);
        addChord("3232", null, null, "$", Mode.Punctuation);

        addChord("3443", null, null, "k", Mode.LowercaseText);
        addChord("3443", null, null, "K", Mode.UppercaseText);
        addChord("3443", null, null, "*", Mode.Punctuation);

        addChord("3434", null, null, "g", Mode.LowercaseText);
        addChord("3434", null, null, "G", Mode.UppercaseText);
        addChord("3434", null, null, "`", Mode.Punctuation);

        addChord("3553", null, null, "q", Mode.LowercaseText);
        addChord("3553", null, null, "Q", Mode.UppercaseText);
        addChord("3553", null, null, "?", Mode.Punctuation);

        // 3535 unassigned

        addChord("4114", null, null, "f", Mode.LowercaseText);
        addChord("4114", null, null, "F", Mode.UppercaseText);
        addChord("4114", null, null, ".", Mode.Punctuation);

        addChord("4141", null, null, "v", Mode.LowercaseText);
        addChord("4141", null, null, "V", Mode.UppercaseText);
        addChord("4141", null, null, "|", Mode.Punctuation);

        addChord("4224", null, null, "c", Mode.LowercaseText);
        addChord("4224", null, null, "C", Mode.UppercaseText);
        addChord("4224", null, null, ",", Mode.Punctuation);

        addChord("4242", null, null, "j", Mode.LowercaseText);
        addChord("4242", null, null, "J", Mode.UppercaseText);
        addChord("4242", null, null, ";", Mode.Punctuation);

        addChord("4334", null, null, "s", Mode.LowercaseText);
        addChord("4334", null, null, "S", Mode.UppercaseText);
        addChord("4334", null, null, "/", Mode.Punctuation);

        addChord("4343", null, null, "z", Mode.LowercaseText);
        addChord("4343", null, null, "Z", Mode.UppercaseText);
        addChord("4343", null, null, "%", Mode.Punctuation);

        addChord("4554", null, null, "h", Mode.LowercaseText);
        addChord("4554", null, null, "H", Mode.UppercaseText);
        addChord("4554", null, null, "^", Mode.Punctuation);

        addChord("4545", null, null, "x", Mode.LowercaseText);
        addChord("4545", null, null, "X", Mode.UppercaseText);
        addChord("4545", null, null, "!", Mode.Punctuation);

        addChord("5115", null, null, "m", Mode.LowercaseText);
        addChord("5115", null, null, "M", Mode.UppercaseText);
        addChord("5115", null, null, "-", Mode.Punctuation);

        // 5151 unassigned

        addChord("5225", null, null, "n", Mode.LowercaseText);
        addChord("5225", null, null, "N", Mode.UppercaseText);
        addChord("5225", null, null, "#", Mode.Punctuation);

        // 5252 unassigned

        addChord("5335", null, null, "l", Mode.LowercaseText);
        addChord("5335", null, null, "L", Mode.UppercaseText);
        addChord("5335", null, null, "\"", Mode.Punctuation);

        // 5353 unassigned

        addChord("5445", null, null, "r", Mode.LowercaseText);
        addChord("5445", null, null, "R", Mode.UppercaseText);
        // no punctuation associated with "r" (reserved for right-handed quotes)

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

    private String modifySymbol(final String symbol) {
        switch (currentModifier) {
            case Control:
                return symbol.length() == 1 ? "<C-" + symbol + ">" : "<" + symbol + ">";
            case None:
                return symbol.length() == 1 ? symbol : "<" + symbol + ">";
            default:
                throw new IllegalStateException();
        }
    }

    private void buttonReleased(int buttonIndex) throws IOException, RippleException {
        totalButtonsCurrentlyPressed--;

        buttonEvent(buttonIndex);

        // at present, events are triggered when the last key of a sequence is released
        if (0 == totalButtonsCurrentlyPressed) {
            if (null != currentButtonState) {
                String symbol = currentButtonState.symbol;
                if (null != symbol) {
                    rippleREPL.handle(symbol, currentModifier, currentMode);

                    String mod = modifySymbol(symbol);
                    //toaster.makeText("typed: " + mod);
                    if (null != brainModeWrapper) {
                        brainModeWrapper.write(mod);
                    }

                    if ("\n".equals(symbol)) {
                        String text = currentLineOfText.toString();
                        currentLineOfText = new StringBuilder();

                        handleLineOfText(text);
                    } else {
                        currentLineOfText.append(symbol);
                    }
                } else {
                    Mode mode = currentButtonState.mode;
                    if (null != mode) {
                        currentMode = mode;

                        sendModeInfo();

                        brainstem.getToaster().makeText("entered mode: " + mode);
                    }
                    Modifier modifier = currentButtonState.modifier;
                    if (null != modifier) {
                        currentModifier = modifier;
                        brainstem.getToaster().makeText("using modifier: " + modifier);
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
