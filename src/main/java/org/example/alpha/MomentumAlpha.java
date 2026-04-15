package org.example.alpha;

import org.ojalgo.matrix.MatrixR064;

/**
 * Cross-sectional + time-series momentum alpha.
 *
 * <p>Signal = cumulative return over the look-back window, minus the
 * cross-sectional mean (dollar-neutral ranking).
 *
 * <p>The look-back window is capped at {@code min(lookback, rows)}.
 */
public class MomentumAlpha implements AlphaModel {

    private final int    lookback;       // days to measure momentum
    private final double shortPenalty;

    public MomentumAlpha(int lookback, double shortPenalty) {
        this.lookback     = lookback;
        this.shortPenalty = shortPenalty;
    }

    public MomentumAlpha(int lookback) { this(lookback, 0.02); }
    public MomentumAlpha()             { this(20, 0.02); }

    // ── AlphaModel ────────────────────────────────────────────────────────────

    @Override
    public MatrixR064 predict(MatrixR064 returns) {
        int rows = (int) returns.countRows();
        int cols = (int) returns.countColumns();
        int lb   = Math.min(lookback, rows);

        double[] cumRet = new double[cols];
        double crossMean = 0;

        // Cumulative simple return over [rows-lb, rows)
        for (int j = 0; j < cols; j++) {
            double prod = 1.0;
            for (int i = rows - lb; i < rows; i++) {
                prod *= (1.0 + returns.get(i, j));
            }
            cumRet[j] = prod - 1.0;
            crossMean += cumRet[j];
        }
        crossMean /= cols;

        // Demean → cross-sectional signal
        double[][] mu = new double[1][cols];
        for (int j = 0; j < cols; j++) {
            double v = cumRet[j] - crossMean;
            mu[0][j] = v < 0 ? v - shortPenalty : v;
        }
        return MatrixR064.FACTORY.rows(mu);
    }

    @Override
    public String name() {
        return String.format("Momentum(lb=%d)", lookback);
    }
}
