package com.smartmedicine.dispenser;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import java.util.List;

public class MedicineLogActivity extends AppCompatActivity {

    private static final String TAG = "MedicineLogActivity";

    private LinearLayout logContainer;
    private TextView emptyLogText;
    private MedicineManager medicineManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setContentView(R.layout.activity_medicine_log);

            // Setup toolbar
            Toolbar toolbar = findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setTitle("Medicine History");
            }

            // Initialize views
            logContainer = findViewById(R.id.log_container);
            emptyLogText = findViewById(R.id.empty_log_text);

            // Initialize medicine manager
            medicineManager = MedicineManager.getInstance(this);

            // Load medicine logs
            loadMedicineLogs();

        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Error loading medicine log: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void loadMedicineLogs() {
        try {
            // Clear existing views
            logContainer.removeAllViews();

            // Get medicine log entries
            List<MedicineLogEntry> logEntries = medicineManager.getMedicineLogEntries();

            if (logEntries == null || logEntries.isEmpty()) {
                // Show empty message
                logContainer.addView(emptyLogText);
                emptyLogText.setVisibility(View.VISIBLE);
            } else {
                // Hide empty message
                emptyLogText.setVisibility(View.GONE);

                // Add log entries
                for (MedicineLogEntry entry : logEntries) {
                    addLogEntryView(entry);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error loading medicine logs: " + e.getMessage(), e);
            Toast.makeText(this, "Error loading logs: " + e.getMessage(), Toast.LENGTH_SHORT).show();

            // Show empty message as fallback
            logContainer.removeAllViews();
            logContainer.addView(emptyLogText);
            emptyLogText.setVisibility(View.VISIBLE);
        }
    }

    private void addLogEntryView(MedicineLogEntry entry) {
        try {
            // Create card view
            CardView cardView = new CardView(this);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardParams.setMargins(0, 0, 0, 16);
            cardView.setLayoutParams(cardParams);
            cardView.setCardElevation(4);
            cardView.setRadius(8);
            cardView.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_background));

            // Create content layout
            LinearLayout contentLayout = new LinearLayout(this);
            contentLayout.setOrientation(LinearLayout.VERTICAL);
            contentLayout.setPadding(16, 16, 16, 16);

            // Medicine name
            TextView nameText = new TextView(this);
            nameText.setText(entry.getMedicineName());
            nameText.setTextSize(18);
            nameText.setTextColor(ContextCompat.getColor(this, R.color.primary_green));
            nameText.setTypeface(null, android.graphics.Typeface.BOLD);

            // Time and date
            TextView timeText = new TextView(this);
            timeText.setText("Taken at: " + entry.getTime());
            timeText.setTextSize(14);
            timeText.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            timeText.setPadding(0, 8, 0, 0);

            TextView dateText = new TextView(this);
            dateText.setText("Date: " + entry.getDate());
            dateText.setTextSize(14);
            dateText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            dateText.setPadding(0, 4, 0, 0);

            // Add views to content layout
            contentLayout.addView(nameText);
            contentLayout.addView(timeText);
            contentLayout.addView(dateText);

            // Add content to card
            cardView.addView(contentLayout);

            // Add card to container
            logContainer.addView(cardView);

        } catch (Exception e) {
            Log.e(TAG, "Error adding log entry view: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            // Reload logs when returning to activity
            loadMedicineLogs();
        } catch (Exception e) {
            Log.e(TAG, "Error in onResume: " + e.getMessage(), e);
        }
    }
}
