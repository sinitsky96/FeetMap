package com.example.tutorial6;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class RunAnalysisFragment extends Fragment {
    private static final String CSV_DIR = "csv_dir";
    private static final String TRACK_DIR = "track";
    private RunTimelineView timelineView;
    private LineChart imuChart;
    private LineChart fsrChart;
    private TextView tvScore;
    private List<RunningDataPoint> runData = new ArrayList<>();
    private ActivityResultLauncher<Intent> filePicker;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        filePicker = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Uri uri = result.getData().getData();
                        loadRunData(uri);
                    }
                });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_run_analysis, container, false);

        timelineView = view.findViewById(R.id.timelineView);
        imuChart = view.findViewById(R.id.imuAnalysisChart);
        fsrChart = view.findViewById(R.id.fsrAnalysisChart);
        tvScore = view.findViewById(R.id.tvScore);

        setupCharts();
        setupTimelineView();

        view.findViewById(R.id.btnSelectRun).setOnClickListener(v -> selectRunFile());

        return view;
    }

//    private void setupCharts() {
//        // Setup IMU chart
//        imuChart.getDescription().setEnabled(false);
//        imuChart.setTouchEnabled(true);
//        imuChart.setDragEnabled(true);
//        imuChart.setScaleEnabled(true);
//
//        // Setup FSR chart
//        fsrChart.getDescription().setEnabled(false);
//        fsrChart.setTouchEnabled(true);
//        fsrChart.setDragEnabled(true);
//        fsrChart.setScaleEnabled(true);
//    }

    private void setupCharts() {
        // Common settings for both charts
        for (LineChart chart : new LineChart[]{imuChart, fsrChart}) {
            chart.getDescription().setEnabled(false);
            chart.setTouchEnabled(true);
            chart.setDragEnabled(true);
            chart.setScaleEnabled(true);

            // Set consistent paddings
            chart.setViewPortOffsets(60f, 20f, 30f, 50f);

            // Enable synchronized scrolling between charts
            chart.setOnChartGestureListener(new OnChartGestureListener() {
                @Override
                public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {}

                @Override
                public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {}

                @Override
                public void onChartLongPressed(MotionEvent me) {}

                @Override
                public void onChartDoubleTapped(MotionEvent me) {}

                @Override
                public void onChartSingleTapped(MotionEvent me) {}

                @Override
                public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {}

                @Override
                public void onChartScale(MotionEvent me, float scaleX, float scaleY) {
                    syncCharts(chart);
                }

                @Override
                public void onChartTranslate(MotionEvent me, float dX, float dY) {
                    syncCharts(chart);
                }
            });
        }
    }

    private void syncCharts(LineChart sourceChart) {
        LineChart targetChart = (sourceChart == imuChart) ? fsrChart : imuChart;

        // Sync the visible range
        targetChart.setVisibleXRangeMaximum(sourceChart.getVisibleXRange());
        targetChart.setVisibleXRangeMinimum(sourceChart.getVisibleXRange());
        targetChart.moveViewToX(sourceChart.getLowestVisibleX());

        // Force refresh
        targetChart.invalidate();
    }

    // In RunAnalysisFragment
    private void setupTimelineView() {
        timelineView.setOnTimeSelectListener(timestamp -> {
            updateChartsPosition(timestamp);
        });
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

    private void selectRunFile() {
        File trackDir = getStorageDir();
        if (trackDir == null) {
            Toast.makeText(getActivity(), "Cannot access storage directory", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");

        // Set initial directory (Android 8.0 and above)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                    Uri.parse("content://com.android.externalstorage.documents/document/primary:" +
                            CSV_DIR + "/" + TRACK_DIR));
        }

        filePicker.launch(intent);
    }

    private void loadRunData(Uri uri) {
        try {
            runData.clear();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            getActivity().getContentResolver().openInputStream(uri)));

            // Skip header
            reader.readLine();

            String line;
            double heelScore = 0;
            double midScore = 0;
            double toeScore = 0;

            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",");
                long timestamp = Long.parseLong(values[0]);

                RunningDataPoint point = new RunningDataPoint(
                        timestamp,
                        Float.parseFloat(values[1]),
                        Float.parseFloat(values[2]),
                        Float.parseFloat(values[3]),
                        Float.parseFloat(values[4]),
                        Float.parseFloat(values[5]),
                        Float.parseFloat(values[6])
                );

                runData.add(point);

                // Accumulate sensor scores
                heelScore += point.fsr1;
                midScore += point.fsr2;
                toeScore += point.fsr3;
            }

            // Calculate final score (0-100)
            double totalPressure = heelScore + midScore + toeScore;
            double balanceScore = 100 * (1 - Math.abs(heelScore/totalPressure - 0.33)
                    - Math.abs(midScore/totalPressure - 0.33)
                    - Math.abs(toeScore/totalPressure - 0.33));

            timelineView.setData(runData);
            updateCharts();
            tvScore.setText(String.format("Balance Score: %.1f/100",
                    Math.max(0, Math.min(100, balanceScore))));

        } catch (Exception e) {
            Toast.makeText(getActivity(),
                    "Error loading run data: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

//    private void loadRunData(Uri uri) {
//        try {
//            runData.clear();
//            BufferedReader reader = new BufferedReader(
//                    new InputStreamReader(
//                            getActivity().getContentResolver().openInputStream(uri)));
//
//            // Skip header
//            reader.readLine();
//
//            String line;
//            double totalEnergyWaste = 0;
//            List<RunTimelineView.TimelineMarker> markers = new ArrayList<>();
//            long startTime = -1;
//
//            while ((line = reader.readLine()) != null) {
//                String[] values = line.split(",");
//                long timestamp = Long.parseLong(values[0]);
//                if (startTime == -1) startTime = timestamp;
//
//                RunningDataPoint point = new RunningDataPoint(
//                        timestamp - startTime,
//                        Float.parseFloat(values[1]),
//                        Float.parseFloat(values[2]),
//                        Float.parseFloat(values[3]),
//                        Float.parseFloat(values[4]),
//                        Float.parseFloat(values[5]),
//                        Float.parseFloat(values[6])
//                );
//
//                runData.add(point);
//
//                if (point.hasHighHeelPressure()) {
//                    double energyWaste = point.getEnergyWaste();
//                    totalEnergyWaste += energyWaste;
//                    markers.add(new RunTimelineView.TimelineMarker(
//                            point.timestamp, energyWaste));
//                }
//            }
//
//            timelineView.setData(markers, runData.get(runData.size() - 1).timestamp);
//            updateCharts();
//            tvEnergyWaste.setText(String.format("Total Energy Waste: %.2f", totalEnergyWaste));
//
//        } catch (Exception e) {
//            Toast.makeText(getActivity(),
//                    "Error loading run data: " + e.getMessage(),
//                    Toast.LENGTH_SHORT).show();
//        }
//    }

    private void updateCharts() {
        // Update IMU chart
        ArrayList<Entry> entriesX = new ArrayList<>();
        ArrayList<Entry> entriesY = new ArrayList<>();
        ArrayList<Entry> entriesZ = new ArrayList<>();

        // Update FSR chart
        ArrayList<Entry> entriesFsr1 = new ArrayList<>();
        ArrayList<Entry> entriesFsr2 = new ArrayList<>();
        ArrayList<Entry> entriesFsr3 = new ArrayList<>();

        for (RunningDataPoint point : runData) {
            float time = point.timestamp / 1000f; // Convert to seconds

            entriesX.add(new Entry(time, point.accX));
            entriesY.add(new Entry(time, point.accY));
            entriesZ.add(new Entry(time, point.accZ));

            entriesFsr1.add(new Entry(time, point.fsr1));
            entriesFsr2.add(new Entry(time, point.fsr2));
            entriesFsr3.add(new Entry(time, point.fsr3));
        }

        // Set IMU data
        LineData imuData = new LineData(
                createDataSet(entriesX, "AccX", Color.RED),
                createDataSet(entriesY, "AccY", Color.GREEN),
                createDataSet(entriesZ, "AccZ", Color.BLUE)
        );
        imuChart.setData(imuData);
        imuChart.invalidate();

        // Set FSR data
        LineData fsrData = new LineData(
                createDataSet(entriesFsr1, "Heel", Color.RED),
                createDataSet(entriesFsr2, "Mid", Color.GREEN),
                createDataSet(entriesFsr3, "Toe", Color.BLUE)
        );
        fsrChart.setData(fsrData);
        fsrChart.invalidate();


//        imuChart.setVisibleXRangeMinimum(5f);  // Show at least 5 seconds
        imuChart.setVisibleXRangeMaximum(20f); // Show at most 30 seconds
//        fsrChart.setVisibleXRangeMinimum(5f);
        fsrChart.setVisibleXRangeMaximum(20f);
    }

    private void updateChartsPosition(long timestamp) {
        float seconds = timestamp / 1000f;
        imuChart.moveViewToX(seconds);
        fsrChart.moveViewToX(seconds);
    }

    private LineDataSet createDataSet(List<Entry> entries, String label, int color) {
        LineDataSet set = new LineDataSet(entries, label);
        set.setColor(color);
        set.setDrawCircles(false);
        set.setLineWidth(2f);
        set.setDrawValues(false);
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        return set;
    }
}