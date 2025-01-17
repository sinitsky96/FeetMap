import numpy as np

def main():
    return "Python acceleration analysis ready!"

def calculate_acceleration_norm(acc_x, acc_y, acc_z):
    """Calculate the acceleration norm using the formula N = √(ACC_X² + ACC_Y² + ACC_Z²)"""
    try:
        return np.sqrt(np.square(acc_x) + np.square(acc_y) + np.square(acc_z))
    except Exception as e:
        print(f"Error in calculate_acceleration_norm: {str(e)}")
        return np.zeros_like(acc_x)

def find_peaks(data, threshold=10.0):
    """Find peaks in acceleration data using a simple threshold method"""
    try:
        peaks = []
        # Convert data to numpy array if it isn't already
        data = np.array(data)
        # Add minimal noise filtering
        window_size = 3
        smoothed_data = np.convolve(data, np.ones(window_size)/window_size, mode='valid')
        
        # Find peaks in smoothed data
        for i in range(1, len(smoothed_data) - 1):
            if smoothed_data[i] > threshold:  # Check if point exceeds threshold
                if smoothed_data[i] > smoothed_data[i-1] and smoothed_data[i] > smoothed_data[i+1]:
                    peaks.append(i + window_size//2)  # Adjust index for original data
        return peaks
    except Exception as e:
        print(f"Error in find_peaks: {str(e)}")
        return []

def analyze_movement(acc_x, acc_y, acc_z, threshold=10.0):
    """Analyze movement data and return [peak_count, peaks[], acc_norm[]]"""
    try:
        print("Starting analysis...")
        if len(acc_x) < 3 or len(acc_y) < 3 or len(acc_z) < 3:
            print("Not enough data points for analysis")
            return [0, [], []]
            
        # Convert inputs to numpy arrays if they aren't already
        acc_x = np.array(acc_x, dtype=np.float32)
        acc_y = np.array(acc_y, dtype=np.float32)
        acc_z = np.array(acc_z, dtype=np.float32)
        
        # Calculate acceleration norm - using Z-axis primarily like in visualization
        acc_norm = np.sqrt(acc_x**2 + acc_y**2 + acc_z**2)
        
        # Find peaks with adjusted threshold
        peaks = find_peaks(acc_norm, threshold)
        print(f"Found {len(peaks)} peaks")
        
        # Return array: [peak_count, peaks[], acc_norm[]]
        result = [
            len(peaks),           # peak_count at index 0
            peaks,               # peaks array at index 1
            acc_norm.tolist()    # acc_norm array at index 2
        ]
        
        print(f"Final result: {result}")
        return result
        
    except Exception as e:
        print(f"Python error in analyze_movement: {str(e)}")
        return [0, [], []]  # Return empty result in case of error