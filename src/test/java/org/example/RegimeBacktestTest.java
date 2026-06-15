package org.example;

import org.example.alpha.BollingerBandAlpha;
import org.example.alpha.MACDAlpha;
import org.example.engine.BacktestEngine;
import org.example.engine.Strategy;
import org.example.model.BacktestResult;
import org.example.portfolio.EqualWeightPortfolio;
import org.example.portfolio.HierarchicalRiskParityPortfolio;
import org.junit.jupiter.api.Test;
import org.ojalgo.matrix.MatrixR064;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RegimeBacktestTest {

    private MatrixR064 makeReturns(double[][] data) {
        return MatrixR064.FACTORY.rows(data);
    }

    private MatrixR064 randomReturns(int rows, int cols, long seed) {
        double[][] data = new double[rows][cols];
        var rand = new java.util.Random(seed);
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                data[i][j] = rand.nextGaussian() * 0.02;
        return makeReturns(data);
    }

    @Test
    void backtestResultHasRegimeHistory() {
        var returns = randomReturns(200, 3, 42);
        var strategy = Strategy.builder("eq", new MACDAlpha(),
                new EqualWeightPortfolio()).build();
        var engine = new BacktestEngine(60, 7,
                new org.example.execution.SimpleExecution());

        var result = engine.run(returns, strategy, null);

        assertNotNull(result.regimeHistory(),
                "BacktestResult should have regime history");
    }

    @Test
    void regimeHistoryIsNonEmpty() {
        var returns = randomReturns(200, 3, 42);
        var strategy = Strategy.builder("eq", new MACDAlpha(),
                new EqualWeightPortfolio()).build();
        var engine = new BacktestEngine(60, 7,
                new org.example.execution.SimpleExecution());

        var result = engine.run(returns, strategy, null);

        assertFalse(result.regimeHistory().isEmpty(),
                "Regime history should be non-empty when backtest runs");
    }

    @Test
    void regimeHistoryEntriesAreValid() {
        var returns = randomReturns(200, 3, 42);
        var strategy = Strategy.builder("eq", new MACDAlpha(),
                new EqualWeightPortfolio()).build();
        var engine = new BacktestEngine(60, 7,
                new org.example.execution.SimpleExecution());

        var result = engine.run(returns, strategy, null);

        Set<String> validRegimes = Set.of("HIGH_CORR", "NORMAL", "LOW_CORR");
        for (String regime : result.regimeHistory()) {
            assertTrue(validRegimes.contains(regime),
                    "Regime should be one of HIGH_CORR, NORMAL, LOW_CORR: " + regime);
        }
    }

    @Test
    void hrpStrategyProducesValidWeights() {
        var returns = randomReturns(100, 3, 42);
        var strategy = Strategy.builder("hrp", new MACDAlpha(),
                new HierarchicalRiskParityPortfolio()).build();

        // Just verify build doesn't throw and returns valid weights
        var weights = strategy.build(returns);

        assertNotNull(weights);
        assertEquals(3, weights.size());
        double sum = weights.stream().mapToDouble(w -> w.doubleValue()).sum();
        assertEquals(1.0, sum, 1e-6, "HRP weights should sum to ~1");
    }

    @Test
    void macdStrategyCompilesAndRuns() {
        var returns = randomReturns(200, 3, 42);
        var strategy = Strategy.builder("macd-eq", new MACDAlpha(),
                new EqualWeightPortfolio()).build();
        var engine = new BacktestEngine(60, 7,
                new org.example.execution.SimpleExecution());

        var result = engine.run(returns, strategy, null);

        assertNotNull(result);
        assertFalse(result.equityCurve().isEmpty());
        assertTrue(result.finalEquity() > 0);
    }

    @Test
    void bollingerStrategyCompilesAndRuns() {
        var returns = randomReturns(200, 3, 42);
        var strategy = Strategy.builder("boll-eq", new BollingerBandAlpha(),
                new EqualWeightPortfolio()).build();
        var engine = new BacktestEngine(60, 7,
                new org.example.execution.SimpleExecution());

        var result = engine.run(returns, strategy, null);

        assertNotNull(result);
        assertFalse(result.equityCurve().isEmpty());
        assertTrue(result.finalEquity() > 0);
    }

    @Test
    void regimeHistoryLengthMatchesEquityCurveLength() {
        var returns = randomReturns(200, 3, 42);
        var strategy = Strategy.builder("eq", new MACDAlpha(),
                new EqualWeightPortfolio()).build();
        var engine = new BacktestEngine(60, 7,
                new org.example.execution.SimpleExecution());

        var result = engine.run(returns, strategy, null);

        assertEquals(result.equityCurve().size(), result.regimeHistory().size(),
                "Regime history length should match equity curve length");
    }
}
