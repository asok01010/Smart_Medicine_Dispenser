package com.smartmedicine.dispenser;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BluetoothManager {
    private static final String TAG = "BluetoothManager";

    // HC-05 always uses this UUID
    private static final UUID HC05_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static BluetoothManager instance;
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private BluetoothDevice connectedDevice;
    private OutputStream outputStream;
    private InputStream inputStream;
    private boolean isConnected = false;
    private boolean isConnecting = false;
    private BluetoothConnectionListener connectionListener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Buffer for incoming data
    private final byte[] buffer = new byte[1024];
    private final StringBuilder dataBuffer = new StringBuilder();

    public interface BluetoothConnectionListener {
        void onConnectionStatusChanged(boolean connected, String deviceName);
        void onDataReceived(String data);
        void onError(String error);
    }

    private BluetoothManager() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public static synchronized BluetoothManager getInstance() {
        if (instance == null) {
            instance = new BluetoothManager();
        }
        return instance;
    }

    public void setConnectionListener(BluetoothConnectionListener listener) {
        this.connectionListener = listener;
    }

    public boolean isBluetoothSupported() {
        return bluetoothAdapter != null;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public Set<BluetoothDevice> getPairedDevices() {
        if (bluetoothAdapter != null) {
            return bluetoothAdapter.getBondedDevices();
        }
        return null;
    }

    @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN})
    public void connectToDevice(BluetoothDevice device) {
        if (isConnecting) {
            notifyError("Already attempting to connect. Please wait.");
            return;
        }

        isConnecting = true;

        executor.execute(() -> {
            try {
                // Close any existing connection
                disconnect();

                Log.d(TAG, "Connecting to " + device.getName() + " - " + device.getAddress());
                notifyToUI("Connecting to " + device.getName() + "...");

                // Create a socket connection to the HC-05
                bluetoothSocket = device.createRfcommSocketToServiceRecord(HC05_UUID);

                // Cancel discovery as it slows down the connection
                if (bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }

                // Connect to the device - this will block until connection succeeds or fails
                bluetoothSocket.connect();

                // Get the input and output streams
                outputStream = bluetoothSocket.getOutputStream();
                inputStream = bluetoothSocket.getInputStream();
                connectedDevice = device;
                isConnected = true;
                isConnecting = false;

                // Notify connection success
                notifyConnectionStatus(true, device.getName());

                // Start listening for incoming data
                startListening();

                // Send a test command to verify connection
                sendCommand("HELLO");

            } catch (IOException e) {
                Log.e(TAG, "Connection failed: " + e.getMessage(), e);
                isConnected = false;
                isConnecting = false;
                notifyError("Connection failed: " + e.getMessage());

                // Try to close the socket
                try {
                    if (bluetoothSocket != null) {
                        bluetoothSocket.close();
                    }
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
            }
        });
    }

    public void disconnect() {
        executor.execute(() -> {
            try {
                isConnected = false;

                if (outputStream != null) {
                    outputStream.close();
                    outputStream = null;
                }

                if (inputStream != null) {
                    inputStream.close();
                    inputStream = null;
                }

                if (bluetoothSocket != null) {
                    bluetoothSocket.close();
                    bluetoothSocket = null;
                }

                connectedDevice = null;

                notifyConnectionStatus(false, "");

            } catch (IOException e) {
                Log.e(TAG, "Error disconnecting: " + e.getMessage(), e);
                notifyError("Error disconnecting: " + e.getMessage());
            }
        });
    }

    public boolean isConnected() {
        return isConnected && bluetoothSocket != null && bluetoothSocket.isConnected();
    }

    public BluetoothDevice getConnectedDevice() {
        return connectedDevice;
    }

    public void sendCommand(String command) {
        if (!isConnected || outputStream == null) {
            notifyError("Not connected to a device");
            return;
        }

        executor.execute(() -> {
            try {
                // Add newline as terminator for Arduino to process
                String fullCommand = command + "\n";
                byte[] bytes = fullCommand.getBytes(StandardCharsets.UTF_8);
                outputStream.write(bytes);
                outputStream.flush();

                Log.d(TAG, "Sent command: " + command);
                notifyToUI("Sent: " + command);

            } catch (IOException e) {
                Log.e(TAG, "Error sending command: " + e.getMessage(), e);
                notifyError("Error sending command: " + e.getMessage());

                // Connection might be lost, update status
                isConnected = false;
                notifyConnectionStatus(false, "");
            }
        });
    }

    private void startListening() {
        executor.execute(() -> {
            int bytes;

            // Keep listening to the InputStream until an exception occurs
            while (isConnected) {
                try {
                    // Read from the InputStream
                    bytes = inputStream.read(buffer);

                    if (bytes > 0) {
                        // Convert received bytes to string
                        String received = new String(buffer, 0, bytes, StandardCharsets.UTF_8);
                        processReceivedData(received);
                    }

                } catch (IOException e) {
                    Log.e(TAG, "Input stream disconnected", e);
                    isConnected = false;
                    notifyConnectionStatus(false, "");
                    notifyError("Connection lost: " + e.getMessage());
                    break;
                }
            }
        });
    }

    private void processReceivedData(String data) {
        // Append data to buffer
        dataBuffer.append(data);

        // Process complete messages (terminated by newline)
        int newlineIndex;
        while ((newlineIndex = dataBuffer.indexOf("\n")) != -1) {
            // Extract the complete message
            String message = dataBuffer.substring(0, newlineIndex).trim();

            // Remove the processed message from buffer
            dataBuffer.delete(0, newlineIndex + 1);

            // Process the message if it's not empty
            if (!message.isEmpty()) {
                Log.d(TAG, "Received message: " + message);
                notifyDataReceived(message);
            }
        }
    }

    // Methods for specific HC-05 commands for Arduino
    public void requestMedicineStatus() {
        sendCommand("GET_STATUS");
    }

    public void requestMedicineHistory() {
        sendCommand("GET_HISTORY");
    }

    public void checkMedicineAvailability(String medicineName) {
        sendCommand("CHECK:" + medicineName);
    }

    public void dispenseMedicine(String medicineName, int quantity) {
        sendCommand("DISPENSE:" + medicineName + ":" + quantity);
    }

    public void setAlarm(String medicineName, String time, int quantity) {
        sendCommand("ALARM:" + medicineName + ":" + time + ":" + quantity);
    }

    public void syncAllAlarms(List<Medicine> medicines) {
        if (medicines == null || medicines.isEmpty()) {
            sendCommand("CLEAR_ALARMS");
            return;
        }

        // First clear existing alarms on Arduino
        sendCommand("CLEAR_ALARMS");

        // Then send each medicine and its alarms
        for (Medicine medicine : medicines) {
            String medicineName = medicine.getName();
            int quantity = medicine.getQuantity();

            for (String time : medicine.getAlarmTimes()) {
                // Format: ALARM:MedicineName:Time:Quantity
                sendCommand("ALARM:" + medicineName + ":" + time + ":" + quantity);

                // Add a small delay to prevent buffer overflow on Arduino
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        // Finalize sync
        sendCommand("SYNC_COMPLETE");
    }

    // Helper methods to notify the UI thread
    private void notifyConnectionStatus(boolean connected, String deviceName) {
        mainHandler.post(() -> {
            if (connectionListener != null) {
                connectionListener.onConnectionStatusChanged(connected, deviceName);
            }
        });
    }

    private void notifyDataReceived(String data) {
        mainHandler.post(() -> {
            if (connectionListener != null) {
                connectionListener.onDataReceived(data);
            }
        });
    }

    private void notifyError(String error) {
        mainHandler.post(() -> {
            if (connectionListener != null) {
                connectionListener.onError(error);
            }
        });
    }

    private void notifyToUI(String message) {
        Log.d(TAG, message);
    }
}