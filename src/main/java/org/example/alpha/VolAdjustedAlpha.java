package org.example.alpha;

import org.ojalgo.matrix.MatrixR064;

/**
 * Volatility-adjusted alpha decorator.
 *
 * <p>Wraps any {@link AlphaModel} and divides each asset's signal by its
 * rolling standard deviation, producing a risk-adjusted signal:
 *
 * <pre>
 *   μ_adjusted[j] = μ[j] / σ[j]
 * </pre>
 *
 * <p>This normalizes signals across assets with different volatilities,
 * preventing the optimizer from over-weighting low-vol assets simply
 * because their raw signal happens to be larger.
 */
public class VolAdjustedAlpha implements AlphaModel {

    private final AlphaModel inner;
    private final int        volWindow;

    /**
     * @param inner     the base alpha model to wrap
     * @param volWindow rolling window for volatility estimation (days)
     */
    public VolAdjustedAlpha(AlphaModel inner, int volWindow) {
        this.inner     = inner;
        this.volWindow = volWindow;
    }

    public VolAdjustedAlpha(AlphaModel inner) {
        this(inner, 20);
    }

    @Override
    public MatrixR064 predict(MatrixR064 returns) {
        int rows = (int) returns.countRows();
        int cols = (int) returns.countColumns();
        int win  = Math.min(volWindow, rows);

        // Get raw alpha signal
        MatrixR064 rawSignal = inner.predict(returns);

        // Compute rolling std for each asset
        double[] vols = new double[cols];
        for (int j = 0; j < cols; j++) {
            double sum = 0, sumSq = 0;
            for (int i = rows - win; i < rows; i++) {
                double v = returns.get(i, j);
                sum   += v;
                sumSq += v * v;
            }
            double mean = sum / win;
            double var  = sumSq / win - mean * mean;
            vols[j] = var > 0 ? Math.sqrt(var) : 1e-8;
        }

        // Divide signal by vol
        double[][] adjusted = new double[1][cols];
        for (int j = 0; j < cols; j++) {
            adjusted[0][j] = rawSignal.get(0, j) / vols[j];
        }
        return MatrixR064.FACTORY.rows(adjusted);
    }

    @Override
    public String name() {
        return "VolAdj(" + inner.name() + ")";
    }
}
