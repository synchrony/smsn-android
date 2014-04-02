package net.fortytwo.extendo.brainstem.osc;

import android.util.Log;
import com.illposed.osc.OSCBundle;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPacket;
import com.illposed.osc.utility.OSCByteArrayToJavaConverter;
import net.fortytwo.extendo.brainstem.Brainstem;
import net.fortytwo.extendo.brainstem.bluetooth.BluetoothManager;
import net.fortytwo.extendo.brainstem.osc.OSCMessageHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class OSCDispatcher {

    private final Map<String, OSCMessageHandler> handlers;

    public OSCDispatcher() {
        handlers = new HashMap<String, OSCMessageHandler>();
    }

    public void register(final String address,
                         final OSCMessageHandler handler) {
        // note: no checking for duplicate addresses

        handlers.put(address, handler);
    }

    /**
     * Route an OSC message to the appropriate handler
     *
     * @param message the received message to be handled
     * @return whether a matching handler was found
     */
    public boolean dispatch(final OSCMessage message) {
        OSCMessageHandler handler = handlers.get(message.getAddress());

        if (null == handler) {
            return false;
        } else {
            handler.handle(message);
            return true;
        }
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

                /*
                // TODO: temporary debugging code
                String address = ((OSCMessage) p).getAddress();
                StringBuilder sb = new StringBuilder("address bytes:");
                for (byte b : address.getBytes()) {
                    sb.append(" ").append((int) b);
                }
                Log.w(Brainstem.TAG, sb.toString());
                */
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
                //OSCBundle bundle = new OSCBundle();
                //bundle.addPacket(message);
                //messageWriter.sendMessage(bundle.getByteArray());
                messageWriter.sendMessage(message.getByteArray());
            } catch (IOException e) {
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
}
