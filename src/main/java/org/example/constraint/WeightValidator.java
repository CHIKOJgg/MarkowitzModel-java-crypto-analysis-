package org.example.constraint;

import java.math.BigDecimal;
import java.util.List;

/**
 * Validates weight sanity and throws {@link IllegalStateException} on failure.
 * Should always be the last constraint in the chain.
 */
public class WeightValidator implements Constraint {

    private final double maxAllowedLeverage;

    public WeightValidator(double maxAllowedLeverage) {
        this.maxAllowedLeverage = maxAllowedLeverage;
    }

    public WeightValidator() { this(10.0); }

    @Override
    public List<BigDecimal> apply(List<BigDecimal> weights) {
        double leverage = 0;
        for (BigDecimal w : weights) {
            double v = w.doubleValue();
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                throw new IllegalStateException(
                        "Weight validation failed: NaN/Inf detected in weights.");
            }
            leverage += Math.abs(v);
        }
        if (leverage > maxAllowedLeverage) {
            throw new IllegalStateException(String.format(
                    "Weight validation failed: leverage %.2f > max %.2f",
                    leverage, maxAllowedLeverage));
        }
        return weights;
    }

    @Override
    public String describe() {
        return String.format("Validate (leverage ≤ %.1f, no NaN)", maxAllowedLeverage);
    }
}
