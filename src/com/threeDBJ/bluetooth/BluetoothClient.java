package com.threeDBJ.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.os.Handler;

import java.io.DataInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

public interface BluetoothClient {
    public static final String TAG = "BTAndroid";
    public static int DEVICE_ID = 0xA7;

    public static int CHUNK_SIZE = 16 * 1024;

    /* Client types */
    public static String DEVICE = "device";
    public static String ANDROID = "android";

    /* API Commands for identification messages */
    public static int REQUEST_PROFILE = 3;
    public static int CLIENT_PROFILE = 4;

    public static int LINE_ACK=77, LINE_NACK=78;

    /* Bundle keys */
    public static final String DEVICE_ADDRESS = "device_address";
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    /* State change message commands */
    public static final int MESSAGE_STATE_CHANGE = 200;
    public static final int MESSAGE_DEVICE_ADDRESS = 202;
    public static final int MESSAGE_DEVICE_NAME = 203;
    public static final int MESSAGE_TOAST = 204;

    public void receivedData(int val, InputStream inputStream) throws IOException;
    public void startService();
    public void stopService();
    public void connectDevice(BluetoothDevice device, boolean secure);
    public boolean isConnected();
    public Handler getHandler();
    public Handler getProgressHandler();
    public boolean send(BluetoothMessage msg);
    public boolean write(String msg);
    public boolean write(byte[] bytes);

}
