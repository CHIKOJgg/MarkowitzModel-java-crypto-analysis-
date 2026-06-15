package org.example.engine;

import org.example.Defaults;
import org.example.execution.ExecutionModel;
import org.example.model.BacktestResult;
import org.example.portfolio.TurnoverConstrainedPortfolio;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Walk-forward backtest engine with comprehensive risk metrics.
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
 * <p>Computes: Sharpe, Sortino, Calmar, VaR(95%), CVaR(95%), MaxDD, benchmark curve.
 */
public class BacktestEngine {

    private final int            window;
    private final int            horizon;
    private final ExecutionModel execution;
    private final double         riskFreeRate;   // annualized, e.g. 0.04
    private final double         maxTurnover;    // per-step turnover cap (0 = disabled)

    public BacktestEngine(int window, int horizon, ExecutionModel execution,
                          double riskFreeRate, double maxTurnover) {
        this.window       = window;
        this.horizon      = horizon;
        this.execution    = execution;
        this.riskFreeRate = riskFreeRate;
        this.maxTurnover  = maxTurnover;
    }

    public BacktestEngine(int window, int horizon, ExecutionModel execution,
                          double riskFreeRate) {
        this(window, horizon, execution, riskFreeRate, 0.0);
    }

    public BacktestEngine(int window, int horizon, ExecutionModel execution) {
        this(window, horizon, execution, 0.0, 0.0);
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
        int totalSteps = totalRows - window - horizon + 1;

        for (int t = window; t <= totalRows - horizon; t++) {
            MatrixR064 train = sliceRows(returns, t - window, t);
            MatrixR064 test  = sliceRows(returns, t, t + horizon);

            // 1. Build portfolio weights from the training window
            List<BigDecimal> weights = strategy.build(train);

            // 2. Apply turnover constraint if enabled
            if (maxTurnover > 0 && prevWeights != null) {
                weights = TurnoverConstrainedPortfolio.constrain(prevWeights, weights, maxTurnover);
            }

            // 3. Apply execution / transaction costs
            double equityBefore = equity;
            equity = execution.applyCosts(equity, prevWeights, weights);
            totalFees += equityBefore - equity;

            // 4. Track turnover
            if (prevWeights != null) {
                totalTO += turnover(prevWeights, weights);
            }

            // 5. Compound daily PnL over the test window
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

        // Build benchmark: equal-weight buy-and-hold
        List<Double> benchmarkCurve = buildBenchmark(returns);

        return buildStats(strategy.getId(), equityCurve, totalFees,
                          steps > 0 ? totalTO / steps : 0.0,
                          benchmarkCurve);
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

    private MatrixR064 sliceRows(MatrixR064 m, int from, int to) {
        int rows = to - from;
        int cols = (int) m.countColumns();
        double[][] data = new double[rows][cols];
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                data[r][c] = m.get(from + r, c);
        return MatrixR064.FACTORY.rows(data);
    }

    /**
     * Build equal-weight buy-and-hold benchmark equity curve.
     */
    private List<Double> buildBenchmark(MatrixR064 returns) {
        int totalRows = (int) returns.countRows();
        int assets    = (int) returns.countColumns();
        double w = 1.0 / assets;

        List<Double> curve = new ArrayList<>();
        double equity = 1.0;
        for (int t = window; t <= totalRows - horizon; t++) {
            double pnl = 0.0;
            for (int d = t; d < t + horizon; d++) {
                double dayRet = 0.0;
                for (int a = 0; a < assets; a++) {
                    dayRet += returns.get(d, a) * w;
                }
                pnl = (1.0 + pnl) * (1.0 + dayRet) - 1.0;
            }
            equity *= (1.0 + pnl);
            curve.add(equity);
        }
        return curve;
    }

    /**
     * Compute summary statistics from the equity curve.
     */
    private BacktestResult buildStats(String id, List<Double> curve,
                                      double fees, double avgTO,
                                      List<Double> benchmarkCurve) {
        if (curve.isEmpty()) {
            return new BacktestResult(id, curve, 1.0, 0.0, 0.0, 0.0, 0.0,
                    0.0, 0.0, avgTO, fees, benchmarkCurve);
        }

        double finalEq = curve.get(curve.size() - 1);

        // Maximum drawdown
        double maxDD = 0.0;
        double peak  = curve.get(0);
        for (double v : curve) {
            if (v > peak) peak = v;
            maxDD = Math.max(maxDD, (peak - v) / peak);
        }

        // Return series (step-level)
        List<Double> rets = new ArrayList<>(curve.size() - 1);
        for (int i = 1; i < curve.size(); i++) {
            rets.add(curve.get(i) / curve.get(i - 1) - 1.0);
        }

        double mean = rets.stream().mapToDouble(x -> x).average().orElse(0.0);

        // Downside deviation (for Sortino)
        double rfPerStep = riskFreeRate * horizon / (double) Defaults.TRADING_DAYS_PER_YEAR;
        double downsideSqSum = 0.0;
        int downsideCount = 0;
        for (double r : rets) {
            double diff = r - rfPerStep;
            if (diff < 0) {
                downsideSqSum += diff * diff;
                downsideCount++;
            }
        }
        double downsideDev = downsideCount > 0
                ? Math.sqrt(downsideSqSum / downsideCount) : 1e-10;

        // Variance & Std
        double var = rets.stream().mapToDouble(x -> (x - mean) * (x - mean)).average().orElse(0.0);
        double std = Math.sqrt(var);

        // Annualized Sharpe ratio
        double sharpe = std > 1e-10
                ? (mean - rfPerStep) / std * Math.sqrt((double) Defaults.TRADING_DAYS_PER_YEAR / horizon) : 0.0;

        // Annualized Sortino ratio
        double sortino = downsideDev > 1e-10
                ? (mean - rfPerStep) / downsideDev * Math.sqrt((double) Defaults.TRADING_DAYS_PER_YEAR / horizon) : 0.0;

        // Calmar ratio (annualized return / max drawdown)
        double annualizedReturn = (finalEq - 1.0) * Defaults.TRADING_DAYS_PER_YEAR / (curve.size() * horizon);
        double calmar = maxDD > 1e-10 ? annualizedReturn / maxDD : 0.0;

        // VaR(95%) and CVaR(95%)
        List<Double> sorted = rets.stream().sorted().toList();
        int idx95 = (int) Math.floor(sorted.size() * 0.05);
        double var95 = idx95 < sorted.size() ? -sorted.get(idx95) : 0.0;

        double cvarSum = 0.0;
        int cvarCount = 0;
        for (int i = 0; i <= idx95 && i < sorted.size(); i++) {
            cvarSum += sorted.get(i);
            cvarCount++;
        }
        double cvar95 = cvarCount > 0 ? -cvarSum / cvarCount : 0.0;

        return new BacktestResult(id, curve, finalEq, maxDD, sharpe, sortino,
                calmar, var95, cvar95, avgTO, fees, benchmarkCurve);
    }
}
