"""
Step Counter Module

This module implements a step detection algorithm using accelerometer data from wearable devices.
It uses signal processing techniques including RMS calculation, bandpass filtering, and peak detection
to identify steps from raw accelerometer data.

Key Features:
- Adaptive thresholding for peak detection
- Bandpass filtering to isolate step-related frequencies
- Support for various CSV file formats
- Optimization capabilities using Optuna
- Comprehensive error analysis and reporting

The algorithm works in several stages:
1. Data preprocessing and gravity compensation
2. Signal filtering using a bandpass filter (0.5-5 Hz)
3. Peak detection with adaptive thresholding
4. Step count validation against ground truth

Usage:
    python step_counter.py

The script will:
1. Load accelerometer data from CSV files
2. Optimize detection parameters using training data
3. Evaluate performance on test data
4. Generate detailed performance metrics
"""

import numpy as np
import pandas as pd
import os
from glob import glob
import scipy.signal
import optuna
import matplotlib.pyplot as plt

def calculate_acceleration_rms(acc_x, acc_y, acc_z):
    """
    Calculate the Root Mean Square (RMS) of acceleration components with gravity compensation.
    
    Args:
        acc_x (array): X-axis acceleration data
        acc_y (array): Y-axis acceleration data
        acc_z (array): Z-axis acceleration data (vertical)
    
    Returns:
        array: RMS values of acceleration combining all three axes
        
    Notes:
        - Compensates for gravity by subtracting 9.81 m/s² from z-axis
        - Uses sqrt((x² + y² + z²)/3) for RMS calculation
    """
    try:
        # Add gravity compensation (assuming z-axis is vertical)
        acc_z = acc_z - 9.81  # Subtract gravity
        return np.sqrt((np.square(acc_x) + np.square(acc_y) + np.square(acc_z)) / 3)
    except Exception as e:
        print(f"Error in calculate_acceleration_rms: {str(e)}")
        return np.zeros_like(acc_x)

def find_peaks(data, sampling_rate=50):
    """
    Find peaks in acceleration data using adaptive thresholding.
    
    Args:
        data (array): Preprocessed acceleration data
        sampling_rate (int): Data sampling rate in Hz (default: 50)
    
    Returns:
        array: Indices of detected peaks representing steps
        
    Algorithm:
        1. Calculates dynamic threshold based on signal statistics
        2. Uses scipy.signal.find_peaks with:
           - Adaptive height threshold
           - Minimum distance between peaks
           - Prominence requirement for peak distinction
    """
    try:
        # Calculate dynamic threshold based on data statistics
        std_dev = np.std(data)
        mean_val = np.mean(data)
        threshold = mean_val + 0.2 * std_dev  # Reduced threshold multiplier
        
        # Find peaks with minimum distance between steps
        min_step_distance = int(0.3 * sampling_rate)  # Reduced minimum step distance
        peaks, _ = scipy.signal.find_peaks(
            data,
            height=threshold,
            distance=min_step_distance,
            prominence=0.5  # Added prominence requirement
        )
        
        return peaks
    except Exception as e:
        print(f"Error in find_peaks: {str(e)}")
        return []

