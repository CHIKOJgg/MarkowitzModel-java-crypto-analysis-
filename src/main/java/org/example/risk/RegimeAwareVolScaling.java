package org.example.risk;

import org.example.util.MatrixUtils;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.List;

/**
 * Regime-Aware Volatility Scaling.
 *
 * <p>Detects the current correlation regime and adjusts the vol target
 * accordingly:
 * <ul>
 *   <li>HIGH_CORR → reduced target (defensive, contagion risk)</li>
 *   <li>LOW_CORR  → increased target (aggressive, diversification works)</li>
 *   <li>NORMAL    → base target</li>
 * </ul>
 *
 * <p>Also enforces a hard leverage cap on the scaled weights.
 */
public class RegimeAwareVolScaling implements RiskModel {

    private final double baseTargetVol;
    private final double maxLeverage;
    private final int    regimeWindow;

    private static final double HIGH_CORR_SCALE = 0.5;
    private static final double LOW_CORR_SCALE  = 1.5;

    public RegimeAwareVolScaling(double baseTargetVol, double maxLeverage, int regimeWindow) {
        this.baseTargetVol = baseTargetVol;
        this.maxLeverage   = maxLeverage;
        this.regimeWindow  = regimeWindow;
    }

    public RegimeAwareVolScaling(double baseTargetVol) {
        this(baseTargetVol, 2.0, 60);
    }

    @Override
    public List<BigDecimal> adjust(List<BigDecimal> weights, MatrixR064 returns) {
        String regime = detectRegime(returns);
        double targetVol = switch (regime) {
            case "HIGH_CORR" -> baseTargetVol * HIGH_CORR_SCALE;
            case "LOW_CORR"  -> baseTargetVol * LOW_CORR_SCALE;
            default          -> baseTargetVol;
        };

        double portfolioVol = estimatePortfolioVol(weights, returns);
        if (portfolioVol < 1e-8) return weights;

        double scale = Math.min(targetVol / portfolioVol, computeMaxScale(weights));

        return weights.stream()
                .map(w -> w.multiply(BigDecimal.valueOf(scale)))
                .toList();
    }

    @Override
    public String describe() {
        return String.format("RegimeVolScale(base=%.1f%%/day, window=%d)",
                baseTargetVol * 100, regimeWindow);
    }

    private String detectRegime(MatrixR064 returns) {
        List<String> regimes = MatrixUtils.correlationRegime(returns, regimeWindow);
        if (regimes.isEmpty()) return "NORMAL";
        return regimes.get(regimes.size() - 1);
    }

    private double estimatePortfolioVol(List<BigDecimal> w, MatrixR064 r) {
        int rows = (int) r.countRows();
        int cols = (int) r.countColumns();

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
        double var  = rows > 1
                ? (sumR2 / rows - mean * mean) * rows / (rows - 1)
                : 0;
        return var > 0 ? Math.sqrt(var) : 0;
    }

    private double computeMaxScale(List<BigDecimal> weights) {
        double leverage = weights.stream()
                .mapToDouble(w -> Math.abs(w.doubleValue())).sum();
        return leverage > 0 ? maxLeverage / leverage : 1.0;
    }
}
