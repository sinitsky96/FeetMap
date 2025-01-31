"""
Movement Classification Model

This module implements a Random Forest classifier for human movement recognition using accelerometer data.
It processes raw accelerometer signals and extracts various features for classification of activities
(walking vs stairs).

Key Features:
- Comprehensive feature extraction from time and frequency domains
- Statistical features (mean, std, min, max, correlations)
- Frequency domain features (FFT components, spectral energy)
- Magnitude-based features (SMA, SVM)
- Random Forest classification with performance evaluation
- Model persistence and visualization capabilities

The processing pipeline includes:
1. Raw data loading and preprocessing
2. Feature extraction from multiple domains
3. Model training and evaluation
4. Performance visualization and analysis
5. Model export for deployment

Usage:
    python model.py

The script will:
1. Load and process accelerometer data
2. Extract comprehensive feature set
3. Train Random Forest classifier
4. Generate performance visualizations
5. Save model for deployment
"""

import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix
from scipy.stats import kurtosis, skew, pearsonr
from scipy.fft import fft
import json

# Load data from .npy files
X = np.load(r'D:\Study Docs\Degree Material\Sem 9 proj\IOT\proj\FeetMap\RandomForest\data\filtered_data\processed\X.npy')
y = np.load(r'D:\Study Docs\Degree Material\Sem 9 proj\IOT\proj\FeetMap\RandomForest\data\filtered_data\processed\y.npy')
print("Original data shape:", X.shape)
print("Labels shape:", y.shape)

def extract_statistical_features(window):
    """
    Extract statistical features from a window of accelerometer data.
    
    Args:
        window (array): Window of accelerometer data of shape (time_steps, 3)
                       where 3 represents x, y, z axes
    
    Returns:
        array: Statistical features including:
            - Mean, std, min, max for each axis
            - Correlations between axes
            - Zero crossing rate
            - Root Mean Square (RMS)
            
    Features Extracted:
        1. Basic statistics (12 features):
           - Mean (3 features, one per axis)
           - Standard deviation (3 features)
           - Minimum value (3 features)
           - Maximum value (3 features)
        2. Correlations (3 features):
           - Pearson correlation between x-y, y-z, x-z axes
        3. Zero crossing rate (3 features, one per axis)
        4. RMS values (3 features, one per axis)
    """
    features = []
    
    # Mean, std, min, max for each axis
    features.extend([
        np.mean(window, axis=0),
        np.std(window, axis=0),
        np.min(window, axis=0),
        np.max(window, axis=0)
    ])
    
    # Flatten the features list since it contains arrays
    features = np.concatenate([f.flatten() for f in features])
    
    # Correlation between axes - with safe handling of edge cases
    correlations = []
    for i, j in [(0,1), (1,2), (0,2)]:
        try:
            # Check if either column is constant (std=0)
            if np.std(window[:, i]) == 0 or np.std(window[:, j]) == 0:
                corr = 0.0  # No correlation if one column is constant
            else:
                corr = pearsonr(window[:, i], window[:, j])[0]
                if np.isnan(corr) or np.isinf(corr):
                    corr = 0.0  # Default to no correlation for invalid cases
        except:
            corr = 0.0  # Default to no correlation if calculation fails
        correlations.append(corr)
    
    features = np.concatenate([features, correlations])
    
    # Zero crossing rate
    zero_crossings = np.sum(np.diff(np.signbit(window), axis=0), axis=0)
    features = np.concatenate([features, zero_crossings])
    
    # RMS
    rms = np.sqrt(np.mean(np.square(window), axis=0))
    features = np.concatenate([features, rms])
    
    return features

def extract_frequency_features(window):
    """
    Extract frequency domain features from a window of accelerometer data.
    
    Args:
        window (array): Window of accelerometer data of shape (time_steps, 3)
    
    Returns:
        array: Frequency domain features including:
            - First 5 FFT components for each axis
            - Spectral energy
            - Dominant frequency
            
    Features Extracted:
        For each axis (x, y, z):
        1. First 5 FFT components (15 features total)
        2. Spectral energy - sum of squared FFT components (3 features)
        3. Dominant frequency - frequency with highest magnitude (3 features)
        
    Notes:
        - Handles edge cases (insufficient data, NaN values)
        - Pads FFT components if less than 5 available
        - Returns zeros for failed calculations to ensure consistent output
    """
    features = []
    
    # FFT for each axis
    for axis in range(3):
        try:
            fft_vals = np.abs(fft(window[:, axis]))
            # Take first 5 FFT components, handle case if less than 5 components
            n_components = min(5, len(fft_vals))
            fft_features = list(fft_vals[:n_components])
            # Pad with zeros if less than 5 components
            fft_features.extend([0] * (5 - n_components))
            features.extend(fft_features)
            
            # Spectral energy - with safety check
            spectral_energy = np.sum(np.square(fft_vals))
            if np.isnan(spectral_energy) or np.isinf(spectral_energy):
                spectral_energy = 0.0
            features.append(spectral_energy)
            
            # Dominant frequency - with safety check
            if len(fft_vals) > 0 and not np.all(np.isnan(fft_vals)):
                dom_freq = np.argmax(fft_vals)
            else:
                dom_freq = 0
            features.append(dom_freq)
        except:
            # If any calculation fails, add zeros for this axis
            features.extend([0] * 7)  # 5 FFT components + energy + dominant freq
    
    return np.array(features)

