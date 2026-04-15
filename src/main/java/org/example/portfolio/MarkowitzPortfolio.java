package org.example.portfolio;

import org.example.util.MatrixUtils;
import org.ojalgo.data.domain.finance.portfolio.MarkowitzModel;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.List;

/**
 * Classic Markowitz mean-variance portfolio.
 *
 * <p>Minimises variance for a given target return. When {@code targetReturn}
 * is null the model falls into minimum-variance mode (ignores μ magnitudes).
 */
public class MarkowitzPortfolio implements PortfolioModel {

    private final double  maxLong;
    private final double  maxShort;
    private final double  shrinkageLambda;
    private final boolean allowShorting;
    private final Double  targetReturn;   // null → min-variance mode

    public MarkowitzPortfolio(double maxLong, double maxShort,
                              double shrinkageLambda, boolean allowShorting,
                              Double targetReturn) {
        this.maxLong         = maxLong;
        this.maxShort        = maxShort;
        this.shrinkageLambda = shrinkageLambda;
        this.allowShorting   = allowShorting;
        this.targetReturn    = targetReturn;
    }

    // ── PortfolioModel ────────────────────────────────────────────────────────

    @Override
    public List<BigDecimal> allocate(MatrixR064 returns, MatrixR064 mu) {
        MatrixR064 cov = MatrixUtils.covarianceMatrix(returns, mu, shrinkageLambda);
        int n = (int) returns.countColumns();

        MarkowitzModel model = new MarkowitzModel(cov, mu);
        model.setShortingAllowed(allowShorting);

        if (targetReturn != null) {
            model.setTargetReturn(BigDecimal.valueOf(targetReturn));
        }
        for (int i = 0; i < n; i++) {
            model.setUpperLimit(i, BigDecimal.valueOf(maxLong));
            if (allowShorting) {
                model.setLowerLimit(i, BigDecimal.valueOf(maxShort));
            }
        }
        return model.getWeights();
    }

    @Override
    public String name() {
        return targetReturn == null
                ? "Markowitz (Min Var)"
                : String.format("Markowitz (Target %.2f%%)", targetReturn * 100);
    }
}
