// IntermediateFragment.java
package com.example.feetmap;



import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.appcompat.app.AlertDialog;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Fragment that:
 *  - Loads a CSV file (fsr1=heel, fsr2=mid, fsr3=toe),
 *  - Computes average balance score,
 *  - Displays a heat map reflecting relative percentages
 *    among the 3 FSR sensors.
 */
public class FeetMapAnalyze extends Fragment {
    // Weights for toe, mid, heel
    private static final float W_T = 1.5f;    // Toe weight
    private static final float W_M = 0.5f;    // Midfoot weight
    private static final float W_H = 0.5f;    // Heel weight

    private SharedViewModel sharedViewModel;
    private String csvUriString;

    // UI
    private HeatmapView heatmapView;
    private TextView tvScore;
    private Button btnFurther;
    private ImageButton btnInfo;
    private TextView tvScoreComment;

    // Data
    private List<RunningDataPoint> runData = new ArrayList<>();
    private float averageBalanceScore = 0f;

    /**
     * Each element is a fraction from 0..1 representing the
     * fraction of total pressure in:
     *    fsrPercentages[0] = Heel
     *    fsrPercentages[1] = Midfoot
     *    fsrPercentages[2] = Toe
     */
    private float[] fsrPercentages = new float[3];

    public static FeetMapAnalyze newInstance(String uriString) {
        FeetMapAnalyze fragment = new FeetMapAnalyze();
        Bundle args = new Bundle();
        args.putString("fileUri", uriString);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);

        // Get CSV Uri
        if (getArguments() != null) {
            csvUriString = getArguments().getString("fileUri");
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.feet_map_analyze_fragment, container, false);

        heatmapView = view.findViewById(R.id.heatmapView);
        heatmapView.setFootImage(R.mipmap.footpic);
        tvScore     = view.findViewById(R.id.tvScore);
        tvScoreComment = view.findViewById(R.id.tvScoreComment);
        btnFurther  = view.findViewById(R.id.btnFurther);
        btnInfo     = view.findViewById(R.id.btnInfo);

        btnFurther.setOnClickListener(v -> navigateToRunAnalysis());
        btnInfo.setOnClickListener(v -> showScoreInfoDialog());

        // If we do not have an empty csvUriString, try loading data
        if (!TextUtils.isEmpty(csvUriString)) {
            loadAndProcessCSV(Uri.parse(csvUriString));
        }

        return view;
    }

    private void navigateToRunAnalysis() {
        RunAnalysisFragment fragment = RunAnalysisFragment.newInstance(csvUriString);
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    /**
     * Reads the CSV data, populates runData, then computes
     * the averageBalanceScore and relative percentages for
     * the heel, mid, toe. Finally, updates UI.
     */
    private void loadAndProcessCSV(Uri uri) {
        try {
            runData.clear();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(requireContext().getContentResolver().openInputStream(uri))
            );

            // Skip header (assuming first line is "timestamp,accX,accY,accZ,fsr1,fsr2,fsr3")
            reader.readLine();

            float heelSum = 0f;
            float midSum  = 0f;
            float toeSum  = 0f;

            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",");
                if (values.length < 7) continue; // skip invalid lines

                long timestamp   = Long.parseLong(values[0].trim());
                float accX       = Float.parseFloat(values[1].trim());
                float accY       = Float.parseFloat(values[2].trim());
                float accZ       = Float.parseFloat(values[3].trim());
                float fsrHeel    = Float.parseFloat(values[4].trim()); // fsr1 is heel
                float fsrMid     = Float.parseFloat(values[5].trim()); // fsr2 is mid
                float fsrToe     = Float.parseFloat(values[6].trim()); // fsr3 is toe

                runData.add(new RunningDataPoint(timestamp, accX, accY, accZ, fsrHeel, fsrMid, fsrToe));

                heelSum += fsrHeel;
                midSum  += fsrMid;
                toeSum  += fsrToe;
            }
            reader.close();

            // Compute average balance score from all data points
//            averageBalanceScore = computeAverageBalanceScore(runData);

            // Compute relative percentages (heel, mid, toe)
            float totalSum = heelSum + midSum + toeSum;
            if (totalSum > 0) {
                // fraction of total for each sensor
                fsrPercentages[0] = heelSum / totalSum; // heel %
                fsrPercentages[1] = midSum  / totalSum; // mid %
                fsrPercentages[2] = toeSum  / totalSum; // toe %
            } else {
                fsrPercentages[0] = 0.0f;
                fsrPercentages[1] = 0.0f;
                fsrPercentages[2] = 0.0f;
            }
            float score =(toeSum+midSum)/totalSum * 100f;

            // Update UI
            tvScore.setText(String.format("Score: %.1f", (score)));
            tvScoreComment.setText(getScoreComment(score));

            // Pass percentages to heatmap
            heatmapView.updateValues(fsrPercentages);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void showScoreInfoDialog() {
        new AlertDialog.Builder(requireContext())
            .setTitle("Score Information")
            .setMessage("The score represents how much out of a 100 the quality of the run is. Higher is better")
            .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
            .create()
            .show();
    }

    private String getScoreComment(float score) {
        if (score >= 95) {
            return "Effortless, do you even have heels?";
        } else if (score >= 80) {
            return "Quality run, good job!";
        } else if (score >= 70) {
            return "Could be better, take your heel off the gas :)";
        } else if (score >= 55) {
            return "Pass and forget? More like broken spine and dreams! Don't run with your heels!";
        } else {
            return "Are you even trying? Don't run with your heel!";
        }
    }
}


