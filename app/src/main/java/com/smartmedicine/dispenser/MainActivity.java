package com.smartmedicine.dispenser;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.util.List;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

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
        List<Medicine> medicines = medicineManager.getAllMedicines();

        if (medicines.isEmpty()) {
            emptyStateText.setVisibility(View.VISIBLE);
            // Hide any existing medicine cards
            for (int i = medicineDisplayContainer.getChildCount() - 1; i >= 0; i--) {
                View child = medicineDisplayContainer.getChildAt(i);
                if (child instanceof CardView) {
                    medicineDisplayContainer.removeView(child);
                }
            }
        } else {
            emptyStateText.setVisibility(View.GONE);
            displayMedicines(medicines);
        }
    }

    private void displayMedicines(List<Medicine> medicines) {
        // Clear existing medicine cards
        for (int i = medicineDisplayContainer.getChildCount() - 1; i >= 0; i--) {
            View child = medicineDisplayContainer.getChildAt(i);
            if (child instanceof CardView) {
                medicineDisplayContainer.removeView(child);
            }
        }

        // Add medicine cards
        for (Medicine medicine : medicines) {
            CardView medicineCard = createMedicineCard(medicine);
            medicineDisplayContainer.addView(medicineCard);
        }
    }

    private CardView createMedicineCard(Medicine medicine) {
        CardView cardView = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 32);
        cardView.setLayoutParams(cardParams);
        cardView.setCardElevation(8);
        cardView.setRadius(16);

        LinearLayout cardContent = new LinearLayout(this);
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setPadding(48, 48, 48, 48);

        TextView nameText = new TextView(this);
        nameText.setText(medicine.getName());
        nameText.setTextSize(28);
        nameText.setTextColor(getResources().getColor(R.color.primary_green));
        nameText.setGravity(View.TEXT_ALIGNMENT_CENTER);

        TextView quantityText = new TextView(this);
        String quantityStr = medicine.getQuantity() + " pill" + (medicine.getQuantity() > 1 ? "s" : "");
        quantityText.setText(quantityStr);
        quantityText.setTextSize(24);
        quantityText.setTextColor(getResources().getColor(R.color.text_primary));

        cardContent.addView(nameText);
        cardContent.addView(quantityText);

        // Add alarm times
        if (!medicine.getAlarmTimes().isEmpty()) {
            LinearLayout timesContainer = new LinearLayout(this);
            timesContainer.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams timesParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            timesParams.setMargins(0, 24, 0, 0);
            timesContainer.setLayoutParams(timesParams);

            for (String time : medicine.getAlarmTimes()) {
                TextView timeText = new TextView(this);
                timeText.setText(time);
                timeText.setBackgroundResource(R.drawable.time_badge_background);
                timeText.setPadding(20, 10, 20, 10);
                timeText.setTextColor(getResources().getColor(R.color.accent_blue));

                LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                timeParams.setMargins(0, 0, 16, 0);
                timeText.setLayoutParams(timeParams);

                timesContainer.addView(timeText);
            }
            cardContent.addView(timesContainer);
        }

        cardView.addView(cardContent);
        return cardView;
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_home) {
            // Already on home, do nothing
        } else if (id == R.id.nav_set_alarm) {
            startActivity(new Intent(this, SetAlarmActivity.class));
        } else if (id == R.id.nav_medicine_log) {
            startActivity(new Intent(this, MedicineLogActivity.class));
        } else if (id == R.id.nav_bluetooth) {
            startActivity(new Intent(this, BluetoothActivity.class));
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