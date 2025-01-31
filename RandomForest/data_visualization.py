import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
from model import extract_important_features
from pathlib import Path

# Define feature names
def get_feature_names():
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
    return feature_names

def load_and_process_data():
    """Load and process the data from .npy files"""
    print("Loading data...")
    X = np.load('data/filtered_data/processed/X.npy')
    y = np.load('data/filtered_data/processed/y.npy')
    
    print("Processing features...")
    X_processed = extract_important_features(X)
    
    return X_processed, y

def create_feature_boxplots(data, labels, feature_names, output_dir):
    """Create box plots for each feature grouped by movement type"""
    print("Creating feature box plots...")
    unique_labels = np.unique(labels)
    
    # Create a DataFrame for easier plotting
    df = pd.DataFrame(data, columns=feature_names)
    df['Movement'] = labels
    
    # Create box plots for groups of related features
    feature_groups = {
        'Statistical': [f for f in feature_names if any(s in f for s in ['mean', 'std', 'min', 'max'])],
        'Correlation': [f for f in feature_names if 'corr' in f],
        'Zero Crossing & RMS': [f for f in feature_names if any(s in f for s in ['zero_cross', 'rms'])],
        'FFT': [f for f in feature_names if 'fft' in f],
        'Spectral': [f for f in feature_names if any(s in f for s in ['spectral_energy', 'dominant_freq'])],
        'Magnitude': [f for f in feature_names if any(s in f for s in ['sma', 'svm'])]
    }
    
    for group_name, group_features in feature_groups.items():
        n_features = len(group_features)
        if n_features == 0:
            continue
            
        fig, axes = plt.subplots(figsize=(15, max(8, n_features * 0.5)))
        plt.title(f'{group_name} Features Distribution by Movement Type')
        
        # Create box plot
        sns.boxplot(data=df.melt(id_vars=['Movement'], value_vars=group_features),
                   x='value', y='variable', hue='Movement', orient='h')
        
        plt.tight_layout()
        plt.savefig(output_dir / f'boxplot_{group_name.lower()}.png', dpi=300, bbox_inches='tight')
        plt.close()

def create_violin_plots(data, labels, feature_names, output_dir):
    """Create violin plots for each feature grouped by movement type"""
    print("Creating violin plots...")
    df = pd.DataFrame(data, columns=feature_names)
    df['Movement'] = labels
    
    # Select top 15 most variable features
    feature_vars = df[feature_names].var().sort_values(ascending=False)
    top_features = feature_vars.head(15).index
    
    plt.figure(figsize=(15, 10))
    sns.violinplot(data=df.melt(id_vars=['Movement'], value_vars=top_features),
                  x='value', y='variable', hue='Movement', orient='h')
    plt.title('Distribution of Top 15 Most Variable Features by Movement Type')
    plt.tight_layout()
    plt.savefig(output_dir / 'violin_plots_top15.png', dpi=300, bbox_inches='tight')
    plt.close()

def create_correlation_heatmap(data, feature_names, output_dir):
    """Create correlation heatmap for features"""
    print("Creating correlation heatmap...")
    corr_matrix = np.corrcoef(data.T)
    
    plt.figure(figsize=(20, 16))
    sns.heatmap(corr_matrix, xticklabels=feature_names, yticklabels=feature_names,
                cmap='RdBu_r', center=0, annot=False)
    plt.title('Feature Correlation Heatmap')
    plt.xticks(rotation=90)
    plt.yticks(rotation=0)
    plt.tight_layout()
    plt.savefig(output_dir / 'correlation_heatmap.png', dpi=300, bbox_inches='tight')
    plt.close()

def create_feature_distributions(data, labels, feature_names, output_dir):
    """Create distribution plots for top features"""
    print("Creating feature distribution plots...")
    df = pd.DataFrame(data, columns=feature_names)
    df['Movement'] = labels
    
    # Select top 9 most variable features
    feature_vars = df[feature_names].var().sort_values(ascending=False)
    top_features = feature_vars.head(9).index
    
    fig, axes = plt.subplots(3, 3, figsize=(15, 15))
    axes = axes.ravel()
    
    for idx, feature in enumerate(top_features):
        sns.kdeplot(data=df, x=feature, hue='Movement', ax=axes[idx])
        axes[idx].set_title(feature)
    
    plt.tight_layout()
    plt.savefig(output_dir / 'feature_distributions.png', dpi=300, bbox_inches='tight')
    plt.close()

