package com.smartmedicine.dispenser;

public class MedicineLogEntry {
    private String medicineName;
    private String takenTime;
    private String takenDate;
    private long timestamp;

    public MedicineLogEntry(String medicineName, String takenTime, String takenDate) {
        this.medicineName = medicineName;
        this.takenTime = takenTime;
        this.takenDate = takenDate;
        this.timestamp = System.currentTimeMillis();
    }

    public MedicineLogEntry(String medicineName, String takenTime, String takenDate, long timestamp) {
        this.medicineName = medicineName;
        this.takenTime = takenTime;
        this.takenDate = takenDate;
        this.timestamp = timestamp;
    }

    // Getters and setters
    public String getMedicineName() {
        return medicineName;
    }

    public void setMedicineName(String medicineName) {
        this.medicineName = medicineName;
    }

    public String getTakenTime() {
        return takenTime;
    }

    public void setTakenTime(String takenTime) {
        this.takenTime = takenTime;
    }

    public String getTakenDate() {
        return takenDate;
    }

    public void setTakenDate(String takenDate) {
        this.takenDate = takenDate;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}