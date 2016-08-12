package me.macnerland.bluetooth;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ExpandableListAdapter;

import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private final static String TAG = "Main";
    private final static int REQUEST_ENABLE_BT = 1;
    private static mPagerAdapter adapter;
    private static Context context;
    private ActionBar actionBar;

    private static UUID hubServiceGattUUID =      new UUID(0x0000ffe000001000L, 0x800000805f9b34fbL);
    private static UUID[] hubUUID = {hubServiceGattUUID};
    private static final UUID sensorGattUUID =   new UUID(0x0000feed00001000L, 0x800000805f9b34fbL);
    private static UUID[] sensorUUID = {sensorGattUUID};
    private static final UUID sensorCharacterGattUUID =   new UUID(0x0000ffe100001000L, 0x800000805f9b34fbL);
    private static final UUID hubCharacteristicGattUUID =   new UUID(0x0000ffe100001000L, 0x800000805f9b34fbL);
    private static UUID commonSerial =     new UUID(0x0000110100001000L, 0x800000805f9b34fbL);

    private static SensorAdapter sensorAdapter;

    private static BluetoothManager bluetoothManager;
    private static BluetoothAdapter bluetoothAdapter;

    private static BluetoothService bluetooth;

    private static final byte[] TempCommand = {(byte)'2', (byte)'\n'};
    private static final byte[] HumidCommand = {(byte)'1', (byte)'\n'};

    private static final byte[] getAlertNumber = {(byte)'1', (byte)'1', (byte)' ', (byte)'\n'};
    private static final byte[] getPortalNumber = {(byte)'1', (byte)'3', (byte)' ', (byte)'\n'};
    private static final byte[] getPortalFreq = {(byte)'1', (byte)'5', (byte)' ', (byte)'\n'};
    private static final byte[] getLogFreq = {(byte)'1', (byte)'7', (byte)' ', (byte)'\n'};
    private static final byte[] getHubTime = {(byte)'1', (byte)'9', (byte)' ', (byte)'\n'};
    private static final byte[] getHubDate = {(byte)'2', (byte)'1', (byte)' ', (byte)'\n'};
    private static final byte[] getCritTemp = {(byte)'2', (byte)'3', (byte)' ', (byte)'\n'};
    private static final byte[] getCritHum = {(byte)'2', (byte)'5', (byte)' ', (byte)'\n'};


    private static Hashtable<String, BluetoothGatt> hubs;
    private static String hubAlertNumber;
    private static String hubPortalNumber;
    private static String hubPortalFreq;
    private static String hubLogFreq;
    private static String hubTime;
    private static String hubDate;
    private static String hubCritTemp;
    private static String hubCritHum;
    private final int HUB_NO_DATA_PENDING = 0;
    private final int HUB_ALERT_PHONE_NUMBER_PENDING = 1;
    private final int HUB_PORTAL_PHONE_NUMBER_PENDING = 2;
    private final int HUB_PORTAL_FREQ_PENDING = 3;
    private final int HUB_LOG_FREQ_PENDING = 4;
    private final int HUB_TIME_PENDING = 5;
    private final int HUB_DATE_PENDING = 6;
    private final int HUB_CRIT_TEMP_PENDING = 7;
    private final int HUB_CRIT_HUM_PENDING = 8;
    private int hubDataState;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tab_pager);
        hubDataState = HUB_NO_DATA_PENDING;
        hubs = new Hashtable<>();
        context = this;
        bluetooth = null;
        sensorAdapter = new SensorAdapter(context);

        adapter = new mPagerAdapter(this.getSupportFragmentManager(), getResources(), context);
        ViewPager vp = (ViewPager) findViewById(R.id.pager);
        vp.setAdapter(adapter);


        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            this.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        ServiceConnection con = new conn();
        Intent btIntent = new Intent(this, BluetoothService.class);
        startService(btIntent);
        Log.i(TAG, "Binding");
        bindService(btIntent, con, BIND_AUTO_CREATE);

        registerReceiver(mGattUpdateReceiver, getGATTFilters());
    }

    public void getTime(){
        bluetoothAdapter.stopLeScan(hubScanCallback);
        Set<String> keys = hubs.keySet();
        for(String key : keys){
            BluetoothGatt bg = hubs.get(key);
            BluetoothGattService bgs = bg.getService(hubServiceGattUUID);
            BluetoothGattCharacteristic bgc = bgs.getCharacteristic(hubCharacteristicGattUUID);
            bgc.setValue(getHubTime);
            hubDataState = HUB_TIME_PENDING;
            bg.writeCharacteristic(bgc);
        }
    }

    public void getPortalNumber(){
        Set<String> keys = hubs.keySet();
        for(String key : keys){
            BluetoothGatt bg = hubs.get(key);
            BluetoothGattService bgs = bg.getService(hubServiceGattUUID);
            BluetoothGattCharacteristic bgc = bgs.getCharacteristic(hubCharacteristicGattUUID);
            bgc.setValue(getPortalNumber);
            hubDataState = HUB_PORTAL_PHONE_NUMBER_PENDING;
            bg.writeCharacteristic(bgc);
        }
    }

    public void getAlertNumber(){
        Set<String> keys = hubs.keySet();
        for(String key : keys){
            BluetoothGatt bg = hubs.get(key);
            BluetoothGattService bgs = bg.getService(hubServiceGattUUID);
            BluetoothGattCharacteristic bgc = bgs.getCharacteristic(hubCharacteristicGattUUID);
            bgc.setValue(getAlertNumber);
            hubDataState = HUB_ALERT_PHONE_NUMBER_PENDING;
            bg.writeCharacteristic(bgc);
        }
    }

    public void getPortalFrequency(){
        Set<String> keys = hubs.keySet();
        for(String key : keys){
            BluetoothGatt bg = hubs.get(key);
            BluetoothGattService bgs = bg.getService(hubServiceGattUUID);
            BluetoothGattCharacteristic bgc = bgs.getCharacteristic(hubCharacteristicGattUUID);
            bgc.setValue(getPortalFreq);
            hubDataState = HUB_PORTAL_FREQ_PENDING;
            bg.writeCharacteristic(bgc);
        }
    }

    public void getLogFrequency(){
        Set<String> keys = hubs.keySet();
        for(String key : keys){
            BluetoothGatt bg = hubs.get(key);
            BluetoothGattService bgs = bg.getService(hubServiceGattUUID);
            BluetoothGattCharacteristic bgc = bgs.getCharacteristic(hubCharacteristicGattUUID);
            bgc.setValue(getLogFreq);
            hubDataState = HUB_LOG_FREQ_PENDING;
            bg.writeCharacteristic(bgc);
        }
    }

    public void getDate(){
        Set<String> keys = hubs.keySet();
        for(String key : keys){
            BluetoothGatt bg = hubs.get(key);
            BluetoothGattService bgs = bg.getService(hubServiceGattUUID);
            BluetoothGattCharacteristic bgc = bgs.getCharacteristic(hubCharacteristicGattUUID);
            bgc.setValue(getHubDate);
            hubDataState = HUB_DATE_PENDING;
            bg.writeCharacteristic(bgc);
        }
    }

    public void getCritTemp(){
        Set<String> keys = hubs.keySet();
        for(String key : keys){
            BluetoothGatt bg = hubs.get(key);
            BluetoothGattService bgs = bg.getService(hubServiceGattUUID);
            BluetoothGattCharacteristic bgc = bgs.getCharacteristic(hubCharacteristicGattUUID);
            bgc.setValue(getCritTemp);
            hubDataState = HUB_CRIT_TEMP_PENDING;
            bg.writeCharacteristic(bgc);
        }
    }

    public void getCritHumid(){
        Set<String> keys = hubs.keySet();
        for(String key : keys){
            BluetoothGatt bg = hubs.get(key);
            BluetoothGattService bgs = bg.getService(hubServiceGattUUID);
            BluetoothGattCharacteristic bgc = bgs.getCharacteristic(hubCharacteristicGattUUID);
            bgc.setValue(getCritHum);
            hubDataState = HUB_CRIT_HUM_PENDING;
            bg.writeCharacteristic(bgc);
        }
    }

    public static String getHubAlertNumber(){
        return hubAlertNumber;
    }
    public static String getHubPortalNumber(){
        return hubPortalNumber;
    }
    public static String getHubPortalFreq() {
        return hubPortalFreq;
    }
    public static String getHubLogFreq(){
        return hubLogFreq;
    }
    public static String getHubTime(){
        return hubTime;
    }
    public static String getHubDate(){
        return hubDate;
    }
    public static String getHubCritTemp(){
        return hubCritTemp;
    }
    public static String getHubCritHum(){
        return hubCritHum;
    }

    public static SensorAdapter getSensorAdapter(){
        return sensorAdapter;
    }

    public void updateH(View Null){
        bluetoothAdapter.stopLeScan(sensorScanCallback);
        sensorAdapter.updateHumidity();
    }

    public void updateT(View Null){
        bluetoothAdapter.stopLeScan(sensorScanCallback);
        sensorAdapter.updateTemperature();
    }

    public void scanForHub(View Null){
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            ((Activity)context).startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        bluetoothAdapter.startLeScan(hubUUID, hubScanCallback);
    }

    public void scanForSensor(View Null){
        //if bluetooth is disabled, launch an intent to enable it.
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            ((Activity)this).startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        bluetoothAdapter.startLeScan(sensorUUID, sensorScanCallback);
    }

    public BluetoothAdapter.LeScanCallback hubScanCallback = new BluetoothAdapter.LeScanCallback(){
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord){
            BluetoothGatt hubGatt = device.connectGatt(context, true, bluetooth.hubGattCallback);
            hubs.put(device.getAddress(), hubGatt);
        }
    };

    public BluetoothAdapter.LeScanCallback sensorScanCallback = new BluetoothAdapter.LeScanCallback(){
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord){
            BluetoothGatt sensorGatt = device.connectGatt(context, true, bluetooth.sensorGattCallback);
            sensorAdapter.addSensor(sensorGatt, context);
        }
    };

    public class conn implements ServiceConnection{
        @Override
        public void onServiceConnected(ComponentName name, IBinder service){
            Log.i(TAG, "Connected!");
            boolean firstConnect = bluetooth == null;
            bluetooth = ((BluetoothService.LocalBinder) service).getService();
            if(firstConnect){
                scanForHub(null);
                scanForSensor(null);
            }

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bluetooth = null;
        }
    }

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
            final String address = intent.getStringExtra(BluetoothService.ADDRESS_DATA);
            if (BluetoothService.SENSOR_ACTION_GATT_CONNECTED.equals(action)) {
                //connection means nothing
            } else if (BluetoothService.SENSOR_ACTION_GATT_DISCONNECTED.equals(action)) {
                sensorAdapter.disconnectSensor(address);
            } else if (BluetoothService.SENSOR_ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                sensorAdapter.connectSensor(address);
                sensorAdapter.updateNotification(address);

            } else if (BluetoothService.SENSOR_ACTION_DATA_AVAILABLE.equals(action)) {
                Log.e(TAG, "New DATA" + intent.getAction());
                Log.e(TAG, "Data:" + intent.getStringExtra(BluetoothService.EXTRA_DATA) + address);
            } else if (BluetoothService.HUB_ACTION_GATT_SERVICES_DISCOVERED.equals(action)){
                BluetoothGatt bg = hubs.get(address);
                BluetoothGattService bgs = bg.getService(hubServiceGattUUID);
                BluetoothGattCharacteristic bgc = bgs.getCharacteristic(hubCharacteristicGattUUID);
                int properties = bgc.getProperties();
                if ((properties | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    bg.setCharacteristicNotification(bgc, true);
                }
                getPortalNumber();
            } else if (BluetoothService.HUB_ACTION_DATA_AVAILABLE.equals(action)){
                String value = intent.getStringExtra(BluetoothService.EXTRA_DATA);
                switch(hubDataState){
                    case HUB_PORTAL_PHONE_NUMBER_PENDING:
                        hubPortalNumber = value;
                        getPortalFrequency();
                    case HUB_PORTAL_FREQ_PENDING:
                        hubPortalFreq = value;
                        getLogFrequency();
                        break;
                    case HUB_LOG_FREQ_PENDING:
                        hubLogFreq = value;
                        getTime();
                        break;
                    case HUB_TIME_PENDING:
                        Log.e(TAG, "The Time is:" + value);
                        hubTime = value;
                        getDate();
                        break;
                    case HUB_DATE_PENDING:
                        hubDate = value;
                        getCritTemp();
                        break;
                    case HUB_CRIT_TEMP_PENDING:
                        hubCritTemp = value;
                        getCritHumid();
                        break;
                    case HUB_CRIT_HUM_PENDING:
                        hubCritHum = value;
                        adapter.refreshUI();
                        break;

                }
            }
        }
    };

    private IntentFilter getGATTFilters(){
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothService.SENSOR_ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothService.SENSOR_ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothService.SENSOR_ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothService.SENSOR_ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothService.HUB_ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothService.HUB_ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothService.HUB_ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothService.HUB_ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}