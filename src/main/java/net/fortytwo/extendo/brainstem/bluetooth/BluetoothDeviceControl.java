package net.fortytwo.extendo.brainstem.bluetooth;

import android.content.Context;
import android.util.Log;
import com.illposed.osc.OSCMessage;
import net.fortytwo.extendo.brainstem.Brainstem;

import java.io.IOException;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public abstract class BluetoothDeviceControl {
    private final String address;
    private Context context;
    private BluetoothManager.BluetoothMessageWriter messageWriter;

    public BluetoothDeviceControl(final String address) {
        this.address = address;
    }

    public String getAddress() {
        return address;
    }

    public void connect(final BluetoothManager.BluetoothMessageWriter messageWriter) {
        this.messageWriter = messageWriter;

        onConnect();
    }

    public void disconnect() {
        this.messageWriter = null;
    }

    // override this method
    protected void onConnect() {
    }

    public void sendOSCMessage(final OSCMessage message) {
        if (null == this.messageWriter) {
            Log.w(Brainstem.TAG, "can't send OSC message; message writer is null");
        }

        try {
            messageWriter.sendMessage(message.getByteArray());
        } catch (IOException e) {
            Log.e(Brainstem.TAG, "error while sending OSC message to Bluetooth device at " + this.getAddress() + ": " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    protected long latestPing;

    // convenience method
    public void doPing() {
        OSCMessage m = new OSCMessage("/exo/tt/ping");
        latestPing = System.currentTimeMillis();
        sendOSCMessage(m);
    }

    public class DeviceInitializationException extends Exception {
        public DeviceInitializationException(final Throwable cause) {
            super(cause);
        }
    }
}
