package org.example.constraint;

import java.math.BigDecimal;
import java.util.List;

/**
 * Makes the portfolio dollar-neutral (Σ w_i = 0) while targeting a specific
 * gross exposure so that all capital is utilised.
 *
 * <h3>Why gross-targeting matters</h3>
 * A naive implementation that only shifts weights to Σ=0 may leave the model
 * using only a fraction of the available capital (e.g. gross=0.5 when the
 * user set leverage=1.0). The surplus would show up as "Cash" without the
 * user ever requesting it.
 *
 * <p>With {@code targetGross} set to the leverage parameter (default 1.0):
 * <ol>
 *   <li>Shift all weights by −mean so Σw = 0 (market-neutral step).</li>
 *   <li>Scale so Σ|w| = targetGross (capital-utilisation step).</li>
 * </ol>
 *
 * <p>The result: longs and shorts always sum to {@code targetGross} in absolute
 * terms, and "Cash = 1 − gross" is 0 % by default (or the explicit difference
 * when the user chooses a leverage < the strategy's natural gross).
 */
public class MarketNeutralConstraint implements Constraint {

    private final double targetGross;

    /**
     * @param targetGross desired gross exposure (Σ|w|); typically equals the
     *                    leverage slider value (default 1.0 = 100 %).
     */
    public MarketNeutralConstraint(double targetGross) {
        this.targetGross = Math.max(1e-6, targetGross);
    }

    /** Backward-compatible no-arg constructor: targets gross = 1.0 (100 %). */
    public MarketNeutralConstraint() {
        this(1.0);
    }

    @Override
    public List<BigDecimal> apply(List<BigDecimal> weights) {
        int n = weights.size();
        if (n == 0) return weights;

        // ── Step 1: neutralise (shift so Σw = 0) ───────────────────────────
        double sum = weights.stream().mapToDouble(BigDecimal::doubleValue).sum();
        double adj = sum / n;
        double[] neutral = new double[n];
        for (int i = 0; i < n; i++) {
            neutral[i] = weights.get(i).doubleValue() - adj;
        }

        // ── Step 2: scale to targetGross ────────────────────────────────────
        double gross = 0.0;
        for (double v : neutral) gross += Math.abs(v);

        double scale = (gross > 1e-12) ? targetGross / gross : 0.0;

        return java.util.Arrays.stream(neutral)
                .mapToObj(v -> BigDecimal.valueOf(v * scale))
                .toList();
    }

    @Override
    public String describe() {
        return String.format("Market Neutral (Σw=0, gross=%.0f%%)", targetGross * 100);
    }
}