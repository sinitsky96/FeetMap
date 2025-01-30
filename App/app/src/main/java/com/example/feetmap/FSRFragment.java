package com.example.feetmap;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class FSRFragment extends Fragment implements ServiceConnection, SerialListener {
    private static final String TAG = "FSRFragment";
    private enum Connected { False, Pending, True }

    private String deviceAddress;
    private SerialService service;
    private Connected connected = Connected.False;
    private boolean initialStart = true;

    private FSRCircleView fsrCircleView;
    private TextView fsrValue1, fsrValue2, fsrValue3;
    private Button btnReturn;

    public static FSRFragment newInstance(String deviceAddress) {
        FSRFragment fragment = new FSRFragment();
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
        View view = inflater.inflate(R.layout.fragment_fsr, container, false);

        fsrCircleView = view.findViewById(R.id.fsrCircleView);
        fsrValue1 = view.findViewById(R.id.fsrValue1);
        fsrValue2 = view.findViewById(R.id.fsrValue2);
        fsrValue3 = view.findViewById(R.id.fsrValue3);
        btnReturn = view.findViewById(R.id.btnReturn);

        btnReturn.setOnClickListener(v -> {
            disconnect();
            getActivity().getSupportFragmentManager().popBackStack();
        });

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize views
        fsrCircleView = view.findViewById(R.id.fsrCircleView);
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
                    if (values.length >= 6) { // We expect 6 values: accX, accY, accZ, fsr1, fsr2, fsr3
                        try {
                            // Parse FSR values (last three values)
                            float fsr1 = Float.parseFloat(values[3].trim());
                            float fsr2 = Float.parseFloat(values[4].trim());
                            float fsr3 = Float.parseFloat(values[5].trim());

                            // Update UI on main thread
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    // Update circles on single view
                                    fsrCircleView.setValue(0, fsr1);
                                    fsrCircleView.setValue(1, fsr2);
                                    fsrCircleView.setValue(2, fsr3);

                                    // Update value labels
                                    fsrValue1.setText(String.format("%.0f", fsr1));
                                    fsrValue2.setText(String.format("%.0f", fsr2));
                                    fsrValue3.setText(String.format("%.0f", fsr3));
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
} 