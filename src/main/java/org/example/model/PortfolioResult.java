package org.example.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable result of a single portfolio optimisation run.
 */
public record PortfolioResult(
        List<BigDecimal> weights,
        double expectedReturn,
        double volatility,
        double sharpe,
        double leverage
) {}
