package org.example.engine;

import org.example.execution.ExecutionModel;
import org.example.model.BacktestResult;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Walk-forward backtest engine (version 2.0).
 *
 * <p>For each strategy it performs:
 * <ol>
 *   <li>Slice training window:  returns[t-window, t)</li>
 *   <li>Build portfolio via {@link Strategy#build}</li>
 *   <li>Apply execution costs via {@link ExecutionModel}</li>
 *   <li>Simulate PnL on test window: returns[t, t+horizon)</li>
 *   <li>Compound equity</li>
 * </ol>
 *
 * <p>All strategies share the same market data, making comparison fair.
 */
public class BacktestEngine {

    private final int            window;
    private final int            horizon;
    private final ExecutionModel execution;

    /**
     * @param window    training window length (days)
     * @param horizon   out-of-sample horizon  (days)
     * @param execution transaction-cost model
     */
    public BacktestEngine(int window, int horizon, ExecutionModel execution) {
        this.window    = window;
        this.horizon   = horizon;
        this.execution = execution;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Run a single strategy.
     *
     * @param returns  full returns matrix [days × assets]
     * @param strategy strategy to test
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

        int totalSteps = (int) returns.countRows() - window - horizon;

        for (int t = window; t < returns.countRows() - horizon; t++) {
            MatrixR064 train = returns.rows(t - window, t);
            MatrixR064 test  = returns.rows(t, t + horizon);

            // 1. Portfolio construction
            List<BigDecimal> weights = strategy.build(train);

            // 2. Execution costs
            double equityBefore = equity;
            equity = execution.applyCosts(equity, prevWeights, weights);
            totalFees += (equityBefore - equity);

            // 3. Turnover tracking
            if (prevWeights != null) {
                totalTO += turnover(prevWeights, weights);
            }

            // 4. Simulate PnL
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
                          steps > 0 ? totalTO / steps : 0);
    }

    /**
     * Run multiple strategies in sequence, returning one result per strategy.
     * Progress is reported via the optional callback.
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

    // ── Private ───────────────────────────────────────────────────────────────

    private double simulate(MatrixR064 test, List<BigDecimal> weights) {
        int days   = (int) test.countRows();
        int assets = (int) test.countColumns();
        double total = 0;
        for (int d = 0; d < days; d++) {
            for (int a = 0; a < assets; a++) {
                total += test.get(d, a) * weights.get(a).doubleValue();
            }
        }
        return total;
    }

    private double turnover(List<BigDecimal> oldW, List<BigDecimal> newW) {
        double t = 0;
        for (int i = 0; i < oldW.size(); i++) {
            t += Math.abs(newW.get(i).doubleValue() - oldW.get(i).doubleValue());
        }
        return t;
    }

    private BacktestResult buildStats(String id, List<Double> curve,
                                      double fees, double avgTO) {
        if (curve.isEmpty()) {
            return new BacktestResult(id, curve, 1.0, 0.0, 0.0, avgTO, fees);
        }

        double finalEq = curve.get(curve.size() - 1);

        // Max drawdown
        double maxDD = 0, peak = curve.get(0);
        for (double v : curve) {
            if (v > peak) peak = v;
            maxDD = Math.max(maxDD, (peak - v) / peak);
        }

        // Annualised Sharpe
        List<Double> rets = new ArrayList<>(curve.size() - 1);
        for (int i = 1; i < curve.size(); i++) {
            rets.add(curve.get(i) / curve.get(i - 1) - 1);
        }
        double mean = rets.stream().mapToDouble(x -> x).average().orElse(0);
        double std  = Math.sqrt(rets.stream()
                .mapToDouble(x -> (x - mean) * (x - mean)).average().orElse(0));
        double sharpe = std > 1e-10 ? mean / std * Math.sqrt(365) : 0;

        return new BacktestResult(id, curve, finalEq, maxDD, sharpe, avgTO, fees);
    }
}
