package com.threeDBJ.bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import android.support.v4.app.DialogFragment;

import java.util.ArrayList;

public class BluetoothDiscoverFragment extends DialogFragment {
    static final String TAG = "BTAndroid";
    static final String NO_DEVICE_TEXT = "No devices found";
    BluetoothActivity btActivity;
    BluetoothAdapter bluetoothAdapter;
    ArrayAdapter<String> deviceDisplay;
    ArrayList<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();
    ProgressBar progress;
    int numRelevant = 0;

    static BluetoothDiscoverFragment newInstance() {
        BluetoothDiscoverFragment f = new BluetoothDiscoverFragment();

        // Supply input as an argument.
        Bundle args = new Bundle();
        f.setArguments(args);

        return f;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            btActivity = (BluetoothActivity)activity;
        } catch(ClassCastException e) {
            throw new ClassCastException(activity.toString() +" must be a BluetoothActivity");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        int style = DialogFragment.STYLE_NORMAL;
        int theme = android.R.style.Theme_Holo_Dialog;
        setShowsDialog(true);
        setStyle(style, theme);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.bluetooth_discover, container, false);
        getDialog().setTitle("Nearby Devices");

        Rect displayRectangle = new Rect();
        Window window = btActivity.getWindow();
        window.getDecorView().getWindowVisibleDisplayFrame(displayRectangle);
        v.setMinimumWidth((int)(displayRectangle.width() * 0.8f));
        v.setMinimumHeight((int)(displayRectangle.height() * 0.7f));

        ListView list = (ListView)v.findViewById(R.id.device_list);
        progress = new ProgressBar(btActivity);
        list.addFooterView(progress, null, false);
        deviceDisplay = new ArrayAdapter<String>(btActivity, android.R.layout.simple_list_item_1); //android.R.id.text1
        list.setAdapter(deviceDisplay);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, final View view, int position, long id) {
                    if(position < devices.size()) {
                        btActivity.connectDevice(devices.get(position), false);
                        dismiss();
                    }
                }
            });

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        btActivity.registerReceiver(bluetoothReceiver, filter);
        bluetoothAdapter.startDiscovery();
    }

    @Override
    public void onPause() {
        super.onPause();
        bluetoothAdapter.cancelDiscovery();
        btActivity.unregisterReceiver(bluetoothReceiver);
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    public void clearDevices() {
        deviceDisplay.clear();
        devices.clear();
        numRelevant = 0;
    }

    public void addDevice(BluetoothDevice device) {
        String display = device.getName() + "\n" + device.getAddress();
        if(deviceDisplay.getPosition(display) != -1) {
            DebugLog.e(TAG, "Duplicate device not added");
        } else if(display.startsWith("Podo")) {
            DebugLog.e(TAG, "Found relevant device: "+display);
            deviceDisplay.insert(display, numRelevant);
            devices.add(numRelevant, device);
            numRelevant += 1;
        } else {
            DebugLog.e(TAG, "Found device: "+display);
            deviceDisplay.add(display);
            devices.add(device);
        }
        DebugLog.e(TAG, "Device list size: "+devices.size()+" "+deviceDisplay.getCount());
    }

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    addDevice(device);
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    DebugLog.e(TAG, "Bluetooth device search complete");
                    //progress.setVisibility(View.GONE);
                }
            }
        };
}
