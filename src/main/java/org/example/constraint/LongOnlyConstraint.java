package org.example.constraint;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Clamps all weights to non-negative and renormalizes to sum to 1.
 * Applied when short selling is disabled in the UI.
 */
public class LongOnlyConstraint implements Constraint {

    @Override
    public List<BigDecimal> apply(List<BigDecimal> weights) {
        int n = weights.size();
        double[] clamped = new double[n];
        double sum = 0;
        for (int i = 0; i < n; i++) {
            double w = Math.max(0, weights.get(i).doubleValue());
            clamped[i] = w;
            sum += w;
        }
        if (sum < 1e-12) {
            double eq = 1.0 / n;
            List<BigDecimal> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) out.add(BigDecimal.valueOf(eq));
            return out;
        }
        List<BigDecimal> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(BigDecimal.valueOf(clamped[i] / sum));
        }
        return out;
    }

    @Override
    public String describe() { return "Long Only (w ≥ 0, Σw = 1)"; }
}
