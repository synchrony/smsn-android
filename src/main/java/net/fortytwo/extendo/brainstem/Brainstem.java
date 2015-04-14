package net.fortytwo.extendo.brainstem;

import android.util.Log;
import edu.rpi.twc.sesamestream.BindingSetHandler;
import edu.rpi.twc.sesamestream.QueryEngine;
import net.fortytwo.extendo.Extendo;
import net.fortytwo.extendo.Main;
import net.fortytwo.extendo.hand.ExtendoHandControl;
import net.fortytwo.extendo.p2p.ExtendoAgent;
import net.fortytwo.extendo.p2p.Pinger;
import net.fortytwo.extendo.p2p.SideEffects;
import net.fortytwo.extendo.p2p.osc.OscControl;
import net.fortytwo.extendo.p2p.osc.OscReceiver;
import net.fortytwo.extendo.rdf.Activities;
import net.fortytwo.extendo.typeatron.TypeatronControl;
import net.fortytwo.extendo.util.TypedProperties;
import org.openrdf.model.URI;
import org.openrdf.query.BindingSet;

import java.io.File;
import java.io.IOException;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class Brainstem {
    // no query expiration for now
    private static final int QUERY_TTL = 0;

    /**
     * Tag for all Brainstem-specific log messages
     */
    public static final String TAG = "Brainstem";

    /**
     * The Brainstem's SDP service name for Bluetooth communication
     */
    public static final String
            BLUETOOTH_NAME = "Extendo";

    public static final String
            PROP_EXTENDOHAND_ADDRESS = "net.fortytwo.extendo.brainstem.handAddress",
            PROP_TYPEATRON_ADDRESS = "net.fortytwo.extendo.brainstem.typeatronAddress";

    /**
     * The expected location of Brainstem's configuration file
     * This file is currently separate from the main Extendo configuration, ./extendo.properties.
     * On Android, the latter may be an inaccessible location (in the file system root)
     */
    public static final String PROPS_PATH = "/sdcard/extendo.properties";

    private ExtendoAgent agent;

    private final BluetoothManager bluetoothManager;
    // receives OSC messages from the Bluetooth devices (as opposed to the WiFi interface)
    private final OscReceiver bluetoothOscReceiver;

    private final NotificationToneGenerator toneGenerator = new NotificationToneGenerator();
    private Main.Speaker speaker;
    private Main.Texter texter;
    private Main.Toaster toaster;

    private static final Brainstem INSTANCE;

    static {
        try {
            INSTANCE = new Brainstem();
        } catch (BrainstemException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private Brainstem() throws BrainstemException {
        bluetoothOscReceiver = new OscReceiver();
        bluetoothManager = BluetoothManager.getInstance(bluetoothOscReceiver);

        try {
            loadConfiguration();
        } catch (TypedProperties.PropertyException e) {
            throw new BrainstemException(e);
        } catch (IOException e) {
            throw new BrainstemException(e);
        } catch (OscControl.DeviceInitializationException e) {
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
    private void loadConfiguration()
            throws BrainstemException, TypedProperties.PropertyException,
            IOException, OscControl.DeviceInitializationException {

        Extendo.addConfiguration(new File(PROPS_PATH));
        TypedProperties configuration = Extendo.getConfiguration();

        // note: currently, setTextEditor() must be called before passing textEditor to the device controls

        try {
            agent = new ExtendoAgent(true);

            final BindingSetHandler gbGestureAnswerHandler = new BindingSetHandler() {
                public void handle(final BindingSet bindings) {
                    //long delay = System.currentTimeMillis() - agent.timeOfLastEvent;

                    toneGenerator.play();

                    //toaster.makeText("latency (before tone) = " + delay + "ms");

                    Log.i(Brainstem.TAG, "received SPARQL query result: " + bindings);
                }
            };
            agent.getQueryEngine().addQuery(QUERY_TTL, Activities.QUERY_FOR_ALL_GB_GESTURES, gbGestureAnswerHandler);

            final BindingSetHandler twcDemoHandler0 = new BindingSetHandler() {
                public void handle(final BindingSet bindings) {
                    Log.i(Brainstem.TAG, "person " + bindings.getValue("pointedTo")
                            + " pointed to: " + bindings.getValue("pointedTo"));
                }
            };
            agent.getQueryEngine().addQuery(QUERY_TTL, Activities.QUERY_FOR_THINGS_POINTED_TO, twcDemoHandler0);

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
            agent.getQueryEngine().addQuery(
                    QUERY_TTL, Activities.QUERY_FOR_THINGS_POINTED_TO_WITH_COMMON_ORG, twcDemoHandler1);

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
            agent.getQueryEngine().addQuery(
                    QUERY_TTL, Activities.QUERY_FOR_THINGS_POINTED_TO_WITH_COMMON_INTEREST, twcDemoHandler2);
        } catch (QueryEngine.InvalidQueryException e) {
            throw new BrainstemException(e);
        } catch (IOException e) {
            throw new BrainstemException(e);
        } catch (QueryEngine.IncompatibleQueryException e) {
            throw new BrainstemException(e);
        }

        SideEffects sideEffects = new BrainstemSideEffects(this);

        String extendoHandAddress = configuration.getProperty(PROP_EXTENDOHAND_ADDRESS);
        if (null != extendoHandAddress) {
            Log.i(TAG, "connecting to Extend-o-Hand at address " + extendoHandAddress);
            addBluetoothDevice(
                    extendoHandAddress, new ExtendoHandControl(bluetoothOscReceiver, agent));
        }

        String typeatronAddress = configuration.getProperty(PROP_TYPEATRON_ADDRESS);
        if (null != typeatronAddress) {
            Log.i(TAG, "connecting to Typeatron at address " + typeatronAddress);
            try {
                addBluetoothDevice(
                        typeatronAddress, new TypeatronControl(bluetoothOscReceiver, this.getAgent(), sideEffects));
            } catch (OscControl.DeviceInitializationException e) {
                throw new BrainstemException(e);
            }
        }
    }

    private void addBluetoothDevice(final String bluetoothAddress,
                                    final OscControl dc) {
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
