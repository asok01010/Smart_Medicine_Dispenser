package com.smartmedicine.dispenser;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class SetAlarmActivity extends AppCompatActivity {

    private static final String TAG = "SetAlarmActivity";

    private EditText medicineNameEdit;
    private EditText medicineQuantityEdit;
    private Spinner hourSpinner;
    private Spinner minuteSpinner;
    private Spinner periodSpinner;
    private Button setAlarmBtn;
    private Button nextBtn;
    private LinearLayout alarmsContainer;
    private MedicineManager medicineManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_set_alarm);

        initViews();
        setupToolbar();
        setupSpinners();
        setupButtons();

        medicineManager = MedicineManager.getInstance(this);
        updateAlarmsList();
    }

    private void initViews() {
        medicineNameEdit = findViewById(R.id.medicine_name_edit);
        medicineQuantityEdit = findViewById(R.id.medicine_quantity_edit);
        hourSpinner = findViewById(R.id.hour_spinner);
        minuteSpinner = findViewById(R.id.minute_spinner);
        periodSpinner = findViewById(R.id.period_spinner);
        setAlarmBtn = findViewById(R.id.set_alarm_btn);
        nextBtn = findViewById(R.id.next_btn);
        alarmsContainer = findViewById(R.id.alarms_container);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Set Alarm");
        }
    }

    private void setupSpinners() {
        // Hour spinner (1-12)
        List<String> hours = new ArrayList<>();
        hours.add("Hour");
        for (int i = 1; i <= 12; i++) {
            hours.add(String.valueOf(i));
        }
        ArrayAdapter<String> hourAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, hours);
        hourAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        hourSpinner.setAdapter(hourAdapter);

        // Minute spinner (00-59)
        List<String> minutes = new ArrayList<>();
        minutes.add("Minute");
        for (int i = 0; i < 60; i++) {
            minutes.add(String.format("%02d", i));
        }
        ArrayAdapter<String> minuteAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, minutes);
        minuteAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        minuteSpinner.setAdapter(minuteAdapter);

        // Period spinner (AM/PM)
        List<String> periods = new ArrayList<>();
        periods.add("AM");
        periods.add("PM");
        ArrayAdapter<String> periodAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, periods);
        periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        periodSpinner.setAdapter(periodAdapter);
    }

    private void setupButtons() {
        setAlarmBtn.setOnClickListener(v -> setAlarm());
        nextBtn.setOnClickListener(v -> nextMedicine());
    }

    private void setAlarm() {
        try {
            String name = medicineNameEdit.getText().toString().trim();
            String quantityStr = medicineQuantityEdit.getText().toString().trim();
            String hour = hourSpinner.getSelectedItem().toString();
            String minute = minuteSpinner.getSelectedItem().toString();
            String period = periodSpinner.getSelectedItem().toString();

            // Validation
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter a medicine name", Toast.LENGTH_SHORT).show();
                return;
            }

            if (quantityStr.isEmpty()) {
                Toast.makeText(this, "Please enter a valid quantity", Toast.LENGTH_SHORT).show();
                return;
            }

            if (hour.equals("Hour") || minute.equals("Minute")) {
                Toast.makeText(this, "Please select a valid time", Toast.LENGTH_SHORT).show();
                return;
            }

            int quantity = Integer.parseInt(quantityStr);

            // Convert to 24-hour format for storage
            int hourInt = Integer.parseInt(hour);
            if (period.equals("PM") && hourInt != 12) {
                hourInt += 12;
            } else if (period.equals("AM") && hourInt == 12) {
                hourInt = 0;
            }

            String timeString24 = String.format("%02d:%02d", hourInt, Integer.parseInt(minute));
            String timeString12 = hour + ":" + minute + " " + period;

            Log.d(TAG, "Setting alarm for: " + name + " at " + timeString24 + " (" + timeString12 + ")");

            // Check if medicine already exists
            Medicine existingMedicine = medicineManager.getMedicineByName(name);

            if (existingMedicine != null) {
                // Medicine exists - check if this time already exists
                if (existingMedicine.getAlarmTimes().contains(timeString24)) {
                    Toast.makeText(this, "Alarm already exists for " + name + " at " + timeString12, Toast.LENGTH_LONG).show();
                    return;
                }

                // Add new alarm time to existing medicine
                existingMedicine.addAlarmTime(timeString24);

                // Update quantity if different
                if (existingMedicine.getQuantity() != quantity) {
                    existingMedicine.setQuantity(quantity);
                }

                // Save the updated medicine (this will schedule the new alarm)
                medicineManager.saveMedicine(existingMedicine);

                Log.d(TAG, "Added alarm time to existing medicine. Total alarms: " + existingMedicine.getAlarmTimes().size());
                Toast.makeText(this, "Added alarm for " + name + " at " + timeString12 +
                        "\nTotal alarms: " + existingMedicine.getAlarmTimes().size(), Toast.LENGTH_LONG).show();

            } else {
                // Medicine doesn't exist - create new one
                Medicine newMedicine = new Medicine(name, quantity);
                newMedicine.addAlarmTime(timeString24);

                // Add the new medicine (this will schedule the alarm)
                medicineManager.addMedicine(newMedicine);

                Log.d(TAG, "Created new medicine with first alarm");
                Toast.makeText(this, "Created " + name + " with alarm at " + timeString12, Toast.LENGTH_LONG).show();
            }

            // Update display
            updateAlarmsList();

            // Reset only time fields, keep medicine name and quantity for easy multiple alarm entry
            hourSpinner.setSelection(0);
            minuteSpinner.setSelection(0);
            periodSpinner.setSelection(0);

        } catch (Exception e) {
            Log.e(TAG, "Error setting alarm: " + e.getMessage(), e);
            Toast.makeText(this, "Error setting alarm: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void nextMedicine() {
        try {
            // First, check if there's a pending alarm to be set
            String name = medicineNameEdit.getText().toString().trim();
            String quantityStr = medicineQuantityEdit.getText().toString().trim();
            String hour = hourSpinner.getSelectedItem().toString();
            String minute = minuteSpinner.getSelectedItem().toString();
            String period = periodSpinner.getSelectedItem().toString();

            // If user has entered time but not set the alarm, ask if they want to set it first
            if (!name.isEmpty() && !quantityStr.isEmpty() &&
                    !hour.equals("Hour") && !minute.equals("Minute")) {

                new AlertDialog.Builder(this)
                        .setTitle("Set Pending Alarm?")
                        .setMessage("You have entered alarm details. Do you want to set this alarm before moving to the next medicine?")
                        .setPositiveButton("Yes, Set Alarm", (dialog, which) -> {
                            setAlarm(); // This will set the alarm
                            clearFormForNextMedicine();
                        })
                        .setNegativeButton("No, Skip", (dialog, which) -> {
                            clearFormForNextMedicine();
                        })
                        .show();
            } else {
                clearFormForNextMedicine();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in nextMedicine: " + e.getMessage(), e);
            clearFormForNextMedicine();
        }
    }

    private void clearFormForNextMedicine() {
        // Clear the form for next medicine
        medicineNameEdit.setText("");
        medicineQuantityEdit.setText("1");
        hourSpinner.setSelection(0);
        minuteSpinner.setSelection(0);
        periodSpinner.setSelection(0);

        // Focus on medicine name for next entry
        medicineNameEdit.requestFocus();

        Toast.makeText(this, "Ready for next medicine", Toast.LENGTH_SHORT).show();
    }

    private void cancelAlarm(String medicineName, String time24) {
        try {
            Log.d(TAG, "Cancelling alarm for: " + medicineName + " at " + time24);

            // Use MedicineManager's cancelAlarm method
            medicineManager.cancelAlarm(medicineName, time24);

            // Remove the alarm time from the medicine
            medicineManager.removeAlarmTime(medicineName, time24);

            // Update UI
            updateAlarmsList();

            // Convert back to 12-hour format for display
            String time12 = convertTo12Hour(time24);
            Toast.makeText(this, "Alarm cancelled for " + medicineName + " at " + time12, Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e(TAG, "Error cancelling alarm: " + e.getMessage(), e);
            Toast.makeText(this, "Error cancelling alarm", Toast.LENGTH_SHORT).show();
        }
    }

    private String convertTo12Hour(String time24) {
        try {
            String[] parts = time24.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);

            String period = hour >= 12 ? "PM" : "AM";
            if (hour == 0) {
                hour = 12;
            } else if (hour > 12) {
                hour -= 12;
            }

            return hour + ":" + String.format("%02d", minute) + " " + period;
        } catch (Exception e) {
            Log.e(TAG, "Error converting time: " + e.getMessage(), e);
            return time24;
        }
    }

    private void clearAllAlarms() {
        try {
            new AlertDialog.Builder(this)
                    .setTitle("Clear All Alarms")
                    .setMessage("Are you sure you want to cancel all alarms? This cannot be undone.")
                    .setPositiveButton("Yes, Clear All", (dialog, which) -> {
                        // Use MedicineManager's method to clear all
                        medicineManager.clearAllMedicines();

                        // Update UI
                        updateAlarmsList();

                        Toast.makeText(this, "All alarms cleared", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "Error clearing all alarms: " + e.getMessage(), e);
        }
    }

    private void updateAlarmsList() {
        try {
            alarmsContainer.removeAllViews();
            List<Medicine> medicines = medicineManager.getAllMedicines();

            Log.d(TAG, "Updating alarms list with " + medicines.size() + " medicines");

            // Create header with title and clear all button
            LinearLayout headerLayout = new LinearLayout(this);
            headerLayout.setOrientation(LinearLayout.HORIZONTAL);
            headerLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            headerLayout.setPadding(0, 0, 0, 16);

            // Title
            TextView titleText = new TextView(this);
            titleText.setText("Scheduled Alarms");
            titleText.setTextSize(18);
            titleText.setTextColor(getResources().getColor(R.color.primary_green));
            titleText.setTypeface(null, Typeface.BOLD);
            titleText.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1
            ));

            headerLayout.addView(titleText);

            // Only add Clear All button if there are alarms
            if (!medicines.isEmpty()) {
                Button clearAllButton = new Button(this);
                clearAllButton.setText("Clear All");
                clearAllButton.setBackgroundResource(R.drawable.button_danger_bg);
                clearAllButton.setTextColor(getResources().getColor(android.R.color.white));
                clearAllButton.setOnClickListener(v -> clearAllAlarms());
                headerLayout.addView(clearAllButton);
            }

            alarmsContainer.addView(headerLayout);

            if (medicines.isEmpty()) {
                TextView emptyText = new TextView(this);
                emptyText.setText("No alarms set yet\n\nEnter medicine details above and click 'Set Alarm' to create your first alarm.");
                emptyText.setGravity(View.TEXT_ALIGNMENT_CENTER);
                emptyText.setPadding(32, 32, 32, 32);
                emptyText.setTextColor(getResources().getColor(android.R.color.darker_gray));
                alarmsContainer.addView(emptyText);
            } else {
                // Count total alarms
                int totalAlarms = 0;
                for (Medicine medicine : medicines) {
                    totalAlarms += medicine.getAlarmTimes().size();
                }

                // Add summary
                TextView summaryText = new TextView(this);
                summaryText.setText("Total: " + medicines.size() + " medicine(s) with " + totalAlarms + " alarm(s)");
                summaryText.setTextSize(14);
                summaryText.setTextColor(getResources().getColor(R.color.text_secondary));
                summaryText.setPadding(0, 0, 0, 16);
                alarmsContainer.addView(summaryText);

                // Add medicine cards
                for (Medicine medicine : medicines) {
                    CardView medicineCard = createMedicineAlarmCard(medicine);
                    alarmsContainer.addView(medicineCard);

                    Log.d(TAG, "Added card for: " + medicine.getName() +
                            " with " + medicine.getAlarmTimes().size() + " alarms");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating alarms list: " + e.getMessage(), e);
        }
    }

    private CardView createMedicineAlarmCard(Medicine medicine) {
        try {
            CardView cardView = new CardView(this);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardParams.setMargins(0, 0, 0, 24);
            cardView.setLayoutParams(cardParams);
            cardView.setCardElevation(4);
            cardView.setRadius(8);

            LinearLayout cardContent = new LinearLayout(this);
            cardContent.setOrientation(LinearLayout.VERTICAL);
            cardContent.setPadding(24, 24, 24, 24);

            // Medicine header
            LinearLayout headerLayout = new LinearLayout(this);
            headerLayout.setOrientation(LinearLayout.HORIZONTAL);
            headerLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));

            TextView headerText = new TextView(this);
            String headerStr = medicine.getName() + " (" + medicine.getQuantity() +
                    " pill" + (medicine.getQuantity() != 1 ? "s" : "") + ")";
            headerText.setText(headerStr);
            headerText.setTextSize(16);
            headerText.setTextColor(getResources().getColor(R.color.text_primary));
            headerText.setTypeface(null, Typeface.BOLD);
            headerText.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1
            ));

            ImageButton editButton = new ImageButton(this);
            editButton.setImageResource(android.R.drawable.ic_menu_edit);
            editButton.setBackgroundResource(0);
            editButton.setOnClickListener(v -> showEditQuantityDialog(medicine));

            headerLayout.addView(headerText);
            headerLayout.addView(editButton);
            cardContent.addView(headerLayout);

            // Alarm times
            if (!medicine.getAlarmTimes().isEmpty()) {
                // Alarm count
                TextView alarmCountText = new TextView(this);
                alarmCountText.setText(medicine.getAlarmTimes().size() + " alarm(s) set:");
                alarmCountText.setTextSize(14);
                alarmCountText.setTextColor(getResources().getColor(R.color.text_secondary));
                alarmCountText.setPadding(0, 8, 0, 8);
                cardContent.addView(alarmCountText);

                LinearLayout timesContainer = new LinearLayout(this);
                timesContainer.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams timesParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                timesParams.setMargins(0, 8, 0, 0);
                timesContainer.setLayoutParams(timesParams);

                for (String time24 : medicine.getAlarmTimes()) {
                    LinearLayout timeRow = new LinearLayout(this);
                    timeRow.setOrientation(LinearLayout.HORIZONTAL);
                    timeRow.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    ));
                    timeRow.setPadding(0, 8, 0, 8);

                    TextView timeText = new TextView(this);
                    String time12 = convertTo12Hour(time24);
                    timeText.setText(time12);
                    timeText.setBackgroundResource(R.drawable.time_badge_background);
                    timeText.setPadding(16, 8, 16, 8);
                    timeText.setTextColor(getResources().getColor(R.color.accent_blue));
                    timeText.setLayoutParams(new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1
                    ));

                    ImageButton deleteButton = new ImageButton(this);
                    deleteButton.setImageResource(android.R.drawable.ic_menu_delete);
                    deleteButton.setBackgroundResource(0);
                    deleteButton.setOnClickListener(v -> {
                        new AlertDialog.Builder(this)
                                .setTitle("Cancel Alarm")
                                .setMessage("Cancel alarm for " + medicine.getName() + " at " + time12 + "?")
                                .setPositiveButton("Yes", (dialog, which) -> cancelAlarm(medicine.getName(), time24))
                                .setNegativeButton("No", null)
                                .show();
                    });

                    timeRow.addView(timeText);
                    timeRow.addView(deleteButton);
                    timesContainer.addView(timeRow);
                }
                cardContent.addView(timesContainer);
            }

            cardView.addView(cardContent);
            return cardView;

        } catch (Exception e) {
            Log.e(TAG, "Error creating medicine card: " + e.getMessage(), e);
            CardView errorCard = new CardView(this);
            TextView errorText = new TextView(this);
            errorText.setText("Error loading: " + medicine.getName());
            errorText.setPadding(16, 16, 16, 16);
            errorCard.addView(errorText);
            return errorCard;
        }
    }

    private void showEditQuantityDialog(Medicine medicine) {
        try {
            final EditText quantityInput = new EditText(this);
            quantityInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            quantityInput.setText(String.valueOf(medicine.getQuantity()));

            new AlertDialog.Builder(this)
                    .setTitle("Edit Quantity")
                    .setMessage("Update quantity for " + medicine.getName())
                    .setView(quantityInput)
                    .setPositiveButton("Update", (dialog, which) -> {
                        String newQuantityStr = quantityInput.getText().toString().trim();
                        if (!newQuantityStr.isEmpty()) {
                            int newQuantity = Integer.parseInt(newQuantityStr);
                            if (newQuantity > 0) {
                                medicineManager.updateMedicineQuantity(medicine.getName(), newQuantity);
                                updateAlarmsList();
                                Toast.makeText(this, "Quantity updated to " + newQuantity, Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, "Quantity must be greater than 0", Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing edit dialog: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}