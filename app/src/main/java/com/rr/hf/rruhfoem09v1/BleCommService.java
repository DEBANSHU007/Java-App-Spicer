package com.rr.hf.rruhfoem09v1;

import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.util.List;
import java.util.UUID;

import androidx.core.app.ActivityCompat;

public class BleCommService extends Service {
    private final static String TAG = BleCommService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattServer mGattServer;
    public BluetoothDevice remoteDevice;
    public boolean isRemoteDeviceAvailable = false;

    public final static String ACTION_GATT_CONNECTED = "com.rr.hfoem09.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "com.rr.hfoem09.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.rr.hfoem09.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_SERVICE_NOT_FOUND = "com.rr.hfoem09.ACTION_SERVICE_NOT_FOUND";
    public final static String ACTION_CHARACTERISTIC_NOT_FOUND = "com.rr.hfoem09.ACTION_CHARACTERISTIC_NOT_FOUND";
    public final static String ACTION_DATA_AVAILABLE = "com.rr.hfoem09.ACTION_DATA_AVAILABLE";
    public final static String BATTERY_LEVEL_AVAILABLE = "com.rr.hfoem09.BATTERY_LEVEL_AVAILABLE";
    public final static String ACTION_DATA_WRITE_SUC = "com.rr.hfoem09.ACTION_DATA_WRITE_SUC";
    public final static String ACTION_DATA_WRITE_FAIL = "com.rr.hfoem09.ACTION_DATA_WRITE_FAIL";
    public final static String ACTION_CHAR_EMPTY = "com.rr.hfoem09.ACTION_CHAR_EMPTY";
    public final static String EXTRA_DATA = "com.rr.hfoem09.EXTRA_DATA";
    public final static String BATTERY_DATA = "com.rr.hfoem09.BATTERY_DATA";

    //UUIDs defined here
    public static final UUID RX_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID RX_CHAR_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID TX_CHAR_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e"); //characteristic from which we read data
    public static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"); //descriptor, which we use to enable motification of characteristic

