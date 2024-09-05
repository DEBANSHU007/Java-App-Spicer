package com.rr.hf.rruhfoem09v1;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import rruhfoem09v1.R;
import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.drawable.ClipDrawable;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
// Import necessary packages for reading Excel files
import org.apache.poi.ss.usermodel.*;
//libraries for reading csv files

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
//libraries for reading csv files

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private Button bConnect = null;
    private Button bWrite = null;
    private Button bInventory, bClear;
    private EditText tCommand;
    public  TextView msgText = null;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    ClipDrawable batteryLevelDrawable;
    ImageView batteryImage;

    private boolean mScanning;
    private Handler handler;
    private List<BluetoothDevice> leDeviceList;
    private ArrayAdapter<String> leDeviceAdapter = null;
    private BleCommService mBleCommService;
    private String mDeviceAddress;
    private static final long SCAN_PERIOD = 5000; // Stops scanning after 5 seconds.
    private ScanCallback leScanCallback = null;
    private String folderPath = "";

    private AlertDialog deviceRelatedDialog = null;
    public static StringBuffer msgBuffer;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBleCommService = ((BleCommService.LocalBinder) service).getService();
            if (!mBleCommService.initialize()) {
                Toast.makeText(MainActivity.this, "Unable to initialize Bluetooth", Toast.LENGTH_LONG).show();
                finish();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBleCommService = null;
        }
    };

    // Method to extract EPC number from Excel file
    private String extractValueFromExcel(int columnIndex, Integer rowIndex) throws IOException {
        String value = "";
        // Open the Excel file from the assets directory
        try (InputStream inputStream = getResources().openRawResource(R.raw.rfid)){
            Workbook workbook = WorkbookFactory.create(inputStream);
            // Assuming the data starts from the second row
            Sheet sheet = workbook.getSheetAt(0);
            Row row = sheet.getRow(rowIndex); // Assuming the second row (index 1)
            if (row != null) {
                Cell cell = row.getCell(columnIndex);
                if (cell != null) {
                    // Check the cell type
                    switch (cell.getCellType()) {
                        case STRING:
                            value = cell.getStringCellValue();
                            break;
                        case NUMERIC:
                            // Convert numeric value to string
                            value = String.valueOf(cell.getNumericCellValue());
                            break;
                        default:
                            // Handle other cell types if needed
                            value = ""; // Or null if preferred
                            break;
                    }
                }
            }
        }
        return value;
    }
    public void writeToExcel(Context context, String receivedData) {
        InputStream inputStream = null;
        Workbook workbook = null;
        FileOutputStream outputStream = null;
        String TAG = "WriteToExcel";

        try {
            // Load the Excel file from the raw resources
            Resources resources = context.getResources();
            inputStream = resources.openRawResource(R.raw.spicerinfo);
            workbook = WorkbookFactory.create(inputStream);

            // Access the first sheet (assuming it's the only one)
            Sheet sheet = workbook.getSheetAt(0);

            // Determine the next available row index
            int lastRowNum = sheet.getLastRowNum();
            int newRowNum = lastRowNum + 1;

            // Get current date and time
            LocalDateTime currentDateTime = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String formattedDateTime = currentDateTime.format(formatter);

            // Create a new row
            Row newRow = sheet.createRow(newRowNum);

            // Set received data in the first column
            Cell cellReceivedData = newRow.createCell(0);
            cellReceivedData.setCellValue(receivedData);

            // Set current date/time in the second column
            Cell cellDateTime = newRow.createCell(1);
            cellDateTime.setCellValue(formattedDateTime);

            // Save the changes back to the Excel file
            outputStream = context.openFileOutput("spicerinfo.xlsx", Context.MODE_PRIVATE);
            workbook.write(outputStream);

            Log.d(TAG, "Excel file updated successfully.");

        } catch (Exception e) {
            Log.e(TAG, "Error writing to Excel file: " + e.getMessage());
        } finally {
            try {
                if (workbook != null) {
                    workbook.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing resources: " + e.getMessage());
            }
        }
    }

    private String extractEpcNumberFromExcel(Integer rowIndex) throws IOException {
        return extractValueFromExcel(0,rowIndex); // Assuming EPC number is in the first column (index 0)
    }

    private String extractMachineNameFromExcel(Integer rowIndex) throws IOException {
        return extractValueFromExcel(1,rowIndex); // Assuming machine name is in the second column (index 1)
    }

    private String extractCellNameFromExcel(Integer rowIndex) throws IOException {
        return extractValueFromExcel(2,rowIndex); // Assuming cell name is in the third column (index 2)
    }

    private String extractOperationFromExcel(Integer rowIndex) throws IOException {
        return extractValueFromExcel(3,rowIndex); // Assuming operation is in the fourth column (index 3)
    }

    private String extractAssetCodeFromExcel(int rowIndex) throws IOException {
        return extractValueFromExcel(4,rowIndex); // Assuming asset code is in the fifth column (index 4)
    }
    private int findRowNumberByEpcNumber(String epcNumber) throws IOException {
        // Open the Excel file from the assets directory
        try (InputStream inputStream = getResources().openRawResource(R.raw.rfid)){
            Workbook workbook = WorkbookFactory.create(inputStream);

            // Assuming the data starts from the second row
            Sheet sheet = workbook.getSheetAt(0);
            Integer rowsCount = sheet.getPhysicalNumberOfRows();
            displayData(rowsCount.toString());
            // Iterate over rows
            for (Integer rowIndex = 1; rowIndex < rowsCount; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row != null) {
                    // Assuming the EPC number is in the first column
                    Cell cell = row.getCell(0);

                    if (cell != null ) {
                        String newEpc=cell.getStringCellValue();
                        Integer size=newEpc.length() ;
                        //displayData(size.toString());
                        //displayData(epcNumber);
                        //displayData(newEpc);
                        if(Objects.equals(newEpc.trim(), epcNumber.trim())) {
                            return rowIndex;
                        }
                    }
                }
            }
        }
        return -1; // Return -1 if no matching row is found
    }
    // Method to display "Hello, World!" in the user interface
    private void displayHelloWorld() {
        // Assuming you have a TextView named 'textViewOutput' in your layout
        View dialogView = getLayoutInflater().inflate(R.layout.layout_dialog_scan_device, null);

        // Find the TextView by its ID within the inflated layout
        TextView textViewOutput = dialogView.findViewById(R.id.message);
        // Update the text of the TextView to display "Hello, World!"
        textViewOutput.setText("Hello,World!");
        addLogs("Hello BlackTag");
    }
    private void displayHiiSpicer() {
        // Assuming you have a TextView named 'textViewOutput' in your layout
        TextView textViewOutput = findViewById(R.id.message);
        // Update the text of the TextView to display "Hello, World!"
        textViewOutput.setText("Hello,Spicer!");
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //adding custom app bar in activity
        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);

        bConnect = findViewById(R.id.btnConnect);
        bWrite = findViewById(R.id.btnWr);
        tCommand = findViewById(R.id.txtData);
        msgText = findViewById(R.id.logTxt);
        batteryImage = findViewById(R.id.battery_fill);
        bClear = findViewById(R.id.btnClear);
        bInventory = findViewById(R.id.btnInvt);

        batteryLevelDrawable = (ClipDrawable) batteryImage.getDrawable();
        leDeviceList = new ArrayList<>();
        leDeviceAdapter = new ArrayAdapter<>(MainActivity.this, R.layout.list_ble_layout);
        msgBuffer = new StringBuffer();
        handler = new Handler();

        //checking whether ble is available or not
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(MainActivity.this, "Bluetooth Low Energy Not Supported, So App can't work.", Toast.LENGTH_LONG).show();
            finish();
        }

        //request to user to enable bluetooth, if not enabled
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        ActivityResultLauncher<Intent> btStartActivity = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK) {
                Toast.makeText(MainActivity.this, "Bluetooth is required to run this app, it will not function without bluetooth", Toast.LENGTH_LONG).show();
                finish();
            }
            else {
                //switch on, and connect with bluetooth
                final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                bluetoothAdapter = bluetoothManager.getAdapter();
                bleScanner = bluetoothAdapter.getBluetoothLeScanner();
            }
        });

        //request location and file writing permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityResultLauncher<String[]> locationPermissionRequest =
                    registerForActivityResult(new ActivityResultContracts
                                    .RequestMultiplePermissions(), result -> {
                                Boolean fineLocationGranted = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
                                if(fineLocationGranted == null)
                                    fineLocationGranted = false;
                                Boolean coarseLocationGranted = result.get(Manifest.permission.ACCESS_COARSE_LOCATION);
                                if(coarseLocationGranted == null)
                                    coarseLocationGranted = false;

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !fineLocationGranted) {
                                    Toast.makeText(MainActivity.this, "Location Permission is required to connect with reader !", Toast.LENGTH_LONG).show();
                                    finish();
                                } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && !coarseLocationGranted) {
                                    Toast.makeText(MainActivity.this, "Location Permission is required to connect with reader !", Toast.LENGTH_LONG).show();
                                    finish();
                                }

                                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { //android 12 or higher device, check for BLE Scan and Connect permissions also
                                    Boolean bleScanGranted = result.get(Manifest.permission.BLUETOOTH_SCAN);
                                    if(bleScanGranted == null)
                                        bleScanGranted = false;
                                    Boolean bleConnectGranted = result.get(Manifest.permission.BLUETOOTH_CONNECT);
                                    if(bleConnectGranted == null)
                                        bleConnectGranted = false;
                                    if (bleScanGranted && bleConnectGranted) {
                                        // ble scan and connect granted, ask for permissions
                                        //enabling bluetooth
                                        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                                            btStartActivity.launch(enableBtIntent);
                                        }
                                    } else {
                                        Toast.makeText(MainActivity.this, "BLE Scan, and BLE Connect permission required to scan and connect with reader !", Toast.LENGTH_LONG).show();
                                        finish();
                                    }
                                }
                            }
                    );
            //requesting location permission
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                locationPermissionRequest.launch(new String[] {
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                });
            }
            else {
                locationPermissionRequest.launch(new String[] {
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                });
            }
        }

        //enabling bluetooth if version is less than 12
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && (bluetoothAdapter == null || !bluetoothAdapter.isEnabled())) {
            btStartActivity.launch(enableBtIntent);
        }

        if(bluetoothAdapter == null || bleScanner == null) {
            //switch on, and connect with bluetooth
            final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            bluetoothAdapter = bluetoothManager.getAdapter();
            bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        }

        //establish connection with service...
        Intent gattServiceIntent = new Intent(this, BleCommService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        //initializing scanning call-back
        leScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, final ScanResult result) {
                super.onScanResult(callbackType, result);
                runOnUiThread(() -> {
                    if(mScanning) {
                        BluetoothDevice device = result.getDevice();
                        //BluetoothDevice device = result.getDevice();
                        if(!leDeviceList.contains(device)) {
                            leDeviceList.add(device);
                            String deviceLabel;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
                                    deviceLabel = device.getName()+ " -- " +device.getAddress();
                                else
                                    deviceLabel = device.getAddress();
                            }
                            else {
                                deviceLabel = device.getName()+ " -- " +device.getAddress();
                            }
                            leDeviceAdapter.add(deviceLabel);
                            leDeviceAdapter.notifyDataSetChanged();
                        }
                    }
                });
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                super.onBatchScanResults(results);
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
            }
        };

        //click event for connect button
        bConnect.setOnClickListener(view -> {
            if(bConnect.getText().toString().trim().equals("Disconnect")) {
                mBleCommService.disconnect();
                return;
            }
            //check whether location is enabled or disabled
            final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                showAlertMessageNoLocation();
            }
            else {
                //starting ble scan
                scanLeDevice(true);
            }
        });

        //click event for send command button
        bWrite.setOnClickListener(view -> {
            //commandMode = false;
            sendData(tCommand.getText().toString());
        });

        //click event for inventory button
        bInventory.setOnClickListener(view -> {
            byte[] Command = new byte[] {
                    0x03, //array length
                    0x50, //command code
                    0x02, //command code
            };
            byte[] commandWithCRC = new byte[Command.length+2];
            System.arraycopy(Command, 0, commandWithCRC, 0, Command.length);
            //byte[] Crc = AddCRC(Command, Command.length);
            byte[] Crc = AddCRC(Command);
            commandWithCRC[commandWithCRC.length-2] = Crc[0];
            commandWithCRC[commandWithCRC.length-1] = Crc[1];
            sendCommand(commandWithCRC);
            addLogs("Inventory Command Sent...");
        });

        //click event for clear button
        bClear.setOnClickListener(v -> {
            msgBuffer.setLength(0);
            msgText.setText("");
        });
    }

    //runnable callback function for 5 second scanning timer
    private final Runnable bleRunnable = new Runnable() {
        @Override
        public void run() {
            mScanning = false;
            //bluetoothAdapter.stopLeScan(leScanCallback);
            try { bleScanner.stopScan(leScanCallback); } catch(SecurityException sece) { Toast.makeText(MainActivity.this, "Permission to stop scan BLE Device is not provided -- " +sece.getMessage(), Toast.LENGTH_LONG).show(); }
            runOnUiThread(() -> {
                //generate dialog with available devices
                deviceRelatedDialog.dismiss();
                deviceRelatedDialog = null;
                deviceRelatedDialog = generateDeviceListDialog(true, true);
                deviceRelatedDialog.setCanceledOnTouchOutside(false);
                deviceRelatedDialog.show();
            });
        }
    };


    //broadcast receivers, and intent filters to react on response of service
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BleCommService.ACTION_GATT_CONNECTED.equals(action)) {
                bConnect.setText(R.string.disconnect);
                enableDisableUi(true);
                addLogs("Connected Successfully To -- " + mDeviceAddress);
            } else if (BleCommService.ACTION_GATT_DISCONNECTED.equals(action)) {
                batteryLevelDrawable.setLevel(0);
                bConnect.setText(R.string.search_ble);
                enableDisableUi(false);
                addLogs("Disconnected From -- " + mDeviceAddress);
            } else if (BleCommService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                mBleCommService.enableBatteryNotification(); //enabled battery notification
                displayGattServices(mBleCommService.getSupportedGattServices()); //display message whether device connected or not
            } else if(BleCommService.ACTION_SERVICE_NOT_FOUND.equals(action)) {
                addLogs("Service Not Found !");
            } else if(BleCommService.ACTION_CHARACTERISTIC_NOT_FOUND.equals(action)) {
                addLogs("Characteristic Not Found !");
            } else if(BleCommService.ACTION_DATA_WRITE_SUC.equals(action)) {
                addLogs("Data Sent Successfully...");
            } else if(BleCommService.ACTION_DATA_WRITE_FAIL.equals(action)) {
                addLogs("Error in writing Data to Device !!");
            } else if(BleCommService.ACTION_CHAR_EMPTY.equals(action)) {
                addLogs("Characteristic is empty...");
            } else if (BleCommService.ACTION_DATA_AVAILABLE.equals(action)) {
                final byte[] txValue = intent.getByteArrayExtra(BleCommService.EXTRA_DATA);
                runOnUiThread(() -> {
                    //try {
                        //String text = getHexString(txValue);
                        //displayData("Received : " + text);
                        //displayData("After Process");
                        //displayData(processResponse(txValue));
                    //} catch (Exception e) {
                      //  addLogs("Error in receiving data -- " + e.getClass().getName() + " -- " + e.getMessage());
                   // }
                    try {
                        // Extract EPC number from Excel file
                        displayData(processResponse(txValue));
                        String receivedData = processResponse(txValue);
                        displayData(receivedData);
                        writeToExcel(context, receivedData);
                        Integer rowIndex=findRowNumberByEpcNumber(receivedData);
                        displayData(rowIndex.toString());
                        String epcNumber = extractEpcNumberFromExcel(rowIndex);
                        displayData(epcNumber);
                        String machineName=extractMachineNameFromExcel(rowIndex);
                        String cellName=extractCellNameFromExcel(rowIndex);
                        String operation=extractOperationFromExcel(rowIndex);
                        String assetCode=extractAssetCodeFromExcel(rowIndex);
                        // Convert received data to a string
                        // Check if the received data contains the EPC number
                        if ((receivedData.equals(epcNumber))){
                            // Display "Hello, World!" in the user interface
                            displayData(machineName);
                            displayData(cellName);
                            displayData(operation);
                            displayData(assetCode);
                        }
                        else {
                            displayData(epcNumber);
                            addLogs("Not matching");
                            displayHelloWorld();
                        }
                    } catch (Exception e) {
                        addLogs("Error in receiving data -- " + e.getClass().getName() + " -- " + e.getMessage());
                    }
                });
            } else if(BleCommService.BATTERY_LEVEL_AVAILABLE.equals(action)) {
                final int batteryLevel = intent.getIntExtra(BleCommService.BATTERY_DATA, 0);
                runOnUiThread(() -> batteryLevelDrawable.setLevel(batteryLevel*100));
            }
        }
    };

    //intent filter to access response type from service
    private static IntentFilter myGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleCommService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BleCommService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BleCommService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BleCommService.ACTION_SERVICE_NOT_FOUND);
        intentFilter.addAction(BleCommService.ACTION_CHARACTERISTIC_NOT_FOUND);
        intentFilter.addAction(BleCommService.ACTION_DATA_WRITE_SUC);
        intentFilter.addAction(BleCommService.ACTION_DATA_WRITE_FAIL);
        intentFilter.addAction(BleCommService.ACTION_CHAR_EMPTY);
        intentFilter.addAction(BleCommService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BleCommService.BATTERY_LEVEL_AVAILABLE);
        return intentFilter;
    }

    //other activity lifecycle events
    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, myGattUpdateIntentFilter());
        if (mBleCommService != null) {
            mBleCommService.connect(mDeviceAddress);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver); //temporarily
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
		unregisterReceiver(mGattUpdateReceiver); //added this line to prevent crashing app after user presses "back" button.
        unbindService(mServiceConnection);
        mBleCommService = null;
    }

    //utility functions
    //function with handler to scan ble devices
    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            handler.postDelayed(bleRunnable, SCAN_PERIOD);

            mScanning = true;
            //bluetoothAdapter.startLeScan(leScanCallback);
            try { bleScanner.startScan(leScanCallback); } catch(SecurityException sece) { Toast.makeText(MainActivity.this, "Permission to scan BLE Device is not provided -- " +sece.getMessage(), Toast.LENGTH_LONG).show(); }
            runOnUiThread(() -> {
                //generate dialog with available devices
                deviceRelatedDialog = generateDeviceListDialog(false, false);
                deviceRelatedDialog.setCanceledOnTouchOutside(false);
                deviceRelatedDialog.show();
            });

        } else {
            mScanning = false;
            //bluetoothAdapter.stopLeScan(leScanCallback);
            try { bleScanner.stopScan(leScanCallback); } catch(SecurityException sece) { Toast.makeText(MainActivity.this, "Permission to  stop scan BLE Device is not provided -- " +sece.getMessage(), Toast.LENGTH_LONG).show(); }
        }
    }

    //function to generate dialog with list
    private AlertDialog generateDeviceListDialog(boolean isComplete, boolean isDevices) {
        LayoutInflater inflater = (LayoutInflater)getApplicationContext().getSystemService (Context.LAYOUT_INFLATER_SERVICE);
        final View dialogView = inflater.inflate(R.layout.layout_dialog_scan_device, null);
        final AlertDialog.Builder deviceListDialog = new AlertDialog.Builder(MainActivity.this);
        deviceListDialog.setView(dialogView);
        TextView statusDevice = dialogView.findViewById(R.id.message);
        //ListView listScanDevices = dialogView.findViewById(R.id.list_devices);
        ProgressBar progressBar = dialogView.findViewById(R.id.progress);
        LinearLayout listLayout = dialogView.findViewById(R.id.listPart);
        LinearLayout progressLayout = dialogView.findViewById(R.id.progressPart);
        statusDevice.setText(R.string.search_device);

        if(!isComplete) {
            listLayout.setVisibility(View.GONE);
            progressLayout.setVisibility(View.VISIBLE);
        }
        else {
            if(!isDevices) {
                listLayout.setVisibility(View.GONE);
                progressLayout.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.GONE);
                statusDevice.setText(R.string.str_msg_mode_no_support);
            }
            if(isDevices) {
                deviceListDialog.setAdapter(leDeviceAdapter, (dialogInterface, i) -> {
                    //code or function to detect clicked device name and connect device.
                    String myDeviceAddress = leDeviceAdapter.getItem(i).substring(leDeviceAdapter.getItem(i).indexOf("--")+3).trim();
                    try {
                        msgBuffer.delete(0, msgBuffer.length());
                        msgBuffer.append(getResources().getString(R.string.text_connecting));
                        msgText.setText(msgBuffer);
                        mDeviceAddress = myDeviceAddress;
                        mBleCommService.connect(mDeviceAddress);
                    }
                    catch(Exception cex) {
                        msgBuffer.append("\r\n").append("Error in connecting Device !").append("\r\n").append(cex.getMessage());
                    }
                });
                progressLayout.setVisibility(View.GONE);
                listLayout.setVisibility(View.VISIBLE);
            }
        }

        deviceListDialog.setTitle("Select Device");

        deviceListDialog.setNegativeButton("Cancel", (dialogInterface, i) -> {
            scanLeDevice(false);
            leDeviceAdapter.clear();
            handler.removeCallbacks(bleRunnable);
            leDeviceList.clear();
            msgBuffer.append("\r\n").append("You have not selected any device, select device to operate");
            msgText.setText(msgBuffer);
            dialogInterface.dismiss();
        });

        deviceListDialog.setOnKeyListener((dialogInterface, keyCode, event) -> {
            if(keyCode == KeyEvent.KEYCODE_BACK) {
                scanLeDevice(false);
                leDeviceAdapter.clear();
                leDeviceList.clear();
                handler.removeCallbacks(bleRunnable);
                msgBuffer.append("\r\n").append("You have not selected any device, select device to operate");
                msgText.setText(msgBuffer);
                dialogInterface.dismiss();
                return true;
            }
            return false;
        });
        return deviceListDialog.create();
    }

    //function to show dialogue, if GPS is off
    private void showAlertMessageNoLocation() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Your Location seems to be disabled, do you want to enable it? This app requires to access location to scan near by BLE Devices.")
                .setCancelable(false)
                .setPositiveButton("Yes", (dialog, id) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                .setNegativeButton("No", (dialog, id) -> {
                    dialog.cancel();
                    Toast.makeText(MainActivity.this, "App Can't run without enabling location", Toast.LENGTH_LONG).show();
                    finish();
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    //function to display gatt services
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        //code to display gatt services goes here
        displayData("Ble Device Connected...");
    }

    //function to enable all disabled controls
    private void enableDisableUi(boolean enable) {
        bWrite.setEnabled(enable);
        tCommand.setEnabled(enable);
        bClear.setEnabled(enable);
        bInventory.setEnabled(enable);
    }

    //function to display data
    private void displayData(String data) {
        if (data != null) {
            addLogs("Data -- " + data);
        }
    }

    //function to convert byte array to hex string
    private String getHexString(byte[] values) {
        StringBuilder sb = new StringBuilder();
        for (byte b : values) {
            String st = String.format("%02X", b);
            sb.append(st);
        }
        return sb.toString();
    }

    //function to prepend logs - so, latest logs can appear at start of logs area
    private void addLogs(String log) {
        msgBuffer.insert(0, log + "\r\n\r\n");
        msgText.setText(msgBuffer);
    }

    //function to convert hex string to byte array
    private byte[] getHexByte(String hex) {
        byte[] val = new byte[hex.length()/2];
        int index = 0;
        int mainVal = 0;
        for(int i = 0; i < val.length; i++) {
            index = i * 2;
            mainVal = Integer.parseInt(hex.substring(index, index+2), 16);
            val[i] = (byte)mainVal;
        }
        return val;
    }

    //function to calculate bcc, and adding length to data
    /*private byte getBcc(byte[] buffer) {
        byte myBcc; // temporary variable
        myBcc = buffer[0]; //copy argument value in temporary variable
        int length = buffer.length-1;
        int pos = 1;
        length--;
        while(length >= 0) // loop for each character
        {
            myBcc ^= buffer[pos];
            pos++;
            length--;
        }
        return myBcc;
    }*/
    //function to calculate crc
    private static byte[] addCrc(byte[] txBuffer, int length) {
        short crc = (short) 0xFFFF;
        byte bytes, bits;

        for (bytes = 0; bytes < length; bytes++) {
            crc = (short) (crc ^ (short)(((int)txBuffer[bytes] & 0x000000FF) << 8));
            for (bits = 0; bits < 8; bits++) {
                if (((int)crc & 0x00008000) == 0x00008000) {
                    crc = (short) (((int)crc << 1) ^ 0x00001021);
                } else {
                    crc = (short) (crc << 1);
                }
            }
        }
        //crc = (short) ~crc;
        return new byte[] { (byte) (crc >>> 8), (byte) (crc) };
    }
    //method to calculate and add crc to bytes of command to be sent
    private byte[] AddCRC(byte[] data) {
        int crc = 0xFFFF;
        byte bytes, bits;
        for (bytes = 0; bytes < data[0]; bytes++) { //changed here condtion from '<=' to '<',and 'data[1]' to 'data[0]' as our packet has one element less
            crc = crc ^ (data[bytes] & 0xFF);
            for (bits = 0; bits < 8; bits++) {
                if ((crc & 0x8000) == 0x8000)
                    crc = (crc << 1) ^ 0x1021;
                else
                    crc = (crc << 1);
            }
        }
        crc = (~crc);
        return new byte[] { (byte) (crc >> 8), (byte) (crc) };
    }

    //function to send command bytes to ble reader
    private void sendCommand(byte[] commandBytes) {
        try {
            if (!mBleCommService.writeRXData(commandBytes))
                addLogs("Data Write Failed...");
            addLogs("Data Sent -- " + getHexString(commandBytes));
        }
        catch(Exception enex) {
            addLogs("Error occurred -- "+enex.getClass().getName()+" -- "+enex.getMessage());
        }
    }

    //common function to send data/write data to server or to client
    private void sendData(String data) {
        byte[] value;
        try {
            //send data to service
            value = getHexByte(data);
            //code added to append bcc
            /*byte[] finalValue = new byte[value.length+1];
            byte bcc = getBcc(value);
            System.arraycopy(value, 0, finalValue, 0, value.length);
            finalValue[finalValue.length-1] = bcc;*/
            //end added
            //code added to append crc
            byte[] finalValue = new byte[value.length+2];
            //byte[] crc = AddCRC(value, value.length);
            byte[] crc = AddCRC(value);
            System.arraycopy(value, 0, finalValue, 0, value.length);
            finalValue[finalValue.length-2] = crc[0];
            finalValue[finalValue.length-1] = crc[1];
            //end added
            sendCommand(finalValue);
        }
        catch(Exception enex) {
            Toast.makeText(MainActivity.this, "Format Not Supported !!", Toast.LENGTH_LONG).show();
        }
    }

    //function to process command response
    /*public String processResponse(byte[] data) {
        byte len = data[0];
        if(data.length == len+2) { //all ok
            byte[] epc = new byte[len];
            System.arraycopy(data, 2, epc, 0, len); // from index 8 the epc is extrated
            return "EPC: " + getHexString(epc);
        }
        else {
            return getHexString(data);
        }
    }*/

  public String processResponse(byte[] data) {
    byte len = data[0];
    if (data.length == len + 2) { // All data received
      byte[] slicedData = new byte[12]; // Subtracting 7 from len to slice 8 bytes from the front and 1 byte from the end
      System.arraycopy(data, 8, slicedData, 0, 12); // Copying 8 bytes from index 1 to index 8 of the slicedData array
      return getHexString(slicedData);
    } else {
      return getHexString(data); // Return the original data array if length is incorrect
    }
  }


  // 30-4-2024 process response from gpt
  /*
  public String processResponse(byte[] data) {
    byte len = data[0];
    if (data.length == len + 2) { // All data received
      byte[] slicedData = new byte[12]; // Subtracting 7 from len to slice 8 bytes from the front and 1 byte from the end
      System.arraycopy(data, 8, slicedData, 0, 12); // Copying 8 bytes from index 1 to index 8 of the slicedData array

      // Read and parse CSV file
      Map<String, String> epcTagMap = parseCSV("assests.csv");
      displayData(epcTagMap.get("E2004704C2206422E2700114"));
      // Convert sliced data to hexadecimal string
      String hexSlicedData = getHexString(slicedData);

      // Find tag ID corresponding to sliced data
      String tagId = findTagId(hexSlicedData, epcTagMap);

      // If tag ID found, display tag ID and tag name
      if (tagId != null) {
        displayData("After Process1");
        String tagName = epcTagMap.get(tagId);
        return displayData(tagId, tagName);
      } else {
        return "Tag ID not found";
      }
    } else {
      return getHexString(data); // Return the original data array if length is incorrect
    }
  }

  // Method to parse CSV file and store tag IDs and tag names in a map
  private Map<String, String> parseCSV(String filename) {
    Map<String, String> epcTagMap = new HashMap<>();
    try (InputStream inputStream = getClass().getResourceAsStream(filename);
         BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
      String line="hello this is first";
      System.out.println("CSV line: " + line);
      while ((line = br.readLine()) != null) {
        System.out.println("CSV line: " + line);
        String[] parts = line.split(",");
        if (parts.length >= 2) {
          epcTagMap.put(parts[0], parts[1]); // Assuming tag ID is at index 0 and tag name is at index 1
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return epcTagMap;
  }

  // Method to find tag ID corresponding to sliced data
  private String findTagId(String slicedData, Map<String, String> epcTagMap) {
    return epcTagMap.get(slicedData);
  }

  // Example method to display tag ID and tag name
  private String displayData(String tagId, String tagName) {
    return "Tag ID: " + tagId + ", Tag Name: " + tagName;
  }

*/
  //function to check existence, and create directory and file, if needed
    public boolean chkFileDir(boolean needToCreate, String fileName) {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            if(Build.VERSION.SDK_INT >= 19) {
                folderPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath();
                folderPath = folderPath.substring(0, folderPath.lastIndexOf('/'));
                folderPath = folderPath + "/RRHFOEM09";
            }
            else {
                folderPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/RRHFOEM09";
            }
            File dirFile = new File(folderPath);
            try {
                if (!(dirFile.exists() && dirFile.isDirectory())) {
                    if(!dirFile.mkdir()) {
                        Toast.makeText(MainActivity.this, "Failed to create Folder for RRHFOEM09", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                }
                File myFile = new File(folderPath+"/"+fileName);
                if(!myFile.exists()) {
                    if(needToCreate) {
                        //myFile = new File(folderPath, fileName);
                        if(!myFile.createNewFile())
                            Toast.makeText(MainActivity.this, "Failed to create File in RRHFOEM09", Toast.LENGTH_SHORT).show();
                        return myFile.exists();
                    }
                    else {
                        return false;
                    }
                }
                else {
                    return true;
                }
            }
            catch(Exception fe) {
                Toast.makeText(MainActivity.this, "Error Occurred in File Operation..." + fe.getMessage(), Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        else {
            Toast.makeText(MainActivity.this, "Storage Not available !!", Toast.LENGTH_LONG).show();
            return false;
        }
    }

    //function to write data in log file
    private boolean writeLogToFile(String fileName, String data) {
        if(chkFileDir(true, fileName)) {
            try {
                BufferedWriter writer = new BufferedWriter(new FileWriter(folderPath+"/"+fileName, true));
                writer.write(data+"\r\n");
                writer.close();
                return true;
            }
            catch(Exception fe) {
                Toast.makeText(MainActivity.this, "Error Occurred in File Write Operation..." + fe.getMessage(), Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        else {
            return false;
        }
    }

    //function to get current date time string
    private String getCurDateTime() {
        Date dNow = new Date( );
        SimpleDateFormat ft = new SimpleDateFormat("dd/MM/yyyy, hh:mm a", Locale.US);
        return ft.format(dNow);
    }
}
