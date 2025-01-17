import numpy as np
import pandas as pd
import os
from pathlib import Path

def load_and_window_data(file_path, window_size=10):
    """
    Load CSV file and create windowed data with labels
    """
    # Read metadata first
    metadata = pd.read_csv(file_path, nrows=4)
    
    # Find the row with activity type by looking for 'ACTIVITY TYPE:' in the first column
    activity_row = metadata[metadata.iloc[:, 0] == 'ACTIVITY TYPE:']
    if activity_row.empty:
        raise ValueError(f"Could not find ACTIVITY TYPE in {file_path}")
    
    # Extract activity type from the second column
    activity_type = activity_row.iloc[0, 1].strip().split(',')[0]
    
    # Read the actual data, skipping metadata rows
    df = pd.read_csv(file_path, skiprows=5)
    
    # Get accelerometer data
    acc_data = df[['ACC Z', 'ACC Y', 'ACC X']].values
    
    # Calculate number of complete windows
    n_windows = len(acc_data) // window_size
    
    # Reshape data into windows
    windowed_data = acc_data[:n_windows * window_size].reshape(n_windows, window_size, 3)
    
    # Create labels array (same label for all windows from this file)
    labels = np.array([activity_type] * n_windows)
    
    print(f"Processing file with activity: {activity_type}")  # Debug print
    
    return windowed_data, labels

def process_data_folder(folder_path, window_size=10):
    """
    Process all CSV files in folder and save as NPY
    """
    folder_path = Path(folder_path)
    all_windowed_data = []
    all_labels = []
    
    # Process each CSV file in the folder
    for file_path in folder_path.glob('*.csv'):
        try:
            windowed_data, labels = load_and_window_data(file_path, window_size)
            all_windowed_data.append(windowed_data)
            all_labels.extend(labels)
        except Exception as e:
            print(f"Error processing {file_path}: {e}")
    
    # Combine all data
    X = np.concatenate(all_windowed_data, axis=0)
    y = np.array(all_labels)
    
    # Save as NPY files
    output_path = folder_path / 'processed'
    output_path.mkdir(exist_ok=True)
    
    np.save(output_path / 'X.npy', X)
    np.save(output_path / 'y.npy', y)
    
    print(f"Saved {len(X)} windows of size {window_size}")
    print(f"Data shape: {X.shape}")
    print(f"Labels shape: {y.shape}")
    print(f"Unique activities: {np.unique(y)}")

# Usage
folder_path = r"D:\Study Docs\Degree Material\Sem 9 proj\IOT\proj"
process_data_folder(folder_path, window_size=10)
