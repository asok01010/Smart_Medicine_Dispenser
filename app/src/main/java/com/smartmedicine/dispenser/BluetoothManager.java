package com.smartmedicine.dispenser;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.RequiresPermission;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class BluetoothManager {
    private static final String TAG = "BluetoothManager";
    private static final UUID HC05_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static final int SOCKET_TIMEOUT = 10000; // 10 seconds socket timeout
    private static final int COMMAND_TIMEOUT = 5000; // 5 seconds command timeout
    private static final int KEEP_ALIVE_INTERVAL = 5000; // 5 seconds between keep-alive pings
    private static final int MAX_RETRY_COUNT = 3; // Maximum command retry attempts

    private static BluetoothManager instance;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private boolean isConnected = false;
    private BluetoothConnectionListener connectionListener;
    private Thread readThread;
    private Handler mainHandler;

    private Thread keepAliveThread;
    private AtomicBoolean isKeepAliveRunning = new AtomicBoolean(false);
    private long lastResponseTime = 0;
    private String lastReceivedData = "";

    public interface BluetoothConnectionListener {
        void onConnectionStatusChanged(boolean connected, String deviceName);
        void onDataReceived(String data);
        void onError(String error);
    }

    private BluetoothManager() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mainHandler = new Handler(Looper.getMainLooper());
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
            Set<BluetoothDevice> allDevices = bluetoothAdapter.getBondedDevices();
            Set<BluetoothDevice> hc05Devices = new HashSet<>();

            if (allDevices != null) {
                for (BluetoothDevice device : allDevices) {
                    try {
                        String deviceName = device.getName();
                        if (deviceName != null) {
                            // Check if device name contains HC-05 or HC05 (case insensitive)
                            String lowerName = deviceName.toLowerCase();
                            if (lowerName.contains("hc-05") || lowerName.contains("hc05")) {
                                hc05Devices.add(device);
                                Log.d(TAG, "Found HC-05 device: " + deviceName + " (" + device.getAddress() + ")");
                            }
                        }
                    } catch (SecurityException e) {
                        Log.w(TAG, "Permission not granted for device name: " + device.getAddress());
                        // If we can't get the name, we can't filter, so skip this device
                    }
                }
            }

            Log.d(TAG, "Filtered " + (allDevices != null ? allDevices.size() : 0) +
                    " paired devices to " + hc05Devices.size() + " HC-05 devices");
            return hc05Devices;
        }
        return null;
    }

    public boolean isConnected() {
        return isConnected && bluetoothSocket != null && bluetoothSocket.isConnected();
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void connectToDevice(BluetoothDevice device) {
        new Thread(() -> {
            try {
                if (bluetoothSocket != null) {
                    disconnect();
                }

                bluetoothSocket = device.createRfcommSocketToServiceRecord(HC05_UUID);
                bluetoothSocket.connect();

                outputStream = bluetoothSocket.getOutputStream();
                inputStream = bluetoothSocket.getInputStream();
                isConnected = true;
                lastResponseTime = System.currentTimeMillis();

                String deviceName = device.getName() != null ? device.getName() : "Unknown Device";
                mainHandler.post(() -> {
                    if (connectionListener != null) {
                        connectionListener.onConnectionStatusChanged(true, deviceName);
                    }
                });

                startReadThread();

                startKeepAliveThread();

                Log.d(TAG, "Connected to " + deviceName);

                sendData("PING");

            } catch (IOException e) {
                Log.e(TAG, "Connection failed: " + e.getMessage(), e);
                isConnected = false;
                mainHandler.post(() -> {
                    if (connectionListener != null) {
                        connectionListener.onError("Connection failed: " + e.getMessage());
                        connectionListener.onConnectionStatusChanged(false, "");
                    }
                });
            }
        }).start();
    }

    public void disconnect() {
        try {
            isConnected = false;

            stopKeepAliveThread();

            if (readThread != null) {
                readThread.interrupt();
                readThread = null;
            }

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

            mainHandler.post(() -> {
                if (connectionListener != null) {
                    connectionListener.onConnectionStatusChanged(false, "");
                }
            });

            Log.d(TAG, "Disconnected from device");

        } catch (IOException e) {
            Log.e(TAG, "Error disconnecting: " + e.getMessage(), e);
        }
    }

    public void sendData(String data) {
        if (!isConnected || outputStream == null) {
            Log.w(TAG, "Cannot send data - not connected");
            if (connectionListener != null) {
                mainHandler.post(() -> connectionListener.onError("Not connected to device"));
            }
            return;
        }

        new Thread(() -> {
            try {
                String message = data + "\n"; // Add newline for Arduino
                outputStream.write(message.getBytes());
                outputStream.flush();
                Log.d(TAG, "Data sent: " + data);

                lastResponseTime = System.currentTimeMillis();

            } catch (IOException e) {
                Log.e(TAG, "Error sending data: " + e.getMessage(), e);

                handleConnectionError(e);
            }
        }).start();
    }

    private void handleConnectionError(Exception e) {
        Log.e(TAG, "Connection error: " + e.getMessage(), e);

        // Only report error if we're still connected (avoid duplicate errors)
        if (isConnected) {
            isConnected = false;

            mainHandler.post(() -> {
                if (connectionListener != null) {
                    connectionListener.onError("Connection error: " + e.getMessage());
                    connectionListener.onConnectionStatusChanged(false, "");
                }
            });

            // Clean disconnect
            disconnect();
        }
    }

    public void syncAllAlarms(List<Medicine> medicines) {
        if (!isConnected) {
            if (connectionListener != null) {
                mainHandler.post(() -> connectionListener.onError("Not connected to device"));
            }
            return;
        }

        new Thread(() -> {
            try {
                mainHandler.post(() -> {
                    if (connectionListener != null) {
                        connectionListener.onDataReceived("SYNC_STARTING");
                    }
                });

                Log.d(TAG, "Starting alarm synchronization...");

                int totalAlarms = countTotalAlarms(medicines);
                final int finalTotalAlarms = totalAlarms;

                // Start sync with longer delay
                boolean syncStarted = sendCommandWithAck("SYNC_START", "SYNC_STARTED", 3000);
                if (!syncStarted) {
                    throw new RuntimeException("Failed to start sync - no acknowledgment received");
                }

                boolean expectSent = sendCommandWithAck("EXPECT_ALARMS:" + totalAlarms, "EXPECTING_ALARMS", 3000);
                if (!expectSent) {
                    throw new RuntimeException("Failed to set expected alarm count");
                }

                // Clear existing alarms
                boolean cleared = sendCommandWithAck("CLEAR_ALARMS", "ALARMS_CLEARED", 3000);
                if (!cleared) {
                    throw new RuntimeException("Failed to clear existing alarms");
                }

                Log.d(TAG, "Sending " + totalAlarms + " alarms to device");

                int currentAlarmCount = 0;

                // Send all alarms with acknowledgment
                for (Medicine medicine : medicines) {
                    List<String> alarmTimes = medicine.getAlarmTimes();
                    if (alarmTimes != null) {
                        for (String time : alarmTimes) {
                            String[] timeParts = time.split(":");
                            if (timeParts.length >= 2) {
                                String hour = timeParts[0];
                                String minute = timeParts[1];

                                // Format: SET_ALARM:MedicineName:Hour:Minute(Quantity)
                                String command = "SET_ALARM:" + medicine.getName() + ":" +
                                        hour + ":" + minute + "(1)";

                                boolean alarmSet = sendCommandWithAck(command, "ALARM_SET", 3000);
                                if (!alarmSet) {
                                    throw new RuntimeException("Failed to set alarm: " + command);
                                }

                                currentAlarmCount++;
                                final int finalCurrentCount = currentAlarmCount;

                                // Update UI with progress
                                mainHandler.post(() -> {
                                    if (connectionListener != null) {
                                        connectionListener.onDataReceived("SYNC_PROGRESS:" +
                                                finalCurrentCount + ":" +
                                                finalTotalAlarms);
                                    }
                                });

                                Log.d(TAG, "Sent alarm " + finalCurrentCount + "/" + finalTotalAlarms +
                                        ": " + command);
                            }
                        }
                    }
                }

                // End sync and wait for confirmation
                boolean syncEnded = sendCommandWithAck("SYNC_END", "SYNC_COMPLETE", 5000);
                if (!syncEnded) {
                    throw new RuntimeException("Failed to complete sync - no acknowledgment received");
                }

                final int finalAlarmCount = currentAlarmCount;
                Log.d(TAG, "Alarm sync completed successfully - " + finalAlarmCount + " alarms sent");

                mainHandler.post(() -> {
                    if (connectionListener != null) {
                        connectionListener.onDataReceived("SYNC_COMPLETE:" + finalAlarmCount);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Sync error: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    if (connectionListener != null) {
                        connectionListener.onError("Sync error: " + e.getMessage());
                    }
                });
            }
        }).start();
    }

    private int countTotalAlarms(List<Medicine> medicines) {
        int count = 0;
        for (Medicine medicine : medicines) {
            List<String> alarmTimes = medicine.getAlarmTimes();
            if (alarmTimes != null) {
                count += alarmTimes.size();
            }
        }
        return count;
    }

    private boolean sendCommandWithAck(String command, String expectedAck, int timeout)
            throws InterruptedException {
        if (!isConnected()) {
            throw new RuntimeException("Not connected to device");
        }

        // Reset last received data
        synchronized (this) {
            lastReceivedData = "";
        }

        // Send command
        try {
            String message = command + "\n";
            outputStream.write(message.getBytes());
            outputStream.flush();
            Log.d(TAG, "Command sent: " + command + ", waiting for: " + expectedAck);
        } catch (IOException e) {
            Log.e(TAG, "Error sending command: " + e.getMessage(), e);
            handleConnectionError(e);
            return false;
        }

        // Wait for acknowledgment
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeout) {
            // Check if we received the expected acknowledgment
            synchronized (this) {
                if (lastReceivedData.contains(expectedAck)) {
                    Log.d(TAG, "Received acknowledgment: " + expectedAck);
                    return true;
                }
            }

            // Check if connection was lost
            if (!isConnected()) {
                Log.e(TAG, "Connection lost while waiting for acknowledgment");
                return false;
            }

            // Wait a bit before checking again
            Thread.sleep(100);
        }

        // Timeout waiting for acknowledgment
        Log.e(TAG, "Timeout waiting for acknowledgment: " + expectedAck);
        return false;
    }

    public void requestMedicineStatus() {
        sendData("STATUS");
    }

    public void requestMedicineHistory() {
        sendData("HISTORY");
    }

    private void startReadThread() {
        readThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;

            while (isConnected && !Thread.currentThread().isInterrupted()) {
                try {
                    if (inputStream.available() > 0) {
                        bytes = inputStream.read(buffer);
                        String receivedData = new String(buffer, 0, bytes).trim();

                        if (!receivedData.isEmpty()) {
                            Log.d(TAG, "Data received: " + receivedData);

                            lastResponseTime = System.currentTimeMillis();
                            synchronized (this) {
                                lastReceivedData = receivedData;
                            }

                            mainHandler.post(() -> {
                                if (connectionListener != null) {
                                    connectionListener.onDataReceived(receivedData);
                                }
                            });
                        }
                    } else {
                        Thread.sleep(50);
                    }
                } catch (IOException e) {
                    if (isConnected) {
                        Log.e(TAG, "Error reading data: " + e.getMessage(), e);
                        handleConnectionError(e);
                    }
                    break;
                } catch (InterruptedException e) {
                    Log.d(TAG, "Read thread interrupted");
                    break;
                }
            }

            Log.d(TAG, "Read thread stopped");
        });

        readThread.start();
    }

    private void startKeepAliveThread() {
        if (keepAliveThread != null && keepAliveThread.isAlive()) {
            return;
        }

        isKeepAliveRunning.set(true);

        keepAliveThread = new Thread(() -> {
            Log.d(TAG, "Keep-alive thread started");

            while (isKeepAliveRunning.get() && isConnected) {
                try {
                    // Check if we haven't received data for a while
                    long timeSinceLastResponse = System.currentTimeMillis() - lastResponseTime;

                    if (timeSinceLastResponse > KEEP_ALIVE_INTERVAL) {
                        // Send keep-alive ping
                        Log.d(TAG, "Sending keep-alive ping");

                        try {
                            String message = "KEEPALIVE\n";
                            outputStream.write(message.getBytes());
                            outputStream.flush();
                        } catch (IOException e) {
                            Log.e(TAG, "Error sending keep-alive: " + e.getMessage(), e);
                            handleConnectionError(e);
                            break;
                        }
                    }

                    // Sleep for a bit
                    Thread.sleep(1000);

                } catch (InterruptedException e) {
                    Log.d(TAG, "Keep-alive thread interrupted");
                    break;
                }
            }

            Log.d(TAG, "Keep-alive thread stopped");
        });

        keepAliveThread.start();
    }

    private void stopKeepAliveThread() {
        isKeepAliveRunning.set(false);

        if (keepAliveThread != null) {
            keepAliveThread.interrupt();
            keepAliveThread = null;
        }
    }

    private void sendDataWithVerification(String data) {
        if (!isConnected || outputStream == null) {
            Log.w(TAG, "Cannot send data - not connected");
            throw new RuntimeException("Not connected to device");
        }

        try {
            String message = data + "\n"; // Add newline for Arduino
            outputStream.write(message.getBytes());
            outputStream.flush();
            Log.d(TAG, "Data sent with verification: " + data);

            lastResponseTime = System.currentTimeMillis();

        } catch (IOException e) {
            Log.e(TAG, "Error sending data: " + e.getMessage(), e);
            handleConnectionError(e);
            throw new RuntimeException("Failed to send data: " + e.getMessage());
        }
    }
}