//import android.net.Uri;
//import android.os.Bundle;
//import android.text.TextUtils;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.Button;
//import android.widget.TextView;
//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
//import androidx.fragment.app.Fragment;
//import androidx.lifecycle.ViewModelProvider;
//import java.io.BufferedReader;
//import java.io.InputStreamReader;
//import java.util.List;
//
//public class FeetMapAnalyze extends Fragment {
//    // Weights for toe, mid, heel
//    private static final float W_T = 1.0f;    // Toe weight
//    private static final float W_M = 0.5f;    // Midfoot weight
//    private static final float W_H = 1.2f;    // Heel weight
//    private SharedViewModel sharedViewModel;
//    private String csvUriString;
//
//    private double balanceScore;
//    private HeatmapView heatmapView;
//    private TextView tvScore;
//    private float[] fsrPercentages = new float[3]; // [heel%, mid%, toe%]
//
//
//    public static FeetMapAnalyze newInstance(String uriString) {
//        FeetMapAnalyze fragment = new FeetMapAnalyze();
//        Bundle args = new Bundle();
//        args.putString("fileUri", uriString);
//        fragment.setArguments(args);
//        return fragment;
//    }
//
//    @Override
//    public void onCreate(@Nullable Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
//        if (getArguments() != null) {
//            csvUriString = getArguments().getString("fileUri");
////            fileUri = Uri.parse(getArguments().getString("fileUri"));
////            parseCsvAndCalculateScore(); // Parse CSV here
//        }
//    }
//
//    @Override
//    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
//        View view = inflater.inflate(R.layout.feet_map_analyze_fragment, container, false);
//        Button btnFurther = view.findViewById(R.id.btnFurther);
//        TextView tvScore = view.findViewById(R.id.tvScore);
//
//        tvScore.setText(String.format("Score: %.1f", balanceScore));
//        if (!TextUtils.isEmpty(csvUriString)) {
//            parseCsvAndCalculateScore(Uri.parse(csvUriString));
//        }
//
//        btnFurther.setOnClickListener(v -> navigateToRunAnalysis());
//        return view;
//    }
//
//    private void parseCsvAndCalculateScore(Uri uri) {
//        try (BufferedReader reader = new BufferedReader(
//                new InputStreamReader(requireActivity().getContentResolver().openInputStream(uri)))) {
//            float S_h = 0, S_m = 0, S_t = 0;
//            reader.readLine();
//            String line;
//            while ((line = reader.readLine()) != null) {
//                String[] values = line.split(",");
//                S_h += Float.parseFloat(values[4]); // fsr1 (heel)
//                S_m += Float.parseFloat(values[5]); // fsr2 (mid)
//                S_t += Float.parseFloat(values[6]); // fsr3 (toe)
//            }
//            reader.close();
//
//            // Calculate percentages
//            float S_total = S_h + S_m + S_t;
//            fsrPercentages[0] = S_h / S_total; // Heel %
//            fsrPercentages[1] = S_m / S_total; // Mid %
//            fsrPercentages[2] = S_t / S_total; // Toe %
//
//            // Calculate balance score
//            float w_t = 1.0f, w_m = 0.5f, w_h = 1.2f;
//            float balanceScore =
//                    (w_t * fsrPercentages[2] +
//                            w_m * fsrPercentages[1] -
//                            w_h * fsrPercentages[0] +
//                            w_h) / (w_t + w_h);
//
//            // Update UI and ViewModel
//            heatmapView.updateValues(fsrPercentages);
////            float averageScore = computeAverageBalanceScore(runData);
//            tvScore.setText(String.format("Balance Score: %.1f/100", balanceScore * 100));
//            sharedViewModel.setBalanceScore(balanceScore);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    /**
//     * Computes the average of the normalized balance scores
//     * across all data points.
//     */
//    private float computeAverageBalanceScore(List<RunningDataPoint> data) {
//        if (data.isEmpty()) return 0f;
//        float sum = 0;
//        for (RunningDataPoint point : data) {
//            sum += computeNormalizedScore(
//                    point.fsr3, // toe
//                    point.fsr2, // mid
//                    point.fsr1  // heel
//            );
//        }
//        return sum / data.size();
//    }
//
//    /**
//     * Implements the normalized formula:
//     *  BalanceScore = [ wT*(St/S) + wM*(Sm/S) - wH*(Sh/S) + wH ] / (wT + wH)
//     */
//    private float computeNormalizedScore(float fsrToe, float fsrMid, float fsrHeel) {
//        float sTotal = fsrToe + fsrMid + fsrHeel;
//        if (sTotal <= 0) return 0.5f; // if no pressure, consider "neutral"
//
//        float st = fsrToe / sTotal;
//        float sm = fsrMid / sTotal;
//        float sh = fsrHeel / sTotal;
//
//        // Weighted formula
//        float numerator = (W_T * st) + (W_M * sm) - (W_H * sh) + W_H;
//        float denominator = W_T + W_H;
//        float rawScore = numerator / denominator;
//
//        // Clip to [0, 1] if you want
//        if (rawScore < 0) rawScore = 0;
//        if (rawScore > 1) rawScore = 1;
//        return rawScore;
//    }
//
//    private void navigateToRunAnalysis() {
////        getParentFragmentManager().beginTransaction()
////                .replace(R.id.fragment_container, new RunAnalysisFragment())
////                .addToBackStack(null)
////                .commit();
//
//        RunAnalysisFragment fragment = RunAnalysisFragment.newInstance(csvUriString);
//        getParentFragmentManager().beginTransaction()
//                .replace(R.id.fragment_container, fragment)
//                .addToBackStack(null)
//                .commit();
//    }
//}
