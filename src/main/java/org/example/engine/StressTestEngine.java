package org.example.engine;

import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Stress testing / scenario analysis engine.
 *
 * <p>Supports three modes:
 * <ol>
 *   <li><b>Historical scenario</b> — apply a predefined shock evenly across all assets</li>
 *   <li><b>Monte Carlo stress</b> — generate N random paths with configurable drift/shock</li>
 *   <li><b>What-if analysis</b> — apply a uniform shock and compute portfolio impact</li>
 * </ol>
 */
public class StressTestEngine {

    private static final long DEFAULT_SEED = 42L;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Run a single stress scenario on the given portfolio.
     *
     * @param weights  current portfolio weights (one per asset)
     * @param returns  historical daily returns [days × assets]
     * @param scenario the stress scenario to apply
     * @return stress test result with equity path and risk metrics
     */
    public StressTestResult runStressTest(List<BigDecimal> weights,
                                          MatrixR064 returns,
                                          StressScenario scenario) {
        int assets = (int) returns.countColumns();
        int days   = (int) returns.countRows();

        // Compute daily shock: distribute total shock evenly across duration
        double dailyShock = -scenario.shockMagnitude() / scenario.durationDays();

        // Build the equity path under stress
        List<Double> equityPath = new ArrayList<>();
        double equity = 1.0;
        double peak   = 1.0;
        double maxDD  = 0.0;
        double worstDay = 0.0;
        List<Double> dailyReturns = new ArrayList<>();

        for (int t = 0; t < days; t++) {
            double dayReturn = 0.0;
            for (int a = 0; a < assets; a++) {
                double assetReturn = returns.get(t, a);
                // Apply shock during the scenario window
                if (t < scenario.durationDays()) {
                    assetReturn += dailyShock;
                }
                dayReturn += assetReturn * weights.get(a).doubleValue();
            }
            equity *= (1.0 + dayReturn);
            equityPath.add(equity);
            dailyReturns.add(dayReturn);

            if (equity > peak) peak = equity;
            double dd = (peak - equity) / peak;
            if (dd > maxDD) maxDD = dd;
            if (dayReturn < worstDay) worstDay = dayReturn;
        }

        // Compute VaR95 and CVaR95 from the daily returns
        List<Double> sorted = dailyReturns.stream().sorted().toList();
        int idx95 = (int) Math.floor(sorted.size() * 0.05);
        double var95 = idx95 < sorted.size() ? -sorted.get(idx95) : 0.0;

        double cvarSum = 0.0;
        int cvarCount = 0;
        for (int i = 0; i <= idx95 && i < sorted.size(); i++) {
            cvarSum += sorted.get(i);
            cvarCount++;
        }
        double cvar95 = cvarCount > 0 ? -cvarSum / cvarCount : 0.0;

        double totalReturn = equity - 1.0;
        return new StressTestResult(scenario.name(), totalReturn, maxDD,
                -worstDay, equityPath, var95, cvar95);
    }

