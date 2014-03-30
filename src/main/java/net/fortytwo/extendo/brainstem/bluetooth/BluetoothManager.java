package net.fortytwo.extendo.brainstem.bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.util.Log;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPacket;
import com.illposed.osc.utility.OSCByteArrayToJavaConverter;
import net.fortytwo.extendo.brainstem.Brainstem;
import net.fortytwo.extendo.brainstem.osc.OSCDispatcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class BluetoothManager {

    private static final int REQUEST_ENABLE_BT = 424242;

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // TODO: this short interval doesn't give the device much time to sleep
    private static final long CONNECTION_RETRY_INTERVAL = 15000;

    public static final int
            SLIP_FRAME_END = 0xC0,
            ACK = 19,
            START_FLAG = 18;

    private boolean started = false;

    // note: currently, we never change this variable once Bluetooth is initially enabled
    private Boolean bluetoothEnabled;

    private BluetoothAdapter adapter;

    private final Map<String, BluetoothDeviceControl> registeredDeviceControlsByAddress;
    private final Map<String, BluetoothDevice> managedDevicesByAddress;
    private final Set<String> connectedDeviceAddresses;

    private OSCDispatcher dispatcher;

    private ServerThread serverThread;
    private ClientThread clientThread;

    private static final BluetoothManager INSTANCE = new BluetoothManager();

    public static BluetoothManager getInstance(final OSCDispatcher dispatcher) {
        INSTANCE.dispatcher = dispatcher;
        return INSTANCE;
    }

    private BluetoothManager() {
        registeredDeviceControlsByAddress = new HashMap<String, BluetoothDeviceControl>();
        managedDevicesByAddress = new HashMap<String, BluetoothDevice>();
        connectedDeviceAddresses = new HashSet<String>();
    }

    public void register(final BluetoothDeviceControl device) {
        registeredDeviceControlsByAddress.put(device.getAddress(), device);
    }

    public synchronized void start(final Activity activity) throws BluetoothException {
        if (started) {
            return;
        }

        adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            throw new BluetoothException("device does not support Bluetooth");
        }

        if (adapter.isEnabled()) {
            bluetoothEnabled = true;
        } else {
            Log.i(Brainstem.TAG, "attempting to enable Bluetooth");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);

            Log.i(Brainstem.TAG, "waiting for Bluetooth activation result");
            while (null == bluetoothEnabled) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new BluetoothException(e);
                }
            }
        }

        if (!bluetoothEnabled) {
            return;
        }

        Log.i(Brainstem.TAG, "Bluetooth is active");

        Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            int count = 0;
            StringBuilder sb = new StringBuilder("found bonded Extendo devices:\n");

            for (BluetoothDevice d : pairedDevices) {
                if (isBondedExtendoDevice(d)) {
                    count++;
                    sb.append("\t").append(d.getAddress())
                            .append(": ").append(d.getName())
                            .append(", ").append(d.getBluetoothClass())
                            .append(", ").append(d.getBondState());

                    managedDevicesByAddress.put(d.getAddress(), d);
                }
            }

            if (count > 0) {
                Log.i(Brainstem.TAG, sb.toString());
            } else {
                Log.w(Brainstem.TAG, "did not find any bonded Extendo devices");
            }
        }

        //serverThread = new ServerThread();
        //serverThread.start();

        clientThread = new ClientThread();
        clientThread.start();

        started = true;
    }

    public void stop() throws IOException {
        if (null != serverThread) {
            serverThread.cancel();
        }

        if (null != clientThread) {
            clientThread.cancel();
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            bluetoothEnabled = resultCode == Activity.RESULT_OK;
        }
    }

    public class BluetoothException extends Exception {
        public BluetoothException(final String message) {
            super(message);
        }

        public BluetoothException(final Throwable cause) {
            super(cause);
        }
    }

    private boolean isBondedExtendoDevice(final BluetoothDevice device) {
        return null != registeredDeviceControlsByAddress.get(device.getAddress())
                && BluetoothDevice.BOND_BONDED == device.getBondState();
    }

    private synchronized void deviceDisconnected(final BluetoothDevice device) {
        Log.i(Brainstem.TAG, "Bluetooth device disconnected: " + device.getName());
        connectedDeviceAddresses.remove(device.getAddress());
        registeredDeviceControlsByAddress.get(device.getAddress()).disconnect();
    }

    private void connectToSocket(final BluetoothSocket socket) throws IOException {
        if (null == socket) {
            Log.w(Brainstem.TAG, "null Bluetooth socket");
        } else if (null == socket.getInputStream() || null == socket.getOutputStream()) {
            Log.e(Brainstem.TAG, "Bluetooth socket for " + socket.getRemoteDevice().getName() + " has null input or output stream");
        } else {
            Log.i(Brainstem.TAG, "attempting connection to Extendo device " + socket.getRemoteDevice().getName());
            socket.connect();

            new BluetoothOSCThread(socket).start();

            connectedDeviceAddresses.add(socket.getRemoteDevice().getAddress());
        }
    }

    private synchronized void connectDevices() {
        for (BluetoothDevice device : managedDevicesByAddress.values()) {
            if (!connectedDeviceAddresses.contains(device.getAddress())) {
                Log.i(Brainstem.TAG, "creating socket for Extendo device " + device.getName() + " at " + device.getAddress());
                try {
                    BluetoothSocket socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                    connectToSocket(socket);
                } catch (IOException e) {
                    Log.i(Brainstem.TAG, "could not connect to Bluetooth device " + device.getName() + ". Will try again later.");
                } catch (Throwable t) {
                    Log.e(Brainstem.TAG, "error while attempting to connect to Bluetooth device " + device.getName() + ": " + t.getMessage());
                    t.printStackTrace(System.err);
                }
            }
        }
    }

    private void handleOSCData(final byte[] data) {
        OSCByteArrayToJavaConverter c = new OSCByteArrayToJavaConverter();

        // TODO: is the array length all that is expected for the second argument?
        OSCPacket p = c.convert(data, data.length);

        if (p instanceof OSCMessage) {
            if (!dispatcher.dispatch((OSCMessage) p)) {
                Log.w(Brainstem.TAG, "no OSC handler at address " + ((OSCMessage) p).getAddress());

                // TODO: temporary debugging code
                String address = ((OSCMessage) p).getAddress();
                StringBuilder sb = new StringBuilder("address bytes:");
                for (byte b : address.getBytes()) {
                    sb.append(" ").append((int) b);
                }
                Log.w(Brainstem.TAG, sb.toString());
            }
        } else {
            Log.w(Brainstem.TAG, "OSC packet is of non-message type " + p.getClass().getSimpleName() + ": " + p);
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

    private class BluetoothOSCThread extends Thread {
        private final BluetoothDevice device;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        private boolean closed;

        public BluetoothOSCThread(BluetoothSocket socket) throws IOException {
            this.device = socket.getRemoteDevice();
            this.inputStream = socket.getInputStream();
            this.outputStream = socket.getOutputStream();
            //Log.i(Brainstem.TAG, "inputStream = " + inputStream + ", outputStream = " + outputStream);
        }

        public OutputStream getOutputStream() {
            return outputStream;
        }

        public void close() {
            Log.i(Brainstem.TAG, "closing device " + device.getName() + ". The device will not be reconnected.");
            closed = true;
        }

        @Override
        public void run() {
            // messages from Extendo devices are generally very short
            byte[] buffer = new byte[1024];

            try {
                Log.i(Brainstem.TAG, "starting Bluetooth+OSC thread");

                byte[] foo = new byte[]{(byte) 0xC0, (byte) 0xDB, (byte) 0xDC, (byte) 0xDD};
                Log.i(Brainstem.TAG, "SLIP frame end: " + (int) foo[0]);
                Log.i(Brainstem.TAG, "SLIP frame escape: " + (int) foo[1]);
                Log.i(Brainstem.TAG, "SLIP transposed frame end: " + (int) foo[2]);
                Log.i(Brainstem.TAG, "SLIP transposed frame escape: " + (int) foo[3]);

                int index = 0;

                // first datagram may be fragmentary, and will be discarded
                boolean isConnected = false;

                while (!closed) {
                    int b = inputStream.read();
                    //Log.i(Brainstem.TAG, "read: " + b);
                    if (b == SLIP_FRAME_END) {
                        if (isConnected) {
                            if (index > 0) {
                                // skip the 19,18 sequence which (I have found) appears between datagrams
                                if (index != 2 || buffer[0] != ACK || buffer[1] != START_FLAG) {
                                    handleMessage(buffer, index);
                                }
                            }
                        } else {
                            // at this point, we are sure we have a SLIP connection,
                            // so fire the device's connection event(s)
                            // TODO: do we really need to wait until we have incoming data, or do we know earlier that we have a SLIP connection?
                            registeredDeviceControlsByAddress.get(device.getAddress()).connect(new BluetoothMessageWriter(outputStream));
                        }

                        index = 0;

                        isConnected = true;
                    } else {
                        if (isConnected) {
                            buffer[index++] = (byte) b;
                        }
                    }
                }
            } catch (Throwable t) {
                Log.e(Brainstem.TAG, "Bluetooth I/O thread failed with error: " + t.getMessage());
                t.printStackTrace(System.err);
            }

            // If the connection was not deliberately closed (i.e. if the connection ended with an error or EOI),
            // wait a short time and then attempt to reconnect.
            if (!closed) {
                deviceDisconnected(device);
            }
        }

        private void handleMessage(byte[] buffer, int n) {
            Log.i(Brainstem.TAG, "" + n + " bytes received from Arduino:");

            // TODO: temporary debugging code
            StringBuilder sb = new StringBuilder("\t");
            for (int i = 0; i < n; i++) {
                sb.append(" ").append((int) buffer[i]);
            }
            Log.i(Brainstem.TAG, sb.toString());

            byte[] data;

            data = Arrays.copyOfRange(buffer, 0, n);
            if (data[0] != '/') {
                Log.w(Brainstem.TAG, "not a valid OSC message");
                return;
            }

            //if (Extendo.VERBOSE) {
            Log.i(Brainstem.TAG, "data from Arduino: " + data + " (length=" + data.length + ")");
            //}

            // TODO: catching ArrayIndexOutOfBoundsException is temporary; fix the problem in the Arduino
            try {
                handleOSCData(data);
            } catch (ArrayIndexOutOfBoundsException e) {
                Log.e(Brainstem.TAG, "array index out of bounds when reading from Arduino");
            }
        }
    }

    private class ServerThread extends Thread {
        private final BluetoothServerSocket serverSocket;

        public ServerThread() {
            // Use a temporary object that is later assigned to serverSocket,
            // because serverSocket is final
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code
                tmp = adapter.listenUsingRfcommWithServiceRecord(Brainstem.BLUETOOTH_NAME, SPP_UUID);
            } catch (IOException e) {
            }
            serverSocket = tmp;
        }

        @Override
        public void run() {
            try {
                Log.i(Brainstem.TAG, "starting Bluetooth listener thread");

                BluetoothSocket socket;
                // Keep listening until exception occurs
                while (true) {
                    try {
                        socket = serverSocket.accept();
                        Log.i(Brainstem.TAG, "got a Bluetooth socket: " + socket);
                    } catch (IOException e) {
                        break;
                    }
                    // If a connection was accepted
                    if (socket != null) {
                        if (isBondedExtendoDevice(socket.getRemoteDevice())) {
                            // Do work to manage the connection (in a separate thread)
                            connectToSocket(socket);

                            // do *not* close the socket or break out; there may be other devices out there
                        } else {
                            socket.close();
                        }
                    }
                }
            } catch (Throwable t) {
                Log.e(Brainstem.TAG, "Bluetooth listener thread failed with error: " + t.getMessage());
                t.printStackTrace(System.err);
            }
        }

        /**
         * Will cancel the listening socket, and cause the thread to finish
         */
        public void cancel() throws IOException {
            serverSocket.close();
        }
    }

    private class ClientThread extends Thread {
        private boolean cancelled;

        public void cancel() {
            cancelled = true;
        }

        @Override
        public void run() {
            while (!cancelled) {
                connectDevices();

                if (connectedDeviceAddresses.size() > 0) {
                    Log.i(Brainstem.TAG, "waiting " + CONNECTION_RETRY_INTERVAL + "ms before retrying "
                            + (managedDevicesByAddress.size() - connectedDeviceAddresses.size()) + " Bluetooth connections");
                }

                try {
                    Thread.sleep(CONNECTION_RETRY_INTERVAL);
                } catch (InterruptedException e) {
                    Log.e(Brainstem.TAG, "interrupted while waiting to retry Bluetooth connections: " + e.getMessage());
                    e.printStackTrace(System.err);
                }
            }
        }
    }

    //private static final int[] START_SEQUENCE = new int[]{ACK, START_FLAG, SLIP_FRAME_END};

    public class BluetoothMessageWriter {
        private final OutputStream outputStream;

        public BluetoothMessageWriter(OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        public void sendMessage(final byte[] message) throws IOException {
            //outputStream.write(START_SEQUENCE);
            /*
            outputStream.write(ACK);
            outputStream.write(START_FLAG);
            outputStream.write(SLIP_FRAME_END);
            */

            outputStream.write(message);
            outputStream.write(SLIP_FRAME_END);
        }
    }
}