    //new battery service uuids
    public static final UUID BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    public static final UUID BATTERY_LEVEL_CHAR_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
    public static final UUID BATTERY_LEVEL_CHAR_DESC_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private BluetoothGattCharacteristic CHAR_TX;
    private BluetoothGattCharacteristic batteryLevelChar;

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e("MY-ERROR", "Can't discover Services as BLUETOOTH_CONNECT Permission Not Granted");
                    return;
                }
                Log.i(TAG, "Attempting to start service discovery:" + mBluetoothGatt.discoverServices());
                //initializing characteristic object to read battery level

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.getUuid().equals(BATTERY_LEVEL_CHAR_UUID)) {
                    broadcastUpdate(BATTERY_LEVEL_AVAILABLE, (int) characteristic.getValue()[0], true);
                } else {
                    broadcastUpdate(characteristic);
                }
            } else {
                Log.d("DBG", "Problem in Reading Characteristic: " + characteristic.getUuid());
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_WRITE_SUC);
            } else {
                broadcastUpdate(ACTION_DATA_WRITE_FAIL);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (characteristic.getUuid().equals(BATTERY_LEVEL_CHAR_UUID))
                broadcastUpdate(BATTERY_LEVEL_AVAILABLE, characteristic.getValue()[0], true);
            else
                broadcastUpdate(characteristic);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (descriptor.getCharacteristic().getUuid().equals(BATTERY_LEVEL_CHAR_UUID)) {
                setCharacteristicNotification(); //enabling notification for reading card UID from here, as it's necessary to wait for battery notification tobe enabled first. otherwise, it will never enable second notification.
            }
        }
    };

    //different versions of broadcast update method
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(BleCommService.ACTION_DATA_AVAILABLE);
        if (TX_CHAR_UUID.equals(characteristic.getUuid()))
            intent.putExtra(EXTRA_DATA, characteristic.getValue());
        sendBroadcast(intent);
    }

    //modified for two purposes -- to detect error code while starting server, and to send battery level to mobile device
    private void broadcastUpdate(final String action, final int batteryLevelOrErrorCode, final boolean isBattery) {
        final Intent intent = new Intent(action);
        if (isBattery) {//means we are sending broadcast for for battery level
            intent.putExtra(BATTERY_DATA, batteryLevelOrErrorCode);
        } else {//means we are sending broadcast for for error code
            intent.putExtra(EXTRA_DATA, batteryLevelOrErrorCode);
        }
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action, byte[] value) {
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_DATA, value);
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        BleCommService getService() {
            return BleCommService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();


    //utility functions
    //initializing local bluetooth adapter
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    //function to connect bluetooth device
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (address.equals(mBluetoothDeviceAddress) && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e("MY-ERROR", "Can't  Connect to Device, as BLUETOOTH_CONNECT Permission Not Granted");
                return false;
            }
            return mBluetoothGatt.connect();
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e("MY-ERROR", "Can't Connect to Device as BLUETOOTH_CONNECT Permission Not Granted");
            return false;
        }
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        return true;
    }

    //Disconnects an existing connection or cancel a pending connection
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e("MY-ERROR", "Can't Disconnect device as BLUETOOTH_CONNECT Permission Not Granted");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    //closing connection with ble gatt
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e("MY-ERROR", "Can't Close Bluetooth connection as BLUETOOTH_CONNECT Permission Not Granted");
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    //read characteristics from ble service
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e("MY-ERROR", "Can't Read Characteristic as BLUETOOTH_CONNECT Permission Not Granted");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
        //Log.d("DBG", "Characteristic read requested: " + characteristic.getUuid());
    }

    //function to enable to notify activity about specific functionality.
    public boolean setCharacteristicNotification() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }
        BluetoothGattService RxService = mBluetoothGatt.getService(RX_SERVICE_UUID);
        if (RxService == null) {
            //we can show error message here using broadcast
            return false;
        }
        BluetoothGattCharacteristic TxChar = RxService.getCharacteristic(TX_CHAR_UUID);
        if (TxChar == null) {
            //we can show error message here using broadcast
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e("MY-ERROR", "Can't Enable Tx Notification as BLUETOOTH_CONNECT Permission Not Granted");
            return false;
        }
        mBluetoothGatt.setCharacteristicNotification(TxChar, true);

        BluetoothGattDescriptor descriptor = TxChar.getDescriptor(CCCD);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e("MY-ERROR", "Can't SET Tx Descriptor as BLUETOOTH_CONNECT Permission Not Granted");
            return false;
        }
        return mBluetoothGatt.writeDescriptor(descriptor);
    }

    //function to enable battery level notification
    public boolean enableBatteryNotification() { //commented as live battery update is not required for now
        BluetoothGattService BatteryService = mBluetoothGatt.getService(BATTERY_SERVICE_UUID);
        if (BatteryService == null) {
            //we can show error message here using broadcast
            broadcastUpdate(ACTION_SERVICE_NOT_FOUND);
            return false;
        }
        batteryLevelChar = BatteryService.getCharacteristic(BATTERY_LEVEL_CHAR_UUID);
        if (batteryLevelChar == null) {
            //we can show error message here using broadcast
            broadcastUpdate(ACTION_CHARACTERISTIC_NOT_FOUND);
            return false;
        }
        //enabling notification for battery service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e("MY-ERROR", "Can't SET Characteristic notification as BLUETOOTH_CONNECT Permission Not Granted");
            return false;
        }
        mBluetoothGatt.setCharacteristicNotification(batteryLevelChar, true);

        BluetoothGattDescriptor descriptorBatteryLevel = batteryLevelChar.getDescriptor(BATTERY_LEVEL_CHAR_DESC_UUID);
        descriptorBatteryLevel.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e("MY-ERROR", "Can't Write Battery level Descriptor as BLUETOOTH_CONNECT Permission Not Granted");
            return false;
        }
        return mBluetoothGatt.writeDescriptor(descriptorBatteryLevel);
    }

    //function to read battery level
    public void readBatteryLevelChar() {
        if (mBluetoothGatt == null) return;
        batteryLevelChar = mBluetoothGatt.getService(BATTERY_SERVICE_UUID).getCharacteristic(BATTERY_LEVEL_CHAR_UUID); //initializing battery level char
        readCharacteristic(batteryLevelChar);
    }

    //function to discover gatt service, invoked after discoverGattService is Executed.
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;
        return mBluetoothGatt.getServices();
    }

    //function to write to Rx characteristics
    public boolean writeRXData(byte[] value) {
        BluetoothGattService RxService = mBluetoothGatt.getService(RX_SERVICE_UUID);
        if (RxService == null) {
            //we can show error message here using broadcast
            return false;
        }
        BluetoothGattCharacteristic RxChar = RxService.getCharacteristic(RX_CHAR_UUID);
        if (RxChar == null) {
            //we can show error message here using broadcast
            return false;
        }
        RxChar.setValue(value);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e("MY-ERROR", "Bluetooth connect permission not provided");
            return false;
        }
        return mBluetoothGatt.writeCharacteristic(RxChar);
    }
}