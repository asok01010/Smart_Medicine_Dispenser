package com.smartmedicine.dispenser;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Set;

public class BluetoothActivity extends AppCompatActivity implements BluetoothManager.BluetoothConnectionListener {

    private static final int BLUETOOTH_PERMISSION_REQUEST = 1001;

    private TextView connectionStatus;
    private Button scanBtn;
    private Button disconnectBtn;
    private LinearLayout deviceList;
    private LinearLayout deviceInfo;
    private TextView emptyDevicesText;
    private TextView emptyDeviceInfoText;

    private BluetoothManager bluetoothManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        initViews();
        setupToolbar();
        setupButtons();

        bluetoothManager = BluetoothManager.getInstance();
        bluetoothManager.setConnectionListener(this);

        updateUI();
    }

    private void initViews() {
        connectionStatus = findViewById(R.id.connection_status);
        scanBtn = findViewById(R.id.scan_btn);
        disconnectBtn = findViewById(R.id.disconnect_btn);
        deviceList = findViewById(R.id.device_list);
        deviceInfo = findViewById(R.id.device_info);
        emptyDevicesText = findViewById(R.id.empty_devices_text);
        emptyDeviceInfoText = findViewById(R.id.empty_device_info_text);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Bluetooth");
        }
    }

    private void setupButtons() {
        scanBtn.setOnClickListener(v -> {
            if (checkAndRequestBluetoothPermissions()) {
                scanForDevices();
            }
        });
        disconnectBtn.setOnClickListener(v -> disconnectDevice());
    }

    private boolean checkAndRequestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ permissions
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
            // Android 11 and below permissions
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
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == BLUETOOTH_PERMISSION_REQUEST) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) {
                Toast.makeText(this, "Permissions granted! You can now scan for devices.", Toast.LENGTH_SHORT).show();
                scanForDevices();
            } else {
                Toast.makeText(this, "Bluetooth permissions are required to scan for devices.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void updateUI() {
        if (bluetoothManager.isConnected()) {
            BluetoothDevice device = bluetoothManager.getConnectedDevice();
            String deviceName = "Unknown";
            if (device != null && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                deviceName = device.getName();
            }
            connectionStatus.setText("Connected to " + deviceName);
            connectionStatus.setBackgroundResource(R.drawable.status_connected_bg);
            disconnectBtn.setEnabled(true);
            updateDeviceInfo(device);
        } else {
            connectionStatus.setText("Disconnected");
            connectionStatus.setBackgroundResource(R.drawable.status_disconnected_bg);
            disconnectBtn.setEnabled(false);
            clearDeviceInfo();
        }
    }

    private void scanForDevices() {
        if (!bluetoothManager.isBluetoothSupported()) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!bluetoothManager.isBluetoothEnabled()) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        // Clear existing device list
        deviceList.removeAllViews();
        emptyDevicesText.setVisibility(View.GONE);

        // Check permissions before accessing Bluetooth
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Bluetooth permissions not granted", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Set<BluetoothDevice> pairedDevices = bluetoothManager.getPairedDevices();

        if (pairedDevices == null || pairedDevices.isEmpty()) {
            emptyDevicesText.setText("No paired devices found");
            emptyDevicesText.setVisibility(View.VISIBLE);
            return;
        }

        // Display paired devices
        boolean foundHCDevice = false;
        for (BluetoothDevice device : pairedDevices) {
            String deviceName = null;
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                deviceName = device.getName();
            }

            if (deviceName != null && (deviceName.contains("HC-05") || deviceName.contains("HC-06"))) {
                addDeviceToList(device);
                foundHCDevice = true;
            }
        }

        if (!foundHCDevice) {
            emptyDevicesText.setText("No HC-05/HC-06 devices found");
            emptyDevicesText.setVisibility(View.VISIBLE);
        }
    }

    private void addDeviceToList(BluetoothDevice device) {
        LinearLayout deviceItem = new LinearLayout(this);
        deviceItem.setOrientation(LinearLayout.HORIZONTAL);
        deviceItem.setPadding(0, 16, 0, 16);

        LinearLayout deviceInfoLayout = new LinearLayout(this);
        deviceInfoLayout.setOrientation(LinearLayout.VERTICAL);
        deviceInfoLayout.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView deviceName = new TextView(this);
        String name = "Unknown Device";
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            name = device.getName() != null ? device.getName() : "Unknown Device";
        }
        deviceName.setText(name);
        deviceName.setTextSize(16);
        deviceName.setTextColor(getResources().getColor(R.color.text_primary));

        TextView deviceAddress = new TextView(this);
        deviceAddress.setText(device.getAddress());
        deviceAddress.setTextSize(12);
        deviceAddress.setTextColor(getResources().getColor(R.color.text_secondary));

        deviceInfoLayout.addView(deviceName);
        deviceInfoLayout.addView(deviceAddress);

        Button connectBtn = new Button(this);
        connectBtn.setText("Connect");
        connectBtn.setBackgroundResource(R.drawable.button_primary_bg);
        connectBtn.setTextColor(getResources().getColor(android.R.color.white));
        connectBtn.setOnClickListener(v -> connectToDevice(device));

        deviceItem.addView(deviceInfoLayout);
        deviceItem.addView(connectBtn);

        deviceList.addView(deviceItem);

        // Add divider
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
        deviceList.addView(divider);
    }

    private void connectToDevice(BluetoothDevice device) {
        String deviceName = "Unknown Device";
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            deviceName = device.getName() != null ? device.getName() : "Unknown Device";
        }
        Toast.makeText(this, "Connecting to " + deviceName + "...", Toast.LENGTH_SHORT).show();
        bluetoothManager.connectToDevice(device);
    }

    private void disconnectDevice() {
        bluetoothManager.disconnect();
    }

    private void updateDeviceInfo(BluetoothDevice device) {
        deviceInfo.removeAllViews();
        emptyDeviceInfoText.setVisibility(View.GONE);

        if (device != null) {
            TextView deviceNameView = new TextView(this);
            String deviceName = "Unknown Device";
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                deviceName = device.getName() != null ? device.getName() : "Unknown Device";
            }
            deviceNameView.setText("Device: " + deviceName);
            deviceNameView.setTextSize(16);
            deviceNameView.setTextColor(getResources().getColor(R.color.text_primary));

            TextView deviceAddress = new TextView(this);
            deviceAddress.setText("Address: " + device.getAddress());
            deviceAddress.setTextSize(14);
            deviceAddress.setTextColor(getResources().getColor(R.color.text_secondary));

            deviceInfo.addView(deviceNameView);
            deviceInfo.addView(deviceAddress);
        }
    }

    private void clearDeviceInfo() {
        deviceInfo.removeAllViews();
        emptyDeviceInfoText.setVisibility(View.VISIBLE);
    }

    // BluetoothConnectionListener implementation
    @Override
    public void onConnectionStatusChanged(boolean connected, String deviceName) {
        runOnUiThread(() -> {
            updateUI();
            if (connected) {
                Toast.makeText(this, "Connected to " + deviceName, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDataReceived(String data) {
        runOnUiThread(() -> {
            // Handle received data from HC-05
            Toast.makeText(this, "Received: " + data, Toast.LENGTH_SHORT).show();

            // Process specific commands
            if (data.startsWith("STATUS:")) {
                // Handle medicine status response
            } else if (data.startsWith("HISTORY:")) {
                // Handle medicine history response
            } else if (data.startsWith("AVAILABILITY:")) {
                // Handle medicine availability response
            }
        });
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            Toast.makeText(this, "Error: " + error, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothManager != null) {
            bluetoothManager.setConnectionListener(null);
        }
    }
}