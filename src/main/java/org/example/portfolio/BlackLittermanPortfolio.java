package org.example.portfolio;

import org.example.Defaults;
import org.example.util.MatrixUtils;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Black-Litterman portfolio optimization.
 *
 * <p>Combines market equilibrium returns (implied by market cap weights)
 * with investor views to produce posterior expected returns, then
 * optimizes using mean-variance.
 *
 * <p>Formula:
 * <pre>
 *   Π = δ * Σ * w_mkt                    (implied equilibrium returns)
 *   P = views matrix [k x n]
 *   Q = view returns [k x 1]
 *   Ω = diag(view confidence)             (uncertainty of views)
 *   τ = scalar (typically 0.025)
 *
 *   μ_BL = [(τΣ)⁻¹ + P'Ω⁻¹P]⁻¹ × [(τΣ)⁻¹Π + P'Ω⁻¹Q]
 *   Σ_BL = [(τΣ)⁻¹ + P'Ω⁻¹P]⁻¹
 * </pre>
 */
public class BlackLittermanPortfolio implements PortfolioModel {

    private final double tau;            // scalar (view uncertainty)
    private final double riskAversion;   // δ (market price of risk)
    private final double maxLong;
    private final double maxShort;
    private final boolean allowShorting;

    public BlackLittermanPortfolio(double tau, double riskAversion,
                                    double maxLong, double maxShort,
                                    boolean allowShorting) {
        this.tau          = tau;
        this.riskAversion = riskAversion;
        this.maxLong      = maxLong;
        this.maxShort     = maxShort;
        this.allowShorting = allowShorting;
    }

    public BlackLittermanPortfolio() {
        this(Defaults.BL_TAU, Defaults.BL_RISK_AVERSION, 0.3, -0.3, true);
    }

    @Override
    public List<BigDecimal> allocate(MatrixR064 returns, MatrixR064 mu) {
        int n = (int) returns.countColumns();

        try {
            return allocateBL(returns, mu, n);
        } catch (IllegalArgumentException e) {
            // Singular matrix: fall back to equal-weight
            List<BigDecimal> ew = new ArrayList<>(n);
            for (int j = 0; j < n; j++) ew.add(BigDecimal.valueOf(1.0 / n));
            return ew;
        }
    }

    private List<BigDecimal> allocateBL(MatrixR064 returns, MatrixR064 mu, int n) {

        // Step 1: Estimate covariance
        MatrixR064 cov = MatrixUtils.covarianceMatrix(returns, mu, 0.5);

        // Step 2: Equal-weight market portfolio (proxy for market cap weights)
        double[] wMkt = new double[n];
        for (int j = 0; j < n; j++) wMkt[j] = 1.0 / n;

        // Step 3: Implied equilibrium returns: Π = δ * Σ * w_mkt
        double[] Pi = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0;
            for (int j = 0; j < n; j++) {
                sum += cov.get(i, j) * wMkt[j];
            }
            Pi[i] = riskAversion * sum;
        }

        // Step 4: Build views from alpha signal
        // Each asset's alpha signal is treated as a view with confidence
        // proportional to its absolute value
        int k = n; // one view per asset (absolute view)
        double[][] P = new double[k][n];
        double[] Q = new double[k];
        double[] omega = new double[k];

        for (int i = 0; i < k; i++) {
            P[i][i] = 1.0;  // absolute view on asset i
            Q[i] = mu.get(0, i);
            // Confidence inversely proportional to vol (higher vol = less confidence)
            double vol = Math.sqrt(cov.get(i, i));
            omega[i] = tau * cov.get(i, i) * (1.0 + vol * 10);
        }

        // Step 5: Posterior expected returns
        // μ_BL = [(τΣ)⁻¹ + P'Ω⁻¹P]⁻¹ × [(τΣ)⁻¹Π + P'Ω⁻¹Q]
        double[][] tauSigma = new double[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                tauSigma[i][j] = tau * cov.get(i, j);

        double[][] tauSigmaInv = invert(tauSigma);
        double[][] omegaInv = new double[k][k];
        for (int i = 0; i < k; i++) omegaInv[i][i] = 1.0 / omega[i];

        // P'Ω⁻¹P
        double[][] PtOmegaInvP = new double[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                for (int l = 0; l < k; l++)
                    PtOmegaInvP[i][j] += P[l][i] * omegaInv[l][l] * P[l][j];

        // (τΣ)⁻¹ + P'Ω⁻¹P
        double[][] sumInv = new double[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                sumInv[i][j] = tauSigmaInv[i][j] + PtOmegaInvP[i][j];

        double[][] posteriorInv = invert(sumInv);

        // (τΣ)⁻¹Π
        double[] term1 = new double[n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                term1[i] += tauSigmaInv[i][j] * Pi[j];

        // P'Ω⁻¹Q
        double[] term2 = new double[n];
        for (int i = 0; i < n; i++)
            for (int l = 0; l < k; l++)
                term2[i] += P[l][i] * omegaInv[l][l] * Q[l];

        // μ_BL
        double[] muBL = new double[n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                muBL[i] += posteriorInv[i][j] * (term1[j] + term2[j]);

        // Step 6: Optimal weights: w* = (δΣ)⁻¹ μ_BL
        double[][] deltaSigma = new double[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                deltaSigma[i][j] = riskAversion * cov.get(i, j);

        double[][] deltaSigmaInv = invert(deltaSigma);

        double[] weights = new double[n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                weights[i] += deltaSigmaInv[i][j] * muBL[j];

        // Build initial result from raw weights
        List<BigDecimal> result = new ArrayList<>(n);
        for (int j = 0; j < n; j++) {
            result.add(BigDecimal.valueOf(weights[j]));
        }

        // Normalize to sum to 1 (long) or 0 (market neutral)
        double sumW = result.stream().mapToDouble(BigDecimal::doubleValue).sum();
        if (Math.abs(sumW) > 1e-10) {
            double scale = 1.0 / sumW;
            result = result.stream()
                    .map(w -> w.multiply(BigDecimal.valueOf(scale)))
                    .toList();
        }

        // Apply constraints AFTER normalization (normalization can violate bounds)
        List<BigDecimal> constrained = new ArrayList<>(n);
        for (int j = 0; j < n; j++) {
            double w = result.get(j).doubleValue();
            if (!allowShorting) w = Math.max(0, w);
            w = Math.max(maxShort, Math.min(maxLong, w));
            constrained.add(BigDecimal.valueOf(w));
        }

        // Re-normalize if clamping changed the sum
        double sumC = constrained.stream().mapToDouble(BigDecimal::doubleValue).sum();
        if (Math.abs(sumC) > 1e-10 && Math.abs(sumC - 1.0) > 1e-6) {
            double scale = 1.0 / sumC;
            constrained = constrained.stream()
                    .map(w -> w.multiply(BigDecimal.valueOf(scale)))
                    .toList();
        }

        return constrained;
    }

    @Override
    public String name() {
        return "Black-Litterman";
    }

    // ── Matrix inversion (Gauss-Jordan) ──────────────────────────────────────

    private static double[][] invert(double[][] matrix) {
        int n = matrix.length;
        double[][] aug = new double[n][2 * n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(matrix[i], 0, aug[i], 0, n);
            aug[i][n + i] = 1.0;
        }
        for (int col = 0; col < n; col++) {
            int maxRow = col;
            for (int row = col + 1; row < n; row++)
                if (Math.abs(aug[row][col]) > Math.abs(aug[maxRow][col]))
                    maxRow = row;
            double[] tmp = aug[col]; aug[col] = aug[maxRow]; aug[maxRow] = tmp;

            double pivot = aug[col][col];
            if (Math.abs(pivot) < 1e-12) {
                throw new IllegalArgumentException(
                        "Singular or near-singular matrix in Black-Litterman inversion at column " + col);
            }
            for (int j = 0; j < 2 * n; j++) aug[col][j] /= pivot;

            for (int row = 0; row < n; row++) {
                if (row == col) continue;
                double factor = aug[row][col];
                for (int j = 0; j < 2 * n; j++) aug[row][j] -= factor * aug[col][j];
            }
        }
        double[][] inv = new double[n][n];
        for (int i = 0; i < n; i++)
            System.arraycopy(aug[i], n, inv[i], 0, n);
        return inv;
    }
}
