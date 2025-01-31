package com.example.feetmap;

public class RunningDataPoint {
    public final long timestamp;
    public final float accX, accY, accZ;
    public final float fsr1, fsr2, fsr3;

    public RunningDataPoint(long timestamp, float accX, float accY, float accZ,
                            float fsr1, float fsr2, float fsr3) {
        this.timestamp = timestamp;
        this.accX = accX;
        this.accY = accY;
        this.accZ = accZ;
        this.fsr1 = fsr1;
        this.fsr2 = fsr2;
        this.fsr3 = fsr3;
    }

    public double getForwardAcceleration() {
        // Assuming Z-axis represents forward motion
        return accZ;
    }

    public boolean hasHighHeelPressure() {
        // Threshold for high heel pressure (FSR1 is heel sensor)
        return fsr1 > 800; // Adjust threshold as needed
    }

    public double getEnergyWaste() {
        // Calculate energy waste when high heel pressure is detected
        if (hasHighHeelPressure()) {
            // Energy waste is proportional to the impact force (heel pressure)
            // and loss of forward acceleration
            return fsr1 * Math.abs(getForwardAcceleration());
        }
        return 0;
    }
}