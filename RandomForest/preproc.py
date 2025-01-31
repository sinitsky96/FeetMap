import numpy as np
import pandas as pd
import os
from pathlib import Path

def load_step_data(file_path):
    """
    Load step count data from a CSV file
    """
    # Get label from filename (first letter, capitalized)
    file_name = Path(file_path).stem
    label = file_name[0].upper()
    
    # Read the metadata first (first 6 rows)
    metadata_df = pd.read_csv(file_path, nrows=6, header=None)
    
    # Extract step count from metadata
    actual_steps = None
    estimated_steps = None
    for idx, row in metadata_df.iterrows():
        if isinstance(row[0], str):
            if "COUNT OF ACTUAL STEPS:" in row[0]:
                actual_steps = int(row[1])
            elif "ESTIMATED NUMBER OF STEPS" in row[0]:
                estimated_steps = int(row[1])
    
    return {
        'file_name': file_name,
        'label': label,
        'actual_steps': actual_steps if actual_steps is not None else -1,
        'estimated_steps': estimated_steps if estimated_steps is not None else -1
    }

def load_and_window_data(file_path, window_size=10):
    """
    Load CSV file and create windowed data with labels
    """
    # Get label from filename (first letter, capitalized)
    file_name = Path(file_path).stem  # Get filename without extension
    label = file_name[0].upper()  # Take first letter and capitalize it
    
    # Read the actual data, skipping metadata rows and empty rows
    df = pd.read_csv(file_path, skiprows=6)  # Skip 6 rows to account for metadata and empty row
    
    # Get accelerometer data - ensure column names match new structure, strip quotes if present
    column_names = [col.strip('"') for col in df.columns]  # Strip quotes from column names
    df.columns = column_names
    
    # Skip if NORM column exists
    if 'NORM' in column_names:
        raise ValueError("File contains NORM column - skipping")
    
    # Check if required columns exist
    required_columns = ['Time [sec]', 'ACC X', 'ACC Y', 'ACC Z']
    if not all(col in column_names for col in required_columns):
        raise ValueError("Missing required columns (Time [sec], ACC X, ACC Y, or ACC Z)")
    
    # Only keep time and accelerometer data
    df = df[required_columns]
    
    # Get accelerometer data only
    acc_data = df[['ACC X', 'ACC Y', 'ACC Z']].values
    
    # Calculate number of complete windows
    n_windows = len(acc_data) // window_size
    
    # Reshape data into windows
    windowed_data = acc_data[:n_windows * window_size].reshape(n_windows, window_size, 3)
    
    # Create labels array (same label for all windows from this file)
    labels = np.array([label] * n_windows)
    
    print(f"Processing file {file_name} with label: {label}")
    
    return windowed_data, labels

def process_data_folder(folder_path, window_size=10):
    """
    Process all CSV files in folder and save as NPY
    """
    folder_path = Path(folder_path)
    all_windowed_data = []
    all_labels = []
    step_data = []
    
    # Process each CSV file in the folder
    for file_path in folder_path.glob('*.csv'):
        try:
            # Get step data
            steps = load_step_data(file_path)
            step_data.append(steps)
            
            # Get windowed data for movement classification
            windowed_data, labels = load_and_window_data(file_path, window_size)
            all_windowed_data.append(windowed_data)
            all_labels.extend(labels)
            print(f"Processing file {file_path.stem}")
        except Exception as e:
            print(f"Error processing {file_path.stem}: {str(e)}")
            continue
    
    if not all_windowed_data:
        print("No valid files were processed!")
        return
        
    # Combine all windowed data
    X = np.concatenate(all_windowed_data, axis=0)
    y = np.array(all_labels)
    
    # Save windowed data as NPY files
    output_path = folder_path / 'processed'
    output_path.mkdir(exist_ok=True)
    
    np.save(output_path / 'X.npy', X)
    np.save(output_path / 'y.npy', y)
    
    # Save step data as CSV
    step_df = pd.DataFrame(step_data)
    step_df.to_csv(output_path / 'step_counts.csv', index=False)
    
    print(f"Saved {len(X)} windows of size {window_size}")
    print(f"Data shape: {X.shape}")
    print(f"Labels shape: {y.shape}")
    print(f"Step counts saved for {len(step_data)} files")
    print(f"Unique activities: {np.unique(y)}")

# Usage
if __name__ == "__main__":
    folder_path = r"D:\Study Docs\Degree Material\Sem 9 proj\IOT\proj\FeetMap\RandomForest\data\filtered_data"
    process_data_folder(folder_path, window_size=20)
