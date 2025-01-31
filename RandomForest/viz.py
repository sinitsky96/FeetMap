import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from sklearn.manifold import TSNE
from sklearn.decomposition import PCA
from sklearn.impute import SimpleImputer
from sklearn.preprocessing import StandardScaler
import joblib
from model import (
    extract_important_features,
    extract_statistical_features,
    extract_frequency_features,
    extract_magnitude_features
)
import matplotlib.animation as animation
from matplotlib.animation import PillowWriter
import os
import imageio.v2 as imageio

def create_scatter_plot(X_2d, y, unique_labels, title, filename):
    plt.figure(figsize=(12, 8))
    
    # Create numeric labels for coloring
    label_to_num = {label: i for i, label in enumerate(unique_labels)}
    y_numeric = np.array([label_to_num[label] for label in y])
    
    scatter = plt.scatter(X_2d[:, 0], X_2d[:, 1], c=y_numeric, 
                         cmap='tab10', alpha=0.6)

    # Add legend
    legend_elements = [plt.Line2D([0], [0], marker='o', color='w', 
                                markerfacecolor=plt.cm.tab10(i), 
                                label=label, markersize=10)
                      for i, label in enumerate(unique_labels)]
    plt.legend(handles=legend_elements, title='Movement Type')

    plt.title(title)
    plt.xlabel('Component 1')
    plt.ylabel('Component 2')
    plt.grid(True, alpha=0.3)
    plt.tight_layout()
    plt.savefig(filename, dpi=300, bbox_inches='tight')
    plt.close()
    print(f"Saved {filename}")

