package com.smartmedicine.dispenser;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class MedicineManager {
    private static final String PREFS_NAME = "medicine_prefs";
    private static final String MEDICINES_KEY = "medicines";
    private static final String LOG_KEY = "medicine_log";

    private static MedicineManager instance;
    private SharedPreferences prefs;
    private Gson gson;

    private MedicineManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public static synchronized MedicineManager getInstance(Context context) {
        if (instance == null) {
            instance = new MedicineManager(context.getApplicationContext());
        }
        return instance;
    }

    public List<Medicine> getAllMedicines() {
        String json = prefs.getString(MEDICINES_KEY, "");
        if (json.isEmpty()) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<Medicine>>(){}.getType();
        return gson.fromJson(json, type);
    }

    public void saveMedicine(Medicine medicine) {
        List<Medicine> medicines = getAllMedicines();

        // Check if medicine already exists
        Medicine existingMedicine = null;
        for (Medicine m : medicines) {
            if (m.getName().equals(medicine.getName())) {
                existingMedicine = m;
                break;
            }
        }

        if (existingMedicine != null) {
            // Update existing medicine
            existingMedicine.setQuantity(medicine.getQuantity());
            for (String time : medicine.getAlarmTimes()) {
                existingMedicine.addAlarmTime(time);
            }
        } else {
            // Add new medicine
            medicines.add(medicine);
        }

        saveMedicines(medicines);
    }

    public void saveMedicines(List<Medicine> medicines) {
        String json = gson.toJson(medicines);
        prefs.edit().putString(MEDICINES_KEY, json).apply();
    }

    public void deleteMedicine(String medicineName) {
        List<Medicine> medicines = getAllMedicines();
        medicines.removeIf(medicine -> medicine.getName().equals(medicineName));
        saveMedicines(medicines);
    }

    public void removeAlarmTime(String medicineName, String time) {
        List<Medicine> medicines = getAllMedicines();
        for (Medicine medicine : medicines) {
            if (medicine.getName().equals(medicineName)) {
                medicine.removeAlarmTime(time);
                if (medicine.getAlarmTimes().isEmpty()) {
                    medicines.remove(medicine);
                }
                break;
            }
        }
        saveMedicines(medicines);
    }

    // New method to update medicine quantity
    public void updateMedicineQuantity(String medicineName, int newQuantity) {
        List<Medicine> medicines = getAllMedicines();
        for (Medicine medicine : medicines) {
            if (medicine.getName().equals(medicineName)) {
                medicine.setQuantity(newQuantity);
                break;
            }
        }
        saveMedicines(medicines);
    }

    // New method to clear all medicines
    public void clearAllMedicines() {
        prefs.edit().putString(MEDICINES_KEY, "").apply();
    }

    // Medicine log methods
    public List<MedicineLogEntry> getMedicineLog() {
        String json = prefs.getString(LOG_KEY, "");
        if (json.isEmpty()) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<MedicineLogEntry>>(){}.getType();
        return gson.fromJson(json, type);
    }

    public void addLogEntry(MedicineLogEntry entry) {
        List<MedicineLogEntry> log = getMedicineLog();
        log.add(0, entry); // Add to beginning
        String json = gson.toJson(log);
        prefs.edit().putString(LOG_KEY, json).apply();
    }
}