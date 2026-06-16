package org.example.alpha;

import org.ojalgo.matrix.MatrixR064;

/**
 * Volume-Weighted alpha.
 *
 * <p>Uses a volume proxy derived from absolute return magnitudes to compute
 * OBV-like signals and volume-price divergence.  Heavy-volume moves receive
 * higher weight; divergences between price direction and volume trend are
 * flagged as reversal signals.
 */
public class VolumeWeightedAlpha implements AlphaModel {

    private final int    window;
    private final double shortPenalty;

    public VolumeWeightedAlpha(int window, double shortPenalty) {
        this.window       = window;
        this.shortPenalty = shortPenalty;
    }

    public VolumeWeightedAlpha(int window) {
        this(window, 0.02);
    }

    public VolumeWeightedAlpha() {
        this(20, 0.02);
    }

    @Override
    public MatrixR064 predict(MatrixR064 returns) {
        int rows = (int) returns.countRows();
        int cols = (int) returns.countColumns();
        int win  = Math.min(window, rows);
        double[][] mu = new double[1][cols];

        for (int j = 0; j < cols; j++) {
            double obv = 0;
            double cumVolUp   = 0;
            double cumVolDown = 0;

            double[] recentMom  = new double[win];
            double[] recentVol  = new double[win];
            int count = 0;

            for (int t = rows - win; t < rows; t++) {
                double r = returns.get(t, j);
                double vol = Math.abs(r);

                if (t > 0) {
                    if (r > 0) {
                        obv += vol;
                        cumVolUp += vol;
                    } else if (r < 0) {
                        obv -= vol;
                        cumVolDown += vol;
                    }
                }

                recentMom[count] = r;
                recentVol[count] = vol;
                count++;
            }

            double totalVol = cumVolUp + cumVolDown;
            double obvTrend = totalVol > 1e-10
                    ? obv / (totalVol * 0.5)
                    : 0;

            double sumMom = 0, sumVol = 0, sumMomVol = 0, sumVolSq = 0;
            for (int i = 0; i < count; i++) {
                sumMom    += recentMom[i];
                sumVol    += recentVol[i];
                sumMomVol += recentMom[i] * recentVol[i];
                sumVolSq  += recentVol[i] * recentVol[i];
            }
            double meanMom = sumMom / count;
            double meanVol = sumVol / count;
            double covMomVol = sumMomVol / count - meanMom * meanVol;
            double varVol = Math.max(sumVolSq / count - meanVol * meanVol, 1e-10);
            double volPriceCorr = covMomVol / (Math.sqrt(varVol) * Math.max(Math.abs(meanMom), 1e-10));

            double divergence = 0;
            if (count > 2) {
                double recentMomSum = 0, oldMomSum = 0;
                int half = count / 2;
                for (int i = count - half; i < count; i++) recentMomSum += recentMom[i];
                for (int i = 0; i < half; i++) oldMomSum += recentMom[i];
                double recentAvgMom = recentMomSum / half;
                double oldAvgMom = oldMomSum / half;

                double recentVolSum = 0, oldVolSum = 0;
                for (int i = count - half; i < count; i++) recentVolSum += recentVol[i];
                for (int i = 0; i < half; i++) oldVolSum += recentVol[i];
                double recentAvgVol = recentVolSum / half;
                double oldAvgVol = oldVolSum / half;

                if (recentAvgMom > 0 && recentAvgVol < oldAvgVol * 0.8) {
                    divergence = -0.5;
                } else if (recentAvgMom < 0 && recentAvgVol < oldAvgVol * 0.8) {
                    divergence = 0.5;
                }
            }

            double rawSignal = obvTrend * 0.5 + volPriceCorr * 0.3 + divergence * 0.2;

            double maxAbs = 0;
            for (int i = 0; i < count; i++) {
                maxAbs = Math.max(maxAbs, Math.abs(recentMom[i]));
            }
            double scaledSignal = maxAbs > 1e-10 ? rawSignal * (maxAbs * 10) : rawSignal;

            double signal = Math.max(-1.0, Math.min(1.0, scaledSignal));
            if (signal < 0) signal -= shortPenalty;
            mu[0][j] = Math.max(-1.0, Math.min(1.0, signal));
        }
        return MatrixR064.FACTORY.rows(mu);
    }

    @Override
    public String name() {
        return String.format("VolumeWeighted(w=%d)", window);
    }
}
