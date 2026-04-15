package org.example.constraint;

import java.math.BigDecimal;
import java.util.List;

/**
 * Makes the portfolio dollar-neutral by subtracting the equal-sized adjustment
 * so that Σ w_i ≈ 0.
 */
public class MarketNeutralConstraint implements Constraint {

    @Override
    public List<BigDecimal> apply(List<BigDecimal> weights) {
        double sum = weights.stream().mapToDouble(BigDecimal::doubleValue).sum();
        double adj = sum / weights.size();
        return weights.stream()
                .map(w -> w.subtract(BigDecimal.valueOf(adj)))
                .toList();
    }

    @Override
    public String describe() { return "Market Neutral (Σw = 0)"; }
}
