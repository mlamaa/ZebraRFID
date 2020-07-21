package com.quayo.solution.rfid;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
public class BluetoothService {

    // Constants thaintt indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device
    public static final int STATE_DISCONNECTED = 4;  // now connected to a remote device
    // Message types sent from the BluetoothService Handler
    public static final int MESSAGE_STATE_CHANGE = 11;
    public static final int MESSAGE_DEVICE_NAME = 12;
    public static final int MESSAGE_TOAST = 13;
    public static final int MESSAGE_DISCONNECTED = 14;
    public static final int MESSAGE_CONN_FAILED = 15;
    public static final int MESSAGE_CONN_LOST = 16;
    // Key names received from the BluetoothService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
    // Intent request codes
    public static final int REQUEST_ENABLE_BT = 22;
    // Debugging
    private static final String TAG = "BluetoothService";
    private static final boolean D = Constants.DEBUG;
    // Name for the SDP record when creating server socket
    private static final String NAME = "RFIDBluetooth";
    // Unique UUID for Reader BT SPP
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final UUID CUSTOM_UUID = UUID.fromString("2ad8a392-0e49-e52c-a6d2-60834c012263");
    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
    //Generic Reader instance to handle the data read from bluetooth
    private BluetoothDataHandler genericReader;
    //string to check whether bluetooth device is RFD device
    private static final String RFD8500 = "RFD8500";

    /**
     * constructor of the class
     *
     * @param context - Context to be used
     * @param handler - Handler object for communication
     */
    public BluetoothService(Context context, Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
    }

    /**
     * method to get paired readers
     *
     * @param devices list of paired readers
     */
    public void getAvailableDevices(Set<BluetoothDevice> devices) {
        for (BluetoothDevice device : mAdapter.getBondedDevices())
            if (isRFIDReader(device))
                devices.add(device);
    }

    /**
     * method to check whether BT device is RFID reader
     *
     * @param device device to check
     * @return true if {@link BluetoothDevice} is RFID Reader, other wise it will be false
     */
    public boolean isRFIDReader(BluetoothDevice device) {
        if (device.getName().startsWith(RFD8500))
            return true;
        return false;
    }

    /**
     * Return the current connection state.
     */
    public synchronized int getState() {
        return mState;
    }

    /**
     * Set the current state of the chat connection
     *
     * @param state An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);

        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(BluetoothService.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Start the service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    public synchronized void start() {
        if (D) Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
        }
        mConnectThread = null;

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to listen on a BluetoothServerSocket
       /*if (mAcceptThread == null) {
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
        }*/

