package org.example.constraint;

import java.math.BigDecimal;
import java.util.List;

/**
 * Scales weights down proportionally so that gross leverage ≤ {@code maxLeverage}.
 *
 * <pre>  leverage = Σ |w_i|  </pre>
 */
public class LeverageConstraint implements Constraint {

    private final double maxLeverage;

    public LeverageConstraint(double maxLeverage) {
        if (maxLeverage <= 0) throw new IllegalArgumentException("maxLeverage must be positive");
        this.maxLeverage = maxLeverage;
    }

    @Override
    public List<BigDecimal> apply(List<BigDecimal> weights) {
        double leverage = weights.stream()
                .mapToDouble(w -> Math.abs(w.doubleValue()))
                .sum();

        if (leverage <= maxLeverage) return weights;

        double scale = maxLeverage / leverage;
        return weights.stream()
                .map(w -> w.multiply(BigDecimal.valueOf(scale)))
                .toList();
    }

    @Override
    public String describe() {
        return String.format("Leverage ≤ %.2f", maxLeverage);
    }
}
