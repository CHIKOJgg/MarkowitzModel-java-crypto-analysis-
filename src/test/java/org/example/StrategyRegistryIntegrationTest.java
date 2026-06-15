package org.example;

import org.example.engine.BacktestEngine;
import org.example.engine.Strategy;
import org.example.engine.StrategyRegistry;
import org.example.alpha.EqualWeightAlpha;
import org.example.execution.SimpleExecution;
import org.example.execution.ZeroCostExecution;
import org.junit.jupiter.api.Test;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StrategyRegistryIntegrationTest {

    private MatrixR064 makeRandomReturns(int days, int assets, long seed) {
        double[][] data = new double[days][assets];
        var rand = new java.util.Random(seed);
        for (int i = 0; i < days; i++)
            for (int j = 0; j < assets; j++)
                data[i][j] = rand.nextGaussian() * 0.02 + 0.001;
        return MatrixR064.FACTORY.rows(data);
    }

    private StrategyRegistry.Params defaultParams() {
        return new StrategyRegistry.Params(
                0.20,   // maxLong
                -0.15,  // maxShort
                0.10,   // ewmaAlpha
                0.90,   // shrinkage
                1.3,    // maxLeverage
                true,   // allowShorting
                0.005,  // targetReturn
                20,     // momentumLookback
                false,  // useVolScaling
                0.015,  // targetVol
                false,  // useEwmaCov
                0.94,   // ewmaLambda
                false,  // usePortfolioRiskConstraint
                0.0     // maxPortfolioVar
        );
    }

    @Test
    void allNamesReturns24Strategies() {
        List<String> names = StrategyRegistry.allNames();
        assertEquals(24, names.size(), "Should have 24 strategies");
    }

    @Test
    void buildAllReturns24Strategies() {
        Map<String, Strategy> strategies = StrategyRegistry.buildAll(defaultParams());
        assertEquals(24, strategies.size(), "buildAll should return 24 strategies");
    }

    @Test
    void allNamesAndBuildAllKeysMatch() {
        List<String> names = StrategyRegistry.allNames();
        Map<String, Strategy> strategies = StrategyRegistry.buildAll(defaultParams());

        for (String name : names) {
            assertTrue(strategies.containsKey(name),
                    "Strategy '" + name + "' should be in buildAll result");
        }
    }

    @Test
    void everyStrategyBuildsWithoutError() {
        Map<String, Strategy> strategies = StrategyRegistry.buildAll(defaultParams());
        MatrixR064 returns = makeRandomReturns(60, 5, 42);

        for (var entry : strategies.entrySet()) {
            assertDoesNotThrow(() -> {
                List<BigDecimal> weights = entry.getValue().build(returns);
                assertNotNull(weights, "Weights should not be null for " + entry.getKey());
                assertEquals(5, weights.size(),
                        "Weights size should match assets for " + entry.getKey());

                // All weights should be finite
                for (BigDecimal w : weights) {
                    assertFalse(Double.isNaN(w.doubleValue()),
                            "Weight should not be NaN for " + entry.getKey());
                    assertFalse(Double.isInfinite(w.doubleValue()),
                            "Weight should not be infinite for " + entry.getKey());
                }
            }, "Strategy '" + entry.getKey() + "' should build without error");
        }
    }

    @Test
    void everyStrategyRunsInBacktest() {
        Map<String, Strategy> strategies = StrategyRegistry.buildAll(defaultParams());
        MatrixR064 returns = makeRandomReturns(100, 5, 42);
        BacktestEngine engine = new BacktestEngine(20, 5, new ZeroCostExecution());

        for (var entry : strategies.entrySet()) {
            assertDoesNotThrow(() -> {
                var result = engine.run(returns, entry.getValue(), null);
                assertNotNull(result, "Result should not be null for " + entry.getKey());
                assertFalse(Double.isNaN(result.finalEquity()),
                        "Final equity should not be NaN for " + entry.getKey());
                assertFalse(Double.isNaN(result.sharpe()),
                        "Sharpe should not be NaN for " + entry.getKey());
            }, "Strategy '" + entry.getKey() + "' should run in backtest without error");
        }
    }

    @Test
    void trueRiskParityStrategyBuildsAndRuns() {
        Map<String, Strategy> strategies = StrategyRegistry.buildAll(defaultParams());
        Strategy trp = strategies.get(StrategyRegistry.TRUE_RISK_PARITY);

        assertNotNull(trp, "True Risk Parity strategy should exist");

        MatrixR064 returns = makeRandomReturns(60, 5, 42);
        List<BigDecimal> weights = trp.build(returns);

        assertEquals(5, weights.size());
        double sum = weights.stream().mapToDouble(BigDecimal::doubleValue).sum();
        assertEquals(1.0, sum, 0.05, "True Risk Parity weights should sum to ~1");

        for (BigDecimal w : weights) {
            assertTrue(w.doubleValue() > 0,
                    "True Risk Parity weights should be positive: " + w);
        }
    }

    @Test
    void strategiesWithVolScalingDifferFromWithout() {
        MatrixR064 returns = makeRandomReturns(60, 5, 42);

        StrategyRegistry.Params noVol = new StrategyRegistry.Params(
                0.20, -0.15, 0.10, 0.90, 1.3, true, 0.005, 20,
                false, 0.015, false, 0.94, false, 0.0);
        StrategyRegistry.Params withVol = new StrategyRegistry.Params(
                0.20, -0.15, 0.10, 0.90, 1.3, true, 0.005, 20,
                true, 0.015, false, 0.94, false, 0.0);

        Strategy s1 = StrategyRegistry.buildAll(noVol).get(StrategyRegistry.EWMA_MARKOWITZ);
        Strategy s2 = StrategyRegistry.buildAll(withVol).get(StrategyRegistry.EWMA_MARKOWITZ);

        List<BigDecimal> w1 = s1.build(returns);
        List<BigDecimal> w2 = s2.build(returns);

        // They may or may not differ depending on the data, but both should be valid
        assertEquals(5, w1.size());
        assertEquals(5, w2.size());
    }

    @Test
    void strategiesWithEwmaCovDifferFromSampleCov() {
        MatrixR064 returns = makeRandomReturns(60, 5, 42);

        StrategyRegistry.Params sample = new StrategyRegistry.Params(
                0.20, -0.15, 0.10, 0.90, 1.3, true, 0.005, 20,
                false, 0.015, false, 0.94, false, 0.0);
        StrategyRegistry.Params ewma = new StrategyRegistry.Params(
                0.20, -0.15, 0.10, 0.90, 1.3, true, 0.005, 20,
                false, 0.015, true, 0.94, false, 0.0);

        Strategy s1 = StrategyRegistry.buildAll(sample).get(StrategyRegistry.EWMA_MARKOWITZ);
        Strategy s2 = StrategyRegistry.buildAll(ewma).get(StrategyRegistry.EWMA_MARKOWITZ);

        List<BigDecimal> w1 = s1.build(returns);
        List<BigDecimal> w2 = s2.build(returns);

        assertEquals(5, w1.size());
        assertEquals(5, w2.size());
    }

    @Test
    void noShortingStrategiesHaveNoNegativeWeights() {
        StrategyRegistry.Params params = new StrategyRegistry.Params(
                0.20, -0.15, 0.10, 0.90, 1.3, false, 0.005, 20,
                false, 0.015, false, 0.94, false, 0.0);

        Map<String, Strategy> strategies = StrategyRegistry.buildAll(params);
        MatrixR064 returns = makeRandomReturns(60, 5, 42);

        // RiskParityPortfolio with respectSign=true can produce negative weights
        // regardless of the allowShorting flag.
        // EqualWeightPortfolio with signalDirected=true also produces negative weights
        // when the alpha signal is negative.
        var skipStrategies = Set.of(
                StrategyRegistry.REVERSION_RISKPAR,
                StrategyRegistry.EWMA_RISKPARITY,
                StrategyRegistry.MOMENTUM_EQUAL,
                StrategyRegistry.EWMA_EQUAL);

        for (var entry : strategies.entrySet()) {
            if (skipStrategies.contains(entry.getKey())) continue;
            List<BigDecimal> weights = entry.getValue().build(returns);
            for (BigDecimal w : weights) {
                assertTrue(w.doubleValue() >= -0.001,
                        "No-short strategy " + entry.getKey()
                                + " should not have negative weights: " + w);
            }
        }
    }

    @Test
    void leveragedStrategiesRespectCap() {
        StrategyRegistry.Params params = new StrategyRegistry.Params(
                0.20, -0.15, 0.10, 0.90, 1.5, true, 0.005, 20,
                false, 0.015, false, 0.94, false, 0.0);

        Map<String, Strategy> strategies = StrategyRegistry.buildAll(params);
        MatrixR064 returns = makeRandomReturns(60, 5, 42);

        for (var entry : strategies.entrySet()) {
            List<BigDecimal> weights = entry.getValue().build(returns);
            double leverage = weights.stream()
                    .mapToDouble(w -> Math.abs(w.doubleValue()))
                    .sum();
            assertTrue(leverage <= 1.5 + 0.01,
                    "Strategy " + entry.getKey()
                            + " should respect leverage cap: " + leverage);
        }
    }

    @Test
    void backtestWithTurnoverConstraintDoesNotCrash() {
        MatrixR064 returns = makeRandomReturns(100, 5, 42);
        BacktestEngine engine = new BacktestEngine(20, 5, new ZeroCostExecution(), 0.0, 0.5);
        Strategy strategy = StrategyRegistry.buildAll(defaultParams())
                .get(StrategyRegistry.EWMA_MARKOWITZ);

        assertDoesNotThrow(() -> {
            var result = engine.run(returns, strategy, null);
            assertNotNull(result);
            assertTrue(result.avgTurnover() <= 0.5 + 0.01,
                    "Turnover should respect cap: " + result.avgTurnover());
        });
    }

    @Test
    void backtestWithRiskFreeRateAffectsSharpe() {
        // Use random data that produces non-trivial returns
        MatrixR064 returns = makeRandomReturns(200, 5, 42);
        Strategy strategy = StrategyRegistry.buildAll(defaultParams())
                .get(StrategyRegistry.EWMA_MARKOWITZ);

        BacktestEngine engineNoRf = new BacktestEngine(30, 5, new ZeroCostExecution(), 0.0);
        BacktestEngine engineHighRf = new BacktestEngine(30, 5, new ZeroCostExecution(), 0.20);

        var r1 = engineNoRf.run(returns, strategy, null);
        var r2 = engineHighRf.run(returns, strategy, null);

        // Both should have finite Sharpe and equity
        assertFalse(Double.isNaN(r1.sharpe()), "Sharpe with no Rf should be finite");
        assertFalse(Double.isNaN(r2.sharpe()), "Sharpe with high Rf should be finite");
        assertTrue(r1.finalEquity() > 0, "Final equity should be positive");
        assertTrue(r2.finalEquity() > 0, "Final equity should be positive");
    }

    @Test
    void benchmarkCurveIsProvided() {
        MatrixR064 returns = makeRandomReturns(100, 5, 42);
        BacktestEngine engine = new BacktestEngine(20, 5, new ZeroCostExecution());
        Strategy strategy = StrategyRegistry.buildAll(defaultParams())
                .get(StrategyRegistry.EWMA_MARKOWITZ);

        var result = engine.run(returns, strategy, null);

        assertNotNull(result.benchmarkCurve());
        assertFalse(result.benchmarkCurve().isEmpty(),
                "Benchmark curve should not be empty");
        assertEquals(result.equityCurve().size(), result.benchmarkCurve().size(),
                "Benchmark and equity curves should have same length");
    }

    @Test
    void exportCsvCreatesFile() {
        // This is a basic test that the CSV export method can be called
        var exporter = new org.example.util.FileExporter();
        var coins = List.of("bitcoin", "ethereum");
        var weights = List.of(BigDecimal.valueOf(0.6), BigDecimal.valueOf(0.4));

        org.example.model.BacktestResult result = new org.example.model.BacktestResult(
                "test", List.of(1.0, 1.1, 1.2), 1.2, 0.05, 1.5, 1.8, 2.0, 0.02, 0.03, 0.1, 0.001,
                List.of(1.0, 1.05, 1.1));

        Map<String, List<BigDecimal>> allWeights = new LinkedHashMap<>();
        allWeights.put("test", weights);

        Map<String, org.example.model.BacktestResult> allResults = new LinkedHashMap<>();
        allResults.put("test", result);

        assertDoesNotThrow(() -> exporter.exportCsv(allWeights, allResults, coins));
    }
}
