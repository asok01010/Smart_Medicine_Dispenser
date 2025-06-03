package com.smartmedicine.dispenser;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class AlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "AlarmReceiver";
    private static final String CHANNEL_ID = "medicine_alarm_channel";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String medicineName = intent.getStringExtra("medicine_name");
            int quantity = intent.getIntExtra("quantity", 1);
            String time = intent.getStringExtra("time");

            Log.d(TAG, "Alarm triggered for: " + medicineName + " at " + time);

            if (medicineName != null) {
                // Get medicine manager instance
                MedicineManager medicineManager = MedicineManager.getInstance(context);

                // Send Bluetooth command to Arduino FIRST (before recording)
                sendDispenseCommand(context, medicineName, quantity);

                // Record that medicine was taken (this will decrease quantity and add log entry)
                medicineManager.recordMedicineTaken(medicineName);

                // Check remaining quantity
                Medicine medicine = medicineManager.getMedicineByName(medicineName);
                int remainingQuantity = medicine != null ? medicine.getQuantity() : 0;

                // Create notification
                createNotification(context, medicineName, quantity, remainingQuantity);

                Log.d(TAG, "Medicine alarm processed: " + medicineName + ", Remaining quantity: " + remainingQuantity);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in alarm receiver: " + e.getMessage(), e);
        }
    }

    private void sendDispenseCommand(Context context, String medicineName, int quantity) {
        try {
            // Get BluetoothManager instance and send dispense command
            BluetoothManager bluetoothManager = BluetoothManager.getInstance();
            if (bluetoothManager != null && bluetoothManager.isConnected()) {
                String command = "DISPENSE:" + medicineName + ":" + quantity;
                bluetoothManager.sendData(command);
                Log.d(TAG, "Dispense command sent to Arduino: " + command);
            } else {
                Log.w(TAG, "Bluetooth not connected - cannot send dispense command");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending dispense command: " + e.getMessage(), e);
        }
    }

    private void createNotification(Context context, String medicineName, int dosage, int remainingQuantity) {
        try {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            // Create notification channel for Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Medicine Alarms",
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Notifications for medicine reminders");
                notificationManager.createNotificationChannel(channel);
            }

            // Create intent to open main activity
            Intent intent = new Intent(context, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Build notification
            String title = "Time to take " + medicineName;
            String message = "Take " + dosage + " pill(s). Remaining: " + remainingQuantity + " pills";

            // Add low stock warning if needed
            if (remainingQuantity <= 5 && remainingQuantity > 0) {
                message += " (Low stock!)";
            } else if (remainingQuantity == 0) {
                message += " (Out of stock!)";
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_logo)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setDefaults(NotificationCompat.DEFAULT_ALL);

            // Show notification with unique ID to avoid overwriting
            int uniqueNotificationId = (medicineName + System.currentTimeMillis()).hashCode();
            notificationManager.notify(uniqueNotificationId, builder.build());

        } catch (Exception e) {
            Log.e(TAG, "Error creating notification: " + e.getMessage(), e);
        }
    }
}