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
import net.fortytwo.extendo.Main;
import net.fortytwo.extendo.brainstem.Brainstem;
import net.fortytwo.extendo.brainstem.osc.OSCDispatcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class BluetoothManager {

    private static final int REQUEST_ENABLE_BT = 424242;

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static final int[] MESSAGE_PREFIX = new int[]{18, -17, -65, -81, -17, -66, -65, -17, -66, -67};

    private boolean started = false;

    // note: currently, we never change this variable once Bluetooth is initially enabled
    private Boolean bluetoothEnabled;

    private BluetoothAdapter adapter;

    private final Map<String, BluetoothDeviceControl> registeredDevices;

    private OSCDispatcher dispatcher;

    private static final BluetoothManager INSTANCE = new BluetoothManager();

    public static BluetoothManager getInstance(final OSCDispatcher dispatcher) {
        INSTANCE.dispatcher = dispatcher;
        return INSTANCE;
    }

    private BluetoothManager() {
        registeredDevices = new HashMap<String, BluetoothDeviceControl>();
    }

    public void register(final BluetoothDeviceControl device) {
        registeredDevices.put(device.getAddress(), device);
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

        new AcceptThread().start();

        if (pairedDevices.size() > 0) {
            StringBuilder sb = new StringBuilder("found paired devices:\n");
            for (BluetoothDevice d : pairedDevices) {
                sb.append("\t").append(d.getAddress())
                        .append(": ").append(d.getName())
                        .append(", ").append(d.getBluetoothClass())
                        .append(", ").append(d.getBondState());

                if (isBondedExtendoDevice(d)) {
                    Log.i(Brainstem.TAG, "creating socket for Extendo device " + d.getName() + " at " + d.getAddress());
                    try {
                        BluetoothSocket socket = d.createRfcommSocketToServiceRecord(SPP_UUID);
                        if (null == socket) {
                            Log.i(Brainstem.TAG, "null Bluetooth socket");
                        } else {
                            Log.i(Brainstem.TAG, "connecting to Extendo device " + d.getName());
                            socket.connect();
                            manageConnectedSocket(socket);
                        }
                    } catch (IOException e) {
                        throw new BluetoothException(e);
                    }
                }
            }
            Log.i(Brainstem.TAG, sb.toString());
        }


        started = true;
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
        return null != registeredDevices.get(device.getAddress())
                && BluetoothDevice.BOND_BONDED == device.getBondState();
    }

    private void manageConnectedSocket(final BluetoothSocket socket) throws IOException {
        // currently, we only connect to devices which have been registered beforehand
        if (isBondedExtendoDevice(socket.getRemoteDevice())) {
            Log.i(Brainstem.TAG, "established new Bluetooth socket");
            if (null == socket.getInputStream() || null == socket.getOutputStream()) {
                Log.w(Brainstem.TAG, "Bluetooth socket for " + socket.getRemoteDevice().getName() + " has null input or output stream");
            } else {
                new BluetoothIOThread(socket).start();
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

    private static final int
            SLIP_FRAME_END = 0xC0,
            SLIP_SKIP_CHAR_AFTER = 19,
            SLIP_SKIP_CHAR_BEFORE = 18;

    private class BluetoothIOThread extends Thread {
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public BluetoothIOThread(BluetoothSocket socket) throws IOException {
            this.inputStream = socket.getInputStream();
            this.outputStream = socket.getOutputStream();
            Log.i(Brainstem.TAG, "inputStream = " + inputStream + ", outputStream = " + outputStream);
        }

        public OutputStream getOutputStream() {
            return outputStream;
        }

        public void run() {
            // messages from Extendo devices are generally very short
            byte[] buffer = new byte[1024];

            try {
                Log.i(Brainstem.TAG, "starting Bluetooth I/O thread");

                byte[] foo = new byte[]{(byte) 0xC0, (byte) 0xDB, (byte) 0xDC, (byte) 0xDD};
                Log.i(Brainstem.TAG, "SLIP frame end: " + (int) foo[0]);
                Log.i(Brainstem.TAG, "SLIP frame escape: " + (int) foo[1]);
                Log.i(Brainstem.TAG, "SLIP transposed frame end: " + (int) foo[2]);
                Log.i(Brainstem.TAG, "SLIP transposed frame escape: " + (int) foo[3]);

                int index = 0;
                // first datagram may be fragmentary, and will be discarded
                boolean isComplete = false;

                //while (inputStream.available() > 0) {
                while (true) {
                    int b = inputStream.read();
                    //Log.i(Brainstem.TAG, "read: " + b);
                    if (b == SLIP_FRAME_END) {
                        if (isComplete && index > 0) {
                            // skip the 19,18 sequence which (I have found) appears between datagrams
                            if (index != 2 || buffer[0] != SLIP_SKIP_CHAR_AFTER || buffer[1] != SLIP_SKIP_CHAR_BEFORE) {
                                handleMessage(buffer, index);
                            }
                        }

                        index = 0;
                        isComplete = true;
                    } else {
                        if (isComplete) {
                            buffer[index++] = (byte) b;
                        }
                    }
                }

                //Log.i(Brainstem.TAG, "Bluetooth I/O thread reached end of input");
            } catch (Throwable t) {
                Log.e(Brainstem.TAG, "Bluetooth I/O thread failed with error: " + t.getMessage());
                t.printStackTrace(System.err);
            }
        }

        private void handleMessage(byte[] buffer, int n) {
            Log.i(Brainstem.TAG, "" + n + " bytes received from Arduino:");

            // TODO: temporary debugging code
            StringBuilder sb = new StringBuilder("bytes: ");
            for (int i = 0; i < n; i++) {
                sb.append(" ").append((int) buffer[i]);
            }
            Log.i(Brainstem.TAG, "\t" + sb.toString());

            //textEditor.setText("OSC: " + data);

            byte[] data;

            data = Arrays.copyOfRange(buffer, 0, n);
            if (data[0] != '/') {
                Log.w(Brainstem.TAG, "not a valid OSC message");
                return;
            }

                    /*
                    // strip off the odd 0xEF 0xBF 0xBD three-byte sequence which sometimes encloses the message
                    // I haven't quite grokked it.  It's like a UTF-8 byte order mark, but not quite, and it appears
                    // both at the end and the beginning of the message.
                    // It appears only when Amarino *and* OSCuino are used to send the OSC data over Bluetooth
                    if (n >= MESSAGE_PREFIX.length) {
                        //Log.i(TAG, "bytes[0] = " + (int) bytes[0] + ", " + "bytes[bytes.length - 3] = " + bytes[bytes.length - 3]);


                        boolean match = true;
                        for (int i = 0; i < MESSAGE_PREFIX.length; i++) {
                            if (buffer[i] != MESSAGE_PREFIX[i]) {
                                match = false;
                                break;
                            }
                        }

                        if (match) {
                            if (MESSAGE_PREFIX.length == n) {
                                Log.w(Brainstem.TAG, "received empty message from Arduino");
                                continue;
                            }

                            Log.i(Brainstem.TAG, "stripping off message prefix");
                            data = new String(Arrays.copyOfRange(buffer, MESSAGE_PREFIX.length, n));
                        } else {
                            data = new String(Arrays.copyOfRange(buffer, 0, n));
                        }
                    } else {
                        data = new String(Arrays.copyOfRange(buffer, 0, n));
                    }*/

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

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket serverSocket;

        public AcceptThread() {
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
                        // Do work to manage the connection (in a separate thread)
                        manageConnectedSocket(socket);

                        // do *not* close the socket or break out; there may be other devices out there
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
}
