package org.example.engine;

import org.example.Defaults;
import org.example.alpha.EWMAAlpha;
import org.example.execution.ExecutionModel;
import org.example.execution.SimpleExecution;
import org.example.model.BacktestResult;
import org.example.portfolio.MarkowitzPortfolio;
import org.example.risk.PassthroughRisk;
import org.ojalgo.matrix.MatrixR064;

/**
 * Parameter sensitivity analysis engine.
 *
 * <p>Sweeps a single parameter across a range of values, running a backtest
 * for each value and recording the resulting Sharpe ratio, max drawdown,
 * and final equity.
 *
 * <p>Supported parameters: shrinkage, alpha (EWMA), leverage, maxLong.
 */
public class ParameterSensitivity {

    private static final int    DEFAULT_WINDOW  = Defaults.BACKTEST_WINDOW;
    private static final int    DEFAULT_HORIZON = Defaults.BACKTEST_HORIZON;
    private static final double DEFAULT_RF      = Defaults.RISK_FREE_RATE;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Sweep a parameter and return sensitivity results.
     *
     * @param returns   full returns matrix [days × assets]
     * @param paramName one of "shrinkage", "alpha", "leverage", "maxLong"
     * @param values    parameter values to test
     * @param window    training window (use Defaults.BACKTEST_WINDOW for default)
     * @param horizon   prediction horizon (use Defaults.BACKTEST_HORIZON for default)
     * @param exec      execution model
     * @param rfRate    risk-free rate (annual)
     * @return sensitivity results
     */
    public SensitivityResult sweep(MatrixR064 returns,
                                   String paramName,
                                   double[] values,
                                   int window,
                                   int horizon,
                                   ExecutionModel exec,
                                   double rfRate) {
        double[] sharpeRatios  = new double[values.length];
        double[] maxDrawdowns  = new double[values.length];
        double[] finalEquities = new double[values.length];

        BacktestEngine engine = new BacktestEngine(window, horizon, exec, rfRate);

        for (int i = 0; i < values.length; i++) {
            Strategy strategy = buildStrategy(paramName, values[i]);
            BacktestResult result = engine.run(returns, strategy, null);

            sharpeRatios[i]  = result.sharpe();
            maxDrawdowns[i]  = result.maxDrawdown();
            finalEquities[i] = result.finalEquity();
        }

        return new SensitivityResult(paramName, values, sharpeRatios,
                maxDrawdowns, finalEquities);
    }

    /**
     * Convenience overload with default window/horizon/rfRate.
     */
    public SensitivityResult sweep(MatrixR064 returns,
                                   String paramName,
                                   double[] values) {
        return sweep(returns, paramName, values,
                DEFAULT_WINDOW, DEFAULT_HORIZON,
                new SimpleExecution(), DEFAULT_RF);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Build a Markowitz+EWMA strategy with the given parameter value substituted.
     */
    private Strategy buildStrategy(String paramName, double value) {
        double shrinkage = Defaults.SHRINKAGE_LAMBDA;
        double ewmaAlpha = Defaults.EWMA_ALPHA;
        double maxLeverage = Defaults.MAX_LEVERAGE;
        double maxLong = Defaults.MAX_LONG;

        switch (paramName) {
            case "shrinkage" -> shrinkage = value;
            case "alpha"     -> ewmaAlpha = value;
            case "leverage"  -> maxLeverage = value;
            case "maxLong"   -> maxLong = value;
            default -> throw new IllegalArgumentException(
                    "Unknown parameter: " + paramName
                            + ". Supported: shrinkage, alpha, leverage, maxLong");
        }

        String id = String.format("Sweep(%s=%.4f)", paramName, value);
        return Strategy.builder(id,
                        new EWMAAlpha(ewmaAlpha, Defaults.SHORT_PENALTY),
                        new MarkowitzPortfolio(maxLong, Defaults.MAX_SHORT,
                                shrinkage, false, 0.0, false, Defaults.EWMA_LAMBDA))
                .risk(new PassthroughRisk())
                .leverage(maxLeverage)
                .validate()
                .build();
    }
}
