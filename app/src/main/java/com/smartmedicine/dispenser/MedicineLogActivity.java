package com.smartmedicine.dispenser;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import java.util.List;

public class MedicineLogActivity extends AppCompatActivity {

    private LinearLayout logContainer;
    private TextView emptyStateText;
    private MedicineManager medicineManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medicine_log);

        initViews();
        setupToolbar();

        medicineManager = MedicineManager.getInstance(this);
        updateLogDisplay();
    }

    private void initViews() {
        logContainer = findViewById(R.id.log_container);
        emptyStateText = findViewById(R.id.empty_state_text);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Medicine Log");
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateLogDisplay();
    }

    private void updateLogDisplay() {
        List<MedicineLogEntry> logEntries = medicineManager.getMedicineLog();

        if (logEntries.isEmpty()) {
            emptyStateText.setVisibility(View.VISIBLE);
            // Hide any existing log cards
            for (int i = logContainer.getChildCount() - 1; i >= 0; i--) {
                View child = logContainer.getChildAt(i);
                if (child instanceof CardView) {
                    logContainer.removeView(child);
                }
            }
        } else {
            emptyStateText.setVisibility(View.GONE);
            displayLogEntries(logEntries);
        }
    }

    private void displayLogEntries(List<MedicineLogEntry> logEntries) {
        // Clear existing log cards
        for (int i = logContainer.getChildCount() - 1; i >= 0; i--) {
            View child = logContainer.getChildAt(i);
            if (child instanceof CardView) {
                logContainer.removeView(child);
            }
        }

        // Add log entry cards
        for (MedicineLogEntry entry : logEntries) {
            CardView logCard = createLogEntryCard(entry);
            logContainer.addView(logCard);
        }
    }

    private CardView createLogEntryCard(MedicineLogEntry entry) {
        CardView cardView = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 16);
        cardView.setLayoutParams(cardParams);
        cardView.setCardElevation(2);
        cardView.setRadius(8);
        cardView.setCardBackgroundColor(getResources().getColor(R.color.background_gray));

        LinearLayout cardContent = new LinearLayout(this);
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setPadding(20, 20, 20, 20);

        // Medicine name and "taken" text
        TextView medicineText = new TextView(this);
        medicineText.setText(entry.getMedicineName() + " taken");
        medicineText.setTextSize(16);
        medicineText.setTextColor(getResources().getColor(R.color.text_primary));
        medicineText.setTypeface(null, Typeface.BOLD); // Corrected line

        // Date and time
        TextView timeText = new TextView(this);
        timeText.setText("at " + entry.getTakenTime() + " on " + entry.getTakenDate());
        timeText.setTextSize(14);
        timeText.setTextColor(getResources().getColor(R.color.text_secondary));

        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        timeParams.setMargins(0, 8, 0, 0);
        timeText.setLayoutParams(timeParams);

        cardContent.addView(medicineText);
        cardContent.addView(timeText);
        cardView.addView(cardContent);

        return cardView;
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}