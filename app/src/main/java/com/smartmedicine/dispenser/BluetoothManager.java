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
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BluetoothManager {
    private static final String TAG = "BluetoothManager";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static BluetoothManager instance;
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private BluetoothDevice connectedDevice;
    private OutputStream outputStream;
    private InputStream inputStream;
    private volatile boolean isConnected = false;
    private volatile boolean isConnecting = false;
    private volatile boolean shouldStopListening = false;
    private BluetoothConnectionListener connectionListener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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

    public Set<BluetoothDevice> getPairedDevices() {
        if (bluetoothAdapter != null) {
            try {
                return bluetoothAdapter.getBondedDevices();
            } catch (SecurityException e) {
                Log.e(TAG, "Permission denied for getting paired devices", e);
                return null;
            }
        }
        return null;
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void connectToDevice(BluetoothDevice device) {
        if (isConnecting) {
            notifyError("Already attempting to connect. Please wait.");
            return;
        }

        isConnecting = true;
        shouldStopListening = false;

        executor.execute(() -> {
            try {
                // Close any existing connection
                cleanupConnection();

                Log.d(TAG, "Attempting to connect to " + device.getAddress());

                // Cancel discovery to improve connection speed
                try {
                    if (bluetoothAdapter.isDiscovering()) {
                        bluetoothAdapter.cancelDiscovery();
                    }
                } catch (SecurityException e) {
                    Log.w(TAG, "Permission issue with discovery", e);
                }

                // Give time for discovery to stop
                Thread.sleep(500);

                // Try reflection method first (most reliable for HC-05)
                boolean connected = false;

                try {
                    Log.d(TAG, "Trying reflection method...");
                    Method m = device.getClass().getMethod("createRfcommSocket", new Class[]{int.class});
                    bluetoothSocket = (BluetoothSocket) m.invoke(device, 1);
                    bluetoothSocket.connect();
                    connected = true;
                    Log.d(TAG, "Reflection method connection successful");
                } catch (Exception e) {
                    Log.w(TAG, "Reflection method failed: " + e.getMessage());

                    // Method 2: Standard connection
                    try {
                        Log.d(TAG, "Trying standard RFCOMM connection...");
                        if (bluetoothSocket != null) {
                            bluetoothSocket.close();
                        }
                        bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                        bluetoothSocket.connect();
                        connected = true;
                        Log.d(TAG, "Standard RFCOMM connection successful");
                    } catch (IOException | SecurityException e2) {
                        Log.w(TAG, "Standard RFCOMM failed: " + e2.getMessage());

                        // Method 3: Insecure connection
                        try {
                            Log.d(TAG, "Trying insecure RFCOMM connection...");
                            if (bluetoothSocket != null) {
                                bluetoothSocket.close();
                            }
                            bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
                            bluetoothSocket.connect();
                            connected = true;
                            Log.d(TAG, "Insecure RFCOMM connection successful");
                        } catch (IOException | SecurityException e3) {
                            Log.e(TAG, "All connection methods failed: " + e3.getMessage());
                            throw new IOException("All connection methods failed", e3);
                        }
                    }
                }

                if (connected && bluetoothSocket != null && bluetoothSocket.isConnected()) {
                    // Wait for connection to stabilize
                    Thread.sleep(1000);

                    // Get streams
                    boolean streamsObtained = false;
                    int retryCount = 0;

                    while (retryCount < 3 && !streamsObtained) {
                        try {
                            Log.d(TAG, "Attempting to get streams, retry: " + retryCount);

                            outputStream = bluetoothSocket.getOutputStream();
                            inputStream = bluetoothSocket.getInputStream();

                            if (outputStream != null && inputStream != null) {
                                outputStream.flush();
                                streamsObtained = true;
                                Log.d(TAG, "Streams obtained successfully");
                            }
                        } catch (IOException streamException) {
                            Log.w(TAG, "Stream attempt " + retryCount + " failed: " + streamException.getMessage());
                            retryCount++;
                            if (retryCount < 3) {
                                Thread.sleep(500);
                            }
                        }
                    }

                    if (streamsObtained) {
                        // Set connection state
                        connectedDevice = device;
                        isConnected = true;
                        isConnecting = false;
                        shouldStopListening = false;

                        Log.d(TAG, "Connection established successfully with streams");

                        // Get device name safely
                        String deviceName = device.getAddress(); // Use address as fallback
                        try {
                            String name = device.getName();
                            if (name != null && !name.isEmpty()) {
                                deviceName = name;
                            }
                        } catch (SecurityException e) {
                            Log.w(TAG, "Permission issue getting device name", e);
                        }

                        // Start listening BEFORE notifying connection success
                        startListening();

                        // Notify connection success
                        notifyConnectionStatus(true, deviceName);

                        // Wait before any operations
                        Thread.sleep(1500);

                        Log.d(TAG, "Connection verified, ready for commands");
                    } else {
                        throw new IOException("Failed to obtain working streams after retries");
                    }
                } else {
                    throw new IOException("Socket connection failed");
                }

            } catch (Exception e) {
                Log.e(TAG, "Connection failed: " + e.getMessage(), e);

                // Reset state
                isConnected = false;
                isConnecting = false;
                shouldStopListening = true;

                // Clean up
                cleanupConnection();

                // Notify error
                String errorMsg = "Connection failed: ";
                if (e.getMessage().contains("read failed")) {
                    errorMsg += "Device not responding. Check HC-05 power and pairing.";
                } else if (e.getMessage().contains("timeout")) {
                    errorMsg += "Connection timeout. Move closer to device.";
                } else {
                    errorMsg += e.getMessage();
                }
                notifyError(errorMsg);
            }
        });
    }

    public void disconnect() {
        Log.d(TAG, "Disconnect requested");
        shouldStopListening = true;
        isConnected = false;

        executor.execute(() -> {
            try {
                cleanupConnection();
                notifyConnectionStatus(false, "");
                Log.d(TAG, "Disconnected successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error disconnecting: " + e.getMessage(), e);
            }
        });
    }

    private void cleanupConnection() {
        shouldStopListening = true;

        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
                Log.w(TAG, "Error closing output stream", e);
            }
            outputStream = null;
        }

        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                Log.w(TAG, "Error closing input stream", e);
            }
            inputStream = null;
        }

        if (bluetoothSocket != null) {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                Log.w(TAG, "Error closing socket", e);
            }
            bluetoothSocket = null;
        }

        connectedDevice = null;
    }

    public boolean isConnected() {
        return isConnected &&
                bluetoothSocket != null &&
                bluetoothSocket.isConnected() &&
                outputStream != null &&
                inputStream != null &&
                !shouldStopListening;
    }

    public BluetoothDevice getConnectedDevice() {
        return connectedDevice;
    }

    public void sendCommand(String command) {
        if (!isConnected || outputStream == null || command == null || command.trim().isEmpty()) {
            notifyError("Cannot send command - not connected or invalid command");
            return;
        }

        executor.execute(() -> {
            try {
                // Verify connection is still active
                if (!isConnected || outputStream == null || bluetoothSocket == null || !bluetoothSocket.isConnected()) {
                    notifyError("Connection lost before sending command");
                    isConnected = false;
                    notifyConnectionStatus(false, "");
                    return;
                }

                String fullCommand = command.trim() + "\n";
                byte[] bytes = fullCommand.getBytes(StandardCharsets.UTF_8);

                synchronized (this) {
                    if (outputStream != null && isConnected) {
                        outputStream.write(bytes);
                        outputStream.flush();
                        Log.d(TAG, "Sent command: " + command);
                    }
                }

            } catch (IOException e) {
                Log.e(TAG, "Error sending command: " + e.getMessage(), e);
                notifyError("Error sending command: " + e.getMessage());

                // Mark as disconnected
                isConnected = false;
                shouldStopListening = true;
                notifyConnectionStatus(false, "");
            }
        });
    }

    private void startListening() {
        executor.execute(() -> {
            Log.d(TAG, "Starting to listen for incoming data");

            while (isConnected && !shouldStopListening && inputStream != null && bluetoothSocket != null) {
                try {
                    // Check socket connection
                    if (!bluetoothSocket.isConnected()) {
                        Log.w(TAG, "Socket disconnected during listening");
                        break;
                    }

                    // Read data if available
                    if (inputStream.available() > 0) {
                        int bytes = inputStream.read(buffer);
                        if (bytes > 0) {
                            String received = new String(buffer, 0, bytes, StandardCharsets.UTF_8);
                            processReceivedData(received);
                        }
                    } else {
                        // Small delay to prevent busy waiting
                        Thread.sleep(50);
                    }

                } catch (IOException e) {
                    Log.e(TAG, "Input stream error: " + e.getMessage(), e);
                    break;
                } catch (InterruptedException e) {
                    Log.d(TAG, "Listening interrupted");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Unexpected error in listening: " + e.getMessage(), e);
                    break;
                }
            }

            // Handle disconnection
            if (isConnected && !shouldStopListening) {
                Log.d(TAG, "Connection lost during listening");
                isConnected = false;
                shouldStopListening = true;
                notifyConnectionStatus(false, "");
                notifyError("Connection lost during communication");
            } else {
                Log.d(TAG, "Listening stopped normally");
            }
        });
    }

    private void processReceivedData(String data) {
        try {
            dataBuffer.append(data);

            int newlineIndex;
            while ((newlineIndex = dataBuffer.indexOf("\n")) != -1) {
                String message = dataBuffer.substring(0, newlineIndex).trim();
                dataBuffer.delete(0, newlineIndex + 1);

                if (!message.isEmpty()) {
                    Log.d(TAG, "Received message: " + message);
                    notifyDataReceived(message);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing received data: " + e.getMessage(), e);
        }
    }

    // Command methods
    public void requestMedicineStatus() {
        sendCommand("GET_STATUS");
    }

    public void requestMedicineHistory() {
        sendCommand("GET_HISTORY");
    }

    public void syncAllAlarms(List<Medicine> medicines) {
        if (!isConnected()) {
            notifyError("Not connected to device");
            return;
        }

        executor.execute(() -> {
            try {
                if (medicines == null || medicines.isEmpty()) {
                    sendCommand("CLEAR_ALARMS");
                    return;
                }

                sendCommand("CLEAR_ALARMS");
                Thread.sleep(500);

                for (Medicine medicine : medicines) {
                    if (medicine == null || !isConnected()) continue;

                    String medicineName = medicine.getName();
                    int quantity = medicine.getQuantity();

                    if (medicineName == null || medicineName.trim().isEmpty()) continue;

                    List<String> alarmTimes = medicine.getAlarmTimes();
                    if (alarmTimes != null) {
                        for (String time : alarmTimes) {
                            if (time != null && !time.trim().isEmpty() && isConnected()) {
                                sendCommand("ALARM:" + medicineName.trim() + ":" + time.trim() + ":" + quantity);
                                Thread.sleep(200);
                            }
                        }
                    }
                }

                if (isConnected()) {
                    Thread.sleep(500);
                    sendCommand("SYNC_COMPLETE");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    // Notification methods
    private void notifyConnectionStatus(boolean connected, String deviceName) {
        if (mainHandler != null && connectionListener != null) {
            mainHandler.post(() -> {
                try {
                    connectionListener.onConnectionStatusChanged(connected, deviceName != null ? deviceName : "");
                } catch (Exception e) {
                    Log.e(TAG, "Error in connection status callback", e);
                }
            });
        }
    }

    private void notifyDataReceived(String data) {
        if (mainHandler != null && connectionListener != null && data != null) {
            mainHandler.post(() -> {
                try {
                    connectionListener.onDataReceived(data);
                } catch (Exception e) {
                    Log.e(TAG, "Error in data received callback", e);
                }
            });
        }
    }

    private void notifyError(String error) {
        if (mainHandler != null && connectionListener != null) {
            mainHandler.post(() -> {
                try {
                    connectionListener.onError(error != null ? error : "Unknown error");
                } catch (Exception e) {
                    Log.e(TAG, "Error in error callback", e);
                }
            });
        }
    }
}