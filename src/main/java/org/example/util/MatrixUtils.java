package org.example.util;

import org.ojalgo.matrix.MatrixR064;

/**
 * Static helpers shared across portfolio construction models.
 */
public final class MatrixUtils {

    private MatrixUtils() {}

    /**
     * Ledoit-Wolf-style diagonal shrinkage.
     *
     * <pre> Σ_shrunk = λ·Σ + (1-λ)·I </pre>
     */
    public static MatrixR064 shrink(MatrixR064 cov, double lambda) {
        int n = (int) cov.countRows();
        MatrixR064 eye = MatrixR064.FACTORY.makeEye(n, n);
        return cov.multiply(lambda).add(eye.multiply(1.0 - lambda));
    }

    /**
     * Subtract column means from a matrix (centres each asset's returns).
     */
    public static MatrixR064 centerColumns(MatrixR064 m) {
        int rows = (int) m.countRows();
        int cols = (int) m.countColumns();
        double[][] out = new double[rows][cols];
        for (int j = 0; j < cols; j++) {
            double mean = 0;
            for (int i = 0; i < rows; i++) mean += m.get(i, j);
            mean /= rows;
            for (int i = 0; i < rows; i++) out[i][j] = m.get(i, j) - mean;
        }
        return MatrixR064.FACTORY.rows(out);
    }

    /**
     * Build shrunk sample covariance from a returns matrix and a [1×n] mean vector.
     */
    public static MatrixR064 covarianceMatrix(MatrixR064 returns,
                                              MatrixR064 mu,
                                              double shrinkageLambda) {
        MatrixR064 centered = returns.subtract(mu);
        MatrixR064 cov = centered.transpose()
                .multiply(centered)
                .divide(returns.countRows() - 1);
        return shrink(cov, shrinkageLambda);
    }

    /**
     * Validate a returns matrix — throws if it contains NaN or Inf.
     */
    public static void assertClean(MatrixR064 m) {
        for (int i = 0; i < m.countRows(); i++) {
            for (int j = 0; j < m.countColumns(); j++) {
                double v = m.get(i, j);
                if (Double.isNaN(v) || Double.isInfinite(v)) {
                    throw new IllegalStateException(
                            "NaN/Inf detected at [" + i + "," + j + "]");
                }
            }
        }
    }
}
