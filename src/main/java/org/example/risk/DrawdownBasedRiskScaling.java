package org.example.risk;

import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.List;

/**
 * Reduces portfolio exposure based on drawdown observed within the training
 * window.  Unlike the previous stateful version, this is stateless: the max
 * drawdown is computed solely from the training returns passed to
 * {@link #adjust}, avoiding cross-step state contamination.
 *
 * <p>Scale factor = max(minScale, 1 - maxDrawdown × reductionFactor).
 * Example: at 20% drawdown, k=2, minScale=0.2 → scale = 1 - 0.4 = 0.6.
 */
public class DrawdownBasedRiskScaling implements RiskModel {

    private final double reductionFactor;
    private final double minScale;

    public DrawdownBasedRiskScaling(double reductionFactor, double minScale) {
        this.reductionFactor = reductionFactor;
        this.minScale        = minScale;
    }

    public DrawdownBasedRiskScaling() {
        this(2.0, 0.2);
    }

    @Override
    public List<BigDecimal> adjust(List<BigDecimal> weights, MatrixR064 returns) {
        int rows = (int) returns.countRows();
        int cols = (int) returns.countColumns();

        // Compute max drawdown within this training window
        double equity = 1.0;
        double peak = 1.0;
        double maxDD = 0;
        for (int d = 0; d < rows; d++) {
            double pRet = 0;
            for (int j = 0; j < cols; j++) pRet += returns.get(d, j) * weights.get(j).doubleValue();
            equity *= (1.0 + pRet);
            if (equity > peak) peak = equity;
            double dd = (peak - equity) / peak;
            if (dd > maxDD) maxDD = dd;
        }

        double scale = Math.max(minScale, 1.0 - maxDD * reductionFactor);
        final double fs = scale;
        return weights.stream()
                .map(w -> w.multiply(BigDecimal.valueOf(fs)))
                .toList();
    }

    @Override
    public String describe() {
        return String.format("DrawdownScaling(k=%.1f, min=%.0f%%)", reductionFactor, minScale * 100);
    }
}
