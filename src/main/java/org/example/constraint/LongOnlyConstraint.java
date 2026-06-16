package org.example.constraint;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Clamps all weights to non-negative but does NOT renormalize.
 *
 * <p>The difference between 1.0 and the resulting sum is treated as a
 * <b>cash allocation</b> — the model's implicit recommendation to hold
 * some portion of the portfolio in risk-free cash. The UI renders this
 * remainder as "Cash" so the total always sums to 100 %.
 *
 * <ul>
 *   <li>If the optimiser returned some short positions that are now
 *       disabled, those funds stay as cash rather than being
 *       redistributed to remaining longs.</li>
 *   <li>If vol-scaling or VaR constraints scaled the weights below 1.0,
 *       the cash preserves that signal instead of erasing it.</li>
 *   <li>If <em>all</em> weights are negative (model wants full short but
 *       shorting is disabled), the result is 100 % cash — the correct
 *       response when no asset passes the long-side filter.</li>
 * </ul>
 */
public class LongOnlyConstraint implements Constraint {

    @Override
    public List<BigDecimal> apply(List<BigDecimal> weights) {
        int n = weights.size();
        double[] clamped = new double[n];
        double sum = 0;
        for (int i = 0; i < n; i++) {
            double w = Math.max(0.0, weights.get(i).doubleValue());
            clamped[i] = w;
            sum += w;
        }

        // If gross long exposure already exceeds 1 (came from a leveraged
        // model without explicit leverage constraint), renormalize to 1 so
        // we don't accidentally create > 100 % long without explicit leverage.
        double scale = sum > 1.0 + 1e-9 ? 1.0 / sum : 1.0;

        List<BigDecimal> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(BigDecimal.valueOf(clamped[i] * scale));
        }
        // sum ≤ 1.0  →  cash = 1.0 − sum  (displayed in UI, not stored here)
        return out;
    }

    @Override
    public String describe() { return "Long Only (w ≥ 0, cash = 1 − Σw)"; }
}