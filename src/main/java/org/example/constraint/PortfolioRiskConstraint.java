package org.example.constraint;

import org.example.util.MatrixUtils;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.List;

/**
 * Post-optimisation constraint that limits portfolio VaR(95%) or CVaR(95%).
 *
 * <p>Computes the portfolio variance from the covariance matrix and current
 * weights. If the VaR (or CVaR) exceeds the user-specified limit, all
 * weights are scaled proportionally.
 */
public class PortfolioRiskConstraint implements Constraint {

    private final double maxVar95;
    private final boolean useCvar;

    public PortfolioRiskConstraint(double maxVar95, boolean useCvar) {
        if (maxVar95 <= 0) throw new IllegalArgumentException("maxVar95 must be positive");
        this.maxVar95 = maxVar95;
        this.useCvar  = useCvar;
    }

    public PortfolioRiskConstraint(double maxVar95) {
        this(maxVar95, false);
    }

    @Override
    public List<BigDecimal> apply(List<BigDecimal> weights) {
        throw new UnsupportedOperationException(
                "PortfolioRiskConstraint requires the returns matrix. Use apply(weights, returns) instead.");
    }

    @Override
    public List<BigDecimal> apply(List<BigDecimal> weights, MatrixR064 returns) {
        int n = weights.size();
        if (n < 2) return weights;

        // Compute covariance from returns
        MatrixR064 cov;
        try {
            cov = MatrixUtils.ledoitWolfCovariance(returns);
        } catch (Exception e) {
            return weights;
        }

        // Compute portfolio variance = w' Σ w
        double[] wArr = weights.stream().mapToDouble(BigDecimal::doubleValue).toArray();
        double var = 0.0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                var += wArr[i] * wArr[j] * cov.get(i, j);
            }
        }
        double sigma = Math.sqrt(var);

        // Portfolio VaR(95%) = -1.645 * sigma (daily, zero-mean approximation)
        double portfolioVar = 1.645 * sigma;

        // Portfolio CVaR(95%) ≈ sigma * φ(Φ⁻¹(0.05)) / 0.05 ≈ 2.063 * sigma
        double portfolioCvar = useCvar ? 2.063 * sigma : portfolioVar;

        double risk = useCvar ? portfolioCvar : portfolioVar;

        if (risk <= maxVar95) return weights;

        // Scale down proportionally
        double scale = maxVar95 / risk;
        final double fs = scale;
        return weights.stream()
                .map(w -> w.multiply(BigDecimal.valueOf(fs)))
                .toList();
    }

    @Override
    public String describe() {
        return useCvar
                ? String.format("Portfolio CVaR(95%%) ≤ %.4f", maxVar95)
                : String.format("Portfolio VaR(95%%) ≤ %.4f", maxVar95);
    }
}
