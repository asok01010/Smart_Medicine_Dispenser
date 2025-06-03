package com.smartmedicine.dispenser;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.util.List;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "MainActivity";

    private DrawerLayout drawerLayout;
    private LinearLayout medicineDisplayContainer;
    private TextView emptyStateText;
    private MedicineManager medicineManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        initViews();

        // Initialize medicine manager
        medicineManager = MedicineManager.getInstance(this);

        // Setup toolbar and navigation
        setupToolbarAndNavigation();

        // Update medicine display
        updateMedicineDisplay();
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        medicineDisplayContainer = findViewById(R.id.medicine_display_container);
        emptyStateText = findViewById(R.id.empty_state_text);
    }

    private void setupToolbarAndNavigation() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateMedicineDisplay();
    }

    private void updateMedicineDisplay() {
        try {
            List<Medicine> medicines = medicineManager.getAllMedicines();

            Log.d(TAG, "Updating display with " + medicines.size() + " medicines");

            if (medicines.isEmpty()) {
                emptyStateText.setVisibility(View.VISIBLE);
                // Hide any existing medicine cards
                clearMedicineCards();
            } else {
                emptyStateText.setVisibility(View.GONE);
                displayMedicines(medicines);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating medicine display: " + e.getMessage(), e);
            Toast.makeText(this, "Error loading medicines", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearMedicineCards() {
        for (int i = medicineDisplayContainer.getChildCount() - 1; i >= 0; i--) {
            View child = medicineDisplayContainer.getChildAt(i);
            if (child instanceof CardView) {
                medicineDisplayContainer.removeView(child);
            }
        }
    }

    private void displayMedicines(List<Medicine> medicines) {
        try {
            // Clear existing medicine cards
            clearMedicineCards();

            // Add medicine cards
            for (Medicine medicine : medicines) {
                CardView medicineCard = createMedicineCard(medicine);
                medicineDisplayContainer.addView(medicineCard);

                Log.d(TAG, "Added card for medicine: " + medicine.getName() +
                        " with " + medicine.getAlarmTimes().size() + " alarms");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error displaying medicines: " + e.getMessage(), e);
        }
    }

    private CardView createMedicineCard(Medicine medicine) {
        try {
            CardView cardView = new CardView(this);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardParams.setMargins(0, 0, 0, 32);
            cardView.setLayoutParams(cardParams);
            cardView.setCardElevation(8);
            cardView.setRadius(16);
            cardView.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_background));

            LinearLayout cardContent = new LinearLayout(this);
            cardContent.setOrientation(LinearLayout.VERTICAL);
            cardContent.setPadding(48, 48, 48, 48);

            // Medicine name
            TextView nameText = new TextView(this);
            nameText.setText(medicine.getName());
            nameText.setTextSize(28);
            nameText.setTextColor(ContextCompat.getColor(this, R.color.primary_green));
            nameText.setGravity(View.TEXT_ALIGNMENT_CENTER);
            nameText.setTypeface(null, android.graphics.Typeface.BOLD);

            // Quantity
            TextView quantityText = new TextView(this);
            String quantityStr = medicine.getQuantity() + " pill" + (medicine.getQuantity() != 1 ? "s" : "");
            quantityText.setText(quantityStr);
            quantityText.setTextSize(24);
            quantityText.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            quantityText.setGravity(View.TEXT_ALIGNMENT_CENTER);

            // Add low stock warning
            if (medicine.getQuantity() <= 5 && medicine.getQuantity() > 0) {
                quantityText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark));
                quantityStr += " (Low Stock!)";
                quantityText.setText(quantityStr);
            } else if (medicine.getQuantity() == 0) {
                quantityText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                quantityStr += " (Out of Stock!)";
                quantityText.setText(quantityStr);
            }

            cardContent.addView(nameText);
            cardContent.addView(quantityText);

            // Add alarm times section
            if (!medicine.getAlarmTimes().isEmpty()) {
                // Alarm times header
                TextView alarmHeader = new TextView(this);
                alarmHeader.setText("Alarm Times:");
                alarmHeader.setTextSize(18);
                alarmHeader.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                alarmHeader.setTypeface(null, android.graphics.Typeface.BOLD);
                LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                headerParams.setMargins(0, 24, 0, 8);
                alarmHeader.setLayoutParams(headerParams);
                cardContent.addView(alarmHeader);

                // Times container with better layout
                LinearLayout timesContainer = new LinearLayout(this);
                timesContainer.setOrientation(LinearLayout.HORIZONTAL);
                timesContainer.setPadding(0, 8, 0, 0);

                // Add each alarm time
                for (int i = 0; i < medicine.getAlarmTimes().size(); i++) {
                    String time = medicine.getAlarmTimes().get(i);
                    TextView timeText = new TextView(this);
                    timeText.setText(convertTo12Hour(time));
                    timeText.setBackgroundResource(R.drawable.time_badge_background);
                    timeText.setPadding(20, 10, 20, 10);
                    timeText.setTextColor(ContextCompat.getColor(this, R.color.accent_blue));
                    timeText.setTextSize(16);
                    timeText.setTypeface(null, android.graphics.Typeface.BOLD);

                    LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    timeParams.setMargins(0, 0, 16, 8);
                    timeText.setLayoutParams(timeParams);

                    timesContainer.addView(timeText);
                }
                cardContent.addView(timesContainer);

                // Show total alarms count
                TextView alarmCount = new TextView(this);
                alarmCount.setText("Total: " + medicine.getAlarmTimes().size() + " alarm" +
                        (medicine.getAlarmTimes().size() != 1 ? "s" : "") + " set");
                alarmCount.setTextSize(14);
                alarmCount.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                alarmCount.setTypeface(null, android.graphics.Typeface.ITALIC);
                LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                countParams.setMargins(0, 8, 0, 0);
                alarmCount.setLayoutParams(countParams);
                cardContent.addView(alarmCount);
            } else {
                // No alarms set
                TextView noAlarms = new TextView(this);
                noAlarms.setText("No alarms set");
                noAlarms.setTextSize(16);
                noAlarms.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                noAlarms.setTypeface(null, android.graphics.Typeface.ITALIC);
                LinearLayout.LayoutParams noAlarmParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                noAlarmParams.setMargins(0, 16, 0, 0);
                noAlarms.setLayoutParams(noAlarmParams);
                cardContent.addView(noAlarms);
            }

            cardView.addView(cardContent);
            return cardView;

        } catch (Exception e) {
            Log.e(TAG, "Error creating medicine card: " + e.getMessage(), e);
            // Return a simple error card
            CardView errorCard = new CardView(this);
            TextView errorText = new TextView(this);
            errorText.setText("Error loading medicine: " + medicine.getName());
            errorText.setPadding(16, 16, 16, 16);
            errorCard.addView(errorText);
            return errorCard;
        }
    }

    // Helper method to convert 24-hour format to 12-hour format
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

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        try {
            if (id == R.id.nav_home) {
                // Already on home, do nothing
            } else if (id == R.id.nav_set_alarm) {
                startActivity(new Intent(this, SetAlarmActivity.class));
            } else if (id == R.id.nav_medicine_log) {
                startActivity(new Intent(this, MedicineLogActivity.class));
            } else if (id == R.id.nav_bluetooth) {
                startActivity(new Intent(this, BluetoothActivity.class));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling navigation: " + e.getMessage(), e);
            Toast.makeText(this, "Error opening activity", Toast.LENGTH_SHORT).show();
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}