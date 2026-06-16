package org.example.alpha;

import org.example.Defaults;
import org.ojalgo.matrix.MatrixR064;

/**
 * EWMA expected-return estimator.
 *
 * <p>Formula:  μ_t = α · r_t + (1 - α) · μ_{t-1}
 *
 * <p>Negative predictions receive an extra {@code shortPenalty} to discourage
 * unnecessary short positions.
 */
public class EWMAAlpha implements AlphaModel {

    private final double alpha;
    private final double shortPenalty;

    /**
     * @param alpha        smoothing factor ∈ (0, 1]; higher = more weight on recent returns
     * @param shortPenalty additive penalty subtracted from negative predictions (≥ 0)
     */
    public EWMAAlpha(double alpha, double shortPenalty) {
        if (alpha <= 0 || alpha > 1) throw new IllegalArgumentException("alpha must be in (0,1]");
        this.alpha        = alpha;
        this.shortPenalty = shortPenalty;
    }

    public EWMAAlpha(double alpha) { this(alpha, Defaults.SHORT_PENALTY); }

    // ── AlphaModel ────────────────────────────────────────────────────────────

    @Override
    public MatrixR064 predict(MatrixR064 returns) {
        int rows = (int) returns.countRows();
        int cols = (int) returns.countColumns();
        double[][] mu = new double[1][cols];

        for (int j = 0; j < cols; j++) {
            double ewma = 0;
            for (int i = 0; i < rows; i++) {
                ewma = alpha * returns.get(i, j) + (1 - alpha) * ewma;
            }
            mu[0][j] = ewma < 0 ? ewma - shortPenalty : ewma;
        }
        return MatrixR064.FACTORY.rows(mu);
    }

    @Override
    public String name() {
        return String.format("EWMA(α=%.2f)", alpha);
    }
}
