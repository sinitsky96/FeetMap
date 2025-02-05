# FeetMap: Smart Foot Pressure Analysis and Step Detection System

## Project Overview
FeetMap is an integrated IoT system that combines foot pressure analysis during running with intelligent step detection and terrain classification. The project consists of three main components:

### 1. Android Application
A mobile application that monitors and analyzes foot pressure distribution across three key points during running sessions. Features include:
- Real-time pressure monitoring
- Performance scoring based on pressure distribution
- User-friendly visualization of pressure data
- Bluetooth connectivity with Arduino sensors

### 2. Machine Learning Component (RandomForest)
A sophisticated step detection and terrain classification system that utilizes accelerometer data to:
- Detect and count steps accurately
- Classify walking surface (flat surface vs. stairs)
- Process accelerometer signals for precise movement analysis
- Implement Random Forest algorithm for terrain classification

### 3. Arduino Hardware
Custom Arduino-based hardware setup for:
- Pressure sensor data collection
- Accelerometer data gathering
- Bluetooth communication with the Android app

## Repository Structure
```
FeetMap/
├── App/                    # Android application source code
├── RandomForest/          # ML component for step detection
│   ├── step_counter.py    # Step detection implementation
│   ├── model.py           # ML model implementation
│   ├── preproc.py        # Data preprocessing
│   └── viz.py            # Visualization utilities
└── Arduino/              # Arduino firmware
    └── proj.ino         # Sensor data collection and transmission
```
