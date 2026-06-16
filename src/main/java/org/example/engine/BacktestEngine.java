package org.example.engine;

import org.example.Defaults;
import org.example.execution.ExecutionModel;
import org.example.model.BacktestResult;
import org.example.portfolio.TurnoverConstrainedPortfolio;
import org.example.util.MatrixUtils;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Walk-forward backtest engine with regime-conditional analysis.
 *
 * <p>For each strategy it performs:
 * <ol>
 *   <li>Detect correlation regime for current training window</li>
 *   <li>Slice training window: returns[t-window, t)</li>
 *   <li>Build portfolio via {@link Strategy#build}</li>
 *   <li>Apply execution costs via {@link ExecutionModel}</li>
 *   <li>Simulate PnL on test window: returns[t, t+horizon)</li>
 *   <li>Compound equity</li>
 * </ol>
 *
 * <p>Computes: Sharpe, Sortino, Calmar, VaR(95%), CVaR(95%), MaxDD, benchmark curve, regime history.
 */
public class BacktestEngine {

    private final int            window;
    private final int            horizon;
    private final ExecutionModel execution;
    private final double         riskFreeRate;
    private final double         maxTurnover;
    private final int            regimeWindow;
    private final int            rebalanceFreq;

    public BacktestEngine(int window, int horizon, ExecutionModel execution,
                          double riskFreeRate, double maxTurnover, int rebalanceFreq) {
        this.window       = window;
        this.horizon      = horizon;
        this.execution    = execution;
        this.riskFreeRate = riskFreeRate;
        this.maxTurnover  = maxTurnover;
        this.rebalanceFreq = Math.max(1, rebalanceFreq);
        this.regimeWindow = Math.min(window, 30);
    }

    public BacktestEngine(int window, int horizon, ExecutionModel execution,
                          double riskFreeRate, double maxTurnover) {
        this(window, horizon, execution, riskFreeRate, maxTurnover, horizon);
    }

    public BacktestEngine(int window, int horizon, ExecutionModel execution,
                          double riskFreeRate) {
        this(window, horizon, execution, riskFreeRate, 0.0, horizon);
    }

    public BacktestEngine(int window, int horizon, ExecutionModel execution) {
        this(window, horizon, execution, 0.0, 0.0, horizon);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Run a single strategy and return its backtest statistics.
     * Produces a <b>daily</b> equity curve for accurate drawdown, VaR, CVaR, etc.
     */
    public BacktestResult run(MatrixR064 returns, Strategy strategy,
                              BiConsumer<String, String> progress) {

        List<Double>     equityCurve = new ArrayList<>();
        List<BigDecimal> prevWeights = null;
        List<String>     regimeHistory = new ArrayList<>();

        double equity    = 1.0;
        double totalFees = 0.0;
        double totalTO   = 0.0;
        int    steps     = 0;

        int totalRows  = (int) returns.countRows();
        int rawSteps = totalRows - window - horizon + 1;
        int totalSteps = Math.max(1, (rawSteps + rebalanceFreq - 1) / rebalanceFreq);

        // Pre-compute regime for the entire series
        List<String> allRegimes = MatrixUtils.correlationRegime(returns, regimeWindow);

        for (int t = window; t <= totalRows - horizon; t += rebalanceFreq) {
            int testEnd = Math.min(t + horizon, totalRows);
            MatrixR064 train = sliceRows(returns, t - window, t);

            // 1. Detect regime for current training window
            int regimeIdx = t - regimeWindow;
            String regime = (regimeIdx >= 0 && regimeIdx < allRegimes.size())
                    ? allRegimes.get(regimeIdx) : "NORMAL";

            // 2. Build portfolio weights from the training window
            List<BigDecimal> weights = strategy.build(train);

            // 3. Apply turnover constraint if enabled
            if (maxTurnover > 0 && prevWeights != null) {
                weights = TurnoverConstrainedPortfolio.constrain(prevWeights, weights, maxTurnover);
            }

            // 4. Apply execution / transaction costs
            double equityBefore = equity;
            equity = execution.applyCosts(equity, prevWeights, weights);
            totalFees += equityBefore - equity;

            // 5. Track turnover
            if (prevWeights != null) {
                totalTO += turnover(prevWeights, weights);
            }

            // 6. Simulate day by day over the test window (daily equity curve)
            int assets = (int) returns.countColumns();
            for (int d = t; d < testEnd; d++) {
                double dayRet = 0.0;
                for (int a = 0; a < assets; a++) {
                    dayRet += returns.get(d, a) * weights.get(a).doubleValue();
                }
                equity *= (1.0 + dayRet);
                equityCurve.add(equity);
                regimeHistory.add(regime);
            }

            prevWeights = weights;
            steps++;

            if (progress != null) {
                progress.accept(strategy.getId(),
                        String.format("[%s] step %d / %d  |  equity = %.4f  |  regime = %s",
                                strategy.getId(), steps, totalSteps, equity, regime));
            }
        }

        List<Double> benchmarkCurve = buildBenchmark(returns);

        return buildStats(strategy.getId(), equityCurve, totalFees,
                          steps > 0 ? totalTO / steps : 0.0,
                          benchmarkCurve, regimeHistory);
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

    /**
     * Run multiple strategies in parallel using the common ForkJoin pool.
     * Progress is accumulated and reported after all finish.
     */
    public Map<String, BacktestResult> runAllParallel(MatrixR064 returns,
                                                      Map<String, Strategy> strategies,
                                                      BiConsumer<String, String> progress) {
        Map<String, BacktestResult> results = new ConcurrentHashMap<>();
        strategies.entrySet().parallelStream().forEach(e -> {
            String id = e.getKey();
            if (progress != null) progress.accept(id, "Running: " + id);
            BacktestResult r = run(returns, e.getValue(), null);
            results.put(id, r);
        });
        return results;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

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
     * Daily equal-weight benchmark starting from the first test window day.
     * Length matches the strategy's daily equity curve.
     */
    private List<Double> buildBenchmark(MatrixR064 returns) {
        int totalRows = (int) returns.countRows();
        int assets    = (int) returns.countColumns();
        double w = 1.0 / assets;

        List<Double> curve = new ArrayList<>();
        double equity = 1.0;
        for (int t = window; t < totalRows; t++) {
            double dayRet = 0.0;
            for (int a = 0; a < assets; a++) {
                dayRet += returns.get(t, a) * w;
            }
            equity *= (1.0 + dayRet);
            curve.add(equity);
        }
        return curve;
    }

    private BacktestResult buildStats(String id, List<Double> curve,
                                      double fees, double avgTO,
                                      List<Double> benchmarkCurve,
                                      List<String> regimeHistory) {
        if (curve.isEmpty()) {
            return new BacktestResult(id, curve, 1.0, 0.0, 0.0, 0.0, 0.0,
                    0.0, 0.0, avgTO, fees, benchmarkCurve, regimeHistory);
        }

        double finalEq = curve.get(curve.size() - 1);

        // Max drawdown from daily equity curve (no longer misses intra-horizon moves)
        double maxDD = 0.0;
        double peak  = curve.get(0);
        for (double v : curve) {
            if (v > peak) peak = v;
            maxDD = Math.max(maxDD, (peak - v) / peak);
        }

        // Daily return series
        List<Double> rets = new ArrayList<>(curve.size() - 1);
        for (int i = 1; i < curve.size(); i++) {
            rets.add(curve.get(i) / curve.get(i - 1) - 1.0);
        }

        double mean = rets.stream().mapToDouble(x -> x).average().orElse(0.0);
        double rfPerDay = riskFreeRate / (double) Defaults.TRADING_DAYS_PER_YEAR;

        // Downside deviation (for Sortino)
        double downsideSqSum = 0.0;
        int downsideCount = 0;
        for (double r : rets) {
            double diff = r - rfPerDay;
            if (diff < 0) {
                downsideSqSum += diff * diff;
                downsideCount++;
            }
        }
        double downsideDev = downsideCount > 0
                ? Math.sqrt(downsideSqSum / downsideCount) : 1e-10;

        // Standard deviation
        double var = rets.stream().mapToDouble(x -> (x - mean) * (x - mean)).average().orElse(0.0);
        double std = Math.sqrt(var);

        // Sharpe & Sortino annualized from daily returns
        double annualFactor = Math.sqrt(Defaults.TRADING_DAYS_PER_YEAR);
        double sharpe = std > 1e-10
                ? (mean - rfPerDay) / std * annualFactor : 0.0;

        double sortino = downsideDev > 1e-10
                ? (mean - rfPerDay) / downsideDev * annualFactor : 0.0;

        // Geometric annualized return
        double annualizedReturn = Math.pow(finalEq,
                (double) Defaults.TRADING_DAYS_PER_YEAR / curve.size()) - 1.0;
        double calmar = maxDD > 1e-10 ? annualizedReturn / maxDD : 0.0;

        // VaR(95%) & CVaR(95%) from daily returns
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
                calmar, var95, cvar95, avgTO, fees, benchmarkCurve, regimeHistory);
    }
}
