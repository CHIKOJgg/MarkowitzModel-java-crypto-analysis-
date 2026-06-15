package org.example.engine;

import org.example.Defaults;
import org.example.execution.SimpleExecution;
import org.example.model.BacktestResult;
import org.example.util.MatrixUtils;
import org.ojalgo.matrix.MatrixR064;

import java.util.ArrayList;
import java.util.List;

/**
 * Grid-search parameter optimizer that finds the combination of
 * shrinkage, alpha, leverage, and maxLong that maximizes the Sharpe ratio.
 *
 * <p>Returns the top-N parameter combinations ranked by Sharpe.
 */
public class ParameterOptimizer {

    private final int window;
    private final int horizon;
    private final double rfRate;

    public ParameterOptimizer(int window, int horizon, double rfRate) {
        this.window  = window;
        this.horizon = horizon;
        this.rfRate  = rfRate;
    }

    public ParameterOptimizer() {
        this(Defaults.BACKTEST_WINDOW, Defaults.BACKTEST_HORIZON, Defaults.RISK_FREE_RATE);
    }

    /** A single parameter combination with its backtest result. */
    public record Result(double shrinkage, double alpha, double leverage, double maxLong,
                         double sharpe, double maxDD, double finalEquity) {}

    /**
     * Run a full grid search and return the top-N results by Sharpe.
     */
    public List<Result> optimize(MatrixR064 returns, int topN) {
        double[] shrinkages = {0.1, 0.3, 0.5, 0.7, 0.9};
        double[] alphas     = {0.01, 0.05, 0.10, 0.20, 0.30};
        double[] leverages  = {1.0, 1.3, 1.5, 2.0, 2.5};
        double[] maxLongs   = {0.10, 0.15, 0.20, 0.30, 0.50};

        List<Result> results = new ArrayList<>();
        var engine = new BacktestEngine(window, horizon, new SimpleExecution(), rfRate);

        for (double s : shrinkages) {
            for (double a : alphas) {
                for (double l : leverages) {
                    for (double ml : maxLongs) {
                        Strategy strat = buildStrategy(s, a, l, ml);
                        try {
                            BacktestResult r = engine.run(returns, strat, null);
                            if (!Double.isNaN(r.sharpe()) && !Double.isInfinite(r.sharpe())) {
                                results.add(new Result(s, a, l, ml,
                                        r.sharpe(), r.maxDrawdown(), r.finalEquity()));
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        results.sort((a, b) -> Double.compare(b.sharpe(), a.sharpe()));
        return results.subList(0, Math.min(topN, results.size()));
    }

    private static Strategy buildStrategy(double shrinkage, double alpha,
                                           double leverage, double maxLong) {
        String id = String.format("Opt(shr=%.2f,α=%.2f,lev=%.1f,ml=%.2f)",
                shrinkage, alpha, leverage, maxLong);
        return Strategy.builder(id,
                        new org.example.alpha.EWMAAlpha(alpha, Defaults.SHORT_PENALTY),
                        new org.example.portfolio.MarkowitzPortfolio(
                                maxLong, Defaults.MAX_SHORT, shrinkage, false, 0.0,
                                false, Defaults.EWMA_LAMBDA))
                .risk(new org.example.risk.PassthroughRisk())
                .leverage(leverage)
                .validate()
                .build();
    }
}
