package com.example.tutorial6;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class StartActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "Tutorial6Prefs";
    private static final String LAST_DEVICE_KEY = "LastDeviceAddress";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        Button btnMainApp = findViewById(R.id.btnMainApp);
        Button btnNewFeature = findViewById(R.id.btnNewFeature);

        // Get the last connected device address from SharedPreferences
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String lastDeviceAddress = prefs.getString(LAST_DEVICE_KEY, null);

        btnMainApp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(StartActivity.this, MainActivity.class);
                if (lastDeviceAddress != null) {
                    intent.putExtra("device", lastDeviceAddress);
                }
                startActivity(intent);
            }
        });

        btnNewFeature.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(StartActivity.this, NewFeatureActivity.class);
                if (lastDeviceAddress != null) {
                    intent.putExtra("device", lastDeviceAddress);
                }
                startActivity(intent);
            }
        });
    }
} 