def analyze_movement(acc_x, acc_y, acc_z, sampling_rate=50):
    """
    Analyze movement data to detect steps using a multi-stage approach.
    
    Args:
        acc_x (array): X-axis acceleration data
        acc_y (array): Y-axis acceleration data
        acc_z (array): Z-axis acceleration data
        sampling_rate (int): Data sampling rate in Hz (default: 50)
    
    Returns:
        list: [number_of_steps, peak_indices, rms_values]
        
    Processing Pipeline:
        1. Data validation and conversion
        2. RMS calculation with gravity compensation
        3. Bandpass filtering (0.5-5 Hz) to isolate step frequencies
        4. Signal normalization
        5. Peak detection for step counting
    """
    try:
        if len(acc_x) < 3 or len(acc_y) < 3 or len(acc_z) < 3:
            print("Not enough data points for analysis")
            return [0, [], []]
            
        acc_x = np.array(acc_x, dtype=np.float32)
        acc_y = np.array(acc_y, dtype=np.float32)
        acc_z = np.array(acc_z, dtype=np.float32)
        
        # Calculate acceleration RMS with gravity compensation
        acc_rms = calculate_acceleration_rms(acc_x, acc_y, acc_z)
        
        # Apply bandpass filter to focus on step frequencies (0.5-5 Hz)
        cutoff_low = 0.5  # Lower cutoff frequency
        cutoff_high = 5.0  # Upper cutoff frequency
        nyquist = 0.5 * sampling_rate
        normal_cutoff_low = cutoff_low / nyquist
        normal_cutoff_high = cutoff_high / nyquist
        b, a = scipy.signal.butter(2, [normal_cutoff_low, normal_cutoff_high], btype='band')
        filtered_rms = scipy.signal.filtfilt(b, a, acc_rms)
        
        # Normalize the filtered data
        filtered_rms = (filtered_rms - np.mean(filtered_rms)) / np.std(filtered_rms)
        
        # Find peaks with adaptive thresholding
        peaks = find_peaks(filtered_rms, sampling_rate)
        
        # Return array: [peak_count, peaks[], acc_rms[]]
        return [len(peaks), peaks, acc_rms.tolist()]
        
    except Exception as e:
        print(f"Error in analyze_movement: {str(e)}")
        return [0, [], []]

def extract_step_count(header_lines):
    """
    Extract ground truth step count from CSV file headers.
    
    Args:
        header_lines (list): List of strings containing file header lines
    
    Returns:
        int or None: Extracted step count or None if not found
        
    Supports Multiple Formats:
        - "COUNT OF ACTUAL STEPS: n"
        - "ACTUAL STEPS: n"
        - "STEPS: n"
        - Various CSV formatting patterns
    """
    for line in header_lines:
        # Check for various possible formats of step count
        if any(pattern in line for pattern in ['COUNT OF ACTUAL STEPS', 'ACTUAL STEPS', 'STEPS:']):
            try:
                # Split by comma and clean each part
                parts = [part.strip().strip('"').strip() for part in line.split(',')]
                
                # If any part is just a number, that's our step count
                for part in parts:
                    if part.isdigit():
                        return int(part)
                
                # If we didn't find a pure number, check for colon format
                for part in parts:
                    if ':' in part:
                        # Split by colon and check the second part
                        colon_parts = [p.strip().strip('"').strip() for p in part.split(':')]
                        if len(colon_parts) >= 2 and colon_parts[1].isdigit():
                            return int(colon_parts[1])
                            
            except (ValueError, IndexError) as e:
                print(f"Error parsing line '{line}': {str(e)}")
                continue
    return None

