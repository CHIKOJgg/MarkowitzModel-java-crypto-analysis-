package org.example.engine;

/**
 * Immutable result produced by {@link MonteCarloSimulator}.
 *
 * @param medianPath    50th-percentile equity path
 * @param p5            5th-percentile equity path (downside tail)
 * @param p25           25th-percentile equity path
 * @param p75           75th-percentile equity path
 * @param p95           95th-percentile equity path (upside tail)
 * @param expectedReturn arithmetic mean of terminal returns
 * @param expectedVol   standard deviation of terminal returns
 * @param var95         95 % Value-at-Risk (positive = loss)
 * @param cvar95        conditional VaR (expected shortfall) at 95 %
 * @param probLoss      fraction of paths with negative return
 * @param horizon       number of simulated days
 * @param numPaths      total number of Monte Carlo paths
 */
public record MonteCarloResult(
        double[] medianPath,
        double[] p5,
        double[] p25,
        double[] p75,
        double[] p95,
        double expectedReturn,
        double expectedVol,
        double var95,
        double cvar95,
        double probLoss,
        int horizon,
        int numPaths
) {}
