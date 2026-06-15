package org.example.alpha;

import org.example.Defaults;
import org.ojalgo.matrix.MatrixR064;

/**
 * Short-term mean-reversion (contrarian) alpha.
 *
 * <p>Assets that are above their recent average get a negative signal
 * (expect reversion down) and vice versa.
 *
 * <p>Signal = -(recent z-score) scaled to [-1, +1] range.
 */
public class MeanReversionAlpha implements AlphaModel {

    private final int    window;
    private final double shortPenalty;

    public MeanReversionAlpha(int window, double shortPenalty) {
        this.window       = window;
        this.shortPenalty = shortPenalty;
    }

    public MeanReversionAlpha(int window) { this(window, Defaults.SHORT_PENALTY); }
    public MeanReversionAlpha()           { this(Defaults.MEAN_REVERSION_WINDOW, Defaults.SHORT_PENALTY); }

    @Override
    public MatrixR064 predict(MatrixR064 returns) {
        int rows = (int) returns.countRows();
        int cols = (int) returns.countColumns();
        int win  = Math.min(window, rows);

        double[][] mu = new double[1][cols];

        for (int j = 0; j < cols; j++) {
            double sum = 0, sumSq = 0;
            for (int i = rows - win; i < rows; i++) {
                double v = returns.get(i, j);
                sum   += v;
                sumSq += v * v;
            }
            double mean = sum / win;
            double var  = sumSq / win - mean * mean;
            double std  = var > 0 ? Math.sqrt(var) : 1e-8;
            double last = returns.get(rows - 1, j);
            double zScore = (last - mean) / std;

            // Contrarian: high recent return → negative signal
            double signal = -zScore / Defaults.MEAN_REVERSION_SCALE;   // scale ≈ [-1, 1]
            mu[0][j] = signal < 0 ? signal - shortPenalty : signal;
        }
        return MatrixR064.FACTORY.rows(mu);
    }

    @Override
    public String name() {
        return String.format("MeanReversion(w=%d)", window);
    }
}
