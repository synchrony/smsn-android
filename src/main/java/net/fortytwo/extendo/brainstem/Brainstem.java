package net.fortytwo.extendo.brainstem;

import android.util.Log;
import com.illposed.osc.OSCMessage;
import edu.rpi.twc.sesamestream.BindingSetHandler;
import edu.rpi.twc.sesamestream.QueryEngine;
import net.fortytwo.extendo.Main;
import net.fortytwo.extendo.p2p.ExtendoAgent;
import net.fortytwo.extendo.p2p.Pinger;
import net.fortytwo.extendo.p2p.SideEffects;
import net.fortytwo.extendo.p2p.osc.OSCDispatcher;
import net.fortytwo.extendo.p2p.osc.SlipOscControl;
import net.fortytwo.extendo.rdf.Gesture;
import net.fortytwo.extendo.typeatron.TypeatronControl;
import net.fortytwo.extendo.util.TypedProperties;
import org.openrdf.model.URI;
import org.openrdf.query.BindingSet;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class Brainstem {
    public static final String TAG = "Brainstem";

    public static final boolean VERBOSE = true;

    public static final String
            BLUETOOTH_NAME = "Extendo";

    public static final String
            PROP_AGENTURI = "net.fortytwo.extendo.agentUri",
            PROP_EXTENDOHAND_ADDRESS = "net.fortytwo.extendo.hand.address",
            PROP_TYPEATRON_ADDRESS = "net.fortytwo.extendo.typeatron.address";

    // TODO: make this configurable
    public static final boolean RELAY_OSC = true;

    private static final String PROPS_PATH = "/sdcard/brainstem.props";

    private Main.Texter texter;

    private final OSCDispatcher oscDispatcher;
    private final BluetoothManager bluetoothManager;

    private final NotificationToneGenerator toneGenerator = new NotificationToneGenerator();

    private Properties configuration;

    private ExtendoAgent agent;

    private Main.Toaster toaster;
    private Main.Speaker speaker;

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
        oscDispatcher.setVerbose(VERBOSE);
        bluetoothManager = BluetoothManager.getInstance(oscDispatcher);

        try {
            loadConfiguration();
        } catch (TypedProperties.PropertyException e) {
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
                           final Main.Speaker speaker,
                           final Main.Texter texter,
                           final boolean emacsAvailable) {
        this.toaster = toaster;
        this.speaker = speaker;
        this.texter = texter;
    }

    public ExtendoAgent getAgent() {
        return agent;
    }

    public BluetoothManager getBluetoothManager() {
        return bluetoothManager;
    }

    public Main.Texter getTexter() {
        return texter;
    }

    public Main.Toaster getToaster() {
        return toaster;
    }

    public Main.Speaker getSpeaker() {
        return speaker;
    }

    // note: in Android, SharedPreferences are preferred to properties files.  This file specifically contains those
    // settings which change more frequently than the APK is loaded, such as network settings.
    // Ideally, this file will go away entirely once the Brainstem becomes reusable software rather than a
    // special-purpose component of a demo.
    private void loadConfiguration() throws BrainstemException, TypedProperties.PropertyException {
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

        // note: currently, setTextEditor() must be called before passing textEditor to the device controls

        String u = configuration.getProperty(PROP_AGENTURI);
        if (null == u) {
            throw new BrainstemException("who are you? Missing value for " + PROP_AGENTURI);
        } else {
            try {
                agent = new ExtendoAgent(u, true);
                Log.i(TAG, "created BrainstemAgent with URI " + u);

                final BindingSetHandler gbGestureAnswerHandler = new BindingSetHandler() {
                    public void handle(final BindingSet bindings) {
                        //long delay = System.currentTimeMillis() - agent.timeOfLastEvent;

                        toneGenerator.play();

                        //toaster.makeText("latency (before tone) = " + delay + "ms");

                        Log.i(Brainstem.TAG, "received SPARQL query result: " + bindings);
                    }
                };
                agent.getQueryEngine().addQuery(Gesture.QUERY_FOR_ALL_GB_GESTURES, gbGestureAnswerHandler);

                final BindingSetHandler twcDemoHandler0 = new BindingSetHandler() {
                    public void handle(final BindingSet bindings) {
                        Log.i(Brainstem.TAG, "person " + bindings.getValue("pointedTo")
                                + " pointed to: " + bindings.getValue("pointedTo"));
                    }
                };
                agent.getQueryEngine().addQuery(Gesture.QUERY_FOR_THING_POINTED_TO, twcDemoHandler0);

                final BindingSetHandler twcDemoHandler1 = new BindingSetHandler() {
                    public void handle(final BindingSet bindings) {
                        //long delay = System.currentTimeMillis() - agent.timeOfLastEvent;

                        toneGenerator.play();

                        String speech = bindings.getValue("personPointedToName").stringValue()
                                + ", you're both members of "
                                + bindings.getValue("orgLabel").stringValue();
                        speaker.speak(speech);

                        //toaster.makeText("latency (before tone) = " + delay + "ms");

                        Log.i(Brainstem.TAG, "pointed to: " + bindings.getValue("personPointedTo") + " with org: "
                                + bindings.getValue("orgLabel"));
                    }
                };
                agent.getQueryEngine().addQuery(Gesture.QUERY_FOR_POINT_WITH_COMMON_ORG, twcDemoHandler1);

                final BindingSetHandler twcDemoHandler2 = new BindingSetHandler() {
                    public void handle(final BindingSet bindings) {
                        //long delay = System.currentTimeMillis() - agent.timeOfLastEvent;

                        toneGenerator.play();

                        String speech = bindings.getValue("personPointedToName").stringValue() + ", you both like "
                                + ((URI) bindings.getValue("interest")).getLocalName().replaceAll("_", " ");
                        speaker.speak(speech);

                        //toaster.makeText("latency (before tone) = " + delay + "ms");

                        Log.i(Brainstem.TAG, "pointed to: " + bindings.getValue("personPointedTo") + " with interest: "
                                + bindings.getValue("interest"));
                    }
                };
                agent.getQueryEngine().addQuery(Gesture.QUERY_FOR_POINT_WITH_COMMON_INTEREST, twcDemoHandler2);

                if (RELAY_OSC) {
                    oscDispatcher.addListener(new OSCDispatcher.OSCMessageListener() {
                        public void handle(final OSCMessage m) {
                            agent.sendOSCMessageToFacilitator(m);
                        }
                    });
                }
            } catch (QueryEngine.InvalidQueryException e) {
                throw new BrainstemException(e);
            } catch (IOException e) {
                throw new BrainstemException(e);
            } catch (QueryEngine.IncompatibleQueryException e) {
                throw new BrainstemException(e);
            }
        }

        SideEffects sideEffects = new BrainstemSideEffects(this);

        String extendoHandAddress = configuration.getProperty(PROP_EXTENDOHAND_ADDRESS);
        if (null != extendoHandAddress) {
            Log.i(TAG, "connecting to Extend-o-Hand at address " + extendoHandAddress);
            addBluetoothDevice(
                    extendoHandAddress, new ExtendoHandControl(oscDispatcher, agent, sideEffects));
        }

        String typeatronAddress = configuration.getProperty(PROP_TYPEATRON_ADDRESS);
        if (null != typeatronAddress) {
            Log.i(TAG, "connecting to Typeatron at address " + typeatronAddress);
            try {
                addBluetoothDevice(
                        typeatronAddress, new TypeatronControl(oscDispatcher, this.getAgent(), sideEffects));
            } catch (SlipOscControl.DeviceInitializationException e) {
                throw new BrainstemException(e);
            }
        }
    }

    private void addBluetoothDevice(final String bluetoothAddress,
                                    final SlipOscControl dc) {
        bluetoothManager.register(bluetoothAddress, dc);
    }

    public class BrainstemException extends Exception {
        public BrainstemException(final String message) {
            super(message);
        }

        public BrainstemException(final Throwable cause) {
            super(cause);
        }
    }

    public void pingFacilitatorConnection() {
        try {
            if (agent.getFacilitatorConnection().isActive()) {
                agent.getPinger().ping(new Pinger.PingResultHandler() {
                    public void handleResult(long delay) {
                        toaster.makeText("ping delay: " + delay + "ms");
                    }
                });
            } else {
                Log.i(TAG, "can't ping facilitator; no connection");
            }
        } catch (Throwable t) {
            Log.e(TAG, "error pinging facilitator: " + t.getMessage());
            t.printStackTrace(System.err);
        }
    }
}
