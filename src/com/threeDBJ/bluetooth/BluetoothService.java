package com.threeDBJ.bluetooth;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
public class BluetoothService {
    // Debugging
    private static final String TAG = "BTAndroid";
    private static final boolean D = true;

    // Name for the SDP record when creating server socket
    private static final String NAME_SECURE = "BluetoothSecure";
    private static final String NAME_INSECURE = "BluetoothInsecure";

    // Unique UUID for this application
    //private static final UUID UUID_SECURE = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    private static final UUID UUID_SECURE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    //private static final UUID UUID_INSECURE = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");
    private static final UUID UUID_INSECURE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Member fields
    private volatile BluetoothClient client;
    private BluetoothAdapter btAdapter;
    private Handler handler;
    private Handler progressHandler;
    private AcceptThread secureAcceptThread;
    private AcceptThread insecureAcceptThread;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;
    private int state;
    private Object clientLock = new Object();

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    public BluetoothService() {
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        state = STATE_NONE;
    }

    /**
     * Constructor. Prepares a new Bluetooth session.
     * @param handler  A Handler to send messages back to the UI Activity
     */
    public BluetoothService(BluetoothClient client) {
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        state = STATE_NONE;
        setClient(client);
    }

    public void setClient(BluetoothClient client) {
        this.client = client;
        ConnectedThread r = null;
        synchronized(this) {
            if (state != STATE_CONNECTED) r = connectedThread;
        }
        if(client == null) {
            handler = null;
            progressHandler = null;
            try{if(r != null) r.wait();} catch(Exception e) {}
        } else {
            handler = client.getHandler();
            progressHandler = client.getProgressHandler();
            if(r != null) r.notify();
        }
    }

    public void removeClient(BluetoothClient client) {
        if(client == this.client) {
            setClient(null);
        }
    }

    public synchronized boolean isConnected() {
        return state == STATE_CONNECTED;
    }

    /**
     * Set the current state of the chat connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        DebugLog.e(TAG, "setState() " + state + " -> " + state);
        this.state = state;

        // Give the new state to the Handler so the UI Activity can update
        if(client != null) {
            handler.obtainMessage(BluetoothClient.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
        }
    }

    /**
     * Return the current connection state. */
    public synchronized int getState() {
        return state;
    }