def process_file(file_path, analyze_func=analyze_movement):
    """
    Process a single accelerometer data file for step counting.
    
    Args:
        file_path (str): Path to the CSV file
        analyze_func (function): Function to use for movement analysis
    
    Returns:
        dict: Results containing:
            - file: filename
            - detected_steps: number of steps detected
            - actual_steps: ground truth step count
            - error: detection error (detected - actual)
            - absolute_error: absolute error value
            - relative_error: error relative to actual count
            
    Features:
        - Flexible CSV format handling
        - Multiple column name patterns supported
        - Automatic data validation and cleaning
        - Comprehensive error reporting
    """
    try:
        # Read the first few lines to determine the format
        with open(file_path, 'r') as f:
            header_lines = [next(f) for _ in range(10)]  # Read more lines to be safe
        
        # Get actual step count from header
        actual_steps = extract_step_count(header_lines)
        if actual_steps is None:
            print(f"Could not extract step count from {file_path}")
            return None
            
        # Find the header row by looking for "Time" or column names
        start_row = None
        header_row = None
        for i, line in enumerate(header_lines):
            if ('Time' in line or 'ACC X' in line or 'ACC Y' in line or 'ACC Z' in line or 
                'AccX' in line or 'AccY' in line or 'AccZ' in line):
                start_row = i
                header_row = i
                break
        
        if start_row is None:
            print(f"Could not find header row in {file_path}")
            return None

        # Read the CSV with the correct header row
        try:
            df = pd.read_csv(file_path, skiprows=header_row)
        except Exception as e:
            print(f"Could not read CSV format in {file_path}: {str(e)}")
            return None

        # Clean up column names by stripping whitespace and quotes
        df.columns = [col.strip().strip('"').strip() for col in df.columns]

        # Check if we have the required columns
        required_cols = ['ACC X', 'ACC Y', 'ACC Z']
        if not all(col in df.columns for col in required_cols):
            # Try alternative column names
            alt_cols = ['AccX', 'AccY', 'AccZ']
            if all(col in df.columns for col in alt_cols):
                df = df.rename(columns={'AccX': 'ACC X', 'AccY': 'ACC Y', 'AccZ': 'ACC Z'})
            else:
                # If columns still not found, try using positional columns if they match expected pattern
                if len(df.columns) >= 4:  # Time + 3 acceleration columns
                    # Check if columns look like acceleration data (mostly numeric with expected ranges)
                    sample = df.iloc[:100]  # Look at first 100 rows
                    try:
                        # Convert columns to numeric, ignoring errors
                        numeric_cols = sample.apply(pd.to_numeric, errors='coerce')
                        # Check which columns have values in typical acceleration range (-25 to 25)
                        acc_cols = [col for col in df.columns[1:] if 
                                  (-25 <= numeric_cols[col].min() <= 25) and 
                                  (-25 <= numeric_cols[col].max() <= 25)]
                        if len(acc_cols) >= 3:
                            # Use the first three suitable columns as acceleration
                            df = df.rename(columns={
                                acc_cols[0]: 'ACC X',
                                acc_cols[1]: 'ACC Y',
                                acc_cols[2]: 'ACC Z'
                            })
                        else:
                            print(f"Missing required acceleration columns in {file_path}")
                            print(f"Available columns: {list(df.columns)}")
                            return None
                    except Exception as e:
                        print(f"Error analyzing columns in {file_path}: {str(e)}")
                        return None
                else:
                    print(f"Missing required acceleration columns in {file_path}")
                    print(f"Available columns: {list(df.columns)}")
                    return None

        # Analyze movement using the provided function
        result = analyze_func(
            df['ACC X'].values,
            df['ACC Y'].values,
            df['ACC Z'].values,
            sampling_rate=33
        )
        
        detected_steps = result[0]  # Number of peaks detected
        
        return {
            'file': os.path.basename(file_path),
            'detected_steps': detected_steps,
            'actual_steps': actual_steps,
            'error': detected_steps - actual_steps,  # Calculate raw error
            'absolute_error': abs(detected_steps - actual_steps),  # Absolute error
            'relative_error': (detected_steps - actual_steps) / actual_steps if actual_steps > 0 else 0  # Relative error
        }
        
    except Exception as e:
        print(f"Error processing {file_path}: {str(e)}")
        return None

