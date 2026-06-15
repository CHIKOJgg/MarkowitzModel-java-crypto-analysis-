package org.example.risk;

import org.example.Defaults;
import org.example.util.MatrixUtils;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.List;

/**
 * Scales portfolio weights so that the estimated VaR(95%) matches a target.
 *
 * <p>VaR_portfolio ≈ 1.645 × σ_portfolio (zero-mean approximation).
 * The scale factor is min(targetVaR / portfolioVaR, maxLeverage / currentLeverage).
 */
public class VaRBasedRiskScaling implements RiskModel {

    private final double targetVaR;    // daily VaR(95%) target (e.g. 0.02 = 2%)
    private final double maxLeverage;

    public VaRBasedRiskScaling(double targetVaR, double maxLeverage) {
        this.targetVaR   = targetVaR;
        this.maxLeverage = maxLeverage;
    }

    public VaRBasedRiskScaling(double targetVaR) {
        this(targetVaR, Defaults.VOL_SCALE_MAX_LEVERAGE);
    }

    @Override
    public List<BigDecimal> adjust(List<BigDecimal> weights, MatrixR064 returns) {
        double portVaR = estimatePortfolioVaR(weights, returns);
        if (portVaR < 1e-10) return weights;

        double scale = Math.min(targetVaR / portVaR, computeMaxScale(weights));
        final double fs = scale;
        return weights.stream()
                .map(w -> w.multiply(BigDecimal.valueOf(fs)))
                .toList();
    }

    @Override
    public String describe() {
        return String.format("VaRScaling(target=%.2f%%)", targetVaR * 100);
    }

    private double estimatePortfolioVaR(List<BigDecimal> w, MatrixR064 r) {
        int rows = (int) r.countRows();
        int cols = (int) r.countColumns();
        double sumR = 0, sumR2 = 0;
        for (int d = 0; d < rows; d++) {
            double pRet = 0;
            for (int j = 0; j < cols; j++) pRet += r.get(d, j) * w.get(j).doubleValue();
            sumR += pRet;
            sumR2 += pRet * pRet;
        }
        double mean = sumR / rows;
        double var = rows > 1
                ? (sumR2 / rows - mean * mean) * rows / (rows - 1)
                : 0;
        double sigma = var > 0 ? Math.sqrt(var) : 0;
        return 1.645 * sigma;
    }

    private double computeMaxScale(List<BigDecimal> weights) {
        double lev = weights.stream().mapToDouble(w -> Math.abs(w.doubleValue())).sum();
        return lev > 0 ? maxLeverage / lev : 1.0;
    }
}
