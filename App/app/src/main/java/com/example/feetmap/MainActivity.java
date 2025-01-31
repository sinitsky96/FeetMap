package com.example.feetmap;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Toast;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.EditText;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.io.FileWriter;
import android.util.Log;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;


import java.util.List;

public class MainActivity extends AppCompatActivity implements FragmentManager.OnBackStackChangedListener {
    private static final String TAG = "MainActivity";
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final UUID MY_UUID = UUID.randomUUID();
    private long startTime;
    private TextView activityStatus;
    private EditText fileNameInput;
    private EditText actualStepCountInput;
    private EditText stepCountInput;
    private boolean isRunningMode = false;  // false for walking, true for running
    private Button modeButton;
    private Python py;
    private PyObject pyModule;
    private int totalStepCount = 0;  // This tracks estimated steps

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Initialize Python at the start
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
        py = Python.getInstance();
        pyModule = py.getModule("test");


        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportFragmentManager().addOnBackStackChangedListener(this);
        if(ContextCompat.checkSelfPermission(MainActivity.this,Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ){
            ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},0);
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment, new DevicesFragment(), "devices")
                    .commit();
        } else {
            onBackStackChanged();
        }

        activityStatus = findViewById(R.id.activityStatus);
        fileNameInput = findViewById(R.id.fileNameInput);
        actualStepCountInput = findViewById(R.id.actualStepCountInput);
        stepCountInput = findViewById(R.id.stepCountInput);
        modeButton = findViewById(R.id.modeButton);
        modeButton.setText("WALK");  // Set initial button text to WALK

        Button startButton = findViewById(R.id.startButton);
        Button stopButton = findViewById(R.id.stopButton);
        Button resetButton = findViewById(R.id.resetButton);
        Button saveButton = findViewById(R.id.saveButton);
        Button loadCsvButton = findViewById(R.id.loadCsvButton);

        modeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isRunningMode = !isRunningMode;
                modeButton.setText(isRunningMode ? "RUN" : "WALK");
                activityStatus.setText("Activity: " + (isRunningMode ? "Running" : "Walking"));
                TerminalFragment terminalFragment = (TerminalFragment) getSupportFragmentManager()
                    .findFragmentByTag("terminal");
                if (terminalFragment != null) {
                    terminalFragment.setActivityMode(isRunningMode);
                }
            }
        });

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "START button clicked");
                startTime = System.currentTimeMillis();
                activityStatus.setText("Activity: " + (isRunningMode ? "Running" : "Walking"));
                TerminalFragment terminalFragment = (TerminalFragment) getSupportFragmentManager()
                    .findFragmentByTag("terminal");
                if (terminalFragment != null) {
                    terminalFragment.setActivityMode(isRunningMode);
                    terminalFragment.startPlotting();
                    Log.d(TAG, "Called startPlotting on TerminalFragment");
                } else {
                    Log.e(TAG, "TerminalFragment not found");
                }
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Keep current activity status (Running/Walking)
                TerminalFragment terminalFragment = (TerminalFragment) getSupportFragmentManager()
                    .findFragmentByTag("terminal");
                if (terminalFragment != null) {
                    terminalFragment.stopPlotting();
                    Log.d(TAG, "Called stopPlotting on TerminalFragment");
                } else {
                    Log.e(TAG, "TerminalFragment not found");
                }
            }
        });

        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TerminalFragment terminalFragment = (TerminalFragment) getSupportFragmentManager()
                        .findFragmentByTag("terminal");
                if (terminalFragment != null) {
                    terminalFragment.clearChart();
                    // Reset total step count
                    totalStepCount = 0;
                    stepCountInput.setText("0");
                    // Start plotting immediately after reset
                    terminalFragment.startPlotting();
                    Log.d(TAG, "Chart and step count reset, plotting started");
                }
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String fileName = fileNameInput.getText().toString().trim();
                
                if (fileName.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Please enter a file name", Toast.LENGTH_SHORT).show();
                    return;
                }

                TerminalFragment terminalFragment = (TerminalFragment) getSupportFragmentManager()
                    .findFragmentByTag("terminal");
                if (terminalFragment != null) {
                    terminalFragment.setStepCount(totalStepCount);  // Save total steps
                    terminalFragment.setFileName(fileName);
                    terminalFragment.saveDataToCSV();
                    Log.d(TAG, "Called saveDataToCSV on TerminalFragment with total steps: " + totalStepCount);
                    
                    // Reset chart and filename, but keep step count
                    terminalFragment.clearChart();
                    fileNameInput.setText("");
                    
                    // Start plotting immediately after reset
                    terminalFragment.startPlotting();
                    Log.d(TAG, "Chart reset and plotting started after save");
                } else {
                    Log.e(TAG, "TerminalFragment not found");
                }
            }
        });

        loadCsvButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TerminalFragment terminalFragment = (TerminalFragment) getSupportFragmentManager()
                    .findFragmentByTag("terminal");
                if (terminalFragment != null) {
                    terminalFragment.showFileChooser();
                    Log.d(TAG, "Called showFileChooser on TerminalFragment");
                } else {
                    Log.e(TAG, "TerminalFragment not found");
                }
            }
        });
        }

    @Override
    public void onBackStackChanged() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(getSupportFragmentManager().getBackStackEntryCount()>0);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void connectToDevice(BluetoothDevice device) {
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
            bluetoothSocket.connect();
            // Save device address to SharedPreferences
            SharedPreferences prefs = getSharedPreferences("Tutorial6Prefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("LastDeviceAddress", device.getAddress());
            editor.apply();
        } catch (IOException e) {
            // Handle connection failure
        }
    }

    private void saveDataToCSV() {
        File path = getExternalFilesDir(null);
        File file = new File(path, "data.csv");
        try (FileWriter fw = new FileWriter(file, true);
             BufferedWriter bw = new BufferedWriter(fw);
             OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(file, true))) {
            // Example data
            String data = "Time,Activity,Steps\n" + System.currentTimeMillis() + ",Running,100\n";
            osw.write(data);
            Toast.makeText(this, "Data saved to " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Error saving data", Toast.LENGTH_SHORT).show();
        }
    }

    public void analyzeAccelerationData(ArrayList<Float> accX, ArrayList<Float> accY, ArrayList<Float> accZ) {
        try {
            Log.d(TAG, "Analyzing data: size=" + accX.size());
            
            // Convert ArrayList to primitive arrays
            float[] accXArray = new float[accX.size()];
            float[] accYArray = new float[accY.size()];
            float[] accZArray = new float[accZ.size()];
            
            for (int i = 0; i < accX.size(); i++) {
                accXArray[i] = accX.get(i);
                accYArray[i] = accY.get(i);
                accZArray[i] = accZ.get(i);
            }
            
            Log.d(TAG, "Calling Python with arrays of size " + accXArray.length);
            
            // Call Python analysis function
            PyObject result = pyModule.callAttr("analyze_movement", accXArray, accYArray, accZArray, 
                isRunningMode ? 30.0 : 11.6); // Higher threshold for running
            
            Log.d(TAG, "Python result: " + (result != null ? result.toString() : "null"));
            
            if (result == null) {
                Log.e(TAG, "Python returned null result");
                Toast.makeText(this, "Error: Analysis failed", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Get values from the result array
            List<PyObject> resultList = result.asList();
            if (resultList.size() >= 3) {
                int newSteps = resultList.get(0).toInt();  // First element is peak count
                PyObject peaks = resultList.get(1);         // Second element is peaks array
                PyObject accNorm = resultList.get(2);       // Third element is acc_norm array
                
                Log.d(TAG, "New steps detected: " + newSteps);
                
                if (newSteps >= 0) {
                    totalStepCount += newSteps;
                    String activityType = isRunningMode ? "Running" : "Walking";
                    String message = activityType + ": Estimated steps: " + totalStepCount;
                    Log.d(TAG, message);
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    
                    // Update estimated step count input
                    stepCountInput.setText(String.valueOf(totalStepCount));
                } else {
                    Log.e(TAG, "Invalid step count");
                    Toast.makeText(this, "Error: Invalid step count", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.e(TAG, "Invalid result format");
                Toast.makeText(this, "Error: Invalid analysis result", Toast.LENGTH_SHORT).show();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error analyzing acceleration data: " + e.getMessage());
            Log.e(TAG, "Stack trace: ", e);
            Toast.makeText(this, "Error analyzing data", Toast.LENGTH_SHORT).show();
        }
    }

}
