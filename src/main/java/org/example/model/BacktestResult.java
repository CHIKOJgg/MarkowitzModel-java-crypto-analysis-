package org.example.model;

import java.util.List;

/**
 * Immutable walk-forward backtest result.
 */
public record BacktestResult(
        String       strategyId,
        List<Double> equityCurve,
        double       finalEquity,
        double       maxDrawdown,
        double       sharpe,
        double       avgTurnover,
        double       totalFees
) {
    public List<Double> returnSeries() {
        if (equityCurve.size() < 2) return List.of();
        List<Double> rets = new java.util.ArrayList<>(equityCurve.size() - 1);
        for (int i = 1; i < equityCurve.size(); i++)
            rets.add(equityCurve.get(i) / equityCurve.get(i - 1) - 1);
        return rets;
    }

    public String summary() {
        return String.format(
                "%-40s  Eq=%.4f  MaxDD=%.1f%%  Sharpe=%.2f  AvgTO=%.2f%%  Fees=%.4f",
                strategyId, finalEquity, maxDrawdown * 100,
                sharpe, avgTurnover * 100, totalFees);
    }
}
