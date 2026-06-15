package org.example.portfolio;

import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Turnover-constrained portfolio wrapper.
 *
 * <p>Wraps any {@link PortfolioModel} and limits how much the portfolio
 * can change from the previous weights, effectively reducing transaction costs.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Compute optimal weights w* from the inner model</li>
 *   <li>Compute turnover: Σ|w*_i - w_prev_i|</li>
 *   <li>If turnover &gt; maxTurnover, dampen: w = w_prev + (maxTurnover/turnover) * (w* - w_prev)</li>
 * </ol>
 *
 * <p>This is a practical heuristic that works with any optimizer and avoids
 * the need for L1 penalties in the objective function.
 */
public class TurnoverConstrainedPortfolio implements PortfolioModel {

    private final PortfolioModel inner;
    private final double         maxTurnover;  // max total turnover per rebalance (e.g. 0.5 = 50%)

    public TurnoverConstrainedPortfolio(PortfolioModel inner, double maxTurnover) {
        this.inner       = inner;
        this.maxTurnover = maxTurnover;
    }

    @Override
    public List<BigDecimal> allocate(MatrixR064 returns, MatrixR064 mu) {
        List<BigDecimal> optimalWeights = inner.allocate(returns, mu);

        // No previous weights on first call — return as-is
        // (previous weights are tracked externally in BacktestEngine)
        return optimalWeights;
    }

    /**
     * Apply turnover constraint given previous weights.
     *
     * @param prevWeights previous period weights (may be null for first period)
     * @param newWeights  newly computed optimal weights
     * @return turnover-constrained weights
     */
    public static List<BigDecimal> constrain(List<BigDecimal> prevWeights,
                                              List<BigDecimal> newWeights,
                                              double maxTurnover) {
        if (prevWeights == null) return newWeights;

        double turnover = 0;
        for (int i = 0; i < newWeights.size(); i++) {
            turnover += Math.abs(newWeights.get(i).doubleValue()
                               - prevWeights.get(i).doubleValue());
        }

        if (turnover <= maxTurnover) return newWeights;

        double alpha = maxTurnover / turnover;
        List<BigDecimal> constrained = new ArrayList<>(newWeights.size());
        for (int i = 0; i < newWeights.size(); i++) {
            double wPrev = prevWeights.get(i).doubleValue();
            double wNew  = newWeights.get(i).doubleValue();
            constrained.add(BigDecimal.valueOf(wPrev + alpha * (wNew - wPrev)));
        }
        return constrained;
    }

    @Override
    public String name() {
        return "TurnoverConstrained(" + inner.name() + ", maxTO=" + "%.0f%%".formatted(maxTurnover * 100) + ")";
    }
}
