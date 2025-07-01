package com.smartmedicine.dispenser;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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
    private Button btnClearAll;
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
            btnClearAll = findViewById(R.id.btn_clear_all);

            // Initialize medicine manager
            medicineManager = MedicineManager.getInstance(this);

            // Setup clear all button click listener
            btnClearAll.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showClearAllConfirmationDialog();
                }
            });

            // Load medicine logs
            loadMedicineLogs();

        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Error loading medicine log: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void showClearAllConfirmationDialog() {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Clear All Logs");
            builder.setMessage("Are you sure you want to clear all medicine history? This action cannot be undone.");
            builder.setIcon(android.R.drawable.ic_dialog_alert);

            builder.setPositiveButton("Clear All", (dialog, which) -> {
                clearAllLogs();
            });

            builder.setNegativeButton("Cancel", (dialog, which) -> {
                dialog.dismiss();
            });

            AlertDialog dialog = builder.create();
            dialog.show();

        } catch (Exception e) {
            Log.e(TAG, "Error showing confirmation dialog: " + e.getMessage(), e);
            Toast.makeText(this, "Error showing dialog", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearAllLogs() {
        try {
            // Clear all log entries using MedicineManager
            medicineManager.clearLogEntries();

            // Reload the UI to show empty state
            loadMedicineLogs();

            // Show success message
            Toast.makeText(this, "All medicine logs cleared successfully", Toast.LENGTH_SHORT).show();

            Log.d(TAG, "All medicine logs cleared by user");

        } catch (Exception e) {
            Log.e(TAG, "Error clearing all logs: " + e.getMessage(), e);
            Toast.makeText(this, "Error clearing logs: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadMedicineLogs() {
        try {
            // Clear existing views
            logContainer.removeAllViews();

            // Get medicine log entries
            List<MedicineLogEntry> logEntries = medicineManager.getMedicineLogEntries();

            if (logEntries == null || logEntries.isEmpty()) {
                // Show empty message and hide clear button
                logContainer.addView(emptyLogText);
                emptyLogText.setVisibility(View.VISIBLE);
                btnClearAll.setVisibility(View.GONE);
            } else {
                // Hide empty message and show clear button
                emptyLogText.setVisibility(View.GONE);
                btnClearAll.setVisibility(View.VISIBLE);

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
            btnClearAll.setVisibility(View.GONE);
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