def visualize_best_trial(file_path, best_params, sampling_rate=33):
    """
    Visualize the step detection results for the best trial.
    
    Args:
        file_path (str): Path to the CSV file to visualize
        best_params (dict): Best parameters from optimization
        sampling_rate (int): Data sampling rate in Hz
    """
    try:
        # Read the first few lines to determine the format
        with open(file_path, 'r') as f:
            header_lines = [next(f) for _ in range(10)]
        
        # Find the header row by looking for "Time" or column names
        start_row = None
        header_row = None
        for i, line in enumerate(header_lines):
            if ('Time' in line or 'ACC X' in line or 'ACC Y' in line or 'ACC Z' in line or 
                'AccX' in line or 'AccY' in line or 'AccZ' in line):
                start_row = i
                header_row = i
                break
        
        if start_row is None:
            print(f"Could not find header row in {file_path}")
            return None

        # Read the CSV with the correct header row
        try:
            df = pd.read_csv(file_path, skiprows=header_row)
        except Exception as e:
            print(f"Could not read CSV format in {file_path}: {str(e)}")
            return None

        # Clean up column names by stripping whitespace and quotes
        df.columns = [col.strip().strip('"').strip() for col in df.columns]

        # Check if we have the required columns
        required_cols = ['ACC X', 'ACC Y', 'ACC Z']
        if not all(col in df.columns for col in required_cols):
            # Try alternative column names
            alt_cols = ['AccX', 'AccY', 'AccZ']
            if all(col in df.columns for col in alt_cols):
                df = df.rename(columns={'AccX': 'ACC X', 'AccY': 'ACC Y', 'AccZ': 'ACC Z'})
            else:
                print(f"Missing required acceleration columns in {file_path}")
                print(f"Available columns: {list(df.columns)}")
                return None

        # Process with best parameters
        acc_x = df['ACC X'].values
        acc_y = df['ACC Y'].values
        acc_z = df['ACC Z'].values
        
        acc_rms = calculate_acceleration_rms(acc_x, acc_y, acc_z)
        
        # Apply bandpass filter with best parameters
        nyquist = 0.5 * sampling_rate
        normal_cutoff_low = best_params['cutoff_low'] / nyquist
        normal_cutoff_high = best_params['cutoff_high'] / nyquist
        b, a = scipy.signal.butter(2, [normal_cutoff_low, normal_cutoff_high], btype='band')
        filtered_rms = scipy.signal.filtfilt(b, a, acc_rms)
        
        # Normalize
        filtered_rms = (filtered_rms - np.mean(filtered_rms)) / np.std(filtered_rms)
        
        # Find peaks with best parameters
        std_dev = np.std(filtered_rms)
        mean_val = np.mean(filtered_rms)
        threshold = mean_val + best_params['threshold_multiplier'] * std_dev
        min_step_samples = int(best_params['min_step_distance'] * sampling_rate)
        peaks, _ = scipy.signal.find_peaks(
            filtered_rms,
            height=threshold,
            distance=min_step_samples,
            prominence=best_params['prominence']
        )
        
        # Create visualization
        plt.figure(figsize=(15, 6))
        
        # Plot raw RMS
        plt.subplot(2, 1, 1)
        plt.plot(acc_rms, label='Raw RMS', alpha=0.5)
        plt.title('Raw RMS Acceleration')
        plt.xlabel('Samples')
        plt.ylabel('Acceleration (m/s²)')
        plt.legend()
        
        # Plot filtered signal with peaks
        plt.subplot(2, 1, 2)
        plt.plot(filtered_rms, label='Filtered Signal')
        plt.plot(peaks, filtered_rms[peaks], "x", label='Detected Steps')
        plt.axhline(threshold, color='r', linestyle='--', label='Threshold')
        plt.title(f'Filtered Signal with Detected Steps (Total: {len(peaks)})')
        plt.xlabel('Samples')
        plt.ylabel('Normalized Acceleration')
        plt.legend()
        
        plt.tight_layout()
        plt.show()
        
    except Exception as e:
        print(f"Error in visualization: {str(e)}")

def main():
    """
    Main execution function for step counting evaluation.
    
    Workflow:
        1. Processes test files
        2. Generates performance metrics
        3. Saves results to CSV
        4. Prints summary statistics
        
    Metrics Reported:
        - Mean error
        - Mean absolute error
        - Mean relative error
        - Root mean squared error
    """
    # Use the globally stored test files for final evaluation
    global TEST_FILES
    results = []
    
    for file_path in TEST_FILES:
        result = process_file(file_path)
        if result:
            results.append(result)
            print(f"File: {result['file']}")
            print(f"Detected Steps: {result['detected_steps']}")
            print(f"Actual Steps: {result['actual_steps']}")
            print("-" * 50)
    
    if results:
        # Save results to CSV
        results_df = pd.DataFrame(results)
        results_df.to_csv('step_detection_results.csv', index=False)
        print("\nResults saved to step_detection_results.csv")
        
        # Print summary statistics
        print("\nSummary Statistics:")
        print(f"Total files processed successfully: {len(results)}")
        print(f"Mean error: {results_df['error'].mean():.2f} steps")
        print(f"Mean absolute error: {results_df['absolute_error'].mean():.2f} steps")
        print(f"Mean relative error: {results_df['relative_error'].mean() * 100:.2f}%")
        print(f"Root mean squared error: {np.sqrt((results_df['error']**2).mean()):.2f} steps")
        
        # Visualize best trial
        if TEST_FILES:
            visualize_best_trial(TEST_FILES[1], best_params)
    else:
        print("No files were processed successfully")