    /**
     * Monte Carlo stress test: generate {@code numSims} random return paths
     * with drift and a one-time shock, then aggregate across the portfolio.
     *
     * @param weights        portfolio weights
     * @param returns        historical returns [days × assets] (used to estimate drift/vol)
     * @param numSims        number of Monte Carlo simulations
     * @param horizon        number of future days to simulate
     * @param shockMagnitude fractional shock applied once at the start (e.g. 0.25 = −25 %)
     * @return aggregated stress result across all simulations
     */
    public StressTestResult runMonteCarloStress(List<BigDecimal> weights,
                                                MatrixR064 returns,
                                                int numSims,
                                                int horizon,
                                                double shockMagnitude) {
        Random rng = new Random(DEFAULT_SEED);
        int assets = (int) returns.countColumns();

        // Estimate per-asset drift and vol from history
        double[] mu    = new double[assets];
        double[] sigma = new double[assets];
        for (int a = 0; a < assets; a++) {
            double sum = 0.0;
            for (int t = 0; t < (int) returns.countRows(); t++) {
                sum += returns.get(t, a);
            }
            mu[a] = sum / returns.countRows();

            double varSum = 0.0;
            for (int t = 0; t < (int) returns.countRows(); t++) {
                double diff = returns.get(t, a) - mu[a];
                varSum += diff * diff;
            }
            sigma[a] = Math.sqrt(varSum / returns.countRows());
        }

        // Collect terminal returns across simulations
        List<Double> terminalReturns = new ArrayList<>(numSims);
        List<Double> allWorstDays = new ArrayList<>(numSims);

        for (int sim = 0; sim < numSims; sim++) {
            double equity = 1.0;
            double peak   = 1.0;
            double simMaxDD = 0.0;
            double simWorstDay = 0.0;

            for (int d = 0; d < horizon; d++) {
                double dayReturn = 0.0;
                for (int a = 0; a < assets; a++) {
                    double z = rng.nextGaussian();
                    double assetReturn = mu[a] + sigma[a] * z;

                    // Apply shock on the first day
                    if (d == 0) {
                        assetReturn -= shockMagnitude;
                    }

                    dayReturn += assetReturn * weights.get(a).doubleValue();
                }
                equity *= (1.0 + dayReturn);
                if (equity > peak) peak = equity;
                double dd = (peak - equity) / peak;
                if (dd > simMaxDD) simMaxDD = dd;
                if (dayReturn < simWorstDay) simWorstDay = dayReturn;
            }
            terminalReturns.add(equity - 1.0);
            allWorstDays.add(simWorstDay);
        }

        // Sort terminal returns
        List<Double> sorted = terminalReturns.stream().sorted().toList();
        int idx95 = (int) Math.floor(sorted.size() * 0.05);
        double var95 = idx95 < sorted.size() ? -sorted.get(idx95) : 0.0;

        double cvarSum = 0.0;
        int cvarCount = 0;
        for (int i = 0; i <= idx95 && i < sorted.size(); i++) {
            cvarSum += sorted.get(i);
            cvarCount++;
        }
        double cvar95 = cvarCount > 0 ? -cvarSum / cvarCount : 0.0;

        double avgReturn = terminalReturns.stream().mapToDouble(d -> d).average().orElse(0.0);
        double avgWorstDay = allWorstDays.stream().mapToDouble(d -> d).average().orElse(0.0);

        // Use median equity path as the representative path
        return new StressTestResult("Monte Carlo Stress (" + numSims + " sims)",
                avgReturn, 0.0, -avgWorstDay, List.of(), var95, cvar95);
    }

    /**
     * What-if analysis: apply a uniform shock to all assets and compute portfolio impact.
     *
     * @param weights  portfolio weights
     * @param returns  historical returns matrix (used as baseline)
     * @param shock    uniform fractional shock (e.g. 0.20 = −20 %)
     * @return stress result for the single-day what-if shock
     */
    public StressTestResult whatIf(List<BigDecimal> weights,
                                   MatrixR064 returns,
                                   double shock) {
        int assets = (int) returns.countColumns();
        int days   = (int) returns.countRows();

        List<Double> equityPath = new ArrayList<>();
        double equity = 1.0;
        double peak   = 1.0;
        double maxDD  = 0.0;
        double worstDay = 0.0;
        List<Double> dailyReturns = new ArrayList<>();

        for (int t = 0; t < days; t++) {
            double dayReturn = 0.0;
            for (int a = 0; a < assets; a++) {
                double assetReturn = returns.get(t, a);
                // Apply uniform shock on day 0
                if (t == 0) {
                    assetReturn -= shock;
                }
                dayReturn += assetReturn * weights.get(a).doubleValue();
            }
            equity *= (1.0 + dayReturn);
            equityPath.add(equity);
            dailyReturns.add(dayReturn);

            if (equity > peak) peak = equity;
            double dd = (peak - equity) / peak;
            if (dd > maxDD) maxDD = dd;
            if (dayReturn < worstDay) worstDay = dayReturn;
        }

        List<Double> sorted = dailyReturns.stream().sorted().toList();
        int idx95 = (int) Math.floor(sorted.size() * 0.05);
        double var95 = idx95 < sorted.size() ? -sorted.get(idx95) : 0.0;

        double cvarSum = 0.0;
        int cvarCount = 0;
        for (int i = 0; i <= idx95 && i < sorted.size(); i++) {
            cvarSum += sorted.get(i);
            cvarCount++;
        }
        double cvar95 = cvarCount > 0 ? -cvarSum / cvarCount : 0.0;

        return new StressTestResult("What-If (" + String.format("%.0f", shock * 100) + "%)",
                equity - 1.0, maxDD, -worstDay, equityPath, var95, cvar95);
    }
}
