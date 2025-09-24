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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class BluetoothManager {
    private static final String TAG = "BluetoothManager";
    private static final UUID HC05_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static final int MAX_RETRIES = 5;
    private static final int ACK_TIMEOUT = 5000;
    private static final int SYNC_DELAY = 500;
    private static final int KEEP_ALIVE_INTERVAL = 10000;

    private static BluetoothManager instance;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private boolean isConnected = false;
    private BluetoothConnectionListener connectionListener;
    private Thread readThread;
    private Thread keepAliveThread;
    private Handler mainHandler;
    private String lastReceivedData = "";
    private final AtomicBoolean isAcknowledgmentReceived = new AtomicBoolean(false);
    private final Object syncLock = new Object();

    private boolean isSyncing = false;
    private int syncRetryCount = 0;
    private boolean isReconnecting = false;

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
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            Set<BluetoothDevice> hc05Devices = new HashSet<>();

            // Filter for HC-05 devices
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                if (deviceName != null &&
                        (deviceName.toLowerCase().contains("hc-05") ||
                                deviceName.toLowerCase().contains("hc05"))) {
                    hc05Devices.add(device);
                }
            }

            return hc05Devices;
        }
        return null;
    }

    public boolean isConnected() {
        return isConnected && bluetoothSocket != null && bluetoothSocket.isConnected();
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void connectToDevice(BluetoothDevice device) {
        // Prevent multiple connection attempts
        if (isReconnecting) {
            Log.d(TAG, "Already attempting to reconnect");
            return;
        }

        isReconnecting = true;

        new Thread(() -> {
            try {
                if (bluetoothSocket != null) {
                    disconnect();
                }

                // Log connection attempt
                Log.d(TAG, "Attempting to connect to " + device.getAddress());

                // Create socket and connect with timeout
                bluetoothSocket = device.createRfcommSocketToServiceRecord(HC05_UUID);

                // Connect with retry logic
                boolean connected = false;
                int retries = 0;
                Exception lastException = null;

                while (!connected && retries < 3) {
                    try {
                        bluetoothSocket.connect();
                        connected = true;
                    } catch (IOException e) {
                        lastException = e;
                        Log.w(TAG, "Connection attempt " + (retries + 1) + " failed: " + e.getMessage());
                        retries++;

                        if (retries < 3) {
                            // Close and recreate socket for retry
                            try {
                                bluetoothSocket.close();
                            } catch (IOException closeEx) {
                                Log.e(TAG, "Error closing socket for retry: " + closeEx.getMessage());
                            }

                            bluetoothSocket = device.createRfcommSocketToServiceRecord(HC05_UUID);
                            Thread.sleep(1000); // Wait before retry
                        }
                    }
                }

                if (!connected) {
                    throw new IOException("Failed to connect after " + retries + " attempts: " +
                            (lastException != null ? lastException.getMessage() : "Unknown error"));
                }

                // Get streams
                outputStream = bluetoothSocket.getOutputStream();
                inputStream = bluetoothSocket.getInputStream();
                isConnected = true;

                String deviceName = device.getName() != null ? device.getName() : "Unknown Device";

                // Update UI
                mainHandler.post(() -> {
                    if (connectionListener != null) {
                        connectionListener.onConnectionStatusChanged(true, deviceName);
                    }
                });

                // Start read thread
                startReadThread();

                // Start keep-alive thread
                startKeepAliveThread();

                Log.d(TAG, "Successfully connected to " + deviceName);

                // Send initial handshake
                sendData("CONNECT");

            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Connection failed: " + e.getMessage(), e);
                isConnected = false;

                mainHandler.post(() -> {
                    if (connectionListener != null) {
                        connectionListener.onError("Connection failed: " + e.getMessage());
                        connectionListener.onConnectionStatusChanged(false, "");
                    }
                });
            } finally {
                isReconnecting = false;
            }
        }).start();
    }

    public void disconnect() {
        try {
            isConnected = false;

            // Stop threads
            if (readThread != null) {
                readThread.interrupt();
                readThread = null;
            }

            if (keepAliveThread != null) {
                keepAliveThread.interrupt();
                keepAliveThread = null;
            }

            // Close streams
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing output stream: " + e.getMessage());
                }
                outputStream = null;
            }

            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing input stream: " + e.getMessage());
                }
                inputStream = null;
            }

            // Close socket
            if (bluetoothSocket != null) {
                try {
                    bluetoothSocket.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing socket: " + e.getMessage());
                }
                bluetoothSocket = null;
            }

            // Update UI
            mainHandler.post(() -> {
                if (connectionListener != null) {
                    connectionListener.onConnectionStatusChanged(false, "");
                }
            });

            Log.d(TAG, "Disconnected from device");

        } catch (Exception e) {
            Log.e(TAG, "Error during disconnect: " + e.getMessage(), e);
        }
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

        // Prevent multiple sync attempts
        if (isSyncing) {
            if (connectionListener != null) {
                mainHandler.post(() -> connectionListener.onError("Sync already in progress"));
            }
            return;
        }

        isSyncing = true;
        syncRetryCount = 0;

        new Thread(() -> {
            try {
                // Notify UI
                mainHandler.post(() -> {
                    if (connectionListener != null) {
                        connectionListener.onDataReceived("SYNC_STARTING");
                    }
                });

                Log.d(TAG, "Starting alarm synchronization...");

                // Count total alarms
                int totalAlarms = countTotalAlarms(medicines);
                final int finalTotalAlarms = totalAlarms;

                // Prepare device for sync with longer delay
                sendData("SYNC_START");
                Thread.sleep(1000);

                // Wait for SYNC_STARTED response
                long startTime = System.currentTimeMillis();
                boolean syncStarted = false;

                while (System.currentTimeMillis() - startTime < 5000) {
                    synchronized (syncLock) {
                        if (lastReceivedData.contains("SYNC_STARTED")) {
                            syncStarted = true;
                            break;
                        }
                    }
                    Thread.sleep(100);
                }

                if (!syncStarted) {
                    throw new RuntimeException("Failed to start sync - no acknowledgment received");
                }

                // Tell Arduino how many alarms to expect
                sendData("EXPECT_ALARMS:" + totalAlarms);
                Thread.sleep(1000);

                // Clear existing alarms
                sendData("CLEAR_ALARMS");
                Thread.sleep(1000);

                Log.d(TAG, "Sending " + totalAlarms + " alarms to device");

                // Send all alarms with proper delays
                int currentAlarmCount = 0;

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

                                // Send command and wait for response
                                sendData(command);

                                // Wait for ALARM_SET response with timeout
                                startTime = System.currentTimeMillis();
                                boolean alarmSet = false;

                                while (System.currentTimeMillis() - startTime < 5000) {
                                    synchronized (syncLock) {
                                        if (lastReceivedData.contains("ALARM_SET")) {
                                            alarmSet = true;
                                            break;
                                        }
                                    }
                                    Thread.sleep(100);
                                }

                                if (!alarmSet) {
                                    Log.w(TAG, "No ALARM_SET confirmation received for: " + command);
                                    // Continue anyway - Arduino might have received it
                                }

                                // Update counter and UI
                                currentAlarmCount++;
                                final int finalCurrentCount = currentAlarmCount;

                                mainHandler.post(() -> {
                                    if (connectionListener != null) {
                                        connectionListener.onDataReceived("SYNC_PROGRESS:" +
                                                finalCurrentCount + ":" +
                                                finalTotalAlarms);
                                    }
                                });

                                Log.d(TAG, "Sent alarm " + finalCurrentCount + "/" + finalTotalAlarms +
                                        ": " + command);

                                // Longer delay between alarms
                                Thread.sleep(1000);
                            }
                        }
                    }
                }

                // End sync
                sendData("SYNC_END");

                // Wait for SYNC_COMPLETE response
                startTime = System.currentTimeMillis();
                boolean syncCompleted = false;

                while (System.currentTimeMillis() - startTime < 10000) {
                    synchronized (syncLock) {
                        if (lastReceivedData.contains("SYNC_COMPLETE")) {
                            syncCompleted = true;
                            break;
                        }
                    }
                    Thread.sleep(100);
                }

                if (!syncCompleted) {
                    Log.w(TAG, "No SYNC_COMPLETE confirmation received, but sync may have succeeded");
                }

                final int finalAlarmCount = currentAlarmCount;
                Log.d(TAG, "Alarm sync completed - " + finalAlarmCount + " alarms sent");

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

                // Try to recover connection if broken
                if (e instanceof IOException) {
                    handleConnectionError(e);
                }

            } finally {
                isSyncing = false;
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

                // Log outgoing data
                Log.d(TAG, "Sending: " + data);

                synchronized (outputStream) {
                    outputStream.write(message.getBytes());
                    outputStream.flush();
                }

            } catch (IOException e) {
                Log.e(TAG, "Error sending data: " + e.getMessage(), e);
                handleConnectionError(e);
            }
        }).start();
    }

    private void startReadThread() {
        if (readThread != null) {
            readThread.interrupt();
        }

        readThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;

            while (isConnected && !Thread.currentThread().isInterrupted()) {
                try {
                    // Check if input stream is available
                    if (inputStream == null) {
                        Log.e(TAG, "Input stream is null");
                        break;
                    }

                    // Read available data
                    if (inputStream.available() > 0) {
                        bytes = inputStream.read(buffer);
                        String receivedData = new String(buffer, 0, bytes).trim();

                        if (!receivedData.isEmpty()) {
                            // Store last received data for acknowledgment checking
                            synchronized (syncLock) {
                                lastReceivedData = receivedData;
                            }

                            Log.d(TAG, "Received: " + receivedData);

                            // Notify UI
                            mainHandler.post(() -> {
                                if (connectionListener != null) {
                                    connectionListener.onDataReceived(receivedData);
                                }
                            });
                        }
                    }

                    // Small delay to prevent CPU hogging
                    Thread.sleep(10);

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
        if (keepAliveThread != null) {
            keepAliveThread.interrupt();
        }

        keepAliveThread = new Thread(() -> {
            while (isConnected && !Thread.currentThread().isInterrupted()) {
                try {
                    // Only send keep-alive if not syncing
                    if (!isSyncing) {
                        sendData("PING");
                    }

                    // Wait for next keep-alive
                    Thread.sleep(KEEP_ALIVE_INTERVAL);

                } catch (InterruptedException e) {
                    Log.d(TAG, "Keep-alive thread interrupted");
                    break;
                }
            }

            Log.d(TAG, "Keep-alive thread stopped");
        });

        keepAliveThread.start();
    }

    public void requestMedicineStatus() {
        sendData("STATUS");
    }

    public void requestMedicineHistory() {
        sendData("HISTORY");
    }
}
