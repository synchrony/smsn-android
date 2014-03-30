package net.fortytwo.extendo.brainstem.bluetooth;

import com.illposed.osc.OSCMessage;
import net.fortytwo.extendo.brainstem.osc.OSCDispatcher;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public abstract class BluetoothDeviceControl {
    private final String address;
    private final OSCDispatcher dispatcher;
    private BluetoothManager.BluetoothMessageWriter messageWriter;

    public BluetoothDeviceControl(final String address,
                                  final OSCDispatcher dispatcher) {
        this.address = address;
        this.dispatcher = dispatcher;
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

    protected long latestPing;

    // convenience method
    public void doPing() {
        OSCMessage message = new OSCMessage("/exo/tt/ping");
        latestPing = System.currentTimeMillis();
        send(message);
    }

    public void send(final OSCMessage message) {
        dispatcher.send(message, messageWriter);
    }

    public class DeviceInitializationException extends Exception {
        public DeviceInitializationException(final Throwable cause) {
            super(cause);
        }
    }
}
