package org.example.portfolio;

import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Inverse-volatility risk-parity portfolio.
 *
 * <p>Each asset's weight is proportional to 1 / σ_i so that every asset
 * contributes equally to total portfolio volatility.
 *
 * <p>The signal ({@code expectedReturns}) is used optionally to exclude
 * assets with strongly negative signals (when {@code respectSignSign} is true).
 */
public class RiskParityPortfolio implements PortfolioModel {

    private final int     window;            // days used to estimate volatility
    private final boolean respectSignSign;   // sign weights by alpha signal

    public RiskParityPortfolio(int window, boolean respectSignSign) {
        this.window           = window;
        this.respectSignSign  = respectSignSign;
    }

    public RiskParityPortfolio() { this(60, true); }

    // ── PortfolioModel ────────────────────────────────────────────────────────

    @Override
    public List<BigDecimal> allocate(MatrixR064 returns, MatrixR064 mu) {
        int rows = (int) returns.countRows();
        int cols = (int) returns.countColumns();
        int win  = Math.min(window, rows);

        double[] vols    = new double[cols];
        double   invSum  = 0;

        for (int j = 0; j < cols; j++) {
            double sum = 0, sumSq = 0;
            for (int i = rows - win; i < rows; i++) {
                double v = returns.get(i, j);
                sum   += v;
                sumSq += v * v;
            }
            double mean = sum / win;
            double var  = Math.max(sumSq / win - mean * mean, 1e-10);
            vols[j]     = Math.sqrt(var);
            invSum     += 1.0 / vols[j];
        }

        double[] raw = new double[cols];
        for (int j = 0; j < cols; j++) {
            double w = (1.0 / vols[j]) / invSum;
            if (respectSignSign && mu.get(0, j) < 0) {
                w = -w * 0.5;
            }
            raw[j] = w;
        }

        // Renormalize so Σ|w| = 1 (risk parity invariant)
        double absSum = 0;
        for (int j = 0; j < cols; j++) absSum += Math.abs(raw[j]);
        if (absSum > 1e-12) {
            double scale = 1.0 / absSum;
            for (int j = 0; j < cols; j++) raw[j] *= scale;
        }

        List<BigDecimal> weights = new ArrayList<>(cols);
        for (int j = 0; j < cols; j++) weights.add(BigDecimal.valueOf(raw[j]));
        return weights;
    }

    @Override
    public String name() {
        return "Risk Parity (Inv-Vol)";
    }
}
