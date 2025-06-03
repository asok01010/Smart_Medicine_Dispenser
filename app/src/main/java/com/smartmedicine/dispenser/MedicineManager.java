package com.smartmedicine.dispenser;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MedicineManager {
    private static final String TAG = "MedicineManager";
    private static final String PREFS_NAME = "MedicinePrefs";
    private static final String MEDICINES_KEY = "medicines";
    private static final String LOG_ENTRIES_KEY = "log_entries";

    private static MedicineManager instance;
    private Context context; // Added context for AlarmManager
    private SharedPreferences sharedPreferences;
    private Gson gson;
    private List<Medicine> medicines;
    private List<MedicineLogEntry> logEntries;

    private MedicineManager(Context context) {
        this.context = context.getApplicationContext(); // Store context
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        medicines = new ArrayList<>();
        logEntries = new ArrayList<>();
        loadMedicines();
        loadLogEntries();
    }

    public static synchronized MedicineManager getInstance(Context context) {
        if (instance == null) {
            instance = new MedicineManager(context.getApplicationContext());
        }
        return instance;
    }

    // ==================== ALARM SCHEDULING METHODS ====================

    public void scheduleAlarm(String medicineName, String timeString, int quantity) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

            // Parse time
            String[] timeParts = timeString.split(":");
            int hour = Integer.parseInt(timeParts[0]);
            int minute = Integer.parseInt(timeParts[1]);

            // Create calendar for alarm time
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            // If time has passed today, schedule for tomorrow
            if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_MONTH, 1);
            }

            // Create intent for alarm
            Intent intent = new Intent(context, AlarmReceiver.class);
            intent.putExtra("medicine_name", medicineName);
            intent.putExtra("quantity", quantity);
            intent.putExtra("time", timeString);

            // CRITICAL: Create unique request code for each medicine + time combination
            String uniqueKey = medicineName + "_" + timeString;
            int requestCode = Math.abs(uniqueKey.hashCode()); // Ensure positive number

            // Create pending intent with unique request code
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Schedule repeating alarm (daily)
            alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY, // Repeat daily
                    pendingIntent
            );

            Log.d(TAG, "Alarm scheduled for " + medicineName + " at " + timeString +
                    " with requestCode: " + requestCode);

        } catch (Exception e) {
            Log.e(TAG, "Error scheduling alarm: " + e.getMessage(), e);
        }
    }

    public void cancelAlarm(String medicineName, String timeString) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

            Intent intent = new Intent(context, AlarmReceiver.class);

            // Use same unique key to cancel specific alarm
            String uniqueKey = medicineName + "_" + timeString;
            int requestCode = Math.abs(uniqueKey.hashCode());

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            alarmManager.cancel(pendingIntent);

            Log.d(TAG, "Alarm cancelled for " + medicineName + " at " + timeString);

        } catch (Exception e) {
            Log.e(TAG, "Error cancelling alarm: " + e.getMessage(), e);
        }
    }

    // Method to schedule all alarms for a medicine
    public void scheduleAllAlarmsForMedicine(Medicine medicine) {
        List<String> alarmTimes = medicine.getAlarmTimes();
        if (alarmTimes != null) {
            for (String time : alarmTimes) {
                scheduleAlarm(medicine.getName(), time, 1); // 1 pill per alarm
            }
            Log.d(TAG, "Scheduled " + alarmTimes.size() + " alarms for " + medicine.getName());
        }
    }

    // Method to cancel all alarms for a medicine
    public void cancelAllAlarmsForMedicine(Medicine medicine) {
        List<String> alarmTimes = medicine.getAlarmTimes();
        if (alarmTimes != null) {
            for (String time : alarmTimes) {
                cancelAlarm(medicine.getName(), time);
            }
            Log.d(TAG, "Cancelled all alarms for " + medicine.getName());
        }
    }

    // ==================== MEDICINE MANAGEMENT METHODS ====================

    public void addMedicine(Medicine medicine) {
        try {
            if (medicine != null) {
                medicines.add(medicine);
                saveMedicines();

                // Schedule alarms for the new medicine
                scheduleAllAlarmsForMedicine(medicine);

                Log.d(TAG, "Medicine added and alarms scheduled: " + medicine.getName());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error adding medicine: " + e.getMessage(), e);
        }
    }

    public void saveMedicine(Medicine medicine) {
        try {
            if (medicine != null) {
                // Cancel existing alarms for this medicine first
                Medicine existingMedicine = getMedicineByName(medicine.getName());
                if (existingMedicine != null) {
                    cancelAllAlarmsForMedicine(existingMedicine);
                }

                // Check if medicine with same name already exists
                boolean exists = false;
                for (int i = 0; i < medicines.size(); i++) {
                    if (medicines.get(i).getName().equals(medicine.getName())) {
                        // Update existing medicine
                        medicines.set(i, medicine);
                        exists = true;
                        break;
                    }
                }

                // If it doesn't exist, add it
                if (!exists) {
                    medicines.add(medicine);
                }

                saveMedicines();

                // Schedule new alarms
                scheduleAllAlarmsForMedicine(medicine);

                Log.d(TAG, "Medicine saved and alarms scheduled: " + medicine.getName());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving medicine: " + e.getMessage(), e);
        }
    }

    public void removeMedicine(Medicine medicine) {
        try {
            if (medicine != null && medicines.remove(medicine)) {
                // Cancel all alarms for this medicine
                cancelAllAlarmsForMedicine(medicine);

                saveMedicines();
                Log.d(TAG, "Medicine removed and alarms cancelled: " + medicine.getName());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error removing medicine: " + e.getMessage(), e);
        }
    }

    public void updateMedicine(Medicine oldMedicine, Medicine newMedicine) {
        try {
            int index = medicines.indexOf(oldMedicine);
            if (index != -1) {
                // Cancel old alarms
                cancelAllAlarmsForMedicine(oldMedicine);

                // Update medicine
                medicines.set(index, newMedicine);
                saveMedicines();

                // Schedule new alarms
                scheduleAllAlarmsForMedicine(newMedicine);

                Log.d(TAG, "Medicine updated and alarms rescheduled: " + newMedicine.getName());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating medicine: " + e.getMessage(), e);
        }
    }

    public void removeAlarmTime(String medicineName, String time) {
        try {
            if (medicineName == null || time == null) {
                return;
            }

            for (Medicine medicine : medicines) {
                if (medicine.getName().equals(medicineName)) {
                    List<String> alarmTimes = medicine.getAlarmTimes();
                    if (alarmTimes != null && alarmTimes.remove(time)) {
                        // Cancel the specific alarm
                        cancelAlarm(medicineName, time);

                        saveMedicines();
                        Log.d(TAG, "Alarm time removed: " + time + " for " + medicineName);

                        // If no more alarm times, remove the medicine entirely
                        if (alarmTimes.isEmpty()) {
                            medicines.remove(medicine);
                            saveMedicines();
                            Log.d(TAG, "Medicine removed (no more alarms): " + medicineName);
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error removing alarm time: " + e.getMessage(), e);
        }
    }

    public void addAlarmTimeToMedicine(String medicineName, String time) {
        try {
            if (medicineName == null || time == null) {
                return;
            }

            for (Medicine medicine : medicines) {
                if (medicine.getName().equals(medicineName)) {
                    medicine.addAlarmTime(time);
                    saveMedicines();

                    // Schedule the new alarm
                    scheduleAlarm(medicineName, time, 1);

                    Log.d(TAG, "Alarm time added and scheduled: " + time + " for " + medicineName);
                    return;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error adding alarm time to medicine: " + e.getMessage(), e);
        }
    }

    // ==================== QUANTITY MANAGEMENT ====================

    public void updateMedicineQuantity(String medicineName, int newQuantity) {
        try {
            if (medicineName == null || newQuantity < 0) {
                return;
            }

            for (Medicine medicine : medicines) {
                if (medicine.getName().equals(medicineName)) {
                    medicine.setQuantity(newQuantity);
                    saveMedicines();
                    Log.d(TAG, "Medicine quantity updated: " + medicineName + " -> " + newQuantity);

                    if (newQuantity == 0) {
                        Log.w(TAG, "Medicine " + medicineName + " is out of stock!");
                    }
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating medicine quantity: " + e.getMessage(), e);
        }
    }

    public boolean decreaseMedicineQuantity(String medicineName) {
        try {
            if (medicineName == null) {
                return false;
            }

            for (Medicine medicine : medicines) {
                if (medicine.getName().equals(medicineName)) {
                    int currentQuantity = medicine.getQuantity();

                    if (currentQuantity > 0) {
                        int newQuantity = currentQuantity - 1;
                        medicine.setQuantity(newQuantity);
                        saveMedicines();

                        Log.d(TAG, "Medicine quantity decreased: " + medicineName + " from " + currentQuantity + " to " + newQuantity);

                        if (newQuantity == 0) {
                            Log.w(TAG, "Medicine " + medicineName + " is now out of stock!");
                        }

                        return true;
                    } else {
                        Log.w(TAG, "Cannot decrease quantity for " + medicineName + " - already at 0");
                        return false;
                    }
                }
            }

            Log.w(TAG, "Medicine not found: " + medicineName);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error decreasing medicine quantity: " + e.getMessage(), e);
            return false;
        }
    }

    // ==================== GETTER METHODS ====================

    public Medicine getMedicineByName(String medicineName) {
        try {
            if (medicineName == null) {
                return null;
            }

            for (Medicine medicine : medicines) {
                if (medicine.getName().equals(medicineName)) {
                    return medicine;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting medicine by name: " + e.getMessage(), e);
        }
        return null;
    }

    public List<Medicine> getAllMedicines() {
        return new ArrayList<>(medicines);
    }

    public void clearAllMedicines() {
        try {
            // Cancel all alarms first
            for (Medicine medicine : medicines) {
                cancelAllAlarmsForMedicine(medicine);
            }

            medicines.clear();
            saveMedicines();
            Log.d(TAG, "All medicines and alarms cleared");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing medicines: " + e.getMessage(), e);
        }
    }

    public Medicine getNextMedicine() {
        try {
            if (medicines.isEmpty()) {
                return null;
            }

            Calendar now = Calendar.getInstance();
            int currentHour = now.get(Calendar.HOUR_OF_DAY);
            int currentMinute = now.get(Calendar.MINUTE);
            int currentTimeInMinutes = currentHour * 60 + currentMinute;

            Medicine nextMedicine = null;
            int nextTimeInMinutes = Integer.MAX_VALUE;
            String nextAlarmTime = null;

            for (Medicine medicine : medicines) {
                // Skip medicines with 0 quantity
                if (medicine.getQuantity() <= 0) {
                    continue;
                }

                List<String> alarmTimes = medicine.getAlarmTimes();
                if (alarmTimes != null) {
                    for (String timeStr : alarmTimes) {
                        try {
                            String[] timeParts = timeStr.split(":");
                            if (timeParts.length == 2) {
                                int hour = Integer.parseInt(timeParts[0]);
                                int minute = Integer.parseInt(timeParts[1]);
                                int timeInMinutes = hour * 60 + minute;

                                // If time is later today
                                if (timeInMinutes > currentTimeInMinutes) {
                                    if (timeInMinutes < nextTimeInMinutes) {
                                        nextTimeInMinutes = timeInMinutes;
                                        nextMedicine = medicine;
                                        nextAlarmTime = timeStr;
                                    }
                                }
                                // If time is tomorrow (add 24 hours)
                                else {
                                    int tomorrowTimeInMinutes = timeInMinutes + (24 * 60);
                                    if (tomorrowTimeInMinutes < nextTimeInMinutes) {
                                        nextTimeInMinutes = tomorrowTimeInMinutes;
                                        nextMedicine = medicine;
                                        nextAlarmTime = timeStr;
                                    }
                                }
                            }
                        } catch (NumberFormatException e) {
                            Log.w(TAG, "Invalid time format: " + timeStr);
                        }
                    }
                }
            }

            // Create a copy with only the next alarm time
            if (nextMedicine != null && nextAlarmTime != null) {
                Medicine result = new Medicine(nextMedicine.getName(), nextMedicine.getQuantity());
                result.addAlarmTime(nextAlarmTime);
                return result;
            }

            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error getting next medicine: " + e.getMessage(), e);
            return null;
        }
    }

    // ==================== LOG MANAGEMENT ====================

    public void addLogEntry(MedicineLogEntry entry) {
        try {
            if (entry != null) {
                logEntries.add(0, entry); // Add to beginning for newest first
                saveLogEntries();
                Log.d(TAG, "Log entry added: " + entry.getMedicineName());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error adding log entry: " + e.getMessage(), e);
        }
    }

    public List<MedicineLogEntry> getMedicineLogEntries() {
        return new ArrayList<>(logEntries);
    }

    public void clearLogEntries() {
        try {
            logEntries.clear();
            saveLogEntries();
            Log.d(TAG, "All log entries cleared");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing log entries: " + e.getMessage(), e);
        }
    }

    public void recordMedicineTaken(String medicineName) {
        try {
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

            Date now = new Date();
            String time = timeFormat.format(now);
            String date = dateFormat.format(now);

            // Decrease medicine quantity
            boolean quantityDecreased = decreaseMedicineQuantity(medicineName);

            // Create log entry
            MedicineLogEntry entry = new MedicineLogEntry(medicineName, time, date);
            addLogEntry(entry);

            if (quantityDecreased) {
                Log.d(TAG, "Medicine taken and quantity decreased: " + medicineName);
            } else {
                Log.w(TAG, "Medicine taken but quantity not decreased (may be out of stock): " + medicineName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error recording medicine taken: " + e.getMessage(), e);
        }
    }

    // ==================== UTILITY METHODS ====================

    public boolean hasMedicine(String medicineName) {
        try {
            if (medicineName == null) {
                return false;
            }

            for (Medicine medicine : medicines) {
                if (medicine.getName().equals(medicineName)) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking if medicine exists: " + e.getMessage(), e);
        }
        return false;
    }

    public List<String> getAllAlarmTimes() {
        List<String> allTimes = new ArrayList<>();
        try {
            for (Medicine medicine : medicines) {
                List<String> alarmTimes = medicine.getAlarmTimes();
                if (alarmTimes != null) {
                    for (String time : alarmTimes) {
                        if (!allTimes.contains(time)) {
                            allTimes.add(time);
                        }
                    }
                }
            }
            Collections.sort(allTimes);
        } catch (Exception e) {
            Log.e(TAG, "Error getting all alarm times: " + e.getMessage(), e);
        }
        return allTimes;
    }

    public boolean isMedicineLowStock(String medicineName, int threshold) {
        try {
            Medicine medicine = getMedicineByName(medicineName);
            if (medicine != null) {
                return medicine.getQuantity() <= threshold;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking low stock: " + e.getMessage(), e);
        }
        return false;
    }

    public List<Medicine> getOutOfStockMedicines() {
        List<Medicine> outOfStock = new ArrayList<>();
        try {
            for (Medicine medicine : medicines) {
                if (medicine.getQuantity() <= 0) {
                    outOfStock.add(medicine);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting out of stock medicines: " + e.getMessage(), e);
        }
        return outOfStock;
    }

    // ==================== PERSISTENCE METHODS ====================

    private void saveMedicines() {
        try {
            String json = gson.toJson(medicines);
            sharedPreferences.edit().putString(MEDICINES_KEY, json).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving medicines: " + e.getMessage(), e);
        }
    }

    private void loadMedicines() {
        try {
            String json = sharedPreferences.getString(MEDICINES_KEY, "");
            if (!json.isEmpty()) {
                Type type = new TypeToken<List<Medicine>>(){}.getType();
                List<Medicine> loadedMedicines = gson.fromJson(json, type);
                if (loadedMedicines != null) {
                    medicines = loadedMedicines;

                    // Reschedule all alarms after loading medicines
                    for (Medicine medicine : medicines) {
                        scheduleAllAlarmsForMedicine(medicine);
                    }
                    Log.d(TAG, "Medicines loaded and alarms rescheduled");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading medicines: " + e.getMessage(), e);
            medicines = new ArrayList<>();
        }
    }

    private void saveLogEntries() {
        try {
            String json = gson.toJson(logEntries);
            sharedPreferences.edit().putString(LOG_ENTRIES_KEY, json).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving log entries: " + e.getMessage(), e);
        }
    }

    private void loadLogEntries() {
        try {
            String json = sharedPreferences.getString(LOG_ENTRIES_KEY, "");
            if (!json.isEmpty()) {
                Type type = new TypeToken<List<MedicineLogEntry>>(){}.getType();
                List<MedicineLogEntry> loadedEntries = gson.fromJson(json, type);
                if (loadedEntries != null) {
                    logEntries = loadedEntries;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading log entries: " + e.getMessage(), e);
            logEntries = new ArrayList<>();
        }
    }
}