def extract_magnitude_features(window):
    """
    Extract magnitude-based features from accelerometer data.
    
    Args:
        window (array): Window of accelerometer data of shape (time_steps, 3)
    
    Returns:
        array: Magnitude features including:
            - Signal Magnitude Area (SMA) for each axis
            - Signal Vector Magnitude (SVM)
            
    Features Extracted:
        1. SMA (3 features):
           - Sum of absolute values for each axis
        2. SVM (1 feature):
           - Root mean square of combined axes
           
    Notes:
        - Handles invalid values (NaN, inf) by replacing with zeros
        - Returns zero array if calculations fail
    """
    try:
        # Signal magnitude area with safety check
        sma = np.sum(np.abs(window), axis=0)
        sma = np.where(np.isnan(sma) | np.isinf(sma), 0, sma)
        
        # Signal vector magnitude with safety check
        squared_sum = np.sum(np.square(window), axis=1)
        squared_sum = np.where(np.isnan(squared_sum) | np.isinf(squared_sum), 0, squared_sum)
        svm = np.sqrt(squared_sum).mean()
        if np.isnan(svm) or np.isinf(svm):
            svm = 0.0
        
        # Combine features
        features = np.concatenate([sma, [svm]])
        
    except:
        # If calculations fail, return zeros
        features = np.zeros(4)  # 3 for SMA (x,y,z) + 1 for SVM
    
    return features

def extract_important_features(data):
    """
    Extract comprehensive feature set from the accelerometer data.
    
    Args:
        data (array): Raw accelerometer data of shape (n_samples, time_steps, 3)
                     where 3 represents x, y, z axes
    
    Returns:
        array: Feature matrix of shape (n_samples, n_features)
        
    Processing Pipeline:
        1. Validates input dimensions
        2. For each window of data:
           - Extracts statistical features
           - Extracts frequency domain features
           - Extracts magnitude features
           - Combines all features into single vector
        3. Stacks all feature vectors into matrix
        
    Total Features Generated:
        - Statistical: 21 features
        - Frequency: 21 features
        - Magnitude: 4 features
        Total: 46 features per window
    """
    if len(data.shape) != 3:
        raise ValueError("Expected 3D input data")
    
    all_features = []
    for window in data:
        # Extract all types of features
        statistical_features = extract_statistical_features(window)
        frequency_features = extract_frequency_features(window)
        magnitude_features = extract_magnitude_features(window)
        
        # Combine all features
        window_features = np.concatenate([
            statistical_features,
            frequency_features,
            magnitude_features
        ])
        all_features.append(window_features)
    
    return np.array(all_features)




