package org.example.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable result of a Markowitz portfolio optimisation.
 */
public record PortfolioResult(
        List<BigDecimal> weights,
        double expectedReturn,   // per day
        double volatility,       // per day
        double sharpe,           // annualised
        double leverage          // sum of |w_i|
) {}
