package com.threeDBJ.bluetooth;

import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.app.ActionBar;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;

public class BluetoothDiscoverActivity extends ActionBarActivity {
    public static final String TAG = "BTAndroid";
    static final int REFRESH_ID = 1;
    static final String NO_DEVICE_TEXT = "No devices found";
    BluetoothAdapter bluetoothAdapter;
    ArrayAdapter<String> deviceDisplay;
    ArrayList<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();
    ProgressBar progress;

    @Override
    public void onCreate(Bundle instanceState) {
        super.onCreate(instanceState);
        setContentView(R.layout.bluetooth_discover);
        deviceDisplay = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1); //android.R.id.text1

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        ListView list = (ListView)findViewById(R.id.device_list);
        list.setAdapter(deviceDisplay);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, final View view, int position, long id) {
                    Intent intent = new Intent();
                    intent.putExtra("device_address", devices.get(position));
                    setResult(RESULT_OK, intent);
                    finish();
                }
            });
        progress = new ProgressBar(this);
        list.addFooterView(progress);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, REFRESH_ID, 0, "Refresh")
            .setIcon(R.drawable.ic_refresh)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            setResult(RESULT_CANCELED);
            finish();
            break;
        case REFRESH_ID:
            deviceDisplay.clear();
            devices.clear();
            bluetoothAdapter.cancelDiscovery();
            bluetoothAdapter.startDiscovery();
            break;
        }
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        deviceDisplay.clear();
        devices.clear();
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(bluetoothReceiver, filter);
        bluetoothAdapter.startDiscovery();
    }

    @Override
    public void onPause() {
        super.onPause();
        bluetoothAdapter.cancelDiscovery();
        unregisterReceiver(bluetoothReceiver);
    }

    public void addDevice(BluetoothDevice device) {
        String display = device.getName() + "\n" + device.getAddress();
        if(deviceDisplay.getPosition(display) != -1) {
            DebugLog.e(TAG, "Duplicate device not added");
        } else if(display.startsWith("Podo")) {
            DebugLog.e(TAG, "Found relevant device: "+display);
            deviceDisplay.insert(display, 0);
            devices.add(0, device);
        } else {
            DebugLog.e(TAG, "Found device: "+display);
            deviceDisplay.add(display);
            devices.add(device);
        }
    }

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    addDevice(device);
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    progress.setVisibility(View.GONE);
                }
            }
        };

}
