package com.example.feetmap;


import androidx.lifecycle.ViewModel;
import java.util.List;

public class SharedViewModel extends ViewModel {
    private List<RunningDataPoint> runData;
    private double balanceScore;

    public List<RunningDataPoint> getRunData() { return runData; }
    public void setRunData(List<RunningDataPoint> runData) { this.runData = runData; }

    public double getBalanceScore() { return balanceScore; }
    public void setBalanceScore(double balanceScore) { this.balanceScore = balanceScore; }
}
