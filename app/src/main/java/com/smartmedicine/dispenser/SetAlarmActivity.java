package com.smartmedicine.dispenser;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
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

        // Minute spinner (00-55 in 5-minute increments)
        List<String> minutes = new ArrayList<>();
        minutes.add("Minute");
        for (int i = 0; i < 60; i += 5) {
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
        String name = medicineNameEdit.getText().toString().trim();
        String quantityStr = medicineQuantityEdit.getText().toString().trim();
        String hour = hourSpinner.getSelectedItem().toString();
        String minute = minuteSpinner.getSelectedItem().toString();
        String period = periodSpinner.getSelectedItem().toString();

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
        String timeString = hour + ":" + minute + " " + period;

        // Create medicine object
        Medicine medicine = new Medicine(name, quantity);
        medicine.addAlarmTime(timeString);

        // Save medicine
        medicineManager.saveMedicine(medicine);

        // Schedule actual alarm
        scheduleAlarm(name, quantity, hour, minute, period);

        // Update display
        updateAlarmsList();

        // Reset only time fields, keep medicine name and quantity
        hourSpinner.setSelection(0);
        minuteSpinner.setSelection(0);

        Toast.makeText(this, "Alarm set successfully!", Toast.LENGTH_SHORT).show();
    }

    private void nextMedicine() {
        // First, check if there's a pending alarm to be set
        String name = medicineNameEdit.getText().toString().trim();
        String quantityStr = medicineQuantityEdit.getText().toString().trim();
        String hour = hourSpinner.getSelectedItem().toString();
        String minute = minuteSpinner.getSelectedItem().toString();
        String period = periodSpinner.getSelectedItem().toString();

        // If user has entered time but not set the alarm, ask if they want to set it first
        if (!name.isEmpty() && !quantityStr.isEmpty() &&
                !hour.equals("Hour") && !minute.equals("Minute")) {

            // Automatically set the alarm before moving to next medicine
            int quantity = Integer.parseInt(quantityStr);
            String timeString = hour + ":" + minute + " " + period;

            // Create medicine object
            Medicine medicine = new Medicine(name, quantity);
            medicine.addAlarmTime(timeString);

            // Save medicine
            medicineManager.saveMedicine(medicine);

            // Schedule actual alarm
            scheduleAlarm(name, quantity, hour, minute, period);

            // Update display
            updateAlarmsList();

            Toast.makeText(this, "Alarm set for " + name + " at " + timeString, Toast.LENGTH_SHORT).show();
        }

        // Clear the form for next medicine
        medicineNameEdit.setText("");
        medicineQuantityEdit.setText("1");
        hourSpinner.setSelection(0);
        minuteSpinner.setSelection(0);

        // Focus on medicine name for next entry
        medicineNameEdit.requestFocus();
    }

    private void scheduleAlarm(String medicineName, int quantity, String hour, String minute, String period) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.putExtra("medicine_name", medicineName);
        intent.putExtra("quantity", quantity);

        int requestCode = (medicineName + hour + minute + period).hashCode();
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Calculate alarm time
        Calendar calendar = Calendar.getInstance();
        int hourInt = Integer.parseInt(hour);
        int minuteInt = Integer.parseInt(minute);

        if (period.equals("PM") && hourInt != 12) {
            hourInt += 12;
        } else if (period.equals("AM") && hourInt == 12) {
            hourInt = 0;
        }

        calendar.set(Calendar.HOUR_OF_DAY, hourInt);
        calendar.set(Calendar.MINUTE, minuteInt);
        calendar.set(Calendar.SECOND, 0);

        // If time has passed today, schedule for tomorrow
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        if (alarmManager != null) {
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY, pendingIntent);
        }
    }

    private void updateAlarmsList() {
        alarmsContainer.removeAllViews();
        List<Medicine> medicines = medicineManager.getAllMedicines();

        if (medicines.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText("No alarms set yet");
            emptyText.setGravity(View.TEXT_ALIGNMENT_CENTER);
            emptyText.setPadding(32, 32, 32, 32);
            emptyText.setTextColor(getResources().getColor(android.R.color.darker_gray));
            alarmsContainer.addView(emptyText);
            return;
        }

        for (Medicine medicine : medicines) {
            CardView medicineCard = createMedicineAlarmCard(medicine);
            alarmsContainer.addView(medicineCard);
        }
    }

    private CardView createMedicineAlarmCard(Medicine medicine) {
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
        TextView headerText = new TextView(this);
        String headerStr = medicine.getName() + " (" + medicine.getQuantity() +
                " pill" + (medicine.getQuantity() > 1 ? "s" : "") + ")";
        headerText.setText(headerStr);
        headerText.setTextSize(16);
        headerText.setTextColor(getResources().getColor(R.color.text_primary));

        cardContent.addView(headerText);

        // Alarm times
        if (!medicine.getAlarmTimes().isEmpty()) {
            LinearLayout timesContainer = new LinearLayout(this);
            timesContainer.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams timesParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            timesParams.setMargins(0, 16, 0, 0);
            timesContainer.setLayoutParams(timesParams);

            for (String time : medicine.getAlarmTimes()) {
                TextView timeText = new TextView(this);
                timeText.setText(time);
                timeText.setBackgroundResource(R.drawable.time_badge_background);
                timeText.setPadding(16, 8, 16, 8);
                timeText.setTextColor(getResources().getColor(R.color.accent_blue));

                LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                timeParams.setMargins(0, 0, 12, 0);
                timeText.setLayoutParams(timeParams);

                timesContainer.addView(timeText);
            }
            cardContent.addView(timesContainer);
        }

        cardView.addView(cardContent);
        return cardView;
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}