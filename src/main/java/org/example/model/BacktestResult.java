package org.example.model;

import java.util.List;

/**
 * Immutable walk-forward backtest result with comprehensive risk metrics
 * and regime history.
 */
public record BacktestResult(
        String       strategyId,
        List<Double> equityCurve,
        double       finalEquity,
        double       maxDrawdown,
        double       sharpe,
        double       sortino,
        double       calmar,
        double       var95,
        double       cvar95,
        double       avgTurnover,
        double       totalFees,
        List<Double> benchmarkCurve,
        List<String> regimeHistory
) {
    /** Backward-compatible constructor without regime history. */
    public BacktestResult(String strategyId, List<Double> equityCurve,
                          double finalEquity, double maxDrawdown,
                          double sharpe, double sortino, double calmar,
                          double var95, double cvar95, double avgTurnover,
                          double totalFees, List<Double> benchmarkCurve) {
        this(strategyId, equityCurve, finalEquity, maxDrawdown, sharpe,
             sortino, calmar, var95, cvar95, avgTurnover, totalFees,
             benchmarkCurve, List.of());
    }

    public List<Double> returnSeries() {
        if (equityCurve.size() < 2) return List.of();
        List<Double> rets = new java.util.ArrayList<>(equityCurve.size() - 1);
        for (int i = 1; i < equityCurve.size(); i++)
            rets.add(equityCurve.get(i) / equityCurve.get(i - 1) - 1);
        return rets;
    }

    public String summary() {
        return String.format(
                "%-40s  Eq=%.4f  MaxDD=%.1f%%  Sharpe=%.2f  Sortino=%.2f  Calmar=%.2f  VaR95=%.2f%%  CVaR95=%.2f%%  AvgTO=%.2f%%  Fees=%.4f",
                strategyId, finalEquity, maxDrawdown * 100,
                sharpe, sortino, calmar, var95 * 100, cvar95 * 100,
                avgTurnover * 100, totalFees);
    }

    public long regimeCount(String regime) {
        return regimeHistory.stream().filter(r -> r.equals(regime)).count();
    }
}
