package net.fortytwo.extendo.brainstem.bluetooth;

import com.illposed.osc.OSCMessage;
import net.fortytwo.extendo.brainstem.osc.OSCDispatcher;

/**
 * A controller for a Bluetooth slave device,
 * communicating with it via OSC messages sent and received using the SLIP protocol
 *
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public abstract class BluetoothDeviceControl {
    private final String bluetoothAddress;
    private final OSCDispatcher dispatcher;
    private BluetoothManager.BluetoothMessageWriter messageWriter;

    /**
     * @param bluetoothAddress the Bluetooth address of the remote slave device
     * @param dispatcher an OSC dispatcher through which to receive messages
     */
    public BluetoothDeviceControl(final String bluetoothAddress,
                                  final OSCDispatcher dispatcher) {
        this.bluetoothAddress = bluetoothAddress;
        this.dispatcher = dispatcher;
    }

    /**
     * @return the Bluetooth address of the remote slave device
     */
    public String getBluetoothAddress() {
        return bluetoothAddress;
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

    public void send(final OSCMessage message) {
        dispatcher.send(message, messageWriter);
    }

    public class DeviceInitializationException extends Exception {
        public DeviceInitializationException(final Throwable cause) {
            super(cause);
        }
    }
}
