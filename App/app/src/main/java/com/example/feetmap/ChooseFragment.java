package com.example.feetmap;


import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;


public class ChooseFragment extends Fragment {
    private static final String TAG = "ChooseFragment";
    private ActivityResultLauncher<Intent> filePicker;
    private String deviceAddress;
    public static ChooseFragment newInstance(String deviceAddress) {
        ChooseFragment fragment = new ChooseFragment();
        Bundle args = new Bundle();
        args.putString("device", deviceAddress);
        fragment.setArguments(args);
        return fragment;
    }

//    @Override
//    public void onCreate(@Nullable Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setRetainInstance(true);
//        deviceAddress = getArguments() != null ? getArguments().getString("device") : null;
//    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        deviceAddress = getArguments() != null ? getArguments().getString("device") : null;

        // Initialize file picker
        filePicker = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        FeetMapAnalyze fragment = FeetMapAnalyze.newInstance(uri.toString());
                        getParentFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, fragment)
                                .addToBackStack(null)
                                .commit();
                    }
                });
    }



    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_choose, container, false);

        Button btnAnalyze = view.findViewById(R.id.btnAnalyze);
        Button btnTrack = view.findViewById(R.id.btnTrack);

//        btnAnalyze.setOnClickListener(v -> {
//            RunAnalysisFragment fragment = new RunAnalysisFragment();
//            getParentFragmentManager().beginTransaction()
//                    .replace(R.id.fragment_container, fragment, "RunAnalysisFragment")
//                    .addToBackStack(null)
//                    .commit();
//        });

        btnAnalyze.setOnClickListener(v -> selectRunFile());

        btnTrack.setOnClickListener(v -> {
            Temp fragment = Temp.newInstance(deviceAddress);
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment, "Temp")
                    .addToBackStack(null)
                    .commit();
        });

        return view;
    }

    private void selectRunFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("text/*");
        filePicker.launch(intent);
    }


}