def create_movement_summary(data, labels, feature_names, output_dir):
    """Create summary statistics for each movement type"""
    print("Creating movement summary statistics...")
    df = pd.DataFrame(data, columns=feature_names)
    df['Movement'] = labels
    
    summary_stats = df.groupby('Movement').agg(['mean', 'std', 'min', 'max'])
    summary_stats.to_csv(output_dir / 'movement_summary_stats.csv')

def create_walking_vs_stairs_comparison(data, labels, feature_names, output_dir):
    """Create box plots comparing walking vs stairs activities"""
    print("Creating walking vs stairs comparison plots...")
    
    # Create DataFrame
    df = pd.DataFrame(data, columns=feature_names)
    df['Movement'] = labels
    
    # Print unique movement types to debug
    unique_movements = np.unique(labels)
    print("Available movement types:", unique_movements)
    
    # Filter for W (walking) and S (stairs) activities
    df_filtered = df[df['Movement'].isin(['W', 'S'])].copy()
    
    # Create a more readable mapping for the labels
    df_filtered['Movement'] = df_filtered['Movement'].map({'W': 'Walking', 'S': 'Stairs'})
    
    print(f"Number of samples after filtering: {len(df_filtered)}")
    print("Movement types in filtered data:", df_filtered['Movement'].unique())
    
    if len(df_filtered) == 0:
        print("No walking or stairs data found!")
        return
    
    # Select mean features for x, y, z axes
    mean_features = ['mean_x', 'mean_y', 'mean_z']
    
    # Verify these features exist
    available_features = [f for f in mean_features if f in feature_names]
    if not available_features:
        print("Error: No mean features found in the data!")
        print("Available features:", feature_names)
        return
    
    # Create box plot
    plt.figure(figsize=(12, 6))
    
    # Melt the dataframe for plotting
    melted_df = df_filtered.melt(
        id_vars=['Movement'],
        value_vars=available_features,
        var_name='Axis',
        value_name='Acceleration'
    )
    
    # Create the box plot with enhanced styling
    ax = sns.boxplot(
        data=melted_df,
        x='Acceleration',
        y='Axis',
        hue='Movement',
        orient='h',
        palette='Set3',
        width=0.7
    )
    
    # Enhance the plot
    plt.title('Walking vs Stairs: Mean Acceleration by Axis', fontsize=14, pad=20)
    plt.xlabel('Acceleration (m/s²)', fontsize=12)
    plt.ylabel('Axis', fontsize=12)
    
    # Add grid for better readability
    ax.grid(True, linestyle='--', alpha=0.7)
    
    # Adjust legend
    plt.legend(title='Movement Type', bbox_to_anchor=(1.05, 1), loc='upper left')
    
    plt.tight_layout()
    plt.savefig(output_dir / 'walking_vs_stairs_comparison.png', dpi=300, bbox_inches='tight')
    plt.close()
    
    # Create detailed summary statistics
    summary = df_filtered.groupby('Movement')[available_features].agg(['mean', 'std', 'min', 'max']).round(3)
    summary.to_csv(output_dir / 'walking_vs_stairs_stats.csv')
    
    # Print summary statistics
    print("\nSummary statistics for filtered data:")
    print(f"Total samples: {len(df_filtered)}")
    print("\nSamples per movement type:")
    print(df_filtered['Movement'].value_counts())
    print("\nMean values per movement type and axis:")
    print(df_filtered.groupby('Movement')[available_features].mean().round(3))

def main():
    # Create output directory
    output_dir = Path('visualizations')
    output_dir.mkdir(exist_ok=True)
    
    # Load and process data
    X_processed, y = load_and_process_data()
    
    # Get feature names
    feature_names = get_feature_names()
    
    # Create visualizations
    create_feature_boxplots(X_processed, y, feature_names, output_dir)
    create_violin_plots(X_processed, y, feature_names, output_dir)
    create_correlation_heatmap(X_processed, feature_names, output_dir)
    create_feature_distributions(X_processed, y, feature_names, output_dir)
    create_movement_summary(X_processed, y, feature_names, output_dir)
    create_walking_vs_stairs_comparison(X_processed, y, feature_names, output_dir)
    
    print("All visualizations have been created in the 'visualizations' directory")

if __name__ == "__main__":
    main() 