package com.example.feetmap;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;
import com.github.mikephil.charting.formatter.ValueFormatter;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class Temp extends Fragment implements ServiceConnection, SerialListener {
    private static final String TAG = "Temp";
    private static final String CSV_DIR = "csv_dir";
    private static final String TRACK_DIR = "track";
    private enum Connected { False, Pending, True }

    // Serial connection
    private String deviceAddress;
    private SerialService service;
    private Connected connected = Connected.False;
    private boolean initialStart = true;

    // UI Elements
    private FootHeatmapView heatmapView;
    private LineChart imuChart;
    private Button btnStartTracking;
//    private Button btnReturn;

    // Tracking state
    private boolean isTracking = false;
    private long trackingStartTime;
    private ArrayList<RunningDataPoint> trackingData = new ArrayList<>();

    public static Temp newInstance(String deviceAddress) {
        Temp fragment = new Temp();
        Bundle args = new Bundle();
        args.putString("device", deviceAddress);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        deviceAddress = getArguments() != null ? getArguments().getString("device") : null;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_temp, container, false);

        heatmapView = view.findViewById(R.id.heatmapView);
        heatmapView.setFootImage(R.mipmap.footpic);
        imuChart = view.findViewById(R.id.imuChart);
        btnStartTracking = view.findViewById(R.id.btnStartTracking);
//        btnReturn = view.findViewById(R.id.btnReturn);

        setupIMUChart();
        setupTracking();

//        btnReturn.setOnClickListener(v -> {
//            disconnect();
//            getActivity().getSupportFragmentManager().popBackStack();
//        });

        return view;
    }

    private void setupIMUChart() {
        // Enable and customize the chart's "description," which acts like a title
        imuChart.getDescription().setEnabled(true);
        imuChart.getDescription().setText("IMU Data Analysis");
        imuChart.getDescription().setTextSize(12f);
        imuChart.getDescription().setTextColor(Color.WHITE);

        // Enable the legend so the color–label matching is visible
        imuChart.getLegend().setEnabled(true);

        imuChart.setTouchEnabled(true);
        imuChart.setDragEnabled(true);
        imuChart.setScaleEnabled(true);

        imuChart.getLegend().setEnabled(true);
        imuChart.getLegend().setTextColor(Color.WHITE);
        imuChart.getLegend().setTextSize(12f);

        // Y-axis settings
        imuChart.getAxisLeft().setEnabled(true);
        imuChart.getAxisLeft().setDrawLabels(true);
        imuChart.getAxisLeft().setTextColor(Color.WHITE);
        imuChart.getAxisLeft().setTextSize(12f);
        imuChart.getAxisLeft().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                // Return the formatted string for your Y-axis
                return String.format(Locale.US, "%.2f", value);
            }
        });

        // If you only want the left axis visible, hide the right one
        imuChart.getAxisRight().setEnabled(false);

        // You can keep your existing code that initializes the LineData, etc.
        LineData data = new LineData();
        data.setValueTextColor(Color.WHITE);
        imuChart.setData(data);
    }

    private void setupTracking() {
        btnStartTracking.setOnClickListener(v -> {
            if (!isTracking) {
                startTracking();
                btnStartTracking.setText("Stop Tracking");
            } else {
                stopTracking();
                btnStartTracking.setText("Start Tracking");
            }
            isTracking = !isTracking;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (initialStart && service != null) {
            initialStart = false;
            connect();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (service != null)
            service.attach(this);
        else {
            getActivity().startService(new Intent(getActivity(), SerialService.class));
            getActivity().bindService(new Intent(getActivity(), SerialService.class), this,
                    Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    public void onStop() {
        if (service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        try {
            getActivity().unbindService(this);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if (initialStart) {
            initialStart = false;
            connect();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            connected = Connected.Pending;
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), device);
            service.connect(socket);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
    }

    @Override
    public void onSerialConnect() {
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        disconnect();
        Toast.makeText(getActivity(), "Connection failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        getActivity().getSupportFragmentManager().popBackStack();
    }

    @Override
    public void onSerialRead(byte[] data) {
        try {
            String message = new String(data);
            String[] lines = message.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.contains(",")) {
                    String[] values = line.split(",");
                    if (values.length >= 6) {
                        try {
                            float accX = Float.parseFloat(values[0].trim());
                            float accY = Float.parseFloat(values[1].trim());
                            float accZ = Float.parseFloat(values[2].trim());
                            float fsr1 = Float.parseFloat(values[3].trim());
                            float fsr2 = Float.parseFloat(values[4].trim());
                            float fsr3 = Float.parseFloat(values[5].trim());

                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    updateVisualizations(accX, accY, accZ, fsr1, fsr2, fsr3);
                                    if (isTracking) {
                                        saveDataPoint(accX, accY, accZ, fsr1, fsr2, fsr3);
                                    }
                                });
                            }
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "Error parsing values: " + e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing data: " + e.getMessage());
        }
    }

    @Override
    public void onSerialIoError(Exception e) {
        disconnect();
        Toast.makeText(getActivity(), "Connection lost: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        getActivity().getSupportFragmentManager().popBackStack();
    }

    private void updateVisualizations(float accX, float accY, float accZ,
                                      float fsr1, float fsr2, float fsr3) {
        // Update heatmap
        heatmapView.updateValues(new float[]{fsr1, fsr2, fsr3});

        // Update IMU chart
        LineData data = imuChart.getData();
        if (data != null) {
            ILineDataSet setX = data.getDataSetByIndex(0);
            ILineDataSet setY = data.getDataSetByIndex(1);
            ILineDataSet setZ = data.getDataSetByIndex(2);

            if (setX == null) {
                setX = createSet("AccX", Color.RED);
                setY = createSet("AccY", Color.GREEN);
                setZ = createSet("AccZ", Color.BLUE);
                data.addDataSet(setX);
                data.addDataSet(setY);
                data.addDataSet(setZ);
            }

            data.addEntry(new Entry(setX.getEntryCount(), accX), 0);
            data.addEntry(new Entry(setY.getEntryCount(), accY), 1);
            data.addEntry(new Entry(setZ.getEntryCount(), accZ), 2);

            data.notifyDataChanged();
            imuChart.notifyDataSetChanged();
            imuChart.setVisibleXRangeMaximum(100);
            imuChart.moveViewToX(data.getEntryCount());

        }
    }

    private LineDataSet createSet(String label, int color) {
        LineDataSet set = new LineDataSet(null, label);
        set.setColor(color);
        set.setLineWidth(2f);
        set.setDrawCircles(false);
        set.setDrawValues(false);
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        return set;
    }

    private void startTracking() {
        trackingStartTime = System.currentTimeMillis();
        trackingData.clear();
    }

    private void stopTracking() {
        saveTrackingData();
    }

    private void saveDataPoint(float accX, float accY, float accZ,
                               float fsr1, float fsr2, float fsr3) {
        long timestamp = System.currentTimeMillis() - trackingStartTime;
        trackingData.add(new RunningDataPoint(timestamp, accX, accY, accZ, fsr1, fsr2, fsr3));
    }

    private File getStorageDir() {
        File baseDir = new File(Environment.getExternalStorageDirectory(), CSV_DIR);
        File trackDir = new File(baseDir, TRACK_DIR);

        if (!trackDir.exists()) {
            if (!trackDir.mkdirs()) {
                Log.e(TAG, "Failed to create directory");
                return null;
            }
        }
        return trackDir;
    }

    private void saveTrackingData() {
        try {
            File trackDir = getStorageDir();
            if (trackDir == null) {
                Toast.makeText(getActivity(), "Cannot access storage directory", Toast.LENGTH_SHORT).show();
                return;
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
            String timestamp = sdf.format(new Date(trackingStartTime));
            long duration = System.currentTimeMillis() - trackingStartTime;
            String filename = String.format("run_%s_%ds.csv", timestamp, duration / 1000);

            File file = new File(trackDir, filename);
            FileWriter writer = new FileWriter(file);
            writer.append("timestamp,accX,accY,accZ,fsr1,fsr2,fsr3\n");

            for (RunningDataPoint point : trackingData) {
                writer.append(String.format(Locale.US, "%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f\n",
                        point.timestamp, point.accX, point.accY, point.accZ,
                        point.fsr1, point.fsr2, point.fsr3));
            }

            writer.flush();
            writer.close();

            // Make the file visible to the system's Media Scanner
            MediaScannerConnection.scanFile(getActivity(),
                    new String[]{file.getAbsolutePath()}, null, null);

            Toast.makeText(getActivity(),
                    "Run data saved to: " + filename,
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error saving tracking data: " + e.getMessage());
            Toast.makeText(getActivity(),
                    "Error saving run data: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }
}