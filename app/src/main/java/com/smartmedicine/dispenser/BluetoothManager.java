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
import java.util.Set;
import java.util.UUID;

public class BluetoothManager {
    private static final String TAG = "BluetoothManager";
    private static final UUID HC05_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static BluetoothManager instance;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private BluetoothDevice connectedDevice;
    private OutputStream outputStream;
    private InputStream inputStream;
    private boolean isConnected = false;
    private BluetoothConnectionListener connectionListener;

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
            return bluetoothAdapter.getBondedDevices();
        }
        return null;
    }

    public void connectToDevice(BluetoothDevice device) {
        new Thread(() -> {
            try {
                if (bluetoothSocket != null && bluetoothSocket.isConnected()) {
                    bluetoothSocket.close();
                }

                bluetoothSocket = device.createRfcommSocketToServiceRecord(HC05_UUID);
                bluetoothSocket.connect();

                outputStream = bluetoothSocket.getOutputStream();
                inputStream = bluetoothSocket.getInputStream();
                connectedDevice = device;
                isConnected = true;

                // Start listening for incoming data
                startListening();

                // Notify connection success
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (connectionListener != null) {
                        connectionListener.onConnectionStatusChanged(true, device.getName());
                    }
                });

                Log.d(TAG, "Connected to " + device.getName());

            } catch (IOException e) {
                Log.e(TAG, "Connection failed: " + e.getMessage());
                isConnected = false;
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (connectionListener != null) {
                        connectionListener.onError("Connection failed: " + e.getMessage());
                    }
                });
            }
        }).start();
    }

    public void disconnect() {
        try {
            isConnected = false;
            if (outputStream != null) {
                outputStream.close();
            }
            if (inputStream != null) {
                inputStream.close();
            }
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
            }
            connectedDevice = null;

            if (connectionListener != null) {
                connectionListener.onConnectionStatusChanged(false, "");
            }

            Log.d(TAG, "Disconnected from Bluetooth device");

        } catch (IOException e) {
            Log.e(TAG, "Error disconnecting: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return isConnected && bluetoothSocket != null && bluetoothSocket.isConnected();
    }

    public BluetoothDevice getConnectedDevice() {
        return connectedDevice;
    }

    public void sendCommand(String command) {
        if (isConnected && outputStream != null) {
            new Thread(() -> {
                try {
                    outputStream.write((command + "\n").getBytes());
                    outputStream.flush();
                    Log.d(TAG, "Sent command: " + command);
                } catch (IOException e) {
                    Log.e(TAG, "Error sending command: " + e.getMessage());
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (connectionListener != null) {
                            connectionListener.onError("Error sending command: " + e.getMessage());
                        }
                    });
                }
            }).start();
        }
    }

    private void startListening() {
        new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;

            while (isConnected) {
                try {
                    bytes = inputStream.read(buffer);
                    String receivedData = new String(buffer, 0, bytes);

                    Log.d(TAG, "Received: " + receivedData);

                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (connectionListener != null) {
                            connectionListener.onDataReceived(receivedData.trim());
                        }
                    });

                } catch (IOException e) {
                    Log.e(TAG, "Error reading data: " + e.getMessage());
                    isConnected = false;
                    break;
                }
            }
        }).start();
    }

    // Methods for specific HC-05 commands
    public void requestMedicineStatus() {
        sendCommand("GET_STATUS");
    }

    public void requestMedicineHistory() {
        sendCommand("GET_HISTORY");
    }

    public void checkMedicineAvailability(String medicineName) {
        sendCommand("CHECK_AVAILABILITY:" + medicineName);
    }

    public void dispenseMedicine(String medicineName, int quantity) {
        sendCommand("DISPENSE:" + medicineName + ":" + quantity);
    }
}