def run_optimization():
    """
    Run hyperparameter optimization using Optuna.
    
    Process:
        1. Splits data into training and test sets
        2. Optimizes parameters using training data
        3. Saves best parameters for evaluation
        
    Optimization Target:
        Minimizes total absolute error in step detection
    """
    # Split files into train and test
    csv_files = glob('data/filtered_data/*.csv')
    np.random.shuffle(csv_files)
    split_idx = int(len(csv_files) * 0.8)
    train_files = csv_files[:split_idx]
    test_files = csv_files[split_idx:]
    
    # Store test files for final evaluation
    global TEST_FILES
    TEST_FILES = test_files
    
    study = optuna.create_study(direction='minimize')
    study.optimize(lambda trial: objective(trial, train_files), n_trials=100)
    
    print("Best parameters:")
    print(study.best_params)
    
    return study.best_params

def objective(trial, train_files):
    """
    Objective function for Optuna optimization.
    
    Args:
        trial: Optuna trial object
        train_files (list): List of training file paths
    
    Parameters Optimized:
        - threshold_multiplier: Peak detection sensitivity
        - min_step_distance: Minimum time between steps
        - prominence: Required peak prominence
        - cutoff_low: Lower bandpass frequency
        - cutoff_high: Upper bandpass frequency
    
    Returns:
        float: Total error metric to minimize
    """
    # Define parameters to optimize
    threshold_multiplier = trial.suggest_float('threshold_multiplier', 0.1, 0.5)
    min_step_distance = trial.suggest_float('min_step_distance', 0.2, 0.5)
    prominence = trial.suggest_float('prominence', 0.3, 1.0)
    cutoff_low = trial.suggest_float('cutoff_low', 0.3, 1.0)
    cutoff_high = trial.suggest_float('cutoff_high', 3.0, 6.0)
    
    # Create optimized analysis function with current parameters
    def analyze_movement_optimized(acc_x, acc_y, acc_z, sampling_rate=50):
        try:
            if len(acc_x) < 3 or len(acc_y) < 3 or len(acc_z) < 3:
                return [0, [], []]
                
            acc_x = np.array(acc_x, dtype=np.float32)
            acc_y = np.array(acc_y, dtype=np.float32)
            acc_z = np.array(acc_z, dtype=np.float32)
            
            acc_rms = calculate_acceleration_rms(acc_x, acc_y, acc_z)
            
            nyquist = 0.5 * sampling_rate
            normal_cutoff_low = cutoff_low / nyquist
            normal_cutoff_high = cutoff_high / nyquist
            b, a = scipy.signal.butter(2, [normal_cutoff_low, normal_cutoff_high], btype='band')
            filtered_rms = scipy.signal.filtfilt(b, a, acc_rms)
            
            filtered_rms = (filtered_rms - np.mean(filtered_rms)) / np.std(filtered_rms)
            
            # Find peaks with current parameters
            std_dev = np.std(filtered_rms)
            mean_val = np.mean(filtered_rms)
            threshold = mean_val + threshold_multiplier * std_dev
            min_step_samples = int(min_step_distance * sampling_rate)
            peaks, _ = scipy.signal.find_peaks(
                filtered_rms,
                height=threshold,
                distance=min_step_samples,
                prominence=prominence
            )
            
            return [len(peaks), peaks, acc_rms.tolist()]
            
        except Exception as e:
            print(f"Error in analyze_movement: {str(e)}")
            return [0, [], []]

    # Process only training files during optimization
    total_error = 0
    for file_path in train_files:
        result = process_file(file_path, analyze_func=analyze_movement_optimized)
        if result:
            total_error += abs(result['error'])
    
    return total_error

def split_train_test(csv_files, test_size=0.2):
    """Split files into training and test sets"""
    np.random.shuffle(csv_files)
    split_idx = int(len(csv_files) * (1 - test_size))
    return csv_files[:split_idx], csv_files[split_idx:]

if __name__ == "__main__":
    # Get all CSV files
    csv_files = glob('data/filtered_data/*.csv')
    
    # Split files into train and test
    train_files, test_files = split_train_test(csv_files)
    
    # Store test files globally for final evaluation
    global TEST_FILES
    TEST_FILES = test_files
    
    # Run optimization with train_files
    best_params = run_optimization()
    
    # Run main with test_files
    main() 