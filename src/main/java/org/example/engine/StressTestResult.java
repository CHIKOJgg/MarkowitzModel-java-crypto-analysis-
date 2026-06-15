package org.example.engine;

import java.util.List;

/**
 * Immutable result produced by {@link StressTestEngine}.
 *
 * @param scenarioName   which scenario was applied
 * @param portfolioReturn  total portfolio return during the stress period
 * @param maxDrawdown    worst peak-to-trough decline
 * @param worstDayLoss   single-day loss magnitude (positive = loss)
 * @param equityPath     daily portfolio value during stress
 * @param var95          historical 95 % Value-at-Risk
 * @param cvar95         conditional VaR (expected shortfall) at 95 %
 */
public record StressTestResult(
        String scenarioName,
        double portfolioReturn,
        double maxDrawdown,
        double worstDayLoss,
        List<Double> equityPath,
        double var95,
        double cvar95
) {
    /**
     * One-line human-readable summary.
     */
    public String summary() {
        return String.format(
                "Stress[%s]  Ret=%.2f%%  MaxDD=%.2f%%  WorstDay=%.2f%%  VaR95=%.2f%%  CVaR95=%.2f%%",
                scenarioName, portfolioReturn * 100, maxDrawdown * 100,
                worstDayLoss * 100, var95 * 100, cvar95 * 100);
    }
}
