package com.basil.thelattice.heartratemonitor_ble;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class HeartRatePeripheralReciever extends AppCompatActivity {

    private final static String TAG = HeartRatePeripheralReciever.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    private static final String UUID_HEART_RATE_SERVICE = "0000180d-0000-1000-8000-00805f9b34fb";
    private static final String[] BODY_SENSOR_LOCATION_TEXT = {"Other", "Chest", "Wrist", "Finger", "Hand", "EarLobe", "Foot"};

    private String mDeviceName;
    private String mDeviceAddress;
    private boolean mConnected = false;
    private BluetoothLeService mBluetoothLeService;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private BluetoothGattCharacteristic heartRateCharacteristic;
    private BluetoothGattCharacteristic bodySensorLocationCharacteristic;

    private TextView deviceName;
    private TextView deviceAddress;
    private TextView deviceConnection;
    private TextView heartRate;
    private TextView energyExpended;
    private TextView bodySensorLocation;

    private Toolbar mToolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_heart_rate_peripheral_reciever);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        mToolbar = (Toolbar)findViewById(R.id.toolbar);

        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setTitle("Peripheral Connection");

        deviceName = (TextView)findViewById(R.id.device_name);
        deviceAddress = (TextView)findViewById(R.id.device_address);
        deviceConnection = (TextView)findViewById(R.id.device_conection);
        heartRate = (TextView)findViewById(R.id.heart_rate);
        energyExpended = (TextView)findViewById(R.id.energy_expended);
        bodySensorLocation = (TextView)findViewById(R.id.sensor_location);

        deviceName.setText(mDeviceName);
        deviceAddress.setText(mDeviceAddress);

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }

        findViewById(R.id.disconnect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mBluetoothLeService.disconnect();
                onBackPressed();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                //clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                Toast.makeText(HeartRatePeripheralReciever.this, "Automatically connecting to HEART RATE SERVICE", Toast.LENGTH_SHORT).show();
                // Getting all the supported services and characteristics and connecting to Heart Rate Service
                // and to Heart Rate Measurement Characteristics.
                getAndConnectToHeartRateService(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                if(intent.hasExtra(BluetoothLeService.BODY_SENSOR_LOC))
                {
                    try {
                        int bodySensorLocationCode = Integer.parseInt(intent.getStringExtra(BluetoothLeService.BODY_SENSOR_LOC));
                        bodySensorLocation.setText(String.format("(%d) %s", bodySensorLocationCode, BODY_SENSOR_LOCATION_TEXT[bodySensorLocationCode]));
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(HeartRatePeripheralReciever.this, "An error occured", Toast.LENGTH_SHORT).show();
                        bodySensorLocation.setText(getResources().getString(R.string.defualt_value));
                    }
                    fetchFromBLE(heartRateCharacteristic);
                }
                else
                {
                    heartRate.setText(String.format("%s bpm", intent.getStringExtra(BluetoothLeService.HEART_RATE)));
                    energyExpended.setText(intent.getStringExtra(BluetoothLeService.ENERGY_EXPENDED));
                    //Body sensor location fetching, as it is in READ mode
                    bodySensorLocation.setText("Fetching...");
                    fetchFromBLE(bodySensorLocationCharacteristic);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    fetchFromBLE(bodySensorLocationCharacteristic);
                }
            }
        }
    };

    private void getAndConnectToHeartRateService(List<BluetoothGattService> gattServices) {
        if (gattServices == null)
            return;
        String uuid = null;
        for (BluetoothGattService gattService : gattServices) {
            uuid = gattService.getUuid().toString();
            if(uuid.equals(UUID_HEART_RATE_SERVICE)) {
                List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    final BluetoothGattCharacteristic characteristic = gattCharacteristic;
                    final int charaProp = characteristic.getProperties();

                    if(BluetoothLeService.UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
                        heartRateCharacteristic = characteristic;
                        fetchFromBLE(heartRateCharacteristic);
                        Toast.makeText(HeartRatePeripheralReciever.this, "Auto-connected to HEART RATE SERVICE", Toast.LENGTH_SHORT).show();
                    }
                    else if((BluetoothLeService.UUID_BODY_SENSOR_LOCATION.equals(characteristic.getUuid()))) {
                        bodySensorLocationCharacteristic = characteristic;
                    }
                }
            }
        }
    }

    private void fetchFromBLE(BluetoothGattCharacteristic characteristic) {
        if ((characteristic.getProperties() | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
            // If there is an active notification on a characteristic, clear
            // it first so it doesn't update the data field on the user interface.
            if (mNotifyCharacteristic != null) {
                mBluetoothLeService.setCharacteristicNotification(
                        mNotifyCharacteristic, false);
                mNotifyCharacteristic = null;
            }
            mBluetoothLeService.readCharacteristic(characteristic);
        }
        if ((characteristic.getProperties() | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
            mNotifyCharacteristic = characteristic;
            mBluetoothLeService.setCharacteristicNotification(
                    characteristic, true);
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                deviceConnection.setText(resourceId);
            }
        });
    }
}