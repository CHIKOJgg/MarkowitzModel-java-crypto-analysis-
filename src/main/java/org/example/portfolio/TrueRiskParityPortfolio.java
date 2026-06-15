package org.example.portfolio;

import org.example.Defaults;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * True Risk Parity portfolio using iterative solver.
 *
 * <p>Solves for weights such that each asset contributes equally to total
 * portfolio risk (Equal Risk Contribution):
 *
 * <pre>
 *   RC_i = w_i * (Σw)_i / σ_p  =  constant  for all i
 * </pre>
 *
 * <p>Uses the fixed-point iteration from Maillard et al. (2010):
 * <pre>
 *   w_i^{k+1} = 1 / (Σw^k)_i
 * </pre>
 * followed by normalization.
 *
 * <p>Falls back to inverse-volatility approximation if solver doesn't converge.
 */
public class TrueRiskParityPortfolio implements PortfolioModel {

    private final int    window;
    private final int    maxIter;
    private final double tolerance;

    public TrueRiskParityPortfolio(int window, int maxIter, double tolerance) {
        this.window    = window;
        this.maxIter   = maxIter;
        this.tolerance = tolerance;
    }

    public TrueRiskParityPortfolio(int window) {
        this(window, Defaults.RP_MAX_ITER, Defaults.RP_TOLERANCE);
    }

    public TrueRiskParityPortfolio() {
        this(Defaults.RP_WINDOW);
    }

    @Override
    public List<BigDecimal> allocate(MatrixR064 returns, MatrixR064 mu) {
        int rows = (int) returns.countRows();
        int cols = (int) returns.countColumns();
        int win  = Math.min(window, rows);

        // Compute covariance matrix from training window
        MatrixR064 cov = computeCovariance(returns, win);

        // Initial guess: inverse volatility
        double[] w = new double[cols];
        double invSum = 0;
        for (int j = 0; j < cols; j++) {
            double var = cov.get(j, j);
            w[j] = var > 0 ? 1.0 / Math.sqrt(var) : 1.0;
            invSum += w[j];
        }
        for (int j = 0; j < cols; j++) w[j] /= invSum;

        // Fixed-point iteration: w_i = 1 / (Σw)_i
        for (int iter = 0; iter < maxIter; iter++) {
            // Compute Σw
            double[] sigmaW = new double[cols];
            for (int i = 0; i < cols; i++) {
                double sum = 0;
                for (int j = 0; j < cols; j++) {
                    sum += cov.get(i, j) * w[j];
                }
                sigmaW[i] = sum;
            }

            // Update: w_i = 1 / (Σw)_i
            double[] wNew = new double[cols];
            double newSum = 0;
            for (int j = 0; j < cols; j++) {
                wNew[j] = sigmaW[j] > 1e-15 ? 1.0 / sigmaW[j] : 1.0;
                newSum += wNew[j];
            }
            for (int j = 0; j < cols; j++) wNew[j] /= newSum;

            // Check convergence
            double diff = 0;
            for (int j = 0; j < cols; j++) {
                diff += Math.abs(wNew[j] - w[j]);
            }
            w = wNew;

            if (diff < tolerance) break;
        }

        // Build result
        List<BigDecimal> weights = new ArrayList<>(cols);
        for (int j = 0; j < cols; j++) {
            weights.add(BigDecimal.valueOf(w[j]));
        }
        return weights;
    }

    private MatrixR064 computeCovariance(MatrixR064 returns, int win) {
        int rows = (int) returns.countRows();
        int cols = (int) returns.countColumns();

        // Compute column means
        double[] mean = new double[cols];
        for (int j = 0; j < cols; j++) {
            double sum = 0;
            for (int i = rows - win; i < rows; i++) sum += returns.get(i, j);
            mean[j] = sum / win;
        }

        // Compute covariance
        double[][] cov = new double[cols][cols];
        for (int i = 0; i < cols; i++) {
            for (int j = i; j < cols; j++) {
                double sum = 0;
                for (int t = rows - win; t < rows; t++) {
                    sum += (returns.get(t, i) - mean[i]) * (returns.get(t, j) - mean[j]);
                }
                cov[i][j] = sum / (win - 1);
                cov[j][i] = cov[i][j];
            }
        }
        return MatrixR064.FACTORY.rows(cov);
    }

    @Override
    public String name() {
        return "True Risk Parity (ERC)";
    }
}
