package org.example.util;

import org.ojalgo.matrix.MatrixR064;

/**
 * Analyzes market data and suggests optimal default parameters.
 *
 * <p>Uses data characteristics (volatility, correlation, return distribution)
 * to recommend sensible starting values for all optimization parameters.
 */
public class SmartDefaults {

    private final int    assets;
    private final int    periods;
    private final double avgVol;
    private final double avgCorr;
    private final double avgReturn;
    private final double sharpe;

    public SmartDefaults(MatrixR064 returns) {
        this.assets   = (int) returns.countColumns();
        this.periods  = (int) returns.countRows();
        this.avgVol   = computeAvgVol(returns);
        this.avgCorr  = computeAvgCorr(returns);
        this.avgReturn = computeAvgReturn(returns);
        this.sharpe   = avgVol > 1e-10 ? avgReturn / avgVol : 0;
    }

    /** Suggested training window (longer for more assets). */
    public int suggestWindow() {
        return Math.max(30, Math.min(120, assets * 15));
    }

    /** Suggested prediction horizon (shorter for high vol). */
    public int suggestHorizon() {
        if (avgVol > 0.04) return 3;
        if (avgVol > 0.02) return 5;
        return 7;
    }

    /** Suggested target vol (proportional to market vol). */
    public double suggestTargetVol() {
        return Math.max(0.005, Math.min(0.05, avgVol * 1.5));
    }

    /** Suggested max leverage (lower for high vol). */
    public double suggestLeverage() {
        if (avgVol > 0.04) return 1.1;
        if (avgVol > 0.03) return 1.3;
        return 1.5;
    }

    /** Suggested shrinkage (higher for few assets). */
    public double suggestShrinkage() {
        if (assets < 5) return 0.95;
        if (assets < 10) return 0.90;
        return 0.80;
    }

    /** Suggested EWMA alpha (lower for noisier data). */
    public double suggestEwmaAlpha() {
        if (sharpe < 0.05) return 0.05;
        if (sharpe < 0.15) return 0.10;
        return 0.15;
    }

    /** Suggested momentum lookback. */
    public int suggestMomentumLookback() {
        if (avgVol > 0.04) return 10;
        if (avgVol > 0.02) return 20;
        return 30;
    }

    /** Suggested max long per asset. */
    public double suggestMaxLong() {
        return Math.max(0.10, Math.min(0.50, 1.0 / assets * 3));
    }

    /** Suggested max short per asset. */
    public double suggestMaxShort() {
        return suggestMaxLong() * 0.75;
    }

    /** Suggested max VaR. */
    public double suggestMaxVaR() {
        return Math.max(0.01, avgVol * 2.5);
    }

    public double avgVol()   { return avgVol; }
    public double avgCorr()  { return avgCorr; }
    public int    assetCount() { return assets; }
    public int    periodCount() { return periods; }

    // ── Private ───────────────────────────────────────────────────────

    private static double computeAvgVol(MatrixR064 r) {
        int rows = (int) r.countRows();
        int cols = (int) r.countColumns();
        double totalVol = 0;
        for (int j = 0; j < cols; j++) {
            double sum = 0, sum2 = 0;
            for (int d = 0; d < rows; d++) { double v = r.get(d, j); sum += v; sum2 += v * v; }
            double mean = sum / rows;
            double var  = rows > 1 ? (sum2 / rows - mean * mean) * rows / (rows - 1) : 0;
            totalVol += Math.sqrt(var);
        }
        return totalVol / cols;
    }

    private static double computeAvgCorr(MatrixR064 r) {
        int rows = (int) r.countRows();
        int cols = (int) r.countColumns();
        if (cols < 2) return 0;
        double[] means = new double[cols];
        double[] vars  = new double[cols];
        for (int j = 0; j < cols; j++) {
            double sum = 0, sum2 = 0;
            for (int d = 0; d < rows; d++) { double v = r.get(d, j); sum += v; sum2 += v * v; }
            means[j] = sum / rows;
            vars[j]  = rows > 1 ? (sum2 / rows - means[j] * means[j]) * rows / (rows - 1) : 0;
        }
        double totalCorr = 0;
        int pairs = 0;
        for (int i = 0; i < cols; i++) {
            for (int j = i + 1; j < cols; j++) {
                double si = Math.sqrt(vars[i]), sj = Math.sqrt(vars[j]);
                if (si < 1e-12 || sj < 1e-12) continue;
                double cov = 0;
                for (int d = 0; d < rows; d++) cov += (r.get(d, i) - means[i]) * (r.get(d, j) - means[j]);
                cov /= (rows - 1);
                totalCorr += cov / (si * sj);
                pairs++;
            }
        }
        return pairs > 0 ? totalCorr / pairs : 0;
    }

    private static double computeAvgReturn(MatrixR064 r) {
        int rows = (int) r.countRows();
        int cols = (int) r.countColumns();
        double total = 0;
        for (int j = 0; j < cols; j++) {
            for (int d = 0; d < rows; d++) total += r.get(d, j);
        }
        return total / (rows * cols);
    }
}