    /**
     * Start the service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume() */
    public synchronized void start() {
        DebugLog.e(TAG, "bluetooth server start");

        cancelConnectThreads();

        setState(STATE_LISTEN);

        // Start the thread to listen on a BluetoothServerSocket
        if (secureAcceptThread == null) {
            secureAcceptThread = new AcceptThread(true);
            secureAcceptThread.start();
        }
        if (insecureAcceptThread == null) {
            insecureAcceptThread = new AcceptThread(false);
            insecureAcceptThread.start();
        }
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    public synchronized void connect(BluetoothDevice device, boolean secure) {
        DebugLog.e(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (state == STATE_CONNECTING) {
            if (connectThread != null) {connectThread.cancel(); connectThread = null;}
        }

        // Cancel any thread currently running a connection
        if (connectedThread != null) {connectedThread.cancel(); connectedThread = null;}

        // Start the thread to connect with the given device
        connectThread = new ConnectThread(device, secure);
        connectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * Attempt to connect to a previously paired device based on it's BDADDR
     * @param bdaddr The BDADDR of the target BluetoothDevice
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     * @return true if paired device with matching BDADDR was found, false otherwise
     */
    public boolean connectToAddress(String bdaddr, boolean secure) {
        if(bdaddr == null) return false;
        Set<BluetoothDevice> devices = btAdapter.getBondedDevices();
        for(BluetoothDevice device : devices) {
            DebugLog.e(TAG, device.getAddress());
            if(bdaddr.equals(device.getAddress())) {
                connect(device, secure);
                return true;
            }
        }
        return false;
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
                                       device, final String socketType) {
        DebugLog.e(TAG, "connected, Socket Type:" + socketType);

        cancelConnectThreads();
        cancelAcceptThreads();

        // Start the thread to manage the connection and perform transmissions
        connectedThread = new ConnectedThread(socket, socketType);
        connectedThread.start();
        setState(STATE_CONNECTED);

        // Send the name of the connected device back to the UI Activity
        if(client != null) {
            Message msg = handler.obtainMessage(BluetoothClient.MESSAGE_DEVICE_NAME);
            Bundle bundle = new Bundle();
            bundle.putString(BluetoothClient.DEVICE_ADDRESS, device.getAddress());
            bundle.putString(BluetoothClient.DEVICE_NAME, device.getName());
            msg.setData(bundle);
            handler.sendMessage(msg);
        }
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        DebugLog.e(TAG, "stop");
        cancelConnectThreads();
        cancelAcceptThreads();
        setState(STATE_NONE);
    }

    /**
     * Cancel the connection pending thread and the connected thread
     */
    public void cancelConnectThreads() {
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
    }

    /**
     * Cancel the secure and insecure accept thread.
     */
    public void cancelAcceptThreads() {
        if (secureAcceptThread != null) {
            secureAcceptThread.cancel();
            secureAcceptThread = null;
        }
        if (insecureAcceptThread != null) {
            insecureAcceptThread.cancel();
            insecureAcceptThread = null;
        }
    }

    /**
     * Send a BluetoothMessage
     *
     * @param msg The BluetoothMessage to send
     * @see ConnectedThread#send(BluetoothMessage)
     */
    public void send(BluetoothMessage msg) {
        ConnectedThread r;
        synchronized(this) {
            if (state != STATE_CONNECTED) return;
            r = connectedThread;
        }
        r.send(msg);
    }

    /**
     * Write a single byte to the connected bluetooth stream.
     *
     * @param cmd The command to write. Only the LSB of the int is used.
     * @see ConnectedThread#write(int)
     */
    public void write(int cmd) {
        ConnectedThread r;
        synchronized (this) {
            if (state != STATE_CONNECTED) return;
            r = connectedThread;
        }
        r.write(cmd);
    }

    /**
     * Write the contents of an InputStream to the connected bluetooth stream
     *
     * @param stream The input stream to write
     * @see ConnectedThread#write(InputStream)
     */
    public void write(InputStream stream) {
        ConnectedThread r;
        synchronized (this) {
            if (state != STATE_CONNECTED) return;
            r = connectedThread;
        }
        r.write(stream);
    }

    /**
     * Write a byte array to the connected bluetooth stream
     *
     * @param out The bytes to write
     * @see BluetoothService#write(byte[], int)
     */
    public void write(byte[] out) {
        write(out, out.length);
    }

    /**
     * Write len bytes from a byte array to the connected bluetooth stream.
     *
     * @param out The byte array to send
     * @param len The number of bytes to send
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out, int len) {
        ConnectedThread r;
        synchronized (this) {
            if (state != STATE_CONNECTED) return;
            r = connectedThread;
        }
        r.write(out, len);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        if(client != null) {
            Message msg = handler.obtainMessage(BluetoothClient.MESSAGE_TOAST);
            Bundle bundle = new Bundle();
            bundle.putString(BluetoothClient.TOAST, "Unable to connect device");
            msg.setData(bundle);
            handler.sendMessage(msg);
        }

        // Start the service over to restart listening mode
        BluetoothService.this.start();
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        if(client != null) {
            Message msg = handler.obtainMessage(BluetoothClient.MESSAGE_TOAST);
            Bundle bundle = new Bundle();
            bundle.putString(BluetoothClient.TOAST, "Device connection was lost");
            msg.setData(bundle);
            handler.sendMessage(msg);
        }
        // Start the service over to restart listening mode
        BluetoothService.this.start();
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread {
        // The local server socket
        private BluetoothServerSocket mmServerSocket;
        private String mSocketType;

        public AcceptThread(boolean secure) {
            BluetoothServerSocket tmp = null;
            mSocketType = secure ? "Secure":"Insecure";

            // Create a new listening server socket
            try {
                if (secure) {
                    tmp = btAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE, UUID_SECURE);
                } else {
                    tmp = btAdapter.listenUsingInsecureRfcommWithServiceRecord(NAME_INSECURE, UUID_INSECURE);
                }
            } catch (IOException e) {
                DebugLog.e(TAG, "Socket Type: " + mSocketType + "listen() failed "+e.getMessage());
            }
            mmServerSocket = tmp;
        }

        public void run() {
            DebugLog.e(TAG, "Socket Type: " + mSocketType + " BEGIN mAcceptThread" + this);
            setName("AcceptThread" + mSocketType);

            BluetoothSocket socket = null;

            DebugLog.e(TAG, "Made it to run thread");
            // Listen to the server socket if we're not connected
            while (state != STATE_CONNECTED) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    if(mmServerSocket != null) {
                        socket = mmServerSocket.accept();
                    }
                } catch (IOException e) {
                    DebugLog.e(TAG, "Socket Type: " + mSocketType + "accept() failed: "+e.getMessage());
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (this) {
                        switch (state) {
                        case STATE_LISTEN:
                        case STATE_CONNECTING:
                            // Situation normal. Start the connected thread.
                            connected(socket, socket.getRemoteDevice(), mSocketType);
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
            DebugLog.e(TAG, "END mAcceptThread, socket Type: " + mSocketType);
        }

        public void cancel() {
            DebugLog.e(TAG, "Socket Type" + mSocketType + "cancel " + this);
            if (mmServerSocket != null) {
                try {mmServerSocket.close();} catch (Exception e) {}
                mmServerSocket = null;
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
        private String mSocketType;

        public ConnectThread(BluetoothDevice device, boolean secure) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            DebugLog.e(TAG, "Connect thread constructor");
            try {
                if (secure) {
                    tmp = device.createRfcommSocketToServiceRecord(UUID_SECURE);
                    DebugLog.e(TAG, "Created secure socket to "+UUID_SECURE);
                } else {
                    tmp = device.createInsecureRfcommSocketToServiceRecord(UUID_INSECURE);
                    //Method m = device.getClass().getMethod("createRfcommSocket", new Class[] {int.class});
                    //tmp = (BluetoothSocket) m.invoke(device, 1);
                    DebugLog.e(TAG, "Created insecure socket to "+UUID_SECURE);
                }
            } catch (Exception e) {
                DebugLog.e(TAG, "Socket Type: " + mSocketType + "create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            DebugLog.e(TAG, "BEGIN connectThread SocketType:" + mSocketType);
            setName("ConnectThread" + mSocketType);

            // Always cancel discovery because it will slow down a connection
            btAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                // Close the socket
                DebugLog.e(TAG, "Failed to connect - "+e.getMessage());
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() "+mSocketType+" socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothService.this) {
                connectThread = null;
            }
            // Start the connected thread
            connected(mmSocket, mmDevice, mSocketType);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect " + mSocketType + " socket failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private BluetoothSocket mmSocket;
        private InputStream mmInStream;
        private OutputStream mmOutStream;
        volatile boolean running = true;

        public ConnectedThread(BluetoothSocket socket, String socketType) {
            Log.d(TAG, "create ConnectedThread: " + socketType);
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            DebugLog.e(TAG, "BEGIN connectedThread");
            byte[] buffer = new byte[1024];
            int bytes;
            Bitmap bitmap;

            // Keep listening to the InputStream while connected
            while (running) {
                try {
                    // Let client handle data
                    int val = mmInStream.read();
                    if(client != null) {
                        client.receivedData(val, mmInStream);
                    } else {}
                } catch (IOException e) {
                    DebugLog.e(TAG, "disconnected", e);
                    connectionLost();
                    // Start the service over to restart listening mode
                    BluetoothService.this.start();
                    break;
                }
            }
            closeSocket();
        }

        public void send(BluetoothMessage msg) {
            try {
                if(msg != null) msg.write(mmOutStream);
                else DebugLog.e(TAG, "Tried to send a null message");
            } catch(IOException e) {
                DebugLog.e(TAG, "Exception sending BluetoothMessage "+e.getMessage());
            }
        }

        public void write(int cmd) {
            try {
                mmOutStream.write(cmd);
                mmOutStream.flush();
            } catch(IOException e) {
                DebugLog.e(TAG, "Exception during write command "+e.getMessage());
            }
        }

        public void write(InputStream stream) {
            try {
                BufferedInputStream bis = new BufferedInputStream(stream, BluetoothClient.CHUNK_SIZE);
                byte[] buffer = new byte[BluetoothClient.CHUNK_SIZE];
                int len;
                while ((len = bis.read(buffer)) != -1) {
                    mmOutStream.write(buffer, 0, len);
                }
                mmOutStream.flush();
            } catch (IOException e) {
                DebugLog.e(TAG, "Exception during write stream"+e.getMessage());
            }
        }

        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        public void write(byte[] buffer, int len) {
            try {
                int i=0, amount;
                while(i < len) {
                    amount = (len - i < BluetoothClient.CHUNK_SIZE) ? len-i : BluetoothClient.CHUNK_SIZE;
                    mmOutStream.write(buffer, i, amount);
                    i += amount;
                }
                mmOutStream.flush();
            } catch (IOException e) {
                DebugLog.e(TAG, "Exception during write", e);
            }
        }

        public void closeSocket() {
            if (mmInStream != null) {
                try {mmInStream.close();} catch (Exception e) {}
                mmInStream = null;
            }
            if (mmOutStream != null) {
                try {mmOutStream.close();} catch (Exception e) {}
                mmOutStream = null;
            }
            if (mmSocket != null) {
                try {mmSocket.close();} catch (Exception e) {}
                mmSocket = null;
            }
        }

        public void cancel() {
            running = false;
        }
    }
}
