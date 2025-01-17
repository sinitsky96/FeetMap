import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix
from scipy.stats import kurtosis, skew
import json

# Load data from .npy files
X = np.load(r'D:\Study Docs\Degree Material\Sem 9 proj\IOT\proj\processed\X.npy')
y = np.load(r'D:\Study Docs\Degree Material\Sem 9 proj\IOT\proj\processed\y.npy')
print("Original data shape:", X.shape)
print("Labels shape:", y.shape)

def extract_important_features(data):
    # Reshape the input data if it's 3D
    if len(data.shape) == 3:
        num_samples, time_steps, features = data.shape
        all_features = []
        for sample in data:
            # Calculate only the important features
            max_val = np.max(sample, axis=0)
            min_val = np.min(sample, axis=0)
            median = np.median(sample, axis=0)
            mean = np.mean(sample, axis=0)
            
            # Concatenate selected features for this sample
            sample_features = np.concatenate([max_val, min_val, median, mean])
            all_features.append(sample_features)
        return np.array(all_features)
    else:
        raise ValueError("Expected 3D input data")

# Apply feature extraction to convert 3D data to 2D
X_processed = extract_important_features(X)
print("Processed features shape:", X_processed.shape)

# Create feature names for visualization
feature_names = []
for stat in ['max', 'min', 'median', 'mean']:
    for axis in ['x', 'y', 'z']:
        feature_names.append(f'{stat}_{axis}')

# Split data
X_train, X_test, y_train, y_test = train_test_split(X_processed, y, test_size=0.2, random_state=42)

# Create and train model
rf_model = RandomForestClassifier(max_depth=7,n_estimators=50, random_state=42)
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
    Make predictions on new sensor data
    Args:
        sensor_data: numpy array of shape (time_steps, 3) containing x, y, z accelerometer data
    Returns:
        prediction: predicted movement class
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
    Plot sensor data and show prediction
    Args:
        sensor_data: numpy array of shape (time_steps, 3) containing x, y, z accelerometer data
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
    Extracts the trained Random Forest model parameters to a JSON file.
    Args:
        model: Trained RandomForestClassifier model.
        feature_names: List of feature names.
        filename: Name of the JSON file to save to.
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

