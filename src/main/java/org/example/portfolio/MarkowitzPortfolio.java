package org.example.portfolio;

import org.example.util.MatrixUtils;
import org.ojalgo.data.domain.finance.portfolio.MarkowitzModel;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Classic Markowitz mean-variance portfolio.
 * Robust fallback to equal-weight when the solver returns null
 * (infeasible target return or degenerate covariance matrix).
 */
public class MarkowitzPortfolio implements PortfolioModel {

    private final double  maxLong;
    private final double  maxShort;
    private final double  shrinkageLambda;
    private final boolean allowShorting;
    private final Double  targetReturn;   // null -> min-variance mode

    public MarkowitzPortfolio(double maxLong, double maxShort,
                              double shrinkageLambda, boolean allowShorting,
                              Double targetReturn) {
        this.maxLong         = maxLong;
        this.maxShort        = maxShort;
        this.shrinkageLambda = shrinkageLambda;
        this.allowShorting   = allowShorting;
        this.targetReturn    = targetReturn;
    }

    @Override
    public List<BigDecimal> allocate(MatrixR064 returns, MatrixR064 mu) {
        MatrixR064 cov = MatrixUtils.covarianceMatrix(returns, mu, shrinkageLambda);
        int n = (int) returns.countColumns();

        // BUG FIX: alpha models emit a [1×n] row vector; ojAlgo MarkowitzModel
        // expects a [n×1] column vector for expectedExcessReturns.
        MarkowitzModel model = new MarkowitzModel(cov, mu.transpose());
        model.setShortingAllowed(allowShorting);

        if (targetReturn != null) {
            // Cap target to max feasible mu to prevent infeasible solver state
            double maxMu = 0;
            for (int j = 0; j < n; j++) maxMu = Math.max(maxMu, mu.get(0, j));
            double safeTarget = Math.min(targetReturn, maxMu * 0.90);
            if (safeTarget > 1e-8) {
                model.setTargetReturn(BigDecimal.valueOf(safeTarget));
            }
        }
        for (int i = 0; i < n; i++) {
            model.setUpperLimit(i, BigDecimal.valueOf(maxLong));
            if (allowShorting) {
                model.setLowerLimit(i, BigDecimal.valueOf(maxShort));
            }
        }

        List<BigDecimal> weights = null;
        try {
            weights = model.getWeights();
        } catch (Exception ignored) {}

        // Fallback: equal-weight when solver is null or degenerate
        if (weights == null || weights.stream().anyMatch(w -> w == null)) {
            weights = Collections.nCopies(n, BigDecimal.valueOf(1.0 / n));
        }
        return weights;
    }

    @Override
    public String name() {
        return targetReturn == null
                ? "Markowitz (Min Var)"
                : String.format("Markowitz (Target %.2f%%)", targetReturn * 100);
    }
}
