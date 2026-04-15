package org.example.risk;

import org.ojalgo.matrix.MatrixR064;
import java.math.BigDecimal;
import java.util.List;

/**
 * Scales the entire weight vector so that the estimated ex-ante portfolio
 * daily volatility equals {@code targetVol}.
 *
 * <p>σ_portfolio ≈ sqrt( w' · Σ · w )
 *
 * <p>If the scaled leverage would exceed {@code maxLeverage} the weights
 * are capped at that level instead of the vol target.
 */
public class VolatilityScalingRisk implements RiskModel {

    private final double targetVol;     // daily (e.g. 0.01 = 1%)
    private final double maxLeverage;   // hard cap on gross leverage

    public VolatilityScalingRisk(double targetVol, double maxLeverage) {
        this.targetVol   = targetVol;
        this.maxLeverage = maxLeverage;
    }

    public VolatilityScalingRisk(double targetVol) { this(targetVol, 2.0); }

    @Override
    public List<BigDecimal> adjust(List<BigDecimal> weights, MatrixR064 returns) {
        double portfolioVol = estimatePortfolioVol(weights, returns);
        if (portfolioVol < 1e-8) return weights;   // degenerate: leave unchanged

        double scale = Math.min(targetVol / portfolioVol,
                                computeMaxScale(weights));

        return weights.stream()
                .map(w -> w.multiply(BigDecimal.valueOf(scale)))
                .toList();
    }

    @Override
    public String describe() {
        return String.format("VolScaling(target=%.1f%%/day)", targetVol * 100);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private double estimatePortfolioVol(List<BigDecimal> w, MatrixR064 r) {
        int rows = (int) r.countRows();
        int cols = (int) r.countColumns();

        // Build daily portfolio returns then compute std
        double sumR = 0, sumR2 = 0;
        for (int d = 0; d < rows; d++) {
            double pRet = 0;
            for (int j = 0; j < cols; j++) {
                pRet += r.get(d, j) * w.get(j).doubleValue();
            }
            sumR  += pRet;
            sumR2 += pRet * pRet;
        }
        double mean = sumR / rows;
        double var  = sumR2 / rows - mean * mean;
        return var > 0 ? Math.sqrt(var) : 0;
    }

    private double computeMaxScale(List<BigDecimal> weights) {
        double leverage = weights.stream()
                .mapToDouble(w -> Math.abs(w.doubleValue())).sum();
        return leverage > 0 ? maxLeverage / leverage : 1.0;
    }
}
