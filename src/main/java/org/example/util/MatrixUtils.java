package org.example.util;

import org.ojalgo.matrix.MatrixR064;

/**
 * Static helpers shared across portfolio construction models.
 */
public final class MatrixUtils {

    private MatrixUtils() {}

    // ── Covariance ────────────────────────────────────────────────────────────

    /**
     * Ledoit-Wolf-style diagonal shrinkage: Sigma_shrunk = lambda*Sigma + (1-lambda)*I
     */
    public static MatrixR064 shrink(MatrixR064 cov, double lambda) {
        int n = (int) cov.countRows();
        MatrixR064 eye = MatrixR064.FACTORY.makeEye(n, n);
        return cov.multiply(lambda).add(eye.multiply(1.0 - lambda));
    }

    /**
     * Subtract each column's mean from every element in that column
     * (centres each asset's return series around zero).
     */
    public static MatrixR064 centerColumns(MatrixR064 m) {
        int rows = (int) m.countRows();
        int cols = (int) m.countColumns();
        double[][] out = new double[rows][cols];
        for (int j = 0; j < cols; j++) {
            double mean = 0.0;
            for (int i = 0; i < rows; i++) mean += m.get(i, j);
            mean /= rows;
            for (int i = 0; i < rows; i++) out[i][j] = m.get(i, j) - mean;
        }
        return MatrixR064.FACTORY.rows(out);
    }

    /**
     * Build a shrunk sample covariance matrix from a returns matrix.
     *
     * <p>Centering uses the empirical column means of {@code returns} via
     * {@link #centerColumns}.  The {@code mu} parameter (alpha signal) is
     * intentionally ignored for centering because:
     * <ul>
     *   <li>it is a [1 x n] row-vector; ojAlgo cannot broadcast-subtract it
     *       from a [T x n] matrix (throws at runtime in ojAlgo 50+), and</li>
     *   <li>the sample covariance must be centred on the empirical mean, not
     *       on the forward-looking alpha signal.</li>
     * </ul>
     */
    public static MatrixR064 covarianceMatrix(MatrixR064 returns,
                                              MatrixR064 mu,
                                              double shrinkageLambda) {
        MatrixR064 centered = centerColumns(returns);
        MatrixR064 cov = centered.transpose()
                .multiply(centered)
                .divide(returns.countRows() - 1);
        return shrink(cov, shrinkageLambda);
    }

    // ── Slicing ───────────────────────────────────────────────────────────────

    /**
     * Extract rows [fromRow, toRowExclusive) with standard half-open semantics.
     *
     * <p><b>Never</b> use {@code matrix.rows(int a, int b)} for range slicing.
     * In ojAlgo that signature is {@code rows(int... rows)} (varargs), which
     * selects the two specific row indices {@code a} and {@code b} — it is
     * NOT a range operation.  On a matrix with N rows, calling
     * {@code matrix.rows(0, N)} tries to read row N (out of bounds) and
     * throws {@code ArrayIndexOutOfBoundsException}.
     */
    public static MatrixR064 sliceRows(MatrixR064 m, int fromRow, int toRowExclusive) {
        int rows = toRowExclusive - fromRow;
        int cols = (int) m.countColumns();
        double[][] data = new double[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                data[r][c] = m.get(fromRow + r, c);
            }
        }
        return MatrixR064.FACTORY.rows(data);
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Throws {@link IllegalStateException} if any element is NaN or infinite.
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
