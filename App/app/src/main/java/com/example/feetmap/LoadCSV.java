package com.example.feetmap;

import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.opencsv.CSVReader;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LoadCSV extends AppCompatActivity {
    private static final String TAG = "LoadCSV";
    private TextView stepCountText;
    private ListView listView;
    private LineChart mpLineChart;
    private static final int FILE_SELECT_CODE = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_load_csv);

        stepCountText = findViewById(R.id.loadedStepCount);
        listView = findViewById(R.id.listView);
        mpLineChart = findViewById(R.id.csv_line_chart);

        // Configure chart
        configureChart();

        // Show files in the CSV directory
        showCSVFiles();
    }

    private void configureChart() {
        mpLineChart.getDescription().setEnabled(false);
        mpLineChart.setTouchEnabled(true);
        mpLineChart.setDragEnabled(true);
        mpLineChart.setScaleEnabled(true);
        mpLineChart.setPinchZoom(true);
        mpLineChart.setBackgroundColor(android.graphics.Color.BLACK);
        mpLineChart.setDrawGridBackground(false);

        // Configure X-axis
        XAxis xAxis = mpLineChart.getXAxis();
        xAxis.setDrawGridLines(true);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(android.graphics.Color.WHITE);
        xAxis.setGridColor(android.graphics.Color.GRAY);

        // Configure Y-axis
        mpLineChart.getAxisLeft().setDrawGridLines(true);
        mpLineChart.getAxisRight().setEnabled(false);
        mpLineChart.getAxisLeft().setTextColor(android.graphics.Color.WHITE);
        mpLineChart.getAxisLeft().setGridColor(android.graphics.Color.GRAY);
        mpLineChart.getLegend().setTextColor(android.graphics.Color.WHITE);
        mpLineChart.getLegend().setTextSize(12f);

        // Set initial ranges
        mpLineChart.setAutoScaleMinMaxEnabled(true);
        mpLineChart.getAxisLeft().setAxisMinimum(-20f);
        mpLineChart.getAxisLeft().setAxisMaximum(20f);
    }

    private void showCSVFiles() {
        File csvDir = new File("/sdcard/csv_dir/");
        if (!csvDir.exists()) {
            Toast.makeText(this, "No CSV files directory found", Toast.LENGTH_SHORT).show();
            return;
        }

        File[] files = csvDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".csv"));
        if (files == null || files.length == 0) {
            Toast.makeText(this, "No CSV files found", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> fileNames = new ArrayList<>();
        for (File file : files) {
            fileNames.add(file.getName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_list_item_1, fileNames);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            File selectedFile = files[position];
            loadCSVFile(selectedFile);
        });
    }

    private void loadCSVFile(File file) {
        try {
            CSVReader reader = new CSVReader(new FileReader(file));
            String[] nextLine;
            int actualSteps = -1;
            int estimatedSteps = -1;
            
            // Clear existing chart data
            mpLineChart.clear();
            
            // Create datasets for the chart
            ArrayList<Entry> xData = new ArrayList<>();
            ArrayList<Entry> yData = new ArrayList<>();
            ArrayList<Entry> zData = new ArrayList<>();
            
            boolean dataSection = false;
            
            while ((nextLine = reader.readNext()) != null) {
                if (nextLine.length >= 2) {
                    if (nextLine[0].equals("COUNT OF ACTUAL STEPS:")) {
                        try {
                            actualSteps = Integer.parseInt(nextLine[1].trim());
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "Error parsing actual step count: " + e.getMessage());
                        }
                    }
                    if (nextLine[0].equals("ESTIMATED NUMBER OF STEPS:")) {
                        try {
                            estimatedSteps = Integer.parseInt(nextLine[1].trim());
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "Error parsing estimated step count: " + e.getMessage());
                        }
                    }
                    
                    // Look for start of data section
                    if (nextLine[0].equals("ACC Z")) {
                        dataSection = true;
                        continue;
                    }
                    
                    // Parse data lines
                    if (dataSection && nextLine.length >= 4) {
                        try {
                            float z = Float.parseFloat(nextLine[0]);
                            float y = Float.parseFloat(nextLine[1]);
                            float x = Float.parseFloat(nextLine[2]);
                            float time = Float.parseFloat(nextLine[3]);
                            
                            xData.add(new Entry(time, x));
                            yData.add(new Entry(time, y));
                            zData.add(new Entry(time, z));
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "Error parsing data line: " + e.getMessage());
                        }
                    }
                }
            }
            reader.close();
            
            // Update step count display
            String stepText = String.format("Actual Steps: %d\nEstimated Steps: %d",
                actualSteps >= 0 ? actualSteps : 0,
                estimatedSteps >= 0 ? estimatedSteps : 0);
            stepCountText.setText(stepText);
            
            // Create and configure datasets
            LineDataSet xSet = new LineDataSet(xData, "X-axis");
            LineDataSet ySet = new LineDataSet(yData, "Y-axis");
            LineDataSet zSet = new LineDataSet(zData, "Z-axis");
            
            configureDataSet(xSet, android.graphics.Color.RED);
            configureDataSet(ySet, android.graphics.Color.GREEN);
            configureDataSet(zSet, android.graphics.Color.BLUE);
            
            // Add data to chart
            LineData lineData = new LineData(xSet, ySet, zSet);
            mpLineChart.setData(lineData);
            
            // Refresh chart
            mpLineChart.notifyDataSetChanged();
            mpLineChart.invalidate();
            mpLineChart.fitScreen();
            
            Toast.makeText(this, "Loaded file: " + file.getName(), Toast.LENGTH_SHORT).show();
            
        } catch (IOException e) {
            Log.e(TAG, "Error reading CSV: " + e.getMessage());
            Toast.makeText(this, "Error reading file", Toast.LENGTH_SHORT).show();
        }
    }

    private void configureDataSet(LineDataSet dataSet, int color) {
        dataSet.setColor(color);
        dataSet.setDrawCircles(false);
        dataSet.setLineWidth(2f);
        dataSet.setDrawValues(false);
        dataSet.setHighlightEnabled(false);
        dataSet.setMode(LineDataSet.Mode.LINEAR);
        dataSet.setDrawFilled(false);
    }
}