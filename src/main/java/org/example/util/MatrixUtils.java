package org.example.util;

import org.ojalgo.matrix.MatrixR064;

/**
 * Static helpers shared across portfolio construction models.
 */
public final class MatrixUtils {

    private MatrixUtils() {}

    // ── Covariance ────────────────────────────────────────────────────────────

    /**
     * Ledoit-Wolf-style diagonal shrinkage toward a constant-variance target:
     * Sigma_shrunk = lambda*Sigma + (1-lambda)*target*I
     * where target = tr(Sigma)/n (the average variance across all assets).
     * This preserves the empirical variance scale instead of inflating variances
     * toward 1.0 as the naive identity-target would.
     */
    public static MatrixR064 shrink(MatrixR064 cov, double lambda) {
        int n = (int) cov.countRows();
        double trace = 0;
        for (int i = 0; i < n; i++) trace += cov.get(i, i);
        double muTarget = trace / n;
        MatrixR064 target = MatrixR064.FACTORY.makeEye(n, n).multiply(muTarget);
        return cov.multiply(lambda).add(target.multiply(1.0 - lambda));
    }

    /**
     * Ledoit-Wolf optimal shrinkage intensity.
     *
     * <p>Analytically computes the shrinkage parameter λ* that minimizes
     * E[||Σ_shrunk - Σ_true||²] using the formula from Ledoit & Wolf (2004).
     *
     * <p>The target is (tr(S)/n) * I where S is the sample covariance.
     *
     * @param returns [T x n] centered return matrix
     * @return optimal λ in [0, 1]
     */
    public static double ledoitWolfLambda(MatrixR064 returns) {
        int T = (int) returns.countRows();
        int n = (int) returns.countColumns();

        // Sample covariance S = X'X / T
        MatrixR064 centered = centerColumns(returns);
        MatrixR064 S = centered.transpose().multiply(centered).divide(T);

        // Target F = (tr(S)/n) * I
        double traceS = 0;
        for (int i = 0; i < n; i++) traceS += S.get(i, i);
        double muTarget = traceS / n;

        // delta = ||S - F||²
        double delta = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double diff = S.get(i, j) - (i == j ? muTarget : 0.0);
                delta += diff * diff;
            }
        }
        if (delta < 1e-15) return 0.0;

        // beta = (1/T) * Σ_t ||x_t * x_t' - S||²
        double beta = 0;
        for (int t = 0; t < T; t++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    double outer = centered.get(t, i) * centered.get(t, j);
                    double diff = outer - S.get(i, j);
                    beta += diff * diff;
                }
            }
        }
        beta /= T;

        // Optimal lambda = max(0, min(1, beta/delta))
        double lambda = Math.max(0, Math.min(1, beta / delta));
        return lambda;
    }

    /**
     * Build an optimally shrunk sample covariance matrix using Ledoit-Wolf (2004).
     *
     * <p>Computes the optimal shrinkage intensity analytically rather than
     * requiring the user to specify a fixed lambda.
     *
     * @param returns [T x n] return matrix
     * @return [n x n] optimally shrunk covariance matrix
     */
    public static MatrixR064 ledoitWolfCovariance(MatrixR064 returns) {
        double lambda = ledoitWolfLambda(returns);
        return covarianceMatrix(returns, null, lambda);
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

    /**
     * Exponentially weighted moving average (EWMA) covariance matrix.
     *
     * <p> Gives more weight to recent observations, adapting faster to
     * regime changes than the simple sample covariance.
     *
     * <p>Formula: Σ_t = (1-λ) * r_t * r_t' + λ * Σ_{t-1}
     * where λ is the decay factor (e.g. 0.94).
     *
     * @param returns [T x n] return matrix
     * @param lambda  decay factor in (0, 1); higher = slower adaptation
     * @return [n x n] EWMA covariance matrix
     */
    public static MatrixR064 ewmaCovariance(MatrixR064 returns, double lambda) {
        int rows = (int) returns.countRows();
        int cols = (int) returns.countColumns();

        // Warmup: use sample covariance from first warmup periods to avoid
        // biasing the EWMA with a single uncentered observation.
        int warmup = Math.min(rows, Math.max(2, Math.min(rows / 2, cols * 2)));
        double[][] cov = new double[cols][cols];
        if (warmup > 1) {
            double[] means = new double[cols];
            for (int t = 0; t < warmup; t++) {
                for (int i = 0; i < cols; i++) means[i] += returns.get(t, i);
            }
            for (int i = 0; i < cols; i++) means[i] /= warmup;
            for (int t = 0; t < warmup; t++) {
                for (int i = 0; i < cols; i++) {
                    double ri = returns.get(t, i) - means[i];
                    for (int j = i; j < cols; j++) {
                        double rj = returns.get(t, j) - means[j];
                        cov[i][j] += ri * rj;
                    }
                }
            }
            int denom = warmup - 1;
            for (int i = 0; i < cols; i++) {
                for (int j = i; j < cols; j++) {
                    cov[i][j] /= denom;
                    cov[j][i] = cov[i][j];
                }
            }
        }

        // EWMA recursion over remaining periods
        for (int t = warmup; t < rows; t++) {
            for (int i = 0; i < cols; i++) {
                double ri = returns.get(t, i);
                for (int j = i; j < cols; j++) {
                    double rj = returns.get(t, j);
                    double outer = ri * rj;
                    cov[i][j] = (1.0 - lambda) * outer + lambda * cov[i][j];
                    cov[j][i] = cov[i][j];
                }
            }
        }

        return MatrixR064.FACTORY.rows(cov);
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

    /**
     * Resample a daily return matrix to a lower frequency.
     *
     * <p>For a factor of N, each output row compounds N consecutive daily returns:
     * <pre>
     *   r_resampled[t] = (1+r[d*N]) * (1+r[d*N+1]) * ... * (1+r[d*N+N-1]) - 1
     * </pre>
     *
     * @param dailyReturns [T x n] daily return matrix
     * @param factor       resampling factor (5=weekly, 22=monthly)
     * @return resampled return matrix
     */
    public static MatrixR064 resample(MatrixR064 dailyReturns, int factor) {
        if (factor <= 1) return dailyReturns;

        int totalRows = (int) dailyReturns.countRows();
        int cols      = (int) dailyReturns.countColumns();
        int outRows   = totalRows / factor;

        double[][] out = new double[outRows][cols];
        for (int t = 0; t < outRows; t++) {
            for (int j = 0; j < cols; j++) {
                double growth = 1.0;
                for (int d = 0; d < factor; d++) {
                    growth *= (1.0 + dailyReturns.get(t * factor + d, j));
                }
                out[t][j] = growth - 1.0;
            }
        }
        return MatrixR064.FACTORY.rows(out);
    }

    /**
     * Compute correlation matrix from a return matrix.
     */
    public static MatrixR064 correlationMatrix(MatrixR064 returns) {
        int rows = (int) returns.countRows();
        int cols = (int) returns.countColumns();

        double[] mean = new double[cols];
        double[] ssq  = new double[cols]; // sum of squared deviations
        for (int j = 0; j < cols; j++) {
            double sum = 0;
            for (int i = 0; i < rows; i++) sum += returns.get(i, j);
            mean[j] = sum / rows;
            double ss = 0;
            for (int i = 0; i < rows; i++) {
                double d = returns.get(i, j) - mean[j];
                ss += d * d;
            }
            ssq[j] = ss;
        }

        double[][] corr = new double[cols][cols];
        for (int i = 0; i < cols; i++) {
            corr[i][i] = 1.0;
            for (int j = i + 1; j < cols; j++) {
                double sum = 0;
                for (int t = 0; t < rows; t++) {
                    sum += (returns.get(t, i) - mean[i]) * (returns.get(t, j) - mean[j]);
                }
                double denom = Math.sqrt(ssq[i] * ssq[j]);
                double c = denom > 1e-15 ? sum / denom : 0.0;
                // Clamp to [-1, 1] for floating-point safety
                c = Math.max(-1.0, Math.min(1.0, c));
                corr[i][j] = c;
                corr[j][i] = c;
            }
        }
        return MatrixR064.FACTORY.rows(corr);
    }

    /**
     * Compute rolling correlation between two assets over a window.
     *
     * @param returns [T x n] return matrix
     * @param i       first asset index
     * @param j       second asset index
     * @param window  rolling window size
     * @return list of correlation values (length = T - window + 1)
     */
    public static java.util.List<Double> rollingCorrelation(
            MatrixR064 returns, int i, int j, int window) {
        int rows = (int) returns.countRows();
        java.util.List<Double> result = new java.util.ArrayList<>();

        for (int t = window - 1; t < rows; t++) {
            double sumI = 0, sumJ = 0, sumIJ = 0, sumI2 = 0, sumJ2 = 0;
            for (int d = t - window + 1; d <= t; d++) {
                double vi = returns.get(d, i);
                double vj = returns.get(d, j);
                sumI  += vi;
                sumJ  += vj;
                sumIJ += vi * vj;
                sumI2 += vi * vi;
                sumJ2 += vj * vj;
            }
            double denom = Math.sqrt((sumI2 - sumI * sumI / window) *
                                     (sumJ2 - sumJ * sumJ / window));
            double corr = denom > 1e-15
                    ? (sumIJ - sumI * sumJ / window) / denom : 0.0;
            result.add(corr);
        }
        return result;
    }

    /**
     * Detect correlation regime: computes average pairwise correlation
     * over a rolling window and classifies as:
     * - HIGH_CORRELATION (>0.7): risk-on / contagion regime
     * - NORMAL (0.3-0.7): normal market
     * - LOW_CORRELATION (<0.3): diversification-friendly regime
     *
     * @param returns [T x n] return matrix
     * @param window  rolling window
     * @return list of regime labels per step
     */
    public static java.util.List<String> correlationRegime(
            MatrixR064 returns, int window) {
        int cols = (int) returns.countColumns();
        int rows = (int) returns.countRows();
        java.util.List<String> regimes = new java.util.ArrayList<>();

        for (int t = window - 1; t < rows; t++) {
            double totalCorr = 0;
            int count = 0;
            for (int i = 0; i < cols; i++) {
                for (int j = i + 1; j < cols; j++) {
                    double sumI = 0, sumJ = 0, sumIJ = 0, sumI2 = 0, sumJ2 = 0;
                    for (int d = t - window + 1; d <= t; d++) {
                        double vi = returns.get(d, i);
                        double vj = returns.get(d, j);
                        sumI  += vi;
                        sumJ  += vj;
                        sumIJ += vi * vj;
                        sumI2 += vi * vi;
                        sumJ2 += vj * vj;
                    }
                    double denom = Math.sqrt((sumI2 - sumI * sumI / window) *
                                             (sumJ2 - sumJ * sumJ / window));
                    double corr = denom > 1e-15
                            ? (sumIJ - sumI * sumJ / window) / denom : 0.0;
                    totalCorr += corr;
                    count++;
                }
            }
            double avgCorr = count > 0 ? totalCorr / count : 0;
            String regime = avgCorr > 0.7 ? "HIGH_CORR" :
                           avgCorr < 0.3 ? "LOW_CORR" : "NORMAL";
            regimes.add(regime);
        }
        return regimes;
    }
}