        setState(STATE_LISTEN);
    }

    /*Start the ConnectThread to initiate a connection to a remote device.
    * @param device  The BluetoothDevice to connect
    * */
    public synchronized void connect(BluetoothDevice device) {
        if (D) Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * method to register {@link BluetoothService.BluetoothDataHandler} with Bluetooth service to get notify when the data is received from reader
     *
     * @param genericReader Generic reader
     */
    public void setGenericReader(BluetoothDataHandler genericReader) {
        this.genericReader = genericReader;
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        if (D) Log.d(TAG, "connected");

        try {
            // Cancel the thread that completed the connection
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }

            // Cancel any thread currently running a connection
            if (mConnectedThread != null) {
                mConnectedThread.cancel();
                mConnectedThread = null;
            }

            // Cancel the accept thread because we only want to connect to one device
            if (mAcceptThread != null) {
                mAcceptThread.cancel();
                mAcceptThread = null;
            }
        } catch (Exception e) {
            Constants.logAsMessage(Constants.TYPE_ERROR, TAG, e.getMessage());
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket, device);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putParcelable(Constants.DATA_BLUETOOTH_DEVICE, device);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        setState(STATE_CONNECTED);
    }

    /*Stop all threads
    */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }
        setState(STATE_NONE);
    }

    /**
     * Disconnect from a connected device
     * @param mConnectedDevice
     */
    public synchronized void disconnect(BluetoothDevice mConnectedDevice) {
       setState(STATE_DISCONNECTED);
        Message msg = mHandler.obtainMessage(MESSAGE_DISCONNECTED);
        Bundle bundle = new Bundle();
        bundle.putParcelable(Constants.DATA_BLUETOOTH_DEVICE, mConnectedDevice);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        stop();
        if (mAdapter.isEnabled())
            start();
    }


    /**
     * Write to the ConnectedThread in an un synchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[]) 181.
     */
    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed(BluetoothDevice device) {
        setState(STATE_LISTEN);
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(MESSAGE_CONN_FAILED);
        Bundle bundle = new Bundle();
        bundle.putString(TOAST, "Unable to connect device");
        bundle.putParcelable(Constants.DATA_BLUETOOTH_DEVICE, device);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        stop();
        if (mAdapter.isEnabled())
            start();
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost(BluetoothDevice device) {
        setState(STATE_LISTEN);
        Message msg = mHandler.obtainMessage(MESSAGE_CONN_LOST);
        Bundle bundle = new Bundle();
        bundle.putParcelable(Constants.DATA_BLUETOOTH_DEVICE, device);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        mConnectedThread=null;
       // stop();
        if (mAdapter.isEnabled())
            start();
    }

    /**
     * method to get device connection asset_status
     *
     * @return true if connected or false if not connected
     */
    public boolean isConnected() {
        if (mConnectedThread != null && mConnectedThread.isAlive())
            return true;
        return false;
    }

    /**
     * Classes wishing to be notified when some data is read over the bluetooth should implement this interface
     */
    public interface BluetoothDataHandler {
        void dataReceivedFromBluetooth(String data);
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread {
        // The local server socket
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;

            // Create a new listening server socket
            try {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN)
                    tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord(NAME, CUSTOM_UUID);
                else
                    tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord(NAME, SPP_UUID);
            } catch (IOException e) {
                if (D) Log.e(TAG, "listen() failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            if (D) Log.d(TAG, "BEGIN mAcceptThread" + this);
            setName("AcceptThread");
            BluetoothSocket socket;

            // Listen to the server socket if we're not connected
            while (mState != STATE_CONNECTED) {
                if (this.isInterrupted())
                    break;
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    if (D) Log.e(TAG, "accept() failed", e);
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (BluetoothService.this) {
                        switch (mState) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                // Situation normal. Start the connected thread.
                                connected(socket, socket.getRemoteDevice());
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                // Either not ready or already connected. Terminate new socket.
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    Log.e(TAG, "Could not close unwanted socket", e);
                                }
                                break;
                        }
                    }
                }
            }
            if (D) Log.i(TAG, "END mAcceptThread");
        }

        public void cancel() {
            if (D) Log.d(TAG, "cancel " + this);
            try {
                this.interrupt();
                mmServerSocket.close();
            } catch (IOException e) {
                if (D) Log.e(TAG, "close() of server failed", e);
            }
        }
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN || Build.MODEL.equals("TC55"))
                    tmp = device.createInsecureRfcommSocketToServiceRecord(CUSTOM_UUID);
                else
                    tmp = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
            } catch (IOException e) {
                if (D) Log.e(TAG, "create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            if (D) Log.i(TAG, "BEGIN mConnectThread");
            setName("ConnectThread");

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                e.printStackTrace();
                connectionFailed(mmDevice);
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    e.printStackTrace();
                    if (D) Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                // Start the service over to restart listening mode
                if (mAdapter.isEnabled())
                    BluetoothService.this.start();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothService.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                if (D) Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {

        private final BluetoothSocket mmSocket;
        private final OutputStream mmOutStream;
        private final BluetoothDevice mDevice;
        //private final InputStream mmInStream;
        private BufferedReader mmReader;
        private String data;

        public ConnectedThread(BluetoothSocket socket, BluetoothDevice device) {
            Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            mDevice = device;
            InputStream tmpIn;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                mmReader = new BufferedReader(new InputStreamReader(tmpIn));
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                if (D) Log.e(TAG, "temp sockets not created", e);
            }

            //mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {

            Log.i(TAG, "BEGIN mConnectedThread");
            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    //bytes = mmReader.readLine();
                    if (this.isInterrupted()) {
                        break;
                    }
                    if (genericReader != null && mmReader != null) {
                        data = mmReader.readLine();
                        if (data != null)
                            genericReader.dataReceivedFromBluetooth(data);
                    }
                 } catch (IOException e) {
                    if (D) Log.e(TAG, "disconnected", e);
                    if (mState != STATE_DISCONNECTED)
                        connectionLost(mDevice);
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
            } catch (IOException e) {
                if (D) Log.e(TAG, "Exception during write", e);
                if (mState != STATE_DISCONNECTED)
                    connectionLost(mDevice);
            }
        }

        public void cancel() {
            try {
                this.interrupt();
                if (mmOutStream != null) {
                    mmOutStream.flush();
                    mmOutStream.close();
                }
                if (mmReader != null)
                    mmReader.close();
                mmSocket.close();
            } catch (IOException e) {
                if (D) Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
}
