package org.example.model;

import java.util.List;

/**
 * Immutable summary of a rolling-window backtest.
 */
public record BacktestResult(
        List<Double> equityCurve,
        double finalEquity,
        double maxDrawdown,   // 0..1
        double sharpe         // annualised
) {}
