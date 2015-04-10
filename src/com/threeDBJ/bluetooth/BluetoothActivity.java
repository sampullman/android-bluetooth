package com.threeDBJ.bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Message;
import android.os.Parcelable;

import java.io.UnsupportedEncodingException;

import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.MenuItemCompat;

public abstract class BluetoothActivity extends FragmentActivity {
    public static final String TAG = "BTAndroid";
    /* Intent constants */
    public static int REQUEST_ENABLE_BT = 10;
    public static int REQUEST_CONNECT_BT = 11;
    public static int REQUEST_CONNECT_COMP = 12;
    public static int MAKE_DISCOVERABLE = 13;

    public BluetoothClient btClient;
    public BluetoothAdapter btAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            // If no bluetooth adapter is available, quit the app
            Util.AlertBox(this, "Error", "Bluetooth not available, exiting app", true);
            finish();
        } else if (!btAdapter.isEnabled()) {
            // Enable the bluetooth adapter if necessary
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
        }
    }

    public void setClient(BluetoothClient client) {
        this.btClient = client;
    }

    private final BroadcastReceiver discReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                    DebugLog.e(TAG, "ACL Disconnected");
                    if(btClient != null) btClient.stopService();
                }
            }
        };

    @Override
    public void onResume() {
        super.onResume();
        if(btClient != null) btClient.startService();
        IntentFilter discFilter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(discReceiver, discFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        if(discReceiver != null) {
            try { unregisterReceiver(discReceiver); }
            catch(IllegalArgumentException e) {}
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        DebugLog.e(TAG, "DESTROYED");
    }

    /**
     * Function called when another activity started from this one with startActivityForResult(int) returns.
     * REQUEST_ENABLE_BT - User has chosen whether to enable bluetooth.
     * REQUEST_CONNECT_BT - User has potentially selected an external bluetooth device to connect to.
     *
     * @param requestCode The int argument to our startActivityForResult(int) call.
     * @param resultCode The result specified by the returning activity in its setResult(int) call. Default 0 (RESULT_CANCELED)
     * @param data The intent returned from a setResult(int, Intent) call, or null
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_ENABLE_BT) {
            if(resultCode == RESULT_OK) {
            } else {
                Util.AlertBox(this, "Error", "This app requires bluetooth to be enabled.", true);
            }
        } else if(requestCode == REQUEST_CONNECT_BT) {
            if(resultCode == RESULT_OK && btClient != null) {
                BluetoothDevice device = (BluetoothDevice)data.getParcelableExtra("device_address");
                connectDevice(device, false);
            }
        }
    }

    public void showDiscoverDialog() {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        Fragment prev = getSupportFragmentManager().findFragmentByTag("discover_dialog");
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);

        // Create and show the dialog.
        DialogFragment df = BluetoothDiscoverFragment.newInstance();
        df.show(ft, "discover_dialog");
    }

    public void connectDevice(BluetoothDevice device, boolean secure) {
        DebugLog.e(TAG, "Connect BT: "+(btClient != null));
        btClient.connectDevice(device, secure);
    }
}
