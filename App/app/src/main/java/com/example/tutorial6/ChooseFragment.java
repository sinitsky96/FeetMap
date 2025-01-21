package com.example.tutorial6;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

public class ChooseFragment extends Fragment {
    private static final String TAG = "ChooseFragment";
    private String deviceAddress;
    public static ChooseFragment newInstance(String deviceAddress) {
        ChooseFragment fragment = new ChooseFragment();
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
        View view = inflater.inflate(R.layout.fragment_choose, container, false);

        Button btnAnalyze = view.findViewById(R.id.btnAnalyze);
        Button btnTrack = view.findViewById(R.id.btnTrack);

        btnAnalyze.setOnClickListener(v -> {
            RunAnalysisFragment fragment = new RunAnalysisFragment();
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment, "RunAnalysisFragment")
                    .addToBackStack(null)
                    .commit();
        });

        btnTrack.setOnClickListener(v -> {
            Temp fragment = Temp.newInstance(deviceAddress);
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment, "Temp")
                    .addToBackStack(null)
                    .commit();
        });

        return view;
    }


}