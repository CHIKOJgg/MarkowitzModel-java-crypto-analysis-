package org.example;

import org.example.engine.BacktestEngine;
import org.example.engine.Strategy;
import org.example.engine.StrategyRegistry;
import org.example.execution.ZeroCostExecution;
import org.junit.jupiter.api.Test;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AllStrategiesVerificationTest {

    private MatrixR064 makeRandomReturns(int days, int assets, long seed) {
        double[][] data = new double[days][assets];
        var rand = new java.util.Random(seed);
        for (int i = 0; i < days; i++)
            for (int j = 0; j < assets; j++)
                data[i][j] = rand.nextGaussian() * 0.02 + 0.001;
        return MatrixR064.FACTORY.rows(data);
    }

    private StrategyRegistry.Params params() {
        return new StrategyRegistry.Params(
                0.20, -0.15, 0.10, 0.90, 1.3, true, 0.005, 20,
                false, 0.015, false, 0.94, false, 0.0
        );
    }

    @Test
    void all24StrategiesBuildWithoutError() {
        Map<String, Strategy> strategies = StrategyRegistry.buildAll(params());
        assertEquals(24, strategies.size());
        MatrixR064 returns = makeRandomReturns(60, 5, 42);

        for (var entry : strategies.entrySet()) {
            assertDoesNotThrow(() -> {
                List<BigDecimal> weights = entry.getValue().build(returns);
                assertNotNull(weights, "Weights should not be null for " + entry.getKey());
                assertEquals(5, weights.size(), "Weights size should match assets for " + entry.getKey());
                for (BigDecimal w : weights) {
                    assertFalse(Double.isNaN(w.doubleValue()), "Weight should not be NaN for " + entry.getKey());
                    assertFalse(Double.isInfinite(w.doubleValue()), "Weight should not be infinite for " + entry.getKey());
                }
            }, "Strategy '" + entry.getKey() + "' should build without error");
        }
    }

    @Test
    void all24StrategiesRunBacktestWithoutError() {
        Map<String, Strategy> strategies = StrategyRegistry.buildAll(params());
        MatrixR064 returns = makeRandomReturns(100, 5, 42);
        BacktestEngine engine = new BacktestEngine(20, 5, new ZeroCostExecution());

        for (var entry : strategies.entrySet()) {
            assertDoesNotThrow(() -> {
                var result = engine.run(returns, entry.getValue(), null);
                assertNotNull(result, "Result should not be null for " + entry.getKey());
                assertTrue(result.finalEquity() > 0, "Final equity should be > 0 for " + entry.getKey());
                assertFalse(Double.isNaN(result.sharpe()), "Sharpe should not be NaN for " + entry.getKey());
                assertFalse(Double.isInfinite(result.sharpe()), "Sharpe should be finite for " + entry.getKey());
                assertTrue(result.maxDrawdown() >= 0, "MaxDD should be >= 0 for " + entry.getKey());
            }, "Strategy '" + entry.getKey() + "' should run backtest without error");
        }
    }

    @Test
    void all24HavePositiveFinalEquity() {
        Map<String, Strategy> strategies = StrategyRegistry.buildAll(params());
        MatrixR064 returns = makeRandomReturns(100, 5, 42);
        BacktestEngine engine = new BacktestEngine(20, 5, new ZeroCostExecution());

        for (var entry : strategies.entrySet()) {
            var result = engine.run(returns, entry.getValue(), null);
            assertNotNull(result);
            double fe = result.finalEquity();
            assertFalse(Double.isNaN(fe), "finalEquity should not be NaN for " + entry.getKey());
            assertTrue(fe > 0, "finalEquity should be > 0 for " + entry.getKey() + " but was " + fe);
        }
    }

    @Test
    void all24HaveFiniteSharpe() {
        Map<String, Strategy> strategies = StrategyRegistry.buildAll(params());
        MatrixR064 returns = makeRandomReturns(100, 5, 42);
        BacktestEngine engine = new BacktestEngine(20, 5, new ZeroCostExecution());

        for (var entry : strategies.entrySet()) {
            var result = engine.run(returns, entry.getValue(), null);
            assertNotNull(result);
            double s = result.sharpe();
            assertFalse(Double.isNaN(s), "Sharpe should not be NaN for " + entry.getKey());
            assertFalse(Double.isInfinite(s), "Sharpe should be finite for " + entry.getKey());
        }
    }

    @Test
    void all24HaveRegimeHistory() {
        Map<String, Strategy> strategies = StrategyRegistry.buildAll(params());
        MatrixR064 returns = makeRandomReturns(100, 5, 42);
        BacktestEngine engine = new BacktestEngine(20, 5, new ZeroCostExecution());

        for (var entry : strategies.entrySet()) {
            var result = engine.run(returns, entry.getValue(), null);
            assertNotNull(result);
            List<String> rh = result.regimeHistory();
            assertNotNull(rh, "regimeHistory should not be null for " + entry.getKey());
            assertFalse(rh.isEmpty(), "regimeHistory should not be empty for " + entry.getKey());
        }
    }

    @Test
    void new10StrategiesAlsoWork() {
        var newNames = List.of(
                StrategyRegistry.HRP_MOMENTUM,
                StrategyRegistry.MACD_MARKOWITZ,
                StrategyRegistry.BOLLINGER_MOMENTUM,
                StrategyRegistry.VOLUME_EWMA,
                StrategyRegistry.REGIME_VOL_MOMENTUM,
                StrategyRegistry.MDR_MOMENTUM,
                StrategyRegistry.ENSEMBLE_MARKOWITZ,
                StrategyRegistry.VAR_SCALED_MOMENTUM,
                StrategyRegistry.DDOWN_PROTECTED_EWMA,
                StrategyRegistry.SEASONALITY_EWMA
        );

        Map<String, Strategy> strategies = StrategyRegistry.buildAll(params());

        MatrixR064 buildReturns = makeRandomReturns(60, 5, 42);
        MatrixR064 btReturns = makeRandomReturns(100, 5, 42);
        BacktestEngine engine = new BacktestEngine(20, 5, new ZeroCostExecution());

        for (String name : newNames) {
            Strategy s = strategies.get(name);
            assertNotNull(s, "Strategy '" + name + "' should exist");

            // Build test
            List<BigDecimal> weights = assertDoesNotThrow(() -> s.build(buildReturns),
                    "Strategy '" + name + "' should build");
            assertNotNull(weights);
            assertEquals(5, weights.size());
            for (BigDecimal w : weights) {
                assertFalse(Double.isNaN(w.doubleValue()), "Weight should not be NaN for " + name);
                assertFalse(Double.isInfinite(w.doubleValue()), "Weight should not be infinite for " + name);
            }

            // Backtest test
            var result = assertDoesNotThrow(() -> engine.run(btReturns, s, null),
                    "Strategy '" + name + "' should run backtest");
            assertNotNull(result);
            assertTrue(result.finalEquity() > 0, "Final equity > 0 for " + name);
            assertFalse(Double.isNaN(result.sharpe()), "Sharpe not NaN for " + name);
            assertFalse(Double.isInfinite(result.sharpe()), "Sharpe finite for " + name);
        }
    }
}
