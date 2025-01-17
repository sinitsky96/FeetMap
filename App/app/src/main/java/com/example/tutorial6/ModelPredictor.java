package com.example.tutorial6;

import android.content.Context;
import android.content.res.AssetManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ModelPredictor {
    private JSONObject modelJson;
    private String[] classes;
    private String[] featureNames;
    
    public ModelPredictor(Context context) {
        try {
            // Load the model from assets
            AssetManager assetManager = context.getAssets();
            InputStream inputStream = assetManager.open("model.json");
            int size = inputStream.available();
            byte[] buffer = new byte[size];
            inputStream.read(buffer);
            inputStream.close();
            
            // Convert to string and parse JSON
            String jsonString = new String(buffer, StandardCharsets.UTF_8);
            modelJson = new JSONObject(jsonString);
            
            // Load classes and feature names
            JSONArray classesArray = modelJson.getJSONArray("classes");
            classes = new String[classesArray.length()];
            for (int i = 0; i < classesArray.length(); i++) {
                classes[i] = classesArray.getString(i);
            }
            
            JSONArray featuresArray = modelJson.getJSONArray("feature_names");
            featureNames = new String[featuresArray.length()];
            for (int i = 0; i < featuresArray.length(); i++) {
                featureNames[i] = featuresArray.getString(i);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private int traverseTree(JSONObject tree, Map<String, Double> features) {
        try {
            JSONArray childrenLeft = tree.getJSONArray("children_left");
            JSONArray childrenRight = tree.getJSONArray("children_right");
            JSONArray feature = tree.getJSONArray("feature");
            JSONArray threshold = tree.getJSONArray("threshold");
            JSONArray value = tree.getJSONArray("value");
            
            int currentNode = 0;
            while (childrenLeft.getInt(currentNode) != -1 || childrenRight.getInt(currentNode) != -1) {
                int featureIndex = feature.getInt(currentNode);
                double thresholdValue = threshold.getDouble(currentNode);
                
                if (features.get(featureNames[featureIndex]) <= thresholdValue) {
                    currentNode = childrenLeft.getInt(currentNode);
                } else {
                    currentNode = childrenRight.getInt(currentNode);
                }
            }
            
            // Get the prediction from the leaf node
            JSONArray nodeValue = value.getJSONArray(currentNode).getJSONArray(0);
            return nodeValue.getDouble(0) > nodeValue.getDouble(1) ? 0 : 1;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
    
    public String predict(Map<String, Double> features) {
        try {
            JSONArray estimators = modelJson.getJSONArray("estimators");
            int[] votes = new int[classes.length];
            
            // Get predictions from all trees
            for (int i = 0; i < estimators.length(); i++) {
                int prediction = traverseTree(estimators.getJSONObject(i), features);
                votes[prediction]++;
            }
            
            // Return the majority vote
            return classes[votes[0] > votes[1] ? 0 : 1];
        } catch (Exception e) {
            e.printStackTrace();
            return classes[0];
        }
    }
    
    // Helper method to create feature map
    public static Map<String, Double> createFeatureMap(
            double max_x, double max_y, double max_z,
            double min_x, double min_y, double min_z,
            double median_x, double median_y, double median_z,
            double mean_x, double mean_y, double mean_z) {
        
        Map<String, Double> features = new HashMap<>();
        features.put("max_x", max_x);
        features.put("max_y", max_y);
        features.put("max_z", max_z);
        features.put("min_x", min_x);
        features.put("min_y", min_y);
        features.put("min_z", min_z);
        features.put("median_x", median_x);
        features.put("median_y", median_y);
        features.put("median_z", median_z);
        features.put("mean_x", mean_x);
        features.put("mean_y", mean_y);
        features.put("mean_z", mean_z);
        return features;
    }
} 