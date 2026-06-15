package org.example.engine;

import java.util.Random;

/**
 * Monte Carlo simulation engine for user-facing portfolio simulations.
 *
 * <p>Generates Geometric Brownian Motion (GBM) paths:
 * <pre>
 *   S(t+1) = S(t) × exp((μ − 0.5 σ²) + σ Z)
 * </pre>
 *
 * <p>Computes fan-chart percentiles and risk metrics (VaR, CVaR, probability of loss).
 */
public class MonteCarloSimulator {

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Run a Monte Carlo simulation.
     *
     * @param numPaths   number of simulated paths
     * @param horizon    number of future trading days
     * @param dailyMu    daily drift (annualized μ / 365)
     * @param dailySigma daily volatility (annualized σ / √365)
     * @param seed       random seed for reproducibility
     * @return simulation result with fan chart and risk metrics
     */
    public MonteCarloResult simulate(int numPaths, int horizon,
                                     double dailyMu, double dailySigma,
                                     long seed) {
        Random rng = new Random(seed);

        double[][] paths = new double[numPaths][horizon + 1];

        for (int p = 0; p < numPaths; p++) {
            paths[p][0] = 1.0; // start at S₀ = 1
            for (int t = 1; t <= horizon; t++) {
                double z = rng.nextGaussian();
                double drift = dailyMu - 0.5 * dailySigma * dailySigma;
                paths[p][t] = paths[p][t - 1] * Math.exp(drift + dailySigma * z);
            }
        }

        // Extract terminal returns
        double[] terminalReturns = new double[numPaths];
        for (int p = 0; p < numPaths; p++) {
            terminalReturns[p] = paths[p][horizon] - 1.0;
        }

        // Percentile fan-chart data
        double[] p5  = percentilePath(paths, 0.05);
        double[] p25 = percentilePath(paths, 0.25);
        double[] medianPath = percentilePath(paths, 0.50);
        double[] p75 = percentilePath(paths, 0.75);
        double[] p95 = percentilePath(paths, 0.95);

        // Risk metrics
        double expectedReturn = mean(terminalReturns);
        double expectedVol    = stdDev(terminalReturns, expectedReturn);

        // VaR95: 5th percentile of returns (positive = loss)
        double[] sorted = terminalReturns.clone();
        java.util.Arrays.sort(sorted);
        int idx95 = (int) Math.floor(sorted.length * 0.05);
        double var95 = idx95 < sorted.length ? -sorted[idx95] : 0.0;

        // CVaR95: average of returns in the 5% tail
        double cvarSum = 0.0;
        int cvarCount = 0;
        for (int i = 0; i <= idx95 && i < sorted.length; i++) {
            cvarSum += sorted[i];
            cvarCount++;
        }
        double cvar95 = cvarCount > 0 ? -cvarSum / cvarCount : 0.0;

        // Probability of loss
        int lossCount = 0;
        for (double r : terminalReturns) {
            if (r < 0) lossCount++;
        }
        double probLoss = (double) lossCount / numPaths;

        return new MonteCarloResult(medianPath, p5, p25, p75, p95,
                expectedReturn, expectedVol, var95, cvar95, probLoss,
                horizon, numPaths);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Compute the q-th percentile at each time step across all paths.
     */
    private double[] percentilePath(double[][] paths, double q) {
        int numPaths = paths.length;
        int horizon  = paths[0].length;
        double[] result = new double[horizon];

        for (int t = 0; t < horizon; t++) {
            double[] values = new double[numPaths];
            for (int p = 0; p < numPaths; p++) {
                values[p] = paths[p][t];
            }
            java.util.Arrays.sort(values);
            double index = q * (numPaths - 1);
            int lo = (int) Math.floor(index);
            int hi = (int) Math.ceil(index);
            if (lo == hi || hi >= numPaths) {
                result[t] = values[lo];
            } else {
                double frac = index - lo;
                result[t] = values[lo] * (1.0 - frac) + values[hi] * frac;
            }
        }
        return result;
    }

    private double mean(double[] data) {
        double sum = 0.0;
        for (double v : data) sum += v;
        return sum / data.length;
    }

    private double stdDev(double[] data, double mean) {
        double varSum = 0.0;
        for (double v : data) {
            double diff = v - mean;
            varSum += diff * diff;
        }
        return Math.sqrt(varSum / data.length);
    }
}
