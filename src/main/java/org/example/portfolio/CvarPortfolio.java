package org.example.portfolio;

import org.example.Defaults;
import org.example.util.MatrixUtils;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CVaR (Conditional Value-at-Risk / Expected Shortfall) portfolio optimization.
 *
 * <p>Minimizes CVaR at a given confidence level, which is a coherent
 * risk measure that captures tail risk better than variance.
 *
 * <p>Uses a linear programming formulation with historical scenarios:
 * <pre>
 *   min  CVaR_α = VaR_α + 1/(T(1-α)) * Σ max(0, -r_p(t) - VaR_α)
 *   s.t. Σ w_i = 1, w_i ≥ 0 (long-only)
 * </pre>
 *
 * <p>Solved via iterative approximation (since we don't have an LP solver).
 */
public class CvarPortfolio implements PortfolioModel {

    private final double confidenceLevel;   // e.g. 0.95 for 95% CVaR
    private final int    maxIter;
    private final double maxLong;

    public CvarPortfolio(double confidenceLevel, int maxIter, double maxLong) {
        this.confidenceLevel = confidenceLevel;
        this.maxIter         = maxIter;
        this.maxLong         = maxLong;
    }

    public CvarPortfolio() {
        this(Defaults.CVAR_CONFIDENCE, Defaults.CVAR_MAX_ITER, Defaults.CVAR_MAX_LONG);
    }

    @Override
    public List<BigDecimal> allocate(MatrixR064 returns, MatrixR064 mu) {
        int n = (int) returns.countColumns();
        int T = (int) returns.countRows();

        // Start with equal weights
        double[] w = new double[n];
        for (int j = 0; j < n; j++) w[j] = 1.0 / n;

        // Iterative gradient descent on CVaR
        for (int iter = 0; iter < maxIter; iter++) {
            double[] grad = cvarGradient(returns, w, mu);

            // Update weights: move opposite to gradient
            double stepSize = 0.01 / (1 + iter * 0.01);
            for (int j = 0; j < n; j++) {
                w[j] += stepSize * grad[j];
                w[j] = Math.max(0, Math.min(maxLong, w[j])); // long-only, cap
            }

            // Normalize
            double sumW = 0;
            for (double v : w) sumW += v;
            if (sumW > 1e-10) {
                for (int j = 0; j < n; j++) w[j] /= sumW;
            }
        }

        List<BigDecimal> result = new ArrayList<>(n);
        for (int j = 0; j < n; j++) {
            result.add(BigDecimal.valueOf(w[j]));
        }
        return result;
    }

    /**
     * Compute gradient of CVaR w.r.t. weights.
     *
     * <p>CVaR_α = (1/(T(1-α))) * Σ max(0, -r_p(t) - VaR_α)
     * where VaR_α is the α-th quantile of portfolio returns.
     */
    private double[] cvarGradient(MatrixR064 returns, double[] w, MatrixR064 mu) {
        int T = (int) returns.countRows();
        int n = (int) returns.countColumns();

        // Compute portfolio returns
        double[] portReturns = new double[T];
        for (int t = 0; t < T; t++) {
            double r = 0;
            for (int j = 0; j < n; j++) r += returns.get(t, j) * w[j];
            portReturns[t] = r;
        }

        // Find VaR at confidence level
        double[] sorted = portReturns.clone();
        java.util.Arrays.sort(sorted);
        int varIdx = (int) Math.floor(T * (1 - confidenceLevel));
        double VaR = -sorted[Math.min(varIdx, T - 1)];

        // Identify tail scenarios (where loss exceeds VaR)
        boolean[] inTail = new boolean[T];
        for (int t = 0; t < T; t++) {
            inTail[t] = -portReturns[t] > VaR;
        }

        // Gradient: d(CVaR)/dw_j = 1/(T*(1-α)) * Σ_{t in tail} -r_j(t)
        // To MINIMIZE CVaR, gradient ascent uses the negative gradient.
        // Assets with good tail returns (positive in tail) have negative gradient
        // contribution, so they receive positive weight updates.
        double[] grad = new double[n];
        double scale = 1.0 / (T * (1 - confidenceLevel));
        for (int j = 0; j < n; j++) {
            double sum = 0;
            for (int t = 0; t < T; t++) {
                if (inTail[t]) {
                    sum += returns.get(t, j);
                }
            }
            // Positive sum (good tail returns) -> positive grad -> increase weight
            grad[j] = scale * sum;

            // Add alpha signal as regularization (push toward high alpha assets)
            if (mu != null) {
                grad[j] += 0.1 * mu.get(0, j);
            }
        }
        return grad;
    }

    @Override
    public String name() {
        return String.format("CVaR (%.0f%%)", confidenceLevel * 100);
    }
}
