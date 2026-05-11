package org.example.engine;

import org.example.execution.ExecutionModel;
import org.example.model.BacktestResult;
import org.example.util.MatrixUtils;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Walk-forward backtest engine.
 *
 * <p>For each strategy it performs:
 * <ol>
 *   <li>Slice training window:  returns[t-window, t)</li>
 *   <li>Build portfolio via {@link Strategy#build}</li>
 *   <li>Apply execution costs via {@link ExecutionModel}</li>
 *   <li>Simulate PnL on test window: returns[t, t+horizon)</li>
 *   <li>Compound equity</li>
 * </ol>
 */
public class BacktestEngine {

    private final int            window;
    private final int            horizon;
    private final ExecutionModel execution;

    public BacktestEngine(int window, int horizon, ExecutionModel execution) {
        this.window    = window;
        this.horizon   = horizon;
        this.execution = execution;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Run a single strategy and return its backtest statistics.
     *
     * @param returns  full returns matrix [days x assets]
     * @param strategy strategy to evaluate
     * @param progress optional step-level callback (strategyId, message); may be null
     */
    public BacktestResult run(MatrixR064 returns, Strategy strategy,
                              BiConsumer<String, String> progress) {

        List<Double>     equityCurve = new ArrayList<>();
        List<BigDecimal> prevWeights = null;

        double equity    = 1.0;
        double totalFees = 0.0;
        double totalTO   = 0.0;
        int    steps     = 0;

        int totalRows  = (int) returns.countRows();
        // Loop: t = window, window+1, ..., totalRows-horizon (inclusive)
        // => totalRows - window - horizon + 1 iterations total.
        int totalSteps = totalRows - window - horizon + 1;

        for (int t = window; t <= totalRows - horizon; t++) {
            // Use MatrixUtils.sliceRows for half-open [from, to) slicing.
            // Never use ojAlgo matrix.rows(a,b) for ranges: that is a varargs
            // overload selecting two specific indices, not a [a,b) range.
            MatrixR064 train = MatrixUtils.sliceRows(returns, t - window, t);
            MatrixR064 test  = MatrixUtils.sliceRows(returns, t, t + horizon);

            // 1. Build portfolio weights from the training window
            List<BigDecimal> weights = strategy.build(train);

            // 2. Apply execution / transaction costs
            double equityBefore = equity;
            equity = execution.applyCosts(equity, prevWeights, weights);
            totalFees += equityBefore - equity;

            // 3. Track turnover
            if (prevWeights != null) {
                totalTO += turnover(prevWeights, weights);
            }

            // 4. Compound daily PnL over the test window
            double pnl = simulate(test, weights);
            equity *= (1.0 + pnl);
            equityCurve.add(equity);

            prevWeights = weights;
            steps++;

            if (progress != null) {
                progress.accept(strategy.getId(),
                        String.format("[%s] step %d / %d  |  equity = %.4f",
                                strategy.getId(), steps, totalSteps, equity));
            }
        }

        return buildStats(strategy.getId(), equityCurve, totalFees,
                          steps > 0 ? totalTO / steps : 0.0);
    }

    /**
     * Run multiple strategies in sequence, returning one result per strategy.
     */
    public List<BacktestResult> runAll(MatrixR064 returns,
                                       List<Strategy> strategies,
                                       BiConsumer<String, String> progress) {
        List<BacktestResult> results = new ArrayList<>(strategies.size());
        for (Strategy s : strategies) {
            results.add(run(returns, s, progress));
        }
        return results;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Compound daily weighted returns over the test window.
     * Returns the total holding-period return as a decimal (0.05 = +5%).
     * Simple summation is only valid for a single day; compounding is required
     * for multi-day horizons.
     */
    private double simulate(MatrixR064 test, List<BigDecimal> weights) {
        int days   = (int) test.countRows();
        int assets = (int) test.countColumns();

        double growth = 1.0;
        for (int d = 0; d < days; d++) {
            double dayRet = 0.0;
            for (int a = 0; a < assets; a++) {
                dayRet += test.get(d, a) * weights.get(a).doubleValue();
            }
            growth *= (1.0 + dayRet);
        }
        return growth - 1.0;
    }

    /** Sum of absolute weight changes between two rebalancing dates. */
    private double turnover(List<BigDecimal> oldW, List<BigDecimal> newW) {
        double t = 0.0;
        for (int i = 0; i < oldW.size(); i++) {
            t += Math.abs(newW.get(i).doubleValue() - oldW.get(i).doubleValue());
        }
        return t;
    }

    /**
     * Compute summary statistics from the equity curve.
     * Sharpe is annualised with sqrt(365/horizon) because each equity-curve
     * step spans 'horizon' calendar days, not one day.
     */
    private BacktestResult buildStats(String id, List<Double> curve,
                                      double fees, double avgTO) {
        if (curve.isEmpty()) {
            return new BacktestResult(id, curve, 1.0, 0.0, 0.0, avgTO, fees);
        }

        double finalEq = curve.get(curve.size() - 1);

        // Maximum drawdown
        double maxDD = 0.0;
        double peak  = curve.get(0);
        for (double v : curve) {
            if (v > peak) peak = v;
            maxDD = Math.max(maxDD, (peak - v) / peak);
        }

        // Annualised Sharpe ratio
        List<Double> rets = new ArrayList<>(curve.size() - 1);
        for (int i = 1; i < curve.size(); i++) {
            rets.add(curve.get(i) / curve.get(i - 1) - 1.0);
        }
        double mean   = rets.stream().mapToDouble(x -> x).average().orElse(0.0);
        double var    = rets.stream().mapToDouble(x -> (x - mean) * (x - mean)).average().orElse(0.0);
        double std    = Math.sqrt(var);
        double sharpe = std > 1e-10 ? mean / std * Math.sqrt(365.0 / horizon) : 0.0;

        return new BacktestResult(id, curve, finalEq, maxDD, sharpe, avgTO, fees);
    }
}
