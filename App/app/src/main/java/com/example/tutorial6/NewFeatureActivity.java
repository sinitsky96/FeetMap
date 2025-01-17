package com.example.tutorial6;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.widget.Toolbar;

public class NewFeatureActivity extends AppCompatActivity implements FragmentManager.OnBackStackChangedListener {
    private static final String TAG = "NewFeatureActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_feature);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportFragmentManager().addOnBackStackChangedListener(this);

        if (savedInstanceState == null) {
            DevicesFragment fragment = new DevicesFragment();
            getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment_container, fragment, "devices")
                .commit();
        } else {
            onBackStackChanged();
        }
    }

    @Override
    public void onBackStackChanged() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(
            getSupportFragmentManager().getBackStackEntryCount() > 0);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    public void onDeviceSelected(String deviceAddress) {
        FSRFragment fragment = FSRFragment.newInstance(deviceAddress);
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragment_container, fragment, "fsr")
            .addToBackStack(null)
            .commit();
    }
} 