def dimensionality_reduction_viz():
    # Set random seed for reproducibility
    np.random.seed(42)

    # Load the data and model
    print("Loading data...")
    X = np.load(r'D:\Study Docs\Degree Material\Sem 9 proj\IOT\proj\FeetMap\RandomForest\data\filtered_data\processed\X.npy')
    y = np.load(r'D:\Study Docs\Degree Material\Sem 9 proj\IOT\proj\FeetMap\RandomForest\data\filtered_data\processed\y.npy')
    model = joblib.load('movement_classifier.joblib')

    # Take a random subsample of 1000 samples
    print("Taking subsample...")
    n_samples = min(1000, len(X))
    indices = np.random.choice(len(X), n_samples, replace=False)
    X = X[indices]
    y = y[indices]

    # Extract features using our comprehensive feature extraction pipeline
    print("Extracting features...")
    X_features = extract_important_features(X)

    # Clean the data - handle NaN and infinite values
    print("Cleaning data...")
    X_features = np.where(np.isfinite(X_features), X_features, np.nan)
    imputer = SimpleImputer(strategy='mean')
    X_features_clean = imputer.fit_transform(X_features)

    # Scale the features
    print("Scaling features...")
    scaler = StandardScaler()
    X_features_scaled = scaler.fit_transform(X_features_clean)

    # Perform t-SNE
    print("Performing t-SNE...")
    tsne = TSNE(n_components=2, random_state=42, perplexity=30, max_iter=1000)
    X_tsne = tsne.fit_transform(X_features_scaled)

    # Perform PCA
    print("Performing PCA...")
    pca = PCA(n_components=2, random_state=42)
    X_pca = pca.fit_transform(X_features_scaled)

    # Get unique labels
    unique_labels = np.unique(y)

    # Create t-SNE visualization
    create_scatter_plot(
        X_tsne, y, unique_labels,
        't-SNE Visualization of Movement Features\n(1000 Sample Subset)',
        'tsne_visualization.png'
    )

    # Create PCA visualization
    create_scatter_plot(
        X_pca, y, unique_labels,
        f'PCA Visualization of Movement Features\n(1000 Sample Subset, Explained Variance: {pca.explained_variance_ratio_.sum():.2%})',
        'pca_visualization.png'
    )

    # Create scree plot for PCA
    plt.figure(figsize=(10, 6))
    pca_full = PCA(random_state=42)
    pca_full.fit(X_features_scaled)
    
    plt.plot(range(1, len(pca_full.explained_variance_ratio_) + 1),
             np.cumsum(pca_full.explained_variance_ratio_), 'bo-')
    plt.axhline(y=0.95, color='r', linestyle='--', label='95% Explained Variance')
    plt.xlabel('Number of Components')
    plt.ylabel('Cumulative Explained Variance Ratio')
    plt.title('PCA Scree Plot')
    plt.grid(True)
    plt.legend()
    plt.tight_layout()
    plt.savefig('pca_scree_plot.png', dpi=300, bbox_inches='tight')
    plt.close()
    print("Saved pca_scree_plot.png")

    # Create feature importance visualization
    print("Creating feature importance visualization...")
    feature_importance = model.feature_importances_
    feature_names = [
        # Statistical features
        *[f'{stat}_{axis}' for stat in ['mean', 'std', 'min', 'max'] for axis in ['x', 'y', 'z']],
        # Correlation features
        'corr_xy', 'corr_yz', 'corr_xz',
        # Zero crossing rate
        'zero_cross_x', 'zero_cross_y', 'zero_cross_z',
        # RMS
        'rms_x', 'rms_y', 'rms_z',
        # FFT components
        *[f'fft_{axis}_{i}' for axis in ['x', 'y', 'z'] for i in range(5)],
        *[f'spectral_energy_{axis}' for axis in ['x', 'y', 'z']],
        *[f'dominant_freq_{axis}' for axis in ['x', 'y', 'z']],
        # Magnitude features
        'sma_x', 'sma_y', 'sma_z', 'svm'
    ]

    # Get top 15 most important features
    sorted_idx = np.argsort(feature_importance)[::-1][:15]
    plt.figure(figsize=(12, 8))
    # Sort the values and names in descending order, then reverse for plotting
    sorted_importance = feature_importance[sorted_idx]
    sorted_names = [feature_names[i] for i in sorted_idx]
    # Reverse the order for plotting (most important at bottom)
    sorted_importance = sorted_importance[::-1]
    sorted_names = sorted_names[::-1]
    bars = plt.barh(range(15), sorted_importance)
    plt.yticks(range(15), sorted_names)
    plt.xlabel('Feature Importance')
    plt.title('Top 15 Most Important Features')
    
    # Add value labels on the bars
    for i, v in enumerate(sorted_importance):
        plt.text(v, i, f' {v:.3f}', va='center')
    
    plt.tight_layout()
    plt.savefig('feature_importance_top15.png', dpi=300, bbox_inches='tight')
    plt.close()
    print("Saved feature_importance_top15.png")

    # Create feature distribution plot
    print("Creating feature distribution plot...")
    plt.figure(figsize=(15, 8))
    top_5_idx = sorted_idx[:5]
    
    for i, label in enumerate(unique_labels):
        mask = y == label
        violin_parts = plt.violinplot(X_features_clean[mask][:, top_5_idx], 
                                    positions=np.arange(5) + i*0.3)
        for pc in violin_parts['bodies']:
            pc.set_facecolor(plt.cm.tab10(i))
            pc.set_alpha(0.3)

    plt.title('Distribution of Top 5 Most Important Features by Movement Type')
    plt.xlabel('Feature')
    plt.ylabel('Feature Value')
    plt.xticks(np.arange(5), [feature_names[i] for i in top_5_idx], rotation=45)
    plt.legend(unique_labels, title='Movement Type')
    plt.grid(True, alpha=0.3)
    plt.tight_layout()
    plt.savefig('feature_distribution_top5.png', dpi=300, bbox_inches='tight')
    plt.close()
    print("Saved feature_distribution_top5.png")

    # Print statistics
    print("\nData cleaning statistics:")
    print(f"Original shape: {X_features.shape}")
    print(f"Number of NaN values before cleaning: {np.isnan(X_features).sum()}")
    print(f"Number of infinite values before cleaning: {np.isinf(X_features).sum()}")
    print(f"Shape after cleaning: {X_features_clean.shape}")
    print(f"\nPCA explained variance ratio: {pca.explained_variance_ratio_}")
    print(f"Total variance explained by 2 components: {pca.explained_variance_ratio_.sum():.2%}")


    # Clean up frame files
    print("Cleaning up frame files...")
    for frame_file in os.listdir('frames'):
        os.remove(os.path.join('frames', frame_file))
    os.rmdir('frames')