if __name__ == "__main__":
    # Create feature names for visualization
    feature_names = []
    # Statistical features
    for stat in ['mean', 'std', 'min', 'max']:
        for axis in ['x', 'y', 'z']:
            feature_names.append(f'{stat}_{axis}')
    # Correlation features
    feature_names.extend(['corr_xy', 'corr_yz', 'corr_xz'])
    # Zero crossing rate
    feature_names.extend(['zero_cross_x', 'zero_cross_y', 'zero_cross_z'])
    # RMS
    feature_names.extend(['rms_x', 'rms_y', 'rms_z'])
    # FFT components
    for axis in ['x', 'y', 'z']:
        for i in range(5):
            feature_names.append(f'fft_{axis}_{i}')
        feature_names.append(f'spectral_energy_{axis}')
        feature_names.append(f'dominant_freq_{axis}')
    # Magnitude features
    feature_names.extend(['sma_x', 'sma_y', 'sma_z', 'svm'])

    # Apply feature extraction to convert 3D data to 2D
    X_processed = extract_important_features(X)
    print("Processed features shape:", X_processed.shape)

    # Split data
    X_train, X_test, y_train, y_test = train_test_split(X_processed, y, test_size=0.2, random_state=42)

    # Create and train model
    rf_model = RandomForestClassifier(max_depth=15,n_estimators=100, random_state=42)
    rf_model.fit(X_train, y_train)

    # Make predictions
    y_pred = rf_model.predict(X_test)

    # Evaluate model
    print("Accuracy:", accuracy_score(y_test, y_pred))
    print("\nClassification Report:")
    print(classification_report(y_test, y_pred))

    # Plot feature importance
    plt.figure(figsize=(12, 6))
    importances = rf_model.feature_importances_
    indices = np.argsort(importances)[::-1]

    plt.title('Feature Importances')
    plt.bar(range(X_processed.shape[1]), importances[indices])
    plt.xticks(range(X_processed.shape[1]), [feature_names[i] for i in indices], rotation=45)
    plt.tight_layout()
    plt.savefig('feature_importance.png')
    plt.close()

    # Plot confusion matrix
    plt.figure(figsize=(10, 8))
    cm = confusion_matrix(y_test, y_pred)
    sns.heatmap(cm, annot=True, fmt='d', cmap='Blues')
    plt.title('Confusion Matrix')
    plt.ylabel('True Label')
    plt.xlabel('Predicted Label')
    plt.tight_layout()
    plt.savefig('confusion_matrix.png')
    plt.close()

    # Function to make predictions on new sensor data
    def predict_movement(sensor_data):
        """
        Make predictions on new sensor data using trained model.
        
        Args:
            sensor_data (array): Raw accelerometer data of shape (time_steps, 3)
                               containing x, y, z accelerometer data
        
        Returns:
            tuple: (prediction, probability)
                - prediction: Predicted movement class
                - probability: Prediction probability vector
                
        Process:
            1. Reshapes input data to match model requirements
            2. Extracts features using same pipeline as training
            3. Makes prediction using trained Random Forest
            4. Returns prediction and confidence scores
        """
        # Reshape to match expected input shape (1, time_steps, 3)
        sensor_data = sensor_data.reshape(1, -1, 3)
        # Extract features
        features = extract_important_features(sensor_data)
        # Make prediction
        prediction = rf_model.predict(features)[0]
        # Get prediction probability
        prob = rf_model.predict_proba(features)[0]
        return prediction, prob

    # Example of using the prediction function
    def plot_sensor_data_with_prediction(sensor_data):
        """
        Visualize sensor data and display movement prediction.
        
        Args:
            sensor_data (array): Raw accelerometer data of shape (time_steps, 3)
        
        Generates:
            - Line plot of acceleration data for all three axes
            - Prediction label and confidence score
            - Saves visualization as 'prediction_example.png'
            
        Visualization includes:
            - X, Y, Z acceleration traces
            - Predicted movement type
            - Prediction confidence
            - Time axis and acceleration units
        """
        prediction, prob = predict_movement(sensor_data)
        
        plt.figure(figsize=(12, 6))
        time = np.arange(len(sensor_data))
        
        plt.subplot(1, 1, 1)
        plt.plot(time, sensor_data[:, 0], label='X-axis')
        plt.plot(time, sensor_data[:, 1], label='Y-axis')
        plt.plot(time, sensor_data[:, 2], label='Z-axis')
        plt.title(f'Predicted Movement: {prediction}\nConfidence: {np.max(prob):.2f}')
        plt.xlabel('Time Steps')
        plt.ylabel('Acceleration')
        plt.legend()
        plt.grid(True)
        plt.tight_layout()
        plt.savefig('prediction_example.png')
        plt.close()

    # Example usage with a sample from test data
    sample_idx = 0
    sample_data = X[sample_idx]
    plot_sensor_data_with_prediction(sample_data)

    # Save the trained model
    import joblib
    joblib.dump(rf_model, 'movement_classifier.joblib')

    # Function to extract the model to JSON
    def extract_model_to_json(model, feature_names, filename='model.json'):
        """
        Export trained Random Forest model parameters to JSON format.
        
        Args:
            model: Trained RandomForestClassifier model
            feature_names (list): Names of features used in training
            filename (str): Output JSON filename
            
        Exports:
            - Model hyperparameters
            - Feature names
            - Tree structures
            - Decision thresholds
            - Class labels
            
        Format suitable for:
            - Model interpretation
            - Deployment to other platforms
            - Documentation of model structure
        """
        model_data = {
            'n_estimators': model.n_estimators,
            'max_depth': model.max_depth,
            'classes': list(model.classes_),
            'feature_names': feature_names,
            'estimators': []
        }
        
        for estimator in model.estimators_:
            tree_data = {
                'children_left': estimator.tree_.children_left.tolist(),
                'children_right': estimator.tree_.children_right.tolist(),
                'feature': estimator.tree_.feature.tolist(),
                'threshold': estimator.tree_.threshold.tolist(),
                'value': estimator.tree_.value.tolist(),
            }
            model_data['estimators'].append(tree_data)
        
        with open(filename, 'w') as f:
            json.dump(model_data, f, indent=4)

    # Extract and save the model to JSON
    extract_model_to_json(rf_model, feature_names)

