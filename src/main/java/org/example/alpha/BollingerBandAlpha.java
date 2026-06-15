package org.example.alpha;

import org.ojalgo.matrix.MatrixR064;

/**
 * Bollinger Band Squeeze/Breakout alpha.
 *
 * <p>Bandwidth = (upper − lower) / middle.  Squeeze detected when bandwidth
 * falls below rolling median.  During squeeze a breakout signal is emitted
 * based on recent momentum; otherwise a mean-reversion signal is used.
 */
public class BollingerBandAlpha implements AlphaModel {

    private final int    period;
    private final double numStd;

    public BollingerBandAlpha(int period, double numStd) {
        this.period = period;
        this.numStd = numStd;
    }

    public BollingerBandAlpha() {
        this(20, 2.0);
    }

    @Override
    public MatrixR064 predict(MatrixR064 returns) {
        int rows = (int) returns.countRows();
        int cols = (int) returns.countColumns();
        double[][] mu = new double[1][cols];

        for (int j = 0; j < cols; j++) {
            double[] bw = new double[rows];
            double[] middle = new double[rows];

            for (int t = period - 1; t < rows; t++) {
                double sum = 0, sumSq = 0;
                for (int i = t - period + 1; i <= t; i++) {
                    double v = returns.get(i, j);
                    sum   += v;
                    sumSq += v * v;
                }
                middle[t] = sum / period;
                double variance = sumSq / period - middle[t] * middle[t];
                variance = Math.max(variance, 1e-10);
                double std = Math.sqrt(variance);
                double upper = middle[t] + numStd * std;
                double lower = middle[t] - numStd * std;
                bw[t] = middle[t] != 0 ? (upper - lower) / Math.abs(middle[t]) : 0;
            }

            double[] bwSorted = new double[rows];
            System.arraycopy(bw, 0, bwSorted, 0, rows);
            java.util.Arrays.sort(bwSorted);
            double medianBw = bwSorted[rows / 2];

            double lastBw = bw[rows - 1];
            boolean squeeze = lastBw < medianBw;

            double lastPrice = returns.get(rows - 1, j);
            double prevPrice = rows > 1 ? returns.get(rows - 2, j) : lastPrice;
            double momentum  = lastPrice - prevPrice;

            double signal;
            if (squeeze) {
                signal = momentum > 0 ? 1.0 : momentum < 0 ? -1.0 : 0.0;
                double momentumScale = 0.5;
                signal *= momentumScale;
            } else {
                double sum = 0, sumSq = 0;
                for (int t = rows - period; t < rows; t++) {
                    double v = returns.get(t, j);
                    sum   += v;
                    sumSq += v * v;
                }
                double mean = sum / period;
                double variance = Math.max(sumSq / period - mean * mean, 1e-10);
                double std = Math.sqrt(variance);

                double upper = mean + numStd * std;
                double lower = mean - numStd * std;
                double bandWidth = upper - lower;

                if (bandWidth > 1e-10) {
                    double pctB = (lastPrice - lower) / bandWidth;
                    signal = -(pctB - 0.5) * 2.0;
                } else {
                    signal = 0;
                }
            }

            mu[0][j] = Math.max(-1.0, Math.min(1.0, signal));
        }
        return MatrixR064.FACTORY.rows(mu);
    }

    @Override
    public String name() {
        return String.format("Bollinger(%d,%.1f)", period, numStd);
    }
}
