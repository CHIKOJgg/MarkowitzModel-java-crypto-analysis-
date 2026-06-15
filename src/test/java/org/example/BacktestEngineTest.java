package org.example;

import org.example.engine.BacktestEngine;
import org.example.engine.Strategy;
import org.example.alpha.EqualWeightAlpha;
import org.example.execution.SimpleExecution;
import org.example.execution.ZeroCostExecution;
import org.junit.jupiter.api.Test;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BacktestEngineTest {

    private MatrixR064 makeReturns(double[][] data) {
        return MatrixR064.FACTORY.rows(data);
    }

    private Strategy makeConstantStrategy(int n) {
        return Strategy.builder("test", new EqualWeightAlpha(n), new org.example.portfolio.EqualWeightPortfolio(false))
            .validate()
            .build();
    }

    @Test
    void backtestWithZeroReturnsProducesFlatEquity() {
        int days = 100, assets = 20;
        double[][] data = new double[days][assets];
        var returns = makeReturns(data);

        var engine = new BacktestEngine(10, 5, new ZeroCostExecution());
        var strategy = makeConstantStrategy(assets);
        var result = engine.run(returns, strategy, null);

        assertEquals(1.0, result.finalEquity(), 1e-10,
            "Zero returns -> equity stays at 1.0");
    }

    @Test
    void backtestWithPositiveReturnsGrowsEquity() {
        int days = 100, assets = 5;
        double[][] data = new double[days][assets];
        for (int i = 0; i < days; i++)
            for (int j = 0; j < assets; j++)
                data[i][j] = 0.01;

        var returns = makeReturns(data);
        var engine = new BacktestEngine(10, 5, new ZeroCostExecution());
        var strategy = makeConstantStrategy(assets);
        var result = engine.run(returns, strategy, null);

        assertTrue(result.finalEquity() > 1.0,
            "Consistent positive returns should grow equity");
    }

    @Test
    void backtestWithNegativeReturnsLosesEquity() {
        int days = 100, assets = 5;
        double[][] data = new double[days][assets];
        for (int i = 0; i < days; i++)
            for (int j = 0; j < assets; j++)
                data[i][j] = -0.01;

        var returns = makeReturns(data);
        var engine = new BacktestEngine(10, 5, new ZeroCostExecution());
        var strategy = makeConstantStrategy(assets);
        var result = engine.run(returns, strategy, null);

        assertTrue(result.finalEquity() < 1.0,
            "Consistent negative returns should lose equity");
    }

    @Test
    void executionCostsReduceEquity() {
        var exec = new SimpleExecution(0.01, 0.005);
        var oldW = List.of(BigDecimal.valueOf(0.5), BigDecimal.valueOf(-0.5));
        var newW = List.of(BigDecimal.valueOf(-0.5), BigDecimal.valueOf(0.5));
        double equity = 1.0;
        double result = exec.applyCosts(equity, oldW, newW);
        assertTrue(result < equity,
            "Transaction costs should reduce equity: " + result + " < " + equity);
    }

    @Test
    void maxDrawdownIsBetween0And1() {
        int days = 100, assets = 5;
        double[][] data = new double[days][assets];
        var rand = new java.util.Random(42);
        for (int i = 0; i < days; i++)
            for (int j = 0; j < assets; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        var engine = new BacktestEngine(10, 5, new ZeroCostExecution());
        var strategy = makeConstantStrategy(assets);
        var result = engine.run(returns, strategy, null);

        assertTrue(result.maxDrawdown() >= 0, "Max drawdown should be non-negative");
        assertTrue(result.maxDrawdown() <= 1.0, "Max drawdown should be at most 100%");
    }

    @Test
    void equityCurveHasCorrectLength() {
        int days = 100, assets = 5;
        double[][] data = new double[days][assets];
        var returns = makeReturns(data);

        var engine = new BacktestEngine(10, 5, new ZeroCostExecution());
        var strategy = makeConstantStrategy(assets);
        var result = engine.run(returns, strategy, null);

        assertEquals(86, result.equityCurve().size());
    }

    @Test
    void benchmarkCurveHasCorrectLength() {
        int days = 100, assets = 5;
        double[][] data = new double[days][assets];
        var returns = makeReturns(data);

        var engine = new BacktestEngine(10, 5, new ZeroCostExecution());
        var strategy = makeConstantStrategy(assets);
        var result = engine.run(returns, strategy, null);

        assertEquals(86, result.benchmarkCurve().size());
    }

    @Test
    void sortinoIsNonNegativeForPositiveReturns() {
        int days = 100, assets = 5;
        double[][] data = new double[days][assets];
        for (int i = 0; i < days; i++)
            for (int j = 0; j < assets; j++)
                data[i][j] = 0.01;

        var returns = makeReturns(data);
        var engine = new BacktestEngine(10, 5, new ZeroCostExecution());
        var strategy = makeConstantStrategy(assets);
        var result = engine.run(returns, strategy, null);

        assertTrue(result.sortino() >= 0, "Sortino should be non-negative for positive returns");
    }

    @Test
    void var95IsNonNegative() {
        int days = 100, assets = 5;
        double[][] data = new double[days][assets];
        var rand = new java.util.Random(42);
        for (int i = 0; i < days; i++)
            for (int j = 0; j < assets; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        var engine = new BacktestEngine(10, 5, new ZeroCostExecution());
        var strategy = makeConstantStrategy(assets);
        var result = engine.run(returns, strategy, null);

        assertTrue(result.var95() >= 0, "VaR95 should be non-negative");
        assertTrue(result.cvar95() >= 0, "CVaR95 should be non-negative");
        assertTrue(result.cvar95() >= result.var95(),
            "CVaR95 should be >= VaR95 (expected shortfall >= value at risk)");
    }

    @Test
    void riskFreeRateAffectsSharpe() {
        int days = 100, assets = 5;
        double[][] data = new double[days][assets];
        var rand = new java.util.Random(42);
        for (int i = 0; i < days; i++)
            for (int j = 0; j < assets; j++)
                data[i][j] = 0.005 + rand.nextGaussian() * 0.01;

        var returns = makeReturns(data);
        var engineNoRf = new BacktestEngine(10, 5, new ZeroCostExecution(), 0.0);
        var engineWithRf = new BacktestEngine(10, 5, new ZeroCostExecution(), 0.10);
        var strategy = makeConstantStrategy(assets);

        var resultNoRf = engineNoRf.run(returns, strategy, null);
        var resultWithRf = engineWithRf.run(returns, strategy, null);

        assertTrue(resultNoRf.sharpe() > resultWithRf.sharpe(),
            "Higher risk-free rate should reduce Sharpe: " + resultNoRf.sharpe() + " > " + resultWithRf.sharpe());
    }
}

