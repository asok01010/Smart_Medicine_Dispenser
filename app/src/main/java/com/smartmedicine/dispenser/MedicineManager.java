package com.smartmedicine.dispenser;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MedicineManager {
    private static final String TAG = "MedicineManager";
    private static final String PREFS_NAME = "MedicinePrefs";
    private static final String MEDICINES_KEY = "medicines";
    private static final String LOG_ENTRIES_KEY = "log_entries";

    private static MedicineManager instance;
    private SharedPreferences sharedPreferences;
    private Gson gson;
    private List<Medicine> medicines;
    private List<MedicineLogEntry> logEntries;

    private MedicineManager(Context context) {
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

    // Medicine management methods
    public void addMedicine(Medicine medicine) {
        try {
            if (medicine != null) {
                medicines.add(medicine);
                saveMedicines();
                Log.d(TAG, "Medicine added: " + medicine.getName());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error adding medicine: " + e.getMessage(), e);
        }
    }

    public void saveMedicine(Medicine medicine) {
        try {
            if (medicine != null) {
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
                Log.d(TAG, "Medicine saved: " + medicine.getName());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving medicine: " + e.getMessage(), e);
        }
    }

    public void removeMedicine(Medicine medicine) {
        try {
            if (medicine != null && medicines.remove(medicine)) {
                saveMedicines();
                Log.d(TAG, "Medicine removed: " + medicine.getName());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error removing medicine: " + e.getMessage(), e);
        }
    }

    public void updateMedicine(Medicine oldMedicine, Medicine newMedicine) {
        try {
            int index = medicines.indexOf(oldMedicine);
            if (index != -1) {
                medicines.set(index, newMedicine);
                saveMedicines();
                Log.d(TAG, "Medicine updated: " + newMedicine.getName());
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

    public void updateMedicineQuantity(String medicineName, int newQuantity) {
        try {
            if (medicineName == null || newQuantity <= 0) {
                return;
            }

            for (Medicine medicine : medicines) {
                if (medicine.getName().equals(medicineName)) {
                    medicine.setQuantity(newQuantity);
                    saveMedicines();
                    Log.d(TAG, "Medicine quantity updated: " + medicineName + " -> " + newQuantity);
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating medicine quantity: " + e.getMessage(), e);
        }
    }

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
            medicines.clear();
            saveMedicines();
            Log.d(TAG, "All medicines cleared");
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

    // Medicine log management methods
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

    // Save and load methods
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

    // Utility method to create a log entry when medicine is taken
    public void recordMedicineTaken(String medicineName) {
        try {
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

            Date now = new Date();
            String time = timeFormat.format(now);
            String date = dateFormat.format(now);

            MedicineLogEntry entry = new MedicineLogEntry(medicineName, time, date);
            addLogEntry(entry);
        } catch (Exception e) {
            Log.e(TAG, "Error recording medicine taken: " + e.getMessage(), e);
        }
    }

    // Utility methods for alarm management
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

    public void addAlarmTimeToMedicine(String medicineName, String time) {
        try {
            if (medicineName == null || time == null) {
                return;
            }

            for (Medicine medicine : medicines) {
                if (medicine.getName().equals(medicineName)) {
                    medicine.addAlarmTime(time);
                    saveMedicines();
                    Log.d(TAG, "Alarm time added: " + time + " for " + medicineName);
                    return;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error adding alarm time to medicine: " + e.getMessage(), e);
        }
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
}
