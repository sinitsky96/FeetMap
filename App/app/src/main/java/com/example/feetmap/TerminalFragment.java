package com.example.feetmap;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.opencsv.CSVWriter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {
    private static final String TAG = "TerminalFragment";

    private enum Connected { False, Pending, True }

    private String deviceAddress;
    private SerialService service;

    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = false;
    private boolean pendingNewline = false;
    private String newline = TextUtil.newline_crlf;

    LineChart mpLineChart;
    LineDataSet lineDataSet1;
    ArrayList<ILineDataSet> dataSets = new ArrayList<>();
    LineData data;

    private float dataIndex = 0;
    private boolean isPlotting = false;

    private ArrayList<String[]> dataToSave = new ArrayList<>();

    private boolean isRunningMode = true;
    private int stepCount = 0;
    private String experimentStartTime = "";
    private long startTime;

    private String fileName = "data";

    private ArrayList<Float> accXData = new ArrayList<>();
    private ArrayList<Float> accYData = new ArrayList<>();
    private ArrayList<Float> accZData = new ArrayList<>();
    private ArrayList<Float> normData = new ArrayList<>();
    private static final int ANALYSIS_WINDOW = 100; // Analyze every 100 data points

    private int estimatedStepCount = 0;
    private int actualStepCount = 0;

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments() != null ? getArguments().getString("device") : null;

    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if(initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);

        mpLineChart = (LineChart) view.findViewById(R.id.line_chart);

        // Configure chart
        mpLineChart.getDescription().setEnabled(false);
        mpLineChart.setTouchEnabled(true);
        mpLineChart.setDragEnabled(true);
        mpLineChart.setScaleEnabled(true);
        mpLineChart.setPinchZoom(true);
        mpLineChart.setBackgroundColor(android.graphics.Color.BLACK);
        mpLineChart.setDrawGridBackground(false);
        mpLineChart.setMinOffset(10f); // Add padding
        mpLineChart.setExtraBottomOffset(10f); // Add bottom padding
        mpLineChart.setExtraTopOffset(10f); // Add top padding
        mpLineChart.setViewPortOffsets(60f, 20f, 20f, 60f); // Left, Top, Right, Bottom offsets

        // Configure X-axis
        XAxis xAxis = mpLineChart.getXAxis();
        xAxis.setDrawGridLines(true);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(android.graphics.Color.WHITE);
        xAxis.setGridColor(android.graphics.Color.GRAY);
        xAxis.setLabelCount(6, true);
        xAxis.setGranularity(1f);
        xAxis.setAxisMinimum(0f);

        // Configure Y-axis
        mpLineChart.getAxisLeft().setDrawGridLines(true);
        mpLineChart.getAxisRight().setEnabled(false);
        mpLineChart.getAxisLeft().setTextColor(android.graphics.Color.WHITE);
        mpLineChart.getAxisLeft().setGridColor(android.graphics.Color.GRAY);
        mpLineChart.getAxisLeft().setAxisMaximum(20f);
        mpLineChart.getAxisLeft().setAxisMinimum(-20f);
        mpLineChart.getAxisLeft().setLabelCount(8, true);
        mpLineChart.getLegend().setTextColor(android.graphics.Color.WHITE);
        mpLineChart.getLegend().setTextSize(12f);
        mpLineChart.getLegend().setYOffset(10f);

        // Initialize datasets
        ArrayList<Entry> emptyData = new ArrayList<>();
        lineDataSet1 = new LineDataSet(emptyData, "X-axis");
        LineDataSet lineDataSet2 = new LineDataSet(new ArrayList<>(emptyData), "Y-axis");
        LineDataSet lineDataSet3 = new LineDataSet(new ArrayList<>(emptyData), "Z-axis");
        LineDataSet lineDataSet4 = new LineDataSet(new ArrayList<>(emptyData), "Norm");
        // Configure datasets
        configureDataSet(lineDataSet1, android.graphics.Color.RED);
        configureDataSet(lineDataSet2, android.graphics.Color.GREEN);
        configureDataSet(lineDataSet3, android.graphics.Color.BLUE);
        configureDataSet(lineDataSet4, android.graphics.Color.YELLOW);
        // Add to datasets
        dataSets.clear();
        dataSets.add(lineDataSet1);
        dataSets.add(lineDataSet2);
        dataSets.add(lineDataSet3);
        dataSets.add(lineDataSet4);
        // Create and set data
        data = new LineData(dataSets);
        mpLineChart.setData(data);
        mpLineChart.getLegend().setEnabled(true);

        // Set initial visible range
        mpLineChart.setVisibleXRangeMaximum(120f);

        mpLineChart.invalidate();
        mpLineChart.setVisibility(View.VISIBLE);

        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
        menu.findItem(R.id.hex).setChecked(hexEnabled);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            return true;
        } else if (id == R.id.newline) {
            String[] newlineNames = getResources().getStringArray(R.array.newline_names);
            String[] newlineValues = getResources().getStringArray(R.array.newline_values);
            int pos = java.util.Arrays.asList(newlineValues).indexOf(newline);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Newline");
            builder.setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
                newline = newlineValues[item1];
                dialog.dismiss();
            });
            builder.create().show();
            return true;
        } else if (id == R.id.hex) {
            hexEnabled = !hexEnabled;
            item.setChecked(hexEnabled);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial + UI
     */
    private String[] clean_str(String[] stringsArr){
        for (int i = 0; i < stringsArr.length; i++)  {
            stringsArr[i]=stringsArr[i].replaceAll(" ","");
        }


        return stringsArr;
    }
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            status("connecting...");
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

    private void send(String str) {
        if(connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String msg;
            byte[] data;
            if(hexEnabled) {
                StringBuilder sb = new StringBuilder();
                TextUtil.toHexString(sb, TextUtil.fromHexString(str));
                TextUtil.toHexString(sb, newline.getBytes());
                msg = sb.toString();
                data = TextUtil.fromHexString(msg);
            } else {
                msg = str;
                data = (str + newline).getBytes();
            }
            service.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(byte[] data) {
        if (data != null && data.length > 0) {
            try {
                // Only process data if we're plotting
                if (!isPlotting) {
                    return;  // Skip data processing when not plotting
                }

                String msg = new String(data);
                // Split by newlines and process each line
                String[] lines = msg.split("\n");
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    
                    if (line.contains(",")) {
                        String[] values = line.split(",");
                        if (values.length >= 3) {
                            try {
                                // Clean and parse each value more carefully
                                float accX = Float.parseFloat(values[0].replaceAll("\\s+", "").trim());
                                float accY = Float.parseFloat(values[1].replaceAll("\\s+", "").trim());
                                float accZ = Float.parseFloat(values[2].replaceAll("\\s+", "").trim());
                                
                                // Only process data if we're plotting
                                if (isPlotting) {
                                    // Add data to arrays
                                    accXData.add(accX);
                                    accYData.add(accY);
                                    accZData.add(accZ);

                                    // When we have enough data points, analyze them
                                    if (accXData.size() >= ANALYSIS_WINDOW) {
                                        analyzeCurrentData();
                                    }

                                    // Calculate norm for visualization
                                    float norm = (float) Math.sqrt(accX * accX + accY * accY + accZ * accZ);

                                    // Update chart using the existing method
                                    updateChartData(System.currentTimeMillis() / 1000f, accX, accY, accZ, norm);
                                    
                                    // Store data for saving
                                    String[] row = new String[]{
                                        String.valueOf(System.currentTimeMillis()),
                                        String.valueOf(accX),
                                        String.valueOf(accY),
                                        String.valueOf(accZ)
                                    };
                                    dataToSave.add(row);
                                }
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "Error parsing number: " + e.getMessage());
                                continue; // Skip this line and continue with the next
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing data: " + e.getMessage());
                Log.e(TAG, "Raw data: " + new String(data));
            }
        }
    }

    private void analyzeCurrentData() {
        // Get reference to MainActivity
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            // Create copies of the data for analysis
            ArrayList<Float> xData = new ArrayList<>(accXData);
            ArrayList<Float> yData = new ArrayList<>(accYData);
            ArrayList<Float> zData = new ArrayList<>(accZData);
            
            // Clear the buffers for next batch
            accXData.clear();
            accYData.clear();
            accZData.clear();
            
            // Call MainActivity's analysis method
            activity.analyzeAccelerationData(xData, yData, zData);
        }
    }

    private void status(String str) {
        Log.d("TerminalFragment", str);
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        try {
            receive(data);}
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }

    private ArrayList<Entry> emptyDataValues()
    {
        ArrayList<Entry> dataVals = new ArrayList<Entry>();
        return dataVals;
    }

    private void OpenLoadCSV(){
        Intent intent = new Intent(getContext(),LoadCSV.class);
        startActivity(intent);
    }

    public void updateChartData(float timestamp, float x, float y, float z, float norm) {
        if (mpLineChart != null && mpLineChart.getData() != null) {
            try {
                LineData data = mpLineChart.getData();

                // Add data points
                data.addEntry(new Entry(dataIndex, x), 0);
                data.addEntry(new Entry(dataIndex, y), 1);
                data.addEntry(new Entry(dataIndex, z), 2);
                data.addEntry(new Entry(dataIndex, norm), 3);
                // Remove old data points if we have more than 120 points
                for (ILineDataSet set : data.getDataSets()) {
                    while (set.getEntryCount() > 120) {
                        set.removeFirst();
                    }
                }

                // Notify data changes
                data.notifyDataChanged();
                mpLineChart.notifyDataSetChanged();

                // Move the view to show latest data
                float visibleRange = 120f;
                mpLineChart.setVisibleXRange(0, visibleRange);
                if (dataIndex > visibleRange) {
                    mpLineChart.moveViewToX(dataIndex - visibleRange);
                }

                // Force redraw
                mpLineChart.invalidate();

                dataIndex++;

            } catch (Exception e) {
                Log.e("TerminalFragment", "Error updating chart: " + e.getMessage());
            }
        }
    }

    public void startPlotting() {
        isPlotting = true;
        startTime = System.currentTimeMillis();
        experimentStartTime = new java.text.SimpleDateFormat("dd/M/yyyy HH:mm")
            .format(new java.util.Date());
        Log.d("TerminalFragment", "Plotting started in " + (isRunningMode ? "Running" : "Walking") + " mode");
    }

    public void stopPlotting() {
        isPlotting = false;
        // Analyze any remaining data when stopping
        if (!accXData.isEmpty()) {
            analyzeCurrentData();
            // Clear the data buffers after analysis
            accXData.clear();
            accYData.clear();
            accZData.clear();
        }
        Log.d(TAG, "Plotting stopped");
    }

    public void clearChart() {
        if (mpLineChart != null && mpLineChart.getData() != null) {
            // Clear stored data
            dataToSave.clear();
            
            // Clear all datasets
            LineData data = mpLineChart.getData();

            // Remove all entries from each dataset
            for (ILineDataSet set : data.getDataSets()) {
                set.clear();
            }

            // Reset index
            dataIndex = 0;

            // Reset view to start
            mpLineChart.moveViewToX(0);
            mpLineChart.setVisibleXRangeMaximum(120f);

            // Notify changes and force refresh
            data.clearValues();
            mpLineChart.clear();
            mpLineChart.notifyDataSetChanged();
            mpLineChart.invalidate();

            // Reset zoom and position
            mpLineChart.fitScreen();

            // Reinitialize empty datasets
            ArrayList<Entry> emptyData = new ArrayList<>();
            lineDataSet1 = new LineDataSet(emptyData, "X-axis");
            LineDataSet lineDataSet2 = new LineDataSet(new ArrayList<>(emptyData), "Y-axis");
            LineDataSet lineDataSet3 = new LineDataSet(new ArrayList<>(emptyData), "Z-axis");
            LineDataSet lineDataSet4 = new LineDataSet(new ArrayList<>(emptyData), "Norm");
            // Configure datasets
            configureDataSet(lineDataSet1, android.graphics.Color.RED);
            configureDataSet(lineDataSet2, android.graphics.Color.GREEN);
            configureDataSet(lineDataSet3, android.graphics.Color.BLUE);
            configureDataSet(lineDataSet4, android.graphics.Color.YELLOW);
            // Add to datasets
            dataSets.clear();
            dataSets.add(lineDataSet1);
            dataSets.add(lineDataSet2);
            dataSets.add(lineDataSet3);
            dataSets.add(lineDataSet4);
            // Create and set new data
            this.data = new LineData(dataSets);
            mpLineChart.setData(this.data);

            Log.d("TerminalFragment", "Chart cleared and reset to start");
        }
    }

    private void configureDataSet(LineDataSet dataSet, int color) {
        dataSet.setColor(color);
        dataSet.setDrawCircles(false);
        dataSet.setLineWidth(2.5f);
        dataSet.setDrawValues(false);
        dataSet.setHighlightEnabled(false);
        dataSet.setMode(LineDataSet.Mode.LINEAR);
        dataSet.setDrawFilled(false);
    }

    public void saveDataToCSV() {
        if (dataToSave.isEmpty()) {
            Toast.makeText(getActivity(), "No data to save", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File file = new File("/sdcard/csv_dir/");
            file.mkdirs();
            String csv = "/sdcard/csv_dir/" + fileName + ".csv";
            CSVWriter csvWriter = new CSVWriter(new FileWriter(csv));
            
            // Get both step counts from MainActivity
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                EditText actualStepInput = activity.findViewById(R.id.actualStepCountInput);
                EditText estimatedStepInput = activity.findViewById(R.id.stepCountInput);
                
                String actualSteps = actualStepInput.getText().toString();
                String estimatedSteps = estimatedStepInput.getText().toString();
                
                // Write experiment info
                csvWriter.writeNext(new String[]{"NAME:", fileName});
                csvWriter.writeNext(new String[]{"EXPERIMENT TIME:", experimentStartTime});
                csvWriter.writeNext(new String[]{"ACTIVITY TYPE:", isRunningMode ? "Running" : "Walking"});
                csvWriter.writeNext(new String[]{"ESTIMATED NUMBER OF STEPS:", estimatedSteps});
                csvWriter.writeNext(new String[]{"COUNT OF ACTUAL STEPS:", actualSteps});
            }
            
            // Write column headers
            csvWriter.writeNext(new String[]{
                "ACC Z", "ACC Y", "ACC X", "Time [sec]"
            });
            
            // Write data rows
            for (String[] row : dataToSave) {
                // row format is [timestamp, x, y, z]
                long timestamp = Long.parseLong(row[0]);
                double timeInSec = (timestamp - startTime) / 1000.0;  // Convert to seconds with decimal points
                String[] newRow = new String[]{
                    row[3],  // ACC Z
                    row[2],  // ACC Y
                    row[1],  // ACC X
                    String.format("%.3f", timeInSec)  // Time in seconds with 3 decimal places
                };
                csvWriter.writeNext(newRow);
            }
            
            csvWriter.close();
            
            // Clear the stored data after saving
            dataToSave.clear();
            
            Toast.makeText(getActivity(), "Data saved to " + csv, Toast.LENGTH_SHORT).show();
            Log.d("TerminalFragment", "Saved data to CSV in required format");
            
        } catch (IOException e) {
            Toast.makeText(getActivity(), "Error saving data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e("TerminalFragment", "Error saving to CSV: " + e.getMessage());
        }
    }

    public void setActivityMode(boolean isRunning) {
        this.isRunningMode = isRunning;
    }

    public void loadAndVisualizeCSV(Uri uri) {
        try {
            // Clear existing data
            clearChart();
            
            // Create CSV reader using input stream
            InputStream inputStream = getActivity().getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            com.opencsv.CSVReader csvReader = new com.opencsv.CSVReader(reader);
            String[] nextLine;
            
            // Skip header lines until we reach the data
            while ((nextLine = csvReader.readNext()) != null) {
                if (nextLine.length > 0 && nextLine[0].equals("ACC Z")) {
                    break;  // Found the column headers
                }
            }
            
            // Read data lines
            float index = 0;
            ArrayList<Entry> xData = new ArrayList<>();
            ArrayList<Entry> yData = new ArrayList<>();
            ArrayList<Entry> zData = new ArrayList<>();
            
            while ((nextLine = csvReader.readNext()) != null) {
                if (nextLine.length >= 4) {  // Make sure we have all columns
                    try {
                        float z = Float.parseFloat(nextLine[0]);  // ACC Z
                        float y = Float.parseFloat(nextLine[1]);  // ACC Y
                        float x = Float.parseFloat(nextLine[2]);  // ACC X
                        float time = Float.parseFloat(nextLine[3]);  // Time [sec]
                        
                        // Store data points
                        xData.add(new Entry(time, x));
                        yData.add(new Entry(time, y));
                        zData.add(new Entry(time, z));
                        index = Math.max(index, time);
                    } catch (NumberFormatException e) {
                        Log.e("TerminalFragment", "Error parsing line: " + e.getMessage());
                    }
                }
            }
            
            // Create new datasets with all the data
            lineDataSet1 = new LineDataSet(xData, "X-axis");
            LineDataSet lineDataSet2 = new LineDataSet(yData, "Y-axis");
            LineDataSet lineDataSet3 = new LineDataSet(zData, "Z-axis");

            // Configure datasets
            configureDataSet(lineDataSet1, android.graphics.Color.RED);
            configureDataSet(lineDataSet2, android.graphics.Color.GREEN);
            configureDataSet(lineDataSet3, android.graphics.Color.BLUE);

            // Add to datasets
            dataSets.clear();
            dataSets.add(lineDataSet1);
            dataSets.add(lineDataSet2);
            dataSets.add(lineDataSet3);

            // Create and set new data
            data = new LineData(dataSets);
            mpLineChart.setData(data);
            
            // Update chart settings
            mpLineChart.getXAxis().setAxisMaximum(index + 1f);
            mpLineChart.getXAxis().setAxisMinimum(0f);
            mpLineChart.setVisibleXRangeMaximum(15f);  // Show 15 seconds at a time by default
            mpLineChart.moveViewToX(0);  // Start at the beginning
            mpLineChart.notifyDataSetChanged();
            mpLineChart.invalidate();
            
            csvReader.close();
            reader.close();
            inputStream.close();
            Toast.makeText(getActivity(), "CSV data loaded and visualized", Toast.LENGTH_SHORT).show();
            
        } catch (IOException e) {
            Toast.makeText(getActivity(), "Error loading CSV: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e("TerminalFragment", "Error loading CSV: " + e.getMessage());
        }
    }

    public void showFileChooser() {
        Intent intent = new Intent(getActivity(), LoadCSV.class);
        startActivity(intent);
    }

    private static final int FILE_SELECT_CODE = 0;

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_SELECT_CODE && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    // Take persistable URI permission
                    final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                    getActivity().getContentResolver().takePersistableUriPermission(uri, takeFlags);
                    loadAndVisualizeCSV(uri);
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setStepCount(int stepCount) {
        this.stepCount = stepCount;
    }

    // Add method to clear data buffers
    public void clearData() {
        accXData.clear();
        accYData.clear();
        accZData.clear();
    }

}
