package com.smartmedicine.dispenser;

import java.util.ArrayList;
import java.util.List;

public class Medicine {
    private String name;
    private int quantity;
    private List<String> alarmTimes;

    public Medicine(String name, int quantity) {
        this.name = name;
        this.quantity = quantity;
        this.alarmTimes = new ArrayList<>();
    }

    public Medicine(String name, int quantity, List<String> alarmTimes) {
        this.name = name;
        this.quantity = quantity;
        this.alarmTimes = alarmTimes != null ? alarmTimes : new ArrayList<>();
    }

    // Getters and setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public List<String> getAlarmTimes() {
        return alarmTimes;
    }

    public void setAlarmTimes(List<String> alarmTimes) {
        this.alarmTimes = alarmTimes;
    }

    public void addAlarmTime(String time) {
        if (!alarmTimes.contains(time)) {
            alarmTimes.add(time);
        }
    }

    public void removeAlarmTime(String time) {
        alarmTimes.remove(time);
    }
}