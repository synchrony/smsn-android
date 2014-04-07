package net.fortytwo.extendo.brainstem;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.util.Log;
import android.widget.EditText;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPacket;
import com.illposed.osc.utility.OSCByteArrayToJavaConverter;
import com.tinkerpop.blueprints.KeyIndexableGraph;
import com.tinkerpop.blueprints.impls.tg.TinkerGraph;
import edu.rpi.twc.sesamestream.BindingSetHandler;
import edu.rpi.twc.sesamestream.QueryEngine;
import net.fortytwo.extendo.Main;
import net.fortytwo.extendo.brain.BrainGraph;
import net.fortytwo.extendo.brain.ExtendoBrain;
import net.fortytwo.extendo.brainstem.bluetooth.BluetoothDeviceControl;
import net.fortytwo.extendo.brainstem.bluetooth.BluetoothManager;
import net.fortytwo.extendo.brainstem.devices.ExtendoHandControl;
import net.fortytwo.extendo.brainstem.devices.TypeatronControl;
import net.fortytwo.extendo.brainstem.ripple.RippleSession;
import net.fortytwo.extendo.brainstem.osc.OSCDispatcher;
import net.fortytwo.extendo.p2p.Pinger;
import net.fortytwo.extendo.util.properties.PropertyException;
import net.fortytwo.extendo.util.properties.TypedProperties;
import net.fortytwo.rdfagents.model.Dataset;
import net.fortytwo.ripple.RippleException;
import org.openrdf.query.BindingSet;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class Brainstem {
    public static final String TAG = "Brainstem";

    public static final boolean VERBOSE = true;

    public static final String
            BLUETOOTH_NAME = "Extendo";

    private static final String PROPS_PATH = "/sdcard/brainstem.props";

    public static final String
            PROP_AGENTURI = "net.fortytwo.extendo.agentUri",
            PROP_REXSTER_URL = "net.fortytwo.extendo.rexsterUrl",
            PROP_REXSTER_GRAPH = "net.fortytwo.extendo.rexsterGraph",
            PROP_EXTENDOHAND_ADDRESS = "net.fortytwo.extendo.hand.address",
            PROP_TYPEATRON_ADDRESS = "net.fortytwo.extendo.typeatron.address";

    private final List<BluetoothDeviceControl> devices;

    private BluetoothDeviceControl extendoHand;
    private BluetoothDeviceControl typeatron;

    private EditText textEditor;

    private final OSCDispatcher oscDispatcher;
    private final BluetoothManager bluetoothManager;

    private final NotificationToneGenerator toneGenerator = new NotificationToneGenerator();

    private Properties configuration;

    private BrainstemAgent agent;
    private final ExtendoBrain brain;
    private final RippleSession rippleSession;

    private Main.Toaster toaster;
    private boolean emacsAvailable;

    private static final Brainstem INSTANCE;

    static {
        try {
            INSTANCE = new Brainstem();
        } catch (BrainstemException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private Brainstem() throws BrainstemException {
        oscDispatcher = new OSCDispatcher();
        bluetoothManager = BluetoothManager.getInstance(oscDispatcher);

        devices = new LinkedList<BluetoothDeviceControl>();

        // TODO: this TinkerGraph is a temporary solution
        KeyIndexableGraph g = new TinkerGraph();
        BrainGraph bg = new BrainGraph(g);
        try {
            brain = new ExtendoBrain(bg);
        } catch (ExtendoBrain.ExtendoBrainException e) {
            throw new BrainstemException(e);
        }

        try {
            rippleSession = new RippleSession();
        } catch (RippleException e) {
            throw new BrainstemException(e);
        }

        try {
            loadConfiguration();
        } catch (PropertyException e) {
            throw new BrainstemException(e);
        }
    }

    public static Brainstem getInstance() {
        return INSTANCE;
    }

    /**
     * Load and configure resources with dependencies which cannot be resolved at construction time,
     * such as (currently) the text editor
     */
    public void initialize(final Main.Toaster toaster,
                           final EditText textEditor,
                           final boolean emacsAvailable) {
        this.toaster = toaster;
        this.textEditor = textEditor;
        this.emacsAvailable = emacsAvailable;
    }

    public BluetoothManager getBluetoothManager() {
        return bluetoothManager;
    }

    public RippleSession getRippleSession() {
        return rippleSession;
    }

    public EditText getTextEditor() {
        return textEditor;
    }

    public Main.Toaster getToaster() {
        return toaster;
    }

    public void disconnect(final Context context) {
        /*
        for (BluetoothDeviceControl d : devices) {
            d.disconnect(context);
        }

        // don't forget to unregister a registered receiver
        context.unregisterReceiver(arduinoReceiver);
        */
    }

    // note: in Android, SharedPreferences are preferred to properties files.  This file specifically contains those
    // settings which change more frequently than the APK is loaded, such as network settings.
    // Ideally, this file will go away entirely once the Brainstem becomes reusable software rather than a
    // special-purpose component of a demo.
    private void loadConfiguration() throws BrainstemException, PropertyException {
        // this configuration is currently separate from the main Extendo configuration, which reads from
        // ./extendo.properties.  On Android, this path may be an inaccessible location (in the file system root)
        if (null == configuration) {
            File f = new File(PROPS_PATH);
            if (!f.exists()) {
                throw new BrainstemException("configuration properties not found: " + PROPS_PATH);
            }

            configuration = new TypedProperties();
            try {
                configuration.load(new FileInputStream(f));
            } catch (IOException e) {
                throw new BrainstemException(e);
            }
        }

        // temporary code to investigate merging brainstem.props with extendo.properties
        /*
        Log.i(TAG, "creating extendo.properties");
        try {
            File f = new File("extendo.properties");
            Log.i(TAG, "f.getAbsolutePath(): " + f.getAbsolutePath());
            Log.i(TAG, "f.exists(): " + f.exists());
            if (!f.exists()) {
                f.createNewFile();
                OutputStream fout = new FileOutputStream(f);
                fout.write("foo".getBytes());
                fout.close();
            }
        } catch (IOException e) {
            throw new BrainstemException(e);
        }
        */

        String rexsterUrl = configuration.getProperty(PROP_REXSTER_URL);
        String rexsterGraph = configuration.getProperty(PROP_REXSTER_GRAPH);
        if (null == rexsterUrl || null == rexsterGraph) {
            throw new BrainstemException("Rexster endpoint info is missing from configuration: use "
                    + PROP_REXSTER_URL + " and " + PROP_REXSTER_GRAPH);
        }

        String endpoint = rexsterUrl + "/graphs/" + rexsterGraph + "/extendo/";

        EventStackProxy proxy = new EventStackProxy(endpoint + "push-event");

        // note: currently, setTextEditor() must be called before passing textEditor to the device controls

        String u = configuration.getProperty(PROP_AGENTURI);
        if (null == u) {
            throw new BrainstemException("who are you? Missing value for " + PROP_AGENTURI);
        } else {
            try {
                agent = new BrainstemAgent(u);
                Log.i(TAG, "just created BrainstemAgent with URI " + u);

                final BindingSetHandler queryAnswerHandler = new BindingSetHandler() {
                    public void handle(final BindingSet bindings) {
                        long delay = System.currentTimeMillis() - agent.timeOfLastEvent;

                        toneGenerator.play();

                        toaster.makeText("latency (before tone) = " + delay + "ms");

                        Log.i(Brainstem.TAG, "received SPARQL query result: " + bindings);
                    }
                };

                agent.getQueryEngine().addQuery(BrainstemAgent.QUERY_FOR_ALL_GB_GESTURES, queryAnswerHandler);
            } catch (QueryEngine.InvalidQueryException e) {
                throw new BrainstemException(e);
            } catch (IOException e) {
                throw new BrainstemException(e);
            } catch (QueryEngine.IncompatibleQueryException e) {
                throw new BrainstemException(e);
            }
        }

        String extendoHandAddress = configuration.getProperty(PROP_EXTENDOHAND_ADDRESS);
        if (null != extendoHandAddress) {
            Log.i(TAG, "loading Extend-o-Hand device at address " + extendoHandAddress);
            extendoHand
                    = new ExtendoHandControl(extendoHandAddress, oscDispatcher, brain, proxy, agent, this);
            addBluetoothDevice(extendoHand);
        }

        String typeatronAddress = configuration.getProperty(PROP_TYPEATRON_ADDRESS);
        if (null != typeatronAddress) {
            Log.i(TAG, "loading Typeatron device at address " + typeatronAddress);
            try {
                typeatron
                        = new TypeatronControl(typeatronAddress, oscDispatcher, this);
            } catch (BluetoothDeviceControl.DeviceInitializationException e) {
                throw new BrainstemException(e);
            }
            addBluetoothDevice(typeatron);
        }
    }

    private void addBluetoothDevice(final BluetoothDeviceControl dc) {
        devices.add(dc);
        bluetoothManager.register(dc);
    }

    private void handleOSCData(final String data) {
        OSCByteArrayToJavaConverter c = new OSCByteArrayToJavaConverter();

        // TODO: is the array length all that is expected for the second argument?
        OSCPacket p = c.convert(data.getBytes(), data.getBytes().length);

        if (p instanceof OSCMessage) {
            if (!oscDispatcher.dispatch((OSCMessage) p)) {
                Log.w(TAG, "no OSC handler at address " + ((OSCMessage) p).getAddress());

                // TODO: temporary debugging code
                String address = ((OSCMessage) p).getAddress();
                StringBuilder sb = new StringBuilder("address bytes:");
                for (byte b : address.getBytes()) {
                    sb.append(" ").append((int) b);
                }
                Log.w(TAG, sb.toString());
            }
        } else {
            Log.w(TAG, "OSC packet is of non-message type " + p.getClass().getSimpleName() + ": " + p);
        }

        /*
        int i = message.indexOf(" ");
        if (i > 0) {
            String prefix = message.substring(i);

            BluetoothOSCDeviceControl dc = deviceByOSCPrefix.get(prefix);
            if (null == dc) {
                Log.w(TAG, "no control matching OSC address " + prefix);
            } else {
                dc.handleOSCStyleMessage(message);
            }
        }
        */
    }

    public class BrainstemException extends Exception {
        public BrainstemException(final String message) {
            super(message);
        }

        public BrainstemException(final Throwable cause) {
            super(cause);
        }
    }

    public class NotificationToneGenerator {
        // Note: is it possible to generate a tone with lower latency than this default generator's?
        //private final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);

        private final AudioTrack audioTrack;
        private final int minSize;
        private final float synth_frequency = 880;
        private final int sampleRate;

        public NotificationToneGenerator() {
            sampleRate = getValidSampleRate();

            minSize = AudioTrack.getMinBufferSize(sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minSize,
                    AudioTrack.MODE_STREAM);
        }

        public void play() {
            //startActivity(new Intent(thisActivity, PlaySound.class));

            //tg.startTone(ToneGenerator.TONE_PROP_BEEP);

            audioTrack.play();
            short[] buffer = new short[minSize];
            float angle = 0;
            //while (true) {
            //    if (play) {
            for (int i = 0; i < buffer.length; i++) {
                float angular_frequency =
                        (float) (2 * Math.PI) * synth_frequency / sampleRate;
                buffer[i] = (short) (Short.MAX_VALUE * ((float) Math.sin(angle)));
                angle += angular_frequency;
            }
            audioTrack.write(buffer, 0, buffer.length);
            //    }
            //}
        }

        private int getValidSampleRate() {
            for (int rate : new int[]{8000, 11025, 16000, 22050, 44100}) {  // add the rates you wish to check against
                int bufferSize = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_CONFIGURATION_DEFAULT, AudioFormat.ENCODING_PCM_16BIT);
                if (bufferSize > 0) {
                    return rate;
                }
            }

            throw new IllegalStateException("could not find a valid sample rate for audio output");
        }
    }

    // TODO: temporary
    public void sendTestMessageToTypeatron(final Context context) {
        OSCMessage m = new OSCMessage();
        m.setAddress("/exo/tt/rgb");
        m.addArgument(0xff0000);

        typeatron.send(m);
    }

    public void simulateGestureEvent() {
        agent.timeOfLastEvent = System.currentTimeMillis();

        Date recognizedAt = new Date();

        Dataset d = agent.datasetForGestureEvent(recognizedAt.getTime());
        try {
            agent.getQueryEngine().addStatements(d.getStatements());
            //agent.broadcastDataset(d);
        } catch (Exception e) {
            Log.e(Brainstem.TAG, "failed to broadcast RDF dataset: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    public void pingFacilitatorConnection() {
        try {
            agent.getPinger().ping(new Pinger.PingResultHandler() {
                public void handleResult(long delay) {
                    toaster.makeText("ping delay: " + delay + "ms");
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "error pinging connection: " + t.getMessage());
            t.printStackTrace(System.err);
        }
    }

    public void pingExtendoHand() {
        agent.timeOfLastEvent = System.currentTimeMillis();
        // note: the timestamp argument is not yet used
        extendoHand.doPing();
    }
}
