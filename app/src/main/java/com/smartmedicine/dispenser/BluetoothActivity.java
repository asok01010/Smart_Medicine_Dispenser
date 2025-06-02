package com.smartmedicine.dispenser;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

public class BluetoothActivity extends AppCompatActivity {

    private static final String TAG = "BluetoothActivity";
    private static final int BLUETOOTH_PERMISSION_REQUEST = 1001;

    // UI Components
    private TextView connectionStatus;
    private Button scanBtn;
    private Button disconnectBtn;
    private Button syncAlarmsBtn;
    private Button requestStatusBtn;
    private Button requestHistoryBtn;
    private LinearLayout deviceList;
    private TextView emptyDevicesText;
    private TextView logTextView;
    private ScrollView logScrollView;

    // Managers
    private BluetoothManager bluetoothManager;
    private MedicineManager medicineManager;

    // Connection listener
    private BluetoothManager.BluetoothConnectionListener connectionListener = new BluetoothManager.BluetoothConnectionListener() {
        @Override
        public void onConnectionStatusChanged(boolean connected, String deviceName) {
            runOnUiThread(() -> {
                try {
                    updateConnectionStatus(connected, deviceName);
                } catch (Exception e) {
                    Log.e(TAG, "Error updating connection status: " + e.getMessage(), e);
                }
            });
        }

        @Override
        public void onDataReceived(String data) {
            runOnUiThread(() -> {
                try {
                    addToLog("Received: " + data);
                    processReceivedData(data);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing received data: " + e.getMessage(), e);
                }
            });
        }

        @Override
        public void onError(String error) {
            runOnUiThread(() -> {
                try {
                    addToLog("Error: " + error);
                    showToast("Error: " + error);
                } catch (Exception e) {
                    Log.e(TAG, "Error showing error message: " + e.getMessage(), e);
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setContentView(R.layout.activity_bluetooth);

            initializeViews();
            setupToolbar();
            setupClickListeners();
            initializeManagers();

            addToLog("Bluetooth Activity started");

        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: " + e.getMessage(), e);
            showToast("Error initializing Bluetooth Activity: " + e.getMessage());
            finish();
        }
    }

    private void initializeViews() {
        try {
            connectionStatus = findViewById(R.id.connection_status);
            scanBtn = findViewById(R.id.scan_btn);
            disconnectBtn = findViewById(R.id.disconnect_btn);
            syncAlarmsBtn = findViewById(R.id.sync_alarms_btn);
            requestStatusBtn = findViewById(R.id.request_status_btn);
            requestHistoryBtn = findViewById(R.id.request_history_btn);
            deviceList = findViewById(R.id.device_list);
            emptyDevicesText = findViewById(R.id.empty_devices_text);
            logTextView = findViewById(R.id.log_text);
            logScrollView = findViewById(R.id.log_scroll);

            // Verify all views are found
            if (connectionStatus == null || scanBtn == null || disconnectBtn == null ||
                    syncAlarmsBtn == null || requestStatusBtn == null || requestHistoryBtn == null ||
                    deviceList == null || emptyDevicesText == null ||
                    logTextView == null || logScrollView == null) {
                throw new RuntimeException("One or more views not found in layout");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error initializing views: " + e.getMessage(), e);
            throw e;
        }
    }

    private void setupToolbar() {
        try {
            Toolbar toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) {
                setSupportActionBar(toolbar);
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                    getSupportActionBar().setTitle("Bluetooth");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up toolbar: " + e.getMessage(), e);
        }
    }

    private void setupClickListeners() {
        try {
            scanBtn.setOnClickListener(v -> {
                try {
                    if (checkBluetoothPermissions()) {
                        scanForDevices();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in scan button click: " + e.getMessage(), e);
                    showToast("Error scanning: " + e.getMessage());
                }
            });

            disconnectBtn.setOnClickListener(v -> {
                try {
                    disconnectDevice();
                } catch (Exception e) {
                    Log.e(TAG, "Error in disconnect button click: " + e.getMessage(), e);
                    showToast("Error disconnecting: " + e.getMessage());
                }
            });

            syncAlarmsBtn.setOnClickListener(v -> {
                try {
                    syncAlarms();
                } catch (Exception e) {
                    Log.e(TAG, "Error in sync alarms button click: " + e.getMessage(), e);
                    showToast("Error syncing alarms: " + e.getMessage());
                }
            });

            requestStatusBtn.setOnClickListener(v -> {
                try {
                    requestStatus();
                } catch (Exception e) {
                    Log.e(TAG, "Error in request status button click: " + e.getMessage(), e);
                    showToast("Error requesting status: " + e.getMessage());
                }
            });

            requestHistoryBtn.setOnClickListener(v -> {
                try {
                    requestHistory();
                } catch (Exception e) {
                    Log.e(TAG, "Error in request history button click: " + e.getMessage(), e);
                    showToast("Error requesting history: " + e.getMessage());
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error setting up click listeners: " + e.getMessage(), e);
            throw e;
        }
    }

    private void initializeManagers() {
        try {
            bluetoothManager = BluetoothManager.getInstance();
            bluetoothManager.setConnectionListener(connectionListener);

            medicineManager = MedicineManager.getInstance(this);

            updateConnectionStatus(false, "");

        } catch (Exception e) {
            Log.e(TAG, "Error initializing managers: " + e.getMessage(), e);
            throw e;
        }
    }

    private boolean checkBluetoothPermissions() {
        try {
            if (!bluetoothManager.isBluetoothSupported()) {
                showToast("Bluetooth not supported on this device");
                return false;
            }

            if (!bluetoothManager.isBluetoothEnabled()) {
                showToast("Please enable Bluetooth in settings");
                return false;
            }

            // Check permissions based on Android version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                String[] permissions = {
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION
                };

                for (String permission : permissions) {
                    if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this, permissions, BLUETOOTH_PERMISSION_REQUEST);
                        return false;
                    }
                }
            } else {
                String[] permissions = {
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.ACCESS_FINE_LOCATION
                };

                for (String permission : permissions) {
                    if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this, permissions, BLUETOOTH_PERMISSION_REQUEST);
                        return false;
                    }
                }
            }

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error checking Bluetooth permissions: " + e.getMessage(), e);
            showToast("Error checking permissions: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == BLUETOOTH_PERMISSION_REQUEST) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                showToast("Permissions granted! You can now scan for devices.");
                scanForDevices();
            } else {
                showToast("Bluetooth permissions are required to use this feature.");
            }
        }
    }

    private void scanForDevices() {
        try {
            addToLog("Scanning for paired devices...");

            // Clear device list
            deviceList.removeAllViews();
            deviceList.addView(emptyDevicesText);
            emptyDevicesText.setText("Scanning...");

            // Get paired devices
            Set<BluetoothDevice> pairedDevices = bluetoothManager.getPairedDevices();

            if (pairedDevices == null || pairedDevices.isEmpty()) {
                emptyDevicesText.setText("No paired devices found. Please pair your HC-05 device in Android Bluetooth settings first.");
                addToLog("No paired devices found");
                return;
            }

            // Remove empty text and add devices
            deviceList.removeView(emptyDevicesText);

            for (BluetoothDevice device : pairedDevices) {
                addDeviceToList(device);
            }

            addToLog("Found " + pairedDevices.size() + " paired device(s)");

        } catch (Exception e) {
            Log.e(TAG, "Error scanning for devices: " + e.getMessage(), e);
            showToast("Error scanning: " + e.getMessage());
            emptyDevicesText.setText("Error scanning for devices");
        }
    }

    private void addDeviceToList(BluetoothDevice device) {
        try {
            // Create device item layout
            LinearLayout deviceItem = new LinearLayout(this);
            deviceItem.setOrientation(LinearLayout.HORIZONTAL);
            deviceItem.setPadding(0, 16, 0, 16);

            // Device info layout
            LinearLayout deviceInfoLayout = new LinearLayout(this);
            deviceInfoLayout.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            deviceInfoLayout.setLayoutParams(infoParams);

            // Device name
            TextView deviceName = new TextView(this);
            String name = "Unknown Device";
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        name = device.getName() != null ? device.getName() : "Unknown Device";
                    }
                } else {
                    name = device.getName() != null ? device.getName() : "Unknown Device";
                }
            } catch (SecurityException e) {
                Log.w(TAG, "Permission not granted for device name");
            }

            deviceName.setText(name);
            deviceName.setTextSize(16);
            deviceName.setTextColor(getResources().getColor(R.color.text_primary));

            // Device address
            TextView deviceAddress = new TextView(this);
            deviceAddress.setText(device.getAddress());
            deviceAddress.setTextSize(12);
            deviceAddress.setTextColor(getResources().getColor(R.color.text_secondary));

            deviceInfoLayout.addView(deviceName);
            deviceInfoLayout.addView(deviceAddress);

            // Connect button
            Button connectBtn = new Button(this);
            connectBtn.setText("CONNECT");
            connectBtn.setBackgroundResource(R.drawable.button_primary_bg);
            connectBtn.setTextColor(getResources().getColor(android.R.color.white));
            connectBtn.setOnClickListener(v -> connectToDevice(device));

            deviceItem.addView(deviceInfoLayout);
            deviceItem.addView(connectBtn);

            deviceList.addView(deviceItem);

            // Add divider
            View divider = new View(this);
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
            divider.setLayoutParams(dividerParams);
            divider.setBackgroundColor(getResources().getColor(R.color.divider_color));
            deviceList.addView(divider);

        } catch (Exception e) {
            Log.e(TAG, "Error adding device to list: " + e.getMessage(), e);
        }
    }

    private void connectToDevice(BluetoothDevice device) {
        try {
            String deviceName = "Unknown Device";
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        deviceName = device.getName() != null ? device.getName() : "Unknown Device";
                    }
                } else {
                    deviceName = device.getName() != null ? device.getName() : "Unknown Device";
                }
            } catch (SecurityException e) {
                Log.w(TAG, "Permission not granted for device name");
            }

            addToLog("Attempting to connect to " + deviceName + " (" + device.getAddress() + ")...");
            showToast("Connecting to " + deviceName + "...");

            bluetoothManager.connectToDevice(device);

        } catch (Exception e) {
            Log.e(TAG, "Error connecting to device: " + e.getMessage(), e);
            showToast("Error connecting: " + e.getMessage());
        }
    }

    private void disconnectDevice() {
        try {
            addToLog("Disconnecting...");
            bluetoothManager.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Error disconnecting: " + e.getMessage(), e);
            showToast("Error disconnecting: " + e.getMessage());
        }
    }

    private void syncAlarms() {
        try {
            if (!bluetoothManager.isConnected()) {
                showToast("Not connected to device");
                addToLog("Sync failed: Not connected to device");
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle("Sync Alarms")
                    .setMessage("Do you want to sync all alarms to the Arduino device?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        try {
                            addToLog("Starting alarm synchronization...");
                            showToast("Syncing alarms...");
                            bluetoothManager.syncAllAlarms(medicineManager.getAllMedicines());
                            addToLog("Sync command sent to device");
                        } catch (Exception e) {
                            Log.e(TAG, "Error syncing alarms: " + e.getMessage(), e);
                            showToast("Error syncing: " + e.getMessage());
                            addToLog("Sync failed: " + e.getMessage());
                        }
                    })
                    .setNegativeButton("No", (dialog, which) -> {
                        addToLog("Alarm sync cancelled by user");
                    })
                    .show();

        } catch (Exception e) {
            Log.e(TAG, "Error in syncAlarms: " + e.getMessage(), e);
            showToast("Error: " + e.getMessage());
            addToLog("Sync error: " + e.getMessage());
        }
    }

    private void requestStatus() {
        try {
            if (bluetoothManager.isConnected()) {
                addToLog("Requesting medicine status from device...");
                bluetoothManager.requestMedicineStatus();
                showToast("Status request sent");
            } else {
                showToast("Not connected to device");
                addToLog("Status request failed: Not connected");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error requesting status: " + e.getMessage(), e);
            showToast("Error: " + e.getMessage());
            addToLog("Status request error: " + e.getMessage());
        }
    }

    private void requestHistory() {
        try {
            if (bluetoothManager.isConnected()) {
                addToLog("Requesting medicine history from device...");
                bluetoothManager.requestMedicineHistory();
                showToast("History request sent");
            } else {
                showToast("Not connected to device");
                addToLog("History request failed: Not connected");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error requesting history: " + e.getMessage(), e);
            showToast("Error: " + e.getMessage());
            addToLog("History request error: " + e.getMessage());
        }
    }

    private void updateConnectionStatus(boolean connected, String deviceName) {
        try {
            if (connected) {
                String displayName = deviceName.isEmpty() ? "Device" : deviceName;
                connectionStatus.setText("Connected to " + displayName);
                connectionStatus.setBackgroundResource(R.drawable.status_connected_bg);
                disconnectBtn.setEnabled(true);
                syncAlarmsBtn.setEnabled(true);
                requestStatusBtn.setEnabled(true);
                requestHistoryBtn.setEnabled(true);
                showToast("Connected to " + displayName);
                addToLog("Successfully connected to " + displayName);
            } else {
                connectionStatus.setText("Disconnected");
                connectionStatus.setBackgroundResource(R.drawable.status_disconnected_bg);
                disconnectBtn.setEnabled(false);
                syncAlarmsBtn.setEnabled(false);
                requestStatusBtn.setEnabled(false);
                requestHistoryBtn.setEnabled(false);
                if (!deviceName.isEmpty()) {
                    showToast("Disconnected");
                    addToLog("Disconnected from device");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating connection status: " + e.getMessage(), e);
        }
    }

    private void addToLog(String message) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            String timestamp = sdf.format(new Date());
            String logEntry = timestamp + " - " + message + "\n";

            runOnUiThread(() -> {
                try {
                    if (logTextView != null) {
                        logTextView.append(logEntry);

                        // Scroll to bottom
                        if (logScrollView != null) {
                            logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error appending to log: " + e.getMessage(), e);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error adding to log: " + e.getMessage(), e);
        }
    }

    private void processReceivedData(String data) {
        try {
            if (data.startsWith("STATUS:")) {
                String[] parts = data.split(":");
                if (parts.length >= 3) {
                    String medicineName = parts[1];
                    String quantity = parts[2];
                    addToLog("Medicine status: " + medicineName + " - " + quantity + " pills left");
                    showToast("Status: " + medicineName + " - " + quantity + " pills");
                }
            } else if (data.startsWith("HISTORY:")) {
                String[] parts = data.split(":");
                if (parts.length >= 4) {
                    String medicineName = parts[1];
                    String time = parts[2];
                    String date = parts[3];
                    addToLog("Medicine taken: " + medicineName + " at " + time + " on " + date);

                    MedicineLogEntry entry = new MedicineLogEntry(medicineName, time, date);
                    medicineManager.addLogEntry(entry);
                    showToast("History: " + medicineName + " taken at " + time);
                }
            } else if (data.equals("SYNC_COMPLETE")) {
                addToLog("Alarm synchronization completed successfully");
                showToast("Alarms synchronized successfully");
            } else if (data.equals("ALARM_SET")) {
                addToLog("Alarm set on device");
                showToast("Alarm set successfully");
            } else {
                addToLog("Device response: " + data);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing received data: " + e.getMessage(), e);
            addToLog("Error processing data: " + e.getMessage());
        }
    }

    private void showToast(String message) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing toast: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        try {
            super.onDestroy();
            if (bluetoothManager != null) {
                bluetoothManager.setConnectionListener(null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy: " + e.getMessage(), e);
        }
    }
}
