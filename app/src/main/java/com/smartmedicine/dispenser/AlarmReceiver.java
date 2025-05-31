package com.smartmedicine.dispenser;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AlarmReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "medicine_alarm_channel";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onReceive(Context context, Intent intent) {
        String medicineName = intent.getStringExtra("medicine_name");
        int quantity = intent.getIntExtra("quantity", 1);

        if (medicineName != null) {
            // Create notification
            createNotification(context, medicineName, quantity);

            // Log the medicine as taken (simulate automatic dispensing)
            logMedicineTaken(context, medicineName);

            // Send command to HC-05 Bluetooth device (if connected)
            sendBluetoothCommand(context, medicineName, quantity);
        }
    }

    private void createNotification(Context context, String medicineName, int quantity) {
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

        // Create intent to open app when notification is tapped
        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setContentTitle("Medicine Reminder")
                .setContentText("Time to take " + medicineName + " (" + quantity + " pill" + (quantity > 1 ? "s" : "") + ")")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void logMedicineTaken(Context context, String medicineName) {
        // Get current time and date
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        Date now = new Date();
        String currentTime = timeFormat.format(now);
        String currentDate = dateFormat.format(now);

        // Create log entry
        MedicineLogEntry logEntry = new MedicineLogEntry(medicineName, currentTime, currentDate);

        // Save to medicine manager
        MedicineManager medicineManager = MedicineManager.getInstance(context);
        medicineManager.addLogEntry(logEntry);
    }

    private void sendBluetoothCommand(Context context, String medicineName, int quantity) {
        // Get Bluetooth manager and send command to HC-05
        BluetoothManager bluetoothManager = BluetoothManager.getInstance();
        if (bluetoothManager.isConnected()) {
            String command = "DISPENSE:" + medicineName + ":" + quantity;
            bluetoothManager.sendCommand(command);
        }
    }
}