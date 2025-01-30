package com.example.feetmap;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

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
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class RunAnalysisFragment extends Fragment {

    private static final String ARG_CSV_URI = "arg_csv_uri";

    private RunTimelineView timelineView;
    private LineChart imuChart;
    private LineChart fsrChart;
    private TextView tvScore;

    private List<RunningDataPoint> runData = new ArrayList<>();

    // Create via newInstance so we can pass in the CSV Uri
    public static RunAnalysisFragment newInstance(String csvUriString) {
        RunAnalysisFragment fragment = new RunAnalysisFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CSV_URI, csvUriString);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

//        if (getArguments() != null) {
//            String csvUriString = getArguments().getString(ARG_CSV_URI);
//            if (csvUriString != null) {
//                loadRunData(Uri.parse(csvUriString));
//            }
//        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_run_analysis, container, false);

        timelineView = view.findViewById(R.id.timelineView);
        imuChart = view.findViewById(R.id.imuAnalysisChart);
        fsrChart = view.findViewById(R.id.fsrAnalysisChart);
        tvScore = view.findViewById(R.id.tvScore);

        if (getArguments() != null) {
            String csvUriString = getArguments().getString(ARG_CSV_URI);
            if (csvUriString != null) {
                loadRunData(Uri.parse(csvUriString));
            }
        }

        setupCharts();
        setupTimelineView();
        return view;
    }

    private void setupCharts() {
        // Common settings for both charts
        for (LineChart chart : new LineChart[]{imuChart, fsrChart}) {
            chart.getDescription().setEnabled(false);
            chart.setTouchEnabled(true);
            chart.setDragEnabled(true);
            chart.setScaleEnabled(true);
            chart.setViewPortOffsets(60f, 20f, 30f, 50f);

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
        targetChart.setVisibleXRangeMaximum(sourceChart.getVisibleXRange());
        targetChart.setVisibleXRangeMinimum(sourceChart.getVisibleXRange());
        targetChart.moveViewToX(sourceChart.getLowestVisibleX());
        targetChart.invalidate();
    }

    private void setupTimelineView() {
        timelineView.setOnTimeSelectListener(this::updateChartsPosition);
    }

    private void loadRunData(Uri uri) {
        try {
            runData.clear();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(requireContext().getContentResolver().openInputStream(uri))
            );

            // Skip header
            reader.readLine();

            double heelScore = 0;
            double midScore = 0;
            double toeScore = 0;

            String line;
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
            reader.close();

            // Calculate final score (0-100)
            double totalPressure = heelScore + midScore + toeScore;
            double balanceScore = 100 * (1
                    - Math.abs(heelScore / totalPressure - 0.33)
                    - Math.abs(midScore / totalPressure - 0.33)
                    - Math.abs(toeScore / totalPressure - 0.33));

            // Update UI
            timelineView.setData(runData);
            updateCharts();


        } catch (Exception e) {
            Toast.makeText(requireContext(),
                    "Error loading run data: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

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

        // IMU chart data sets
        LineDataSet setX = createDataSet(entriesX, "AccX (Red)", 0xFFFF0000);
        LineDataSet setY = createDataSet(entriesY, "AccY (Green)", 0xFF00FF00);
        LineDataSet setZ = createDataSet(entriesZ, "AccZ (Blue)", 0xFF0000FF);

        LineData imuData = new LineData(setX, setY, setZ);
        imuChart.setData(imuData);

        // Y-axis settings
        imuChart.getAxisLeft().setEnabled(true);
        imuChart.getAxisLeft().setDrawLabels(true);
        imuChart.getAxisLeft().setTextColor(Color.WHITE);
        imuChart.getAxisLeft().setTextSize(12f);

        // If you only want the left axis visible, hide the right one
        imuChart.getAxisRight().setEnabled(false);

        // Turn on the legend to show label–color matching
        imuChart.getLegend().setEnabled(true);
        imuChart.getLegend().setTextColor(Color.WHITE);
        imuChart.getLegend().setTextSize(12f);

        imuChart.getDescription().setEnabled(true);
        imuChart.getDescription().setText("IMU Data Analysis");
        imuChart.getDescription().setTextSize(12f);
        imuChart.getDescription().setTextColor(Color.WHITE); // Or any color you prefer

        imuChart.invalidate();

        // FSR chart data sets
        LineDataSet setHeel = createDataSet(entriesFsr1, "Heel (Red)", 0xFFFF0000);
        LineDataSet setMid = createDataSet(entriesFsr2, "Mid (Green)", 0xFF00FF00);
        LineDataSet setToe = createDataSet(entriesFsr3, "Toe (Blue)", 0xFF0000FF);

        LineData fsrData = new LineData(setHeel, setMid, setToe);
        fsrChart.setData(fsrData);

        // Y-axis settings
        fsrChart.getAxisLeft().setEnabled(true);
        fsrChart.getAxisLeft().setDrawLabels(true);
        fsrChart.getAxisLeft().setTextColor(Color.WHITE);
        fsrChart.getAxisLeft().setTextSize(12f);

        // If you only want the left axis visible, hide the right one
        fsrChart.getAxisRight().setEnabled(false);

        fsrChart.getLegend().setEnabled(true);
        fsrChart.getLegend().setTextColor(Color.WHITE);
        fsrChart.getLegend().setTextSize(12f);

        fsrChart.getDescription().setEnabled(true);
        fsrChart.getDescription().setText("FSR Sensor Data");
        fsrChart.getDescription().setTextSize(12f);
        fsrChart.getDescription().setTextColor(Color.WHITE);

        fsrChart.invalidate();

        imuChart.setVisibleXRangeMaximum(20f);
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
