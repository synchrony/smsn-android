package net.fortytwo.extendo.brainstem.osc;

import android.util.Log;
import com.illposed.osc.OSCBundle;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPacket;
import com.illposed.osc.utility.OSCByteArrayToJavaConverter;
import net.fortytwo.extendo.brainstem.Brainstem;
import net.fortytwo.extendo.brainstem.bluetooth.BluetoothManager;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class OSCDispatcher {

    private final Map<String, OSCMessageHandler> handlers;

    private final Set<OSCMessageListener> listeners;

    public OSCDispatcher() {
        handlers = new HashMap<String, OSCMessageHandler>();
        listeners = new HashSet<OSCMessageListener>();
    }

    public void register(final String oscAddress,
                         final OSCMessageHandler handler) {
        // note: no checking for duplicate addresses

        handlers.put(oscAddress, handler);
    }

    public void addListener(final OSCMessageListener listener) {
        listeners.add(listener);
    }

    public void removeListener(final OSCMessageListener listener) {
        listeners.remove(listener);
    }

    /**
     * Route an OSC message to the appropriate handler
     *
     * @param message the received message to be handled
     * @return whether a matching handler was found
     */
    public boolean dispatch(final OSCMessage message) {
        OSCMessageHandler handler = handlers.get(message.getAddress());

        boolean handled;
        if (null == handler) {
            handled = false;
        } else {
            handler.handle(message);
            handled = true;
        }

        // give the message to the listeners after any time-critical handlers have been served
        for (OSCMessageListener listener : listeners) {
            listener.handle(message);
        }

        return handled;
    }

    public void receive(final byte[] data) {

        if (data[0] != '/') {
            Log.w(Brainstem.TAG, "not a valid OSC message: " + new String(data));
            return;
        }

        OSCByteArrayToJavaConverter c = new OSCByteArrayToJavaConverter();

        OSCPacket p = c.convert(data, data.length);

        if (p instanceof OSCMessage) {
            if (Brainstem.VERBOSE) {
                Log.i(Brainstem.TAG, "received OSC message: " + toString((OSCMessage) p));
            }

            if (!dispatch((OSCMessage) p)) {
                Log.w(Brainstem.TAG, "no OSC handler at address " + ((OSCMessage) p).getAddress());
            }
        } else {
            Log.w(Brainstem.TAG, "OSC packet is of non-message type " + p.getClass().getSimpleName() + ": " + p);
        }
    }

    public void send(final OSCMessage message,
                     final BluetoothManager.BluetoothMessageWriter messageWriter) {
        if (Brainstem.VERBOSE) {
            Log.i(Brainstem.TAG, "sending OSC message: " + toString(message));
        }

        if (null == messageWriter) {
            Log.w(Brainstem.TAG, "can't send OSC message; message writer is null");
        } else {
            try {
                //messageWriter.sendMessage(message.getByteArray());

                // Arduino-based Extendo devices receive OSC bundles and send OSC messages
                OSCBundle bundle = new OSCBundle();
                bundle.addPacket(message);
                messageWriter.sendMessage(bundle.getByteArray());
            } catch (IOException e) {
                Log.e(Brainstem.TAG, "I/O error while sending OSC message: " + e.getMessage());
                e.printStackTrace(System.err);
            } catch (BluetoothManager.BluetoothException e) {
                Log.e(Brainstem.TAG, "error while sending OSC message: " + e.getMessage());
                e.printStackTrace(System.err);
            }
        }
    }

    private String toString(final OSCMessage message) {
        StringBuilder sb = new StringBuilder(message.getAddress());

        for (Object arg : message.getArguments()) {
            sb.append(" ");
            sb.append(arg);
        }

        return sb.toString();
    }

    public interface OSCMessageListener {
        void handle(OSCMessage m);
    }
}
