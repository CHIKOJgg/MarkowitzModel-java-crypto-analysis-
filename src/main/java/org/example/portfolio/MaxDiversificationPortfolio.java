package org.example.portfolio;

import org.example.Defaults;
import org.example.util.MatrixUtils;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Maximum Diversification Ratio portfolio (Choueifaty &amp; Coignard 2008).
 *
 * <p>Maximizes DR(w) = (w'σ) / sqrt(w'Σw), where σ is the vector of
 * asset volatilities and Σ is the covariance matrix. The unconstrained
 * solution is w ∝ Σ⁻¹σ, projected onto the leverage constraint.
 */
public class MaxDiversificationPortfolio implements PortfolioModel {

    private final double maxLong;
    private final double maxShort;
    private final double leverage;

    public MaxDiversificationPortfolio(double maxLong, double maxShort, double leverage) {
        this.maxLong  = maxLong;
        this.maxShort = maxShort;
        this.leverage = leverage;
    }

    @Override
    public List<BigDecimal> allocate(MatrixR064 returns, MatrixR064 expectedReturns) {
        int n = (int) returns.countColumns();
        MatrixR064 cov = MatrixUtils.ledoitWolfCovariance(returns);

        // Asset volatilities (diagonal of covariance)
        double[] sigma = new double[n];
        for (int i = 0; i < n; i++) sigma[i] = Math.sqrt(cov.get(i, i));

        try {
            // Unconstrained MDR: w ∝ Σ⁻¹σ
            MatrixR064 covInv = cov.invert();
            double[] raw = new double[n];
            double sum = 0;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) raw[i] += covInv.get(i, j) * sigma[j];
                sum += raw[i];
            }

            // Normalize to Σ|w| = leverage
            double absSum = 0;
            for (double v : raw) absSum += Math.abs(v);
            if (absSum < 1e-12) return equalWeights(n);
            double scale = leverage / absSum;
            List<BigDecimal> weights = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                double w = raw[i] * scale;
                if (w > maxLong) w = maxLong;
                if (w < -maxShort) w = -maxShort;
                weights.add(BigDecimal.valueOf(w));
            }
            // Re-normalize after clamping to preserve target leverage
            double newAbsSum = 0;
            for (BigDecimal w : weights) newAbsSum += Math.abs(w.doubleValue());
            if (newAbsSum > 1e-12 && Math.abs(newAbsSum - leverage) > 1e-10) {
                double reScale = leverage / newAbsSum;
                for (int i = 0; i < n; i++) {
                    weights.set(i, weights.get(i).multiply(BigDecimal.valueOf(reScale)));
                }
            }
            return weights;
        } catch (Exception e) {
            return equalWeights(n);
        }
    }

    private List<BigDecimal> equalWeights(int n) {
        List<BigDecimal> w = new ArrayList<>(n);
        double eq = 1.0 / n;
        for (int i = 0; i < n; i++) w.add(BigDecimal.valueOf(eq));
        return w;
    }

    @Override
    public String name() {
        return "Max Diversification (MDR)";
    }
}
