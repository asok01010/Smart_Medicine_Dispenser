package com.smartmedicine.dispenser;

public class MedicineLogEntry {
    private String medicineName;
    private String time;
    private String date;

    public MedicineLogEntry() {
        // Default constructor for Gson
    }

    public MedicineLogEntry(String medicineName, String time, String date) {
        this.medicineName = medicineName;
        this.time = time;
        this.date = date;
    }

    // Getter methods
    public String getMedicineName() {
        return medicineName;
    }

    public String getTime() {
        return time;
    }

    public String getDate() {
        return date;
    }

    // Setter methods
    public void setMedicineName(String medicineName) {
        this.medicineName = medicineName;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public void setDate(String date) {
        this.date = date;
    }

    @Override
    public String toString() {
        return "MedicineLogEntry{" +
                "medicineName='" + medicineName + '\'' +
                ", time='" + time + '\'' +
                ", date='" + date + '\'' +
                '}';
    }
}
