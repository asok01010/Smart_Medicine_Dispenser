package com.smartmedicine.dispenser;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BluetoothManager {
    private static final String TAG = "BluetoothManager";
    private static final UUID HC05_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static BluetoothManager instance;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private boolean isConnected = false;
    private BluetoothConnectionListener connectionListener;
    private Thread readThread;
    private Handler mainHandler;

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

    public Set<BluetoothDevice> getPairedDevices() {
        if (bluetoothAdapter != null) {
            return bluetoothAdapter.getBondedDevices();
        }
        return null;
    }

    public boolean isConnected() {
        return isConnected && bluetoothSocket != null && bluetoothSocket.isConnected();
    }

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

                String deviceName = device.getName() != null ? device.getName() : "Unknown Device";

                mainHandler.post(() -> {
                    if (connectionListener != null) {
                        connectionListener.onConnectionStatusChanged(true, deviceName);
                    }
                });

                startReadThread();
                Log.d(TAG, "Connected to " + deviceName);

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
            } catch (IOException e) {
                Log.e(TAG, "Error sending data: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    if (connectionListener != null) {
                        connectionListener.onError("Error sending data: " + e.getMessage());
                    }
                });
            }
        }).start();
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
                // Start sync
                sendData("SYNC_START");
                Thread.sleep(500); // Wait for Arduino to process

                // Clear existing alarms
                sendData("CLEAR_ALARMS");
                Thread.sleep(500);

                // Send all alarms
                for (Medicine medicine : medicines) {
                    List<String> alarmTimes = medicine.getAlarmTimes();
                    if (alarmTimes != null) {
                        for (String time : alarmTimes) {
                            String command = "SET_ALARM:" + medicine.getName() + ":" + time + ":1";
                            sendData(command);
                            Thread.sleep(200); // Small delay between commands
                        }
                    }
                }

                // End sync
                Thread.sleep(500);
                sendData("SYNC_END");

                Log.d(TAG, "Alarm sync completed");

            } catch (InterruptedException e) {
                Log.e(TAG, "Sync interrupted: " + e.getMessage(), e);
            }
        }).start();
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
                    bytes = inputStream.read(buffer);
                    String receivedData = new String(buffer, 0, bytes).trim();

                    if (!receivedData.isEmpty()) {
                        Log.d(TAG, "Data received: " + receivedData);
                        mainHandler.post(() -> {
                            if (connectionListener != null) {
                                connectionListener.onDataReceived(receivedData);
                            }
                        });
                    }
                } catch (IOException e) {
                    if (isConnected) {
                        Log.e(TAG, "Error reading data: " + e.getMessage(), e);
                        mainHandler.post(() -> {
                            if (connectionListener != null) {
                                connectionListener.onError("Connection lost");
                                connectionListener.onConnectionStatusChanged(false, "");
                            }
                        });
                        disconnect();
                    }
                    break;
                }
            }
        });
        readThread.start();
    }
}