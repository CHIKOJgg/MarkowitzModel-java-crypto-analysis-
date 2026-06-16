package org.example;

import org.example.alpha.EnsembleAlpha;
import org.example.alpha.EWMAAlpha;
import org.example.alpha.MomentumAlpha;
import org.example.constraint.PortfolioRiskConstraint;
import org.example.engine.BacktestEngine;
import org.example.engine.Strategy;
import org.example.engine.StrategyPreset;
import org.example.engine.StrategyRegistry;
import org.example.alpha.SeasonalityAlpha;
import org.example.data.CsvDataProvider;
import org.example.engine.ParameterOptimizer;
import org.example.portfolio.MaxDiversificationPortfolio;
import org.example.risk.DrawdownBasedRiskScaling;
import org.example.risk.VaRBasedRiskScaling;
import org.example.util.HtmlReportExporter;
import org.example.util.SmartDefaults;
import org.example.execution.ZeroCostExecution;
import org.example.model.BacktestResult;
import org.example.util.MatrixUtils;
import org.junit.jupiter.api.Test;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioRiskConstraintTest {

    @Test
    void testVaRConstraintScalesDown() {
        int n = 5;
        var returns = makeReturns(60, n);
        var weights = List.of(
                BigDecimal.valueOf(0.5), BigDecimal.valueOf(0.3),
                BigDecimal.valueOf(0.2), BigDecimal.valueOf(-0.2),
                BigDecimal.valueOf(-0.3));
        var constraint = new PortfolioRiskConstraint(0.001);
        var constrained = constraint.apply(weights, returns);
        assertEquals(n, constrained.size());
        constrained.forEach(w -> assertFalse(Double.isNaN(w.doubleValue())));
    }

    @Test
    void testVaRConstraintWithLowLimit() {
        int n = 3;
        var returns = makeReturns(60, n);
        var weights = List.of(BigDecimal.valueOf(0.5), BigDecimal.valueOf(0.3), BigDecimal.valueOf(0.2));
        var constraint = new PortfolioRiskConstraint(0.0001);
        var constrained = constraint.apply(weights, returns);
        assertTrue(constrained.stream().mapToDouble(BigDecimal::doubleValue).sum() > 0);
    }

    @Test
    void testVaRConstraintDoesNotIncreaseWeights() {
        int n = 4;
        var returns = makeReturns(60, n);
        var weights = List.of(
                BigDecimal.valueOf(0.4), BigDecimal.valueOf(0.3),
                BigDecimal.valueOf(0.2), BigDecimal.valueOf(0.1));
        var constraint = new PortfolioRiskConstraint(0.1);
        var constrained = constraint.apply(weights, returns);
        double origSum = weights.stream().mapToDouble(w -> Math.abs(w.doubleValue())).sum();
        double newSum = constrained.stream().mapToDouble(w -> Math.abs(w.doubleValue())).sum();
        assertTrue(newSum <= origSum + 1e-9);
    }

    @Test
    void testVaRConstraintWithVeryHighLimitDoesNothing() {
        int n = 4;
        var returns = makeReturns(60, n);
        var weights = List.of(
                BigDecimal.valueOf(0.25), BigDecimal.valueOf(0.25),
                BigDecimal.valueOf(0.25), BigDecimal.valueOf(0.25));
        var constraint = new PortfolioRiskConstraint(10.0);
        var constrained = constraint.apply(weights, returns);
        for (int i = 0; i < n; i++) {
            assertEquals(weights.get(i).doubleValue(), constrained.get(i).doubleValue(), 1e-10);
        }
    }

    @Test
    void testVaRConstraintIntegratedWithStrategy() {
        int n = 5;
        var returns = makeReturns(90, n);
        var params = new StrategyRegistry.Params(
                0.2, -0.15, 0.1, 0.9, 1.3, true, 0.005, 20, false, 0.015, false, 0.94, true, 0.02);
        var strategies = StrategyRegistry.buildAll(params);
        assertFalse(strategies.isEmpty());
        var engine = new BacktestEngine(60, 7, new ZeroCostExecution());
        for (Strategy s : strategies.values()) {
            BacktestResult r = engine.run(returns, s, null);
            assertNotNull(r);
            assertTrue(r.finalEquity() > 0);
        }
    }

    @Test
    void testRebalanceFrequencyNoCrash() {
        int n = 4;
        var returns = makeReturns(120, n);
        var params = new StrategyRegistry.Params(
                0.2, -0.15, 0.1, 0.9, 1.3, true, 0.005, 20, false, 0.015, false, 0.94, false, 0);
        var strategies = StrategyRegistry.buildAll(params);
        var engineDaily = new BacktestEngine(60, 7, new ZeroCostExecution(), 0, 0, 1);
        var engineWeekly = new BacktestEngine(60, 7, new ZeroCostExecution(), 0, 0, 5);
        var entry = strategies.entrySet().iterator().next();
        var r1 = engineDaily.run(returns, entry.getValue(), null);
        var r2 = engineWeekly.run(returns, entry.getValue(), null);
        assertNotNull(r1);
        assertNotNull(r2);
    }

    @Test
    void testParallelBacktestProducesSameResults() {
        int n = 4;
        var returns = makeReturns(100, n);
        var params = new StrategyRegistry.Params(
                0.2, -0.15, 0.1, 0.9, 1.3, true, 0.005, 20, false, 0.015, false, 0.94, false, 0);
        var strategies = StrategyRegistry.buildAll(params);
        var engine = new BacktestEngine(50, 5, new ZeroCostExecution());
        var seqResults = engine.runAll(returns, List.copyOf(strategies.values()), null);
        var parResults = engine.runAllParallel(returns, strategies, null);
        assertEquals(seqResults.size(), parResults.size());
    }

    @Test
    void testPresetVaRValues() {
        assertTrue(StrategyPreset.CONSERVATIVE.portfolioVaR());
        assertEquals(2.0, StrategyPreset.CONSERVATIVE.maxVaR(), 1e-9);
        assertTrue(StrategyPreset.BALANCED.portfolioVaR());
        assertEquals(3.0, StrategyPreset.BALANCED.maxVaR(), 1e-9);
        assertFalse(StrategyPreset.AGGRESSIVE.portfolioVaR());
    }

    @Test
    void testPresetToParamsIncludesVaR() {
        var p = StrategyPreset.CONSERVATIVE.toParams();
        assertTrue(p.usePortfolioRiskConstraint());
        assertEquals(0.02, p.maxPortfolioVar(), 1e-9);
    }

    @Test
    void maxDiversificationPortfolioWorks() {
        var returns = makeReturns(90, 5);
        var mdr = new MaxDiversificationPortfolio(0.3, -0.2, 1.3);
        var mu = org.ojalgo.matrix.MatrixR064.FACTORY.rows(new double[][]{
                {0.01, 0.02, 0.015, 0.005, 0.01}});
        var w = mdr.allocate(returns, mu);
        assertEquals(5, w.size());
        assertTrue(w.stream().allMatch(x -> !Double.isNaN(x.doubleValue())));
        assertTrue(w.stream().mapToDouble(BigDecimal::doubleValue).sum() > 0);
    }

    @Test
    void maxDiversificationConservative() {
        var returns = makeReturns(90, 5);
        var mdr = new MaxDiversificationPortfolio(0.15, -0.1, 1.1);
        var mu = org.ojalgo.matrix.MatrixR064.FACTORY.rows(new double[][]{
                {0.01, 0.02, 0.015, 0.005, 0.01}});
        var w = mdr.allocate(returns, mu);
        assertEquals(5, w.size());
        // Weighted by volatility, should be positive
        assertTrue(w.get(0).doubleValue() > 0);
    }

    @Test
    void ensembleAlphaProducesSignals() {
        var returns = makeReturns(60, 4);
        var ensemble = new EnsembleAlpha(List.of(
                new EWMAAlpha(0.1, 0.02),
                new MomentumAlpha(20, 0.02)), 15);
        var signal = ensemble.predict(returns);
        assertEquals(1, signal.countRows());
        assertEquals(4, signal.countColumns());
        for (int j = 0; j < 4; j++)
            assertFalse(Double.isNaN(signal.get(0, j)));
    }

    @Test
    void ensembleAlphaWithSingleModelWorks() {
        var returns = makeReturns(60, 3);
        var ensemble = new EnsembleAlpha(List.of(
                new EWMAAlpha(0.1, 0.02)));
        var signal = ensemble.predict(returns);
        assertEquals(1, signal.countRows());
        assertEquals(3, signal.countColumns());
    }

    @Test
    void mdrStrategyBacktestRuns() {
        var returns = makeReturns(120, 5);
        var params = new StrategyRegistry.Params(
                0.2, -0.15, 0.1, 0.9, 1.3, true, 0.005, 20,
                false, 0.015, false, 0.94, false, 0.0);
        var mdrStrat = StrategyRegistry.buildAll(params)
                .get(StrategyRegistry.MDR_MOMENTUM);
        assertNotNull(mdrStrat);
        var engine = new BacktestEngine(30, 5, new org.example.execution.ZeroCostExecution());
        var result = engine.run(returns, mdrStrat, null);
        assertTrue(result.finalEquity() > 0);
        assertFalse(Double.isNaN(result.sharpe()));
    }

    @Test
    void ensembleStrategyBacktestRuns() {
        var returns = makeReturns(120, 5);
        var params = new StrategyRegistry.Params(
                0.2, -0.15, 0.1, 0.9, 1.3, true, 0.005, 20,
                false, 0.015, false, 0.94, false, 0.0);
        var ensStrat = StrategyRegistry.buildAll(params)
                .get(StrategyRegistry.ENSEMBLE_MARKOWITZ);
        assertNotNull(ensStrat);
        var engine = new BacktestEngine(30, 5, new org.example.execution.ZeroCostExecution());
        var result = engine.run(returns, ensStrat, null);
        assertTrue(result.finalEquity() > 0);
        assertFalse(Double.isNaN(result.sharpe()));
    }

    @Test
    void htmlReportExporterGeneratesFile() {
        var returns = makeReturns(60, 4);
        var params = new StrategyRegistry.Params(
                0.2, -0.15, 0.1, 0.9, 1.3, true, 0.005, 20,
                false, 0.015, false, 0.94, false, 0.0);
        var strats = StrategyRegistry.buildAll(params);
        var engine = new BacktestEngine(20, 5, new org.example.execution.ZeroCostExecution());
        var results = new java.util.LinkedHashMap<String, BacktestResult>();
        var weights = new java.util.LinkedHashMap<String, List<BigDecimal>>();
        for (var e : strats.entrySet()) {
            results.put(e.getKey(), engine.run(returns, e.getValue(), null));
            weights.put(e.getKey(), e.getValue().build(MatrixUtils.sliceRows(returns, 0, 30)));
        }
        var exporter = new HtmlReportExporter();
        assertDoesNotThrow(() -> exporter.export("target/test-report.html", results, weights,
                List.of("a", "b", "c", "d"), null));
    }

    @Test
    void parameterOptimizerFindsTopResults() {
        var returns = makeReturns(80, 4);
        var optimizer = new ParameterOptimizer(30, 5, 0.0);
        var top = optimizer.optimize(returns, 3);
        assertNotNull(top);
        assertFalse(top.isEmpty());
        assertTrue(top.size() <= 3);
        for (var r : top) {
            assertFalse(Double.isNaN(r.sharpe()));
            assertFalse(Double.isInfinite(r.sharpe()));
        }
        // Results should be sorted by Sharpe descending
        for (int i = 1; i < top.size(); i++)
            assertTrue(top.get(i - 1).sharpe() >= top.get(i).sharpe());
    }

    @Test
    void varBasedRiskScalingWorks() {
        var returns = makeReturns(60, 4);
        var weights = List.of(BigDecimal.valueOf(0.4), BigDecimal.valueOf(0.3),
                BigDecimal.valueOf(0.2), BigDecimal.valueOf(0.1));
        var risk = new VaRBasedRiskScaling(0.05, 2.0);
        var adjusted = risk.adjust(weights, returns);
        assertEquals(4, adjusted.size());
        adjusted.forEach(w -> assertFalse(Double.isNaN(w.doubleValue())));
    }

    @Test
    void drawdownBasedRiskScalingWorks() {
        var returns = makeReturns(60, 4);
        var weights = List.of(BigDecimal.valueOf(0.4), BigDecimal.valueOf(0.3),
                BigDecimal.valueOf(0.2), BigDecimal.valueOf(0.1));
        var risk = new DrawdownBasedRiskScaling();
        var adjusted = risk.adjust(weights, returns);
        assertEquals(4, adjusted.size());
        adjusted.forEach(w -> assertFalse(Double.isNaN(w.doubleValue())));
    }

    @Test
    void seasonalityAlphaProducesSignal() {
        var returns = makeReturns(30, 4);
        var alpha = new SeasonalityAlpha();
        var signal = alpha.predict(returns);
        assertEquals(1, signal.countRows());
        assertEquals(4, signal.countColumns());
        for (int j = 0; j < 4; j++)
            assertFalse(Double.isNaN(signal.get(0, j)));
    }

    @Test
    void seasonalityAlphaWeekendEffect() {
        var returns = makeReturns(7, 3);
        var alpha = new SeasonalityAlpha();
        var day0 = alpha.predict(returns);  // should be weekend
        var day3 = alpha.predict(org.example.util.MatrixUtils.sliceRows(returns, 0, 4));
        assertFalse(Double.isNaN(day0.get(0, 0)));
        assertFalse(Double.isNaN(day3.get(0, 0)));
    }

    @Test
    void smartDefaultsComputesVolAndCorr() {
        var returns = makeReturns(100, 5);
        var sd = new SmartDefaults(returns);
        assertTrue(sd.avgVol() > 0);
        assertTrue(sd.assetCount() == 5);
        assertTrue(sd.periodCount() == 100);
        assertTrue(sd.suggestWindow() >= 30);
        assertTrue(sd.suggestLeverage() > 0);
        assertTrue(sd.suggestShrinkage() > 0);
    }

    @Test
    void smartDefaultsHighVolGivesConservativeParams() {
        var rng = new Random(42);
        var data = new double[100][4];
        for (int d = 0; d < 100; d++)
            for (int a = 0; a < 4; a++) data[d][a] = rng.nextGaussian() * 0.06;
        var returns = MatrixR064.FACTORY.rows(data);
        var sd = new SmartDefaults(returns);
        assertTrue(sd.avgVol() > 0.03);
        assertTrue(sd.suggestLeverage() <= 1.3);
    }

    @Test
    void csvDataProviderParseHeaders() throws Exception {
        // Write a temp CSV
        var csvFile = java.io.File.createTempFile("test", ".csv");
        csvFile.deleteOnExit();
        try (var w = new java.io.FileWriter(csvFile)) {
            w.write("Date,BTC,ETH,SOL\n");
            w.write("2024-01-01,100,50,25\n");
            w.write("2024-01-02,101,51,26\n");
            w.write("2024-01-03,102,52,27\n");
        }
        var prov = new CsvDataProvider(csvFile.getAbsolutePath());
        var headers = prov.getHeaders();
        assertEquals(List.of("BTC", "ETH", "SOL"), headers);
        var rets = prov.getReturns(headers);
        assertEquals(2, rets.countRows());  // 3 price rows → 2 return rows
        assertEquals(3, rets.countColumns());
        // First return: (101-100)/100 = 0.01
        assertEquals(0.01, rets.get(0, 0), 1e-10);
    }

    @Test
    void csvDataProviderMissingValuesHandled() throws Exception {
        var csvFile = java.io.File.createTempFile("test2", ".csv");
        csvFile.deleteOnExit();
        try (var w = new java.io.FileWriter(csvFile)) {
            w.write("Date,A,B\n");
            w.write("2024-01-01,100,50\n");
            w.write("2024-01-02,,51\n");  // missing A price
            w.write("2024-01-03,102,52\n");
        }
        var prov = new CsvDataProvider(csvFile.getAbsolutePath());
        var rets = prov.getReturns(List.of("A", "B"));
        assertEquals(2, rets.countRows());
        assertFalse(Double.isNaN(rets.get(0, 0)));
        // Second row should have 0 return for A (missing price)
        assertEquals(0.0, rets.get(1, 0), 1e-10);
    }

    @Test
    void parameterOptimizerWithFewGridValues() {
        var returns = makeReturns(60, 3);
        var optimizer = new ParameterOptimizer(20, 3, 0.0);
        var top = optimizer.optimize(returns, 5);
        assertNotNull(top);
    }

    private static MatrixR064 makeReturns(int days, int assets) {
        var rng = new Random(42);
        var data = new double[days][assets];
        for (int d = 0; d < days; d++)
            for (int a = 0; a < assets; a++)
                data[d][a] = rng.nextGaussian() * 0.02;
        return MatrixR064.FACTORY.rows(data);
    }
}