def create_window_animation(window_size=20, total_frames=300, sequences_per_label=2):
    """Create an animation showing moving windows of accelerometer data with real-time feature updates"""
    print("Creating window animation...")
    
    # Load the data
    X = np.load(r'D:\Study Docs\Degree Material\Sem 9 proj\IOT\proj\FeetMap\RandomForest\data\filtered_data\processed\X.npy')
    y = np.load(r'D:\Study Docs\Degree Material\Sem 9 proj\IOT\proj\FeetMap\RandomForest\data\filtered_data\processed\y.npy')
    
    print(f"Data shape: {X.shape}")
    
    # Create output directory for frames
    os.makedirs('frames', exist_ok=True)
    
    # Get unique labels
    unique_labels = np.unique(y)
    print(f"Unique labels: {unique_labels}")
    
    # Create concatenated sequences for each label
    data_windows = []
    for label in unique_labels:
        print(f"Processing label {label}...")
        # Get indices for this label
        label_indices = np.where(y == label)[0]
        
        # Take a random selection of sequences
        selected_indices = np.random.choice(label_indices, sequences_per_label, replace=False)
        
        # Concatenate the sequences with a small gap between them
        gap = np.zeros((5, 3))  # 5 samples of zeros as gap
        concatenated_data = []
        for idx in selected_indices:
            if concatenated_data:
                concatenated_data.append(gap)
            sequence = X[idx]
            concatenated_data.extend([sequence] * 2)  # Repeat each sequence twice
        
        concatenated_data = np.vstack(concatenated_data)
        data_windows.append((label, concatenated_data))
        print(f"Created sequence of length {len(concatenated_data)}")
    
    # Calculate step size to get desired number of frames
    data_length = min([len(data) for _, data in data_windows])
    print(f"Minimum sequence length: {data_length}")
    
    if data_length <= window_size:
        print("Error: Data length too short for animation")
        return
    
    max_start = data_length - window_size
    step_size = max(1, max_start // total_frames)
    frame_positions = range(0, max_start, step_size)
    n_frames = len(frame_positions)
    
    print(f"Data length: {data_length}")
    print(f"Window size: {window_size}")
    print(f"Step size: {step_size}")
    print(f"Number of frames: {n_frames}")
    
    # Set up the figure with a consistent size
    plt.rcParams['figure.figsize'] = [15, 10]
    plt.rcParams['figure.dpi'] = 100
    plt.rcParams['axes.grid'] = True
    plt.rcParams['grid.alpha'] = 0.3
    
    # Calculate y-axis limits for consistency
    all_data = np.vstack([data for _, data in data_windows])
    y_min, y_max = np.min(all_data), np.max(all_data)
    y_range = y_max - y_min
    y_limits = (y_min - 0.1 * y_range, y_max + 0.1 * y_range)
    
    # Pre-calculate all features for better y-axis limits
    all_means = []
    all_spectral = []
    for _, data in data_windows:
        for i in range(0, len(data) - window_size + 1, step_size):
            window = data[i:i+window_size]
            features = extract_statistical_features(window)
            freq_features = extract_frequency_features(window)
            all_means.append(features[:3])
            all_spectral.append(freq_features[15:18])
    
    mean_min = np.min(all_means)
    mean_max = np.max(all_means)
    mean_range = mean_max - mean_min
    mean_limits = (mean_min - 0.1 * mean_range, mean_max + 0.1 * mean_range)
    
    spectral_max = np.max(all_spectral)
    
    # Generate frames
    print(f"Generating {n_frames} frames...")
    frames = []
    
    for frame_idx, start_idx in enumerate(frame_positions):
        fig = plt.figure(figsize=(15, 10))
        gs = plt.GridSpec(3, len(unique_labels), height_ratios=[2, 1, 1], hspace=0.4, wspace=0.3)
        end_idx = start_idx + window_size
        
        # Create subplots for each label
        for label_idx, (label, data) in enumerate(data_windows):
            # Signal plot
            ax_signal = fig.add_subplot(gs[0, label_idx])
            ax_signal.set_title(f'{"Walking" if label == "W" else "Stairs"} Activity', pad=10)
            
            # Plot full sequence in light gray
            time_full = np.arange(len(data))
            ax_signal.plot(time_full, data[:, 0], 'r-', alpha=0.1, label='_nolegend_')
            ax_signal.plot(time_full, data[:, 1], 'g-', alpha=0.1, label='_nolegend_')
            ax_signal.plot(time_full, data[:, 2], 'b-', alpha=0.1, label='_nolegend_')
            
            # Plot window
            if end_idx <= len(data):
                window_data = data[start_idx:end_idx]
                time_window = np.arange(start_idx, end_idx)
                ax_signal.plot(time_window, window_data[:, 0], 'r-', label='X', linewidth=2)
                ax_signal.plot(time_window, window_data[:, 1], 'g-', label='Y', linewidth=2)
                ax_signal.plot(time_window, window_data[:, 2], 'b-', label='Z', linewidth=2)
                
                # Add window boundaries
                ax_signal.axvline(x=start_idx, color='k', linestyle='--', alpha=0.3)
                ax_signal.axvline(x=end_idx, color='k', linestyle='--', alpha=0.3)
                
                # Extract and plot features
                features = extract_statistical_features(window_data)
                mean_features = features[:3]  # First 3 are mean values
                
                # Mean values bar plot
                ax_means = fig.add_subplot(gs[1, label_idx])
                bars = ax_means.bar(['X', 'Y', 'Z'], mean_features, color=['r', 'g', 'b'])
                ax_means.set_title('Mean Acceleration', pad=10)
                ax_means.set_ylim(mean_limits)
                
                # Add value labels on bars
                for bar in bars:
                    height = bar.get_height()
                    ax_means.text(bar.get_x() + bar.get_width()/2., height,
                                f'{height:.2f}', ha='center', va='bottom')
                
                # Frequency features
                freq_features = extract_frequency_features(window_data)
                spectral_energy = freq_features[15:18]  # Spectral energy features
                
                # Spectral energy bar plot
                ax_freq = fig.add_subplot(gs[2, label_idx])
                bars = ax_freq.bar(['X', 'Y', 'Z'], spectral_energy, color=['r', 'g', 'b'])
                ax_freq.set_title('Spectral Energy', pad=10)
                ax_freq.set_ylim(0, spectral_max * 1.1)
                
                # Add value labels on bars
                for bar in bars:
                    height = bar.get_height()
                    ax_freq.text(bar.get_x() + bar.get_width()/2., height,
                               f'{height:.1e}', ha='center', va='bottom')
            
            ax_signal.grid(True, alpha=0.3)
            ax_signal.set_ylim(y_limits)
            if label_idx == 0:  # Only add legend to first plot
                ax_signal.legend(loc='upper right')
        
        plt.suptitle(f'Movement Analysis: Walking vs Stairs\nFrame {frame_idx + 1}/{n_frames}', 
                    y=0.95, fontsize=14)
        
        # Save frame with fixed size
        plt.savefig(f'frames/frame_{frame_idx:03d}.png', 
                   bbox_inches='tight',
                   dpi=100)
        plt.close()
        
        # Read the saved frame
        frame = imageio.imread(f'frames/frame_{frame_idx:03d}.png')
        frames.append(frame)
        
        # Print progress
        if frame_idx % 10 == 0:
            print(f"Generated frame {frame_idx + 1}/{n_frames}")
    
    # Save as GIF with faster frame rate
    print("Saving animation...")
    imageio.mimsave('window_animation.gif', frames, fps=15)  # Increased FPS for smoother animation
    print("Animation saved as window_animation.gif")
    
    # Clean up frame files
    print("Cleaning up frame files...")
    for frame_file in os.listdir('frames'):
        os.remove(os.path.join('frames', frame_file))
    os.rmdir('frames')

if __name__ == "__main__":
    # dimensionality_reduction_viz()
    create_window_animation(window_size=20, total_frames=200, sequences_per_label=2)
    # pass