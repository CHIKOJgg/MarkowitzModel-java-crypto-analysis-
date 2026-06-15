package org.example.risk;

import org.example.Defaults;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.List;

/**
 * Reduces portfolio exposure during drawdown periods.
 *
 * <p>Tracks historical peak equity and scales weights by
 * max(1 - drawdown × k, minScale) where k is the reduction
 * sensitivity and minScale is the minimum allowed exposure.
 *
 * <p>Example: at 20% drawdown, k=2, minScale=0.2 → scale = 1 - 0.4 = 0.6
 */
public class DrawdownBasedRiskScaling implements RiskModel {

    private final double reductionFactor;
    private final double minScale;
    private double peakEquity;

    public DrawdownBasedRiskScaling(double reductionFactor, double minScale) {
        this.reductionFactor = reductionFactor;
        this.minScale        = minScale;
        this.peakEquity      = 1.0;
    }

    public DrawdownBasedRiskScaling() {
        this(2.0, 0.2);
    }

    @Override
    public List<BigDecimal> adjust(List<BigDecimal> weights, MatrixR064 returns) {
        int rows = (int) returns.countRows();
        int cols = (int) returns.countColumns();

        // Simulate equity curve from this period's returns
        double equity = peakEquity;
        double currentPeak = peakEquity;
        for (int d = 0; d < rows; d++) {
            double pRet = 0;
            for (int j = 0; j < cols; j++) pRet += returns.get(d, j) * weights.get(j).doubleValue();
            equity *= (1.0 + pRet);
            if (equity > currentPeak) currentPeak = equity;
        }
        peakEquity = currentPeak;

        double drawdown = (peakEquity - equity) / peakEquity;
        double scale = Math.max(minScale, 1.0 - drawdown * reductionFactor);

        final double fs = scale;
        return weights.stream()
                .map(w -> w.multiply(BigDecimal.valueOf(fs)))
                .toList();
    }

    @Override
    public String describe() {
        return String.format("DrawdownScaling(k=%.1f, min=%.0f%%)", reductionFactor, minScale * 100);
    }

    /** Reset tracking state (call at start of each backtest). */
    public void reset() { this.peakEquity = 1.0; }
}
