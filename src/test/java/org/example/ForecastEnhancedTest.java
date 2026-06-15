package org.example;

import org.example.engine.StrategyPreset;
import org.example.engine.StrategyRegistry;
import org.example.forecast.ForecastEngine;
import org.example.forecast.ForecastResult;
import org.example.forecast.ForecastResult.RiskLevel;
import org.example.forecast.ForecastResult.Signal;
import org.junit.jupiter.api.Test;
import org.ojalgo.matrix.MatrixR064;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ForecastEnhancedTest {

    private MatrixR064 makeReturns(double[][] data) {
        return MatrixR064.FACTORY.rows(data);
    }

    private double[][] randomReturns(int rows, int cols, long seed, double vol) {
        double[][] data = new double[rows][cols];
        var rand = new java.util.Random(seed);
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                data[i][j] = rand.nextGaussian() * vol;
        return data;
    }

    // ── Signal tests ──────────────────────────────────────────────────────

    @Test
    void signalIsNeverNull() {
        var engine = new ForecastEngine();
        var results = engine.forecast(makeReturns(randomReturns(60, 3, 42, 0.02)),
                7, List.of("A", "B", "C"));
        for (var r : results) {
            assertNotNull(r.signal(), "Signal should never be null");
        }
    }

    @Test
    void signalIsOneOfValidValues() {
        var engine = new ForecastEngine();
        var results = engine.forecast(makeReturns(randomReturns(60, 2, 42, 0.02)),
                7, List.of("A", "B"));
        for (var r : results) {
            Signal s = r.signal();
            assertTrue(s == Signal.STRONG_BUY || s == Signal.BUY || s == Signal.HOLD
                    || s == Signal.SELL || s == Signal.STRONG_SELL,
                    "Signal should be one of the defined values: " + s);
        }
    }

    @Test
    void signalHasValidColor() {
        for (Signal s : Signal.values()) {
            assertNotNull(s.color(), "Signal color should not be null: " + s);
            assertTrue(s.color().startsWith("#"), "Signal color should start with #: " + s);
            assertEquals(7, s.color().length(), "Signal color should be 7 chars: " + s);
        }
    }

    @Test
    void signalHasValidLabel() {
        for (Signal s : Signal.values()) {
            assertNotNull(s.label(), "Signal label should not be null: " + s);
            assertFalse(s.label().isBlank(), "Signal label should not be blank: " + s);
        }
    }

    // ── RiskLevel tests ───────────────────────────────────────────────────

    @Test
    void riskLevelIsNeverNull() {
        var engine = new ForecastEngine();
        var results = engine.forecast(makeReturns(randomReturns(60, 2, 42, 0.02)),
                7, List.of("A", "B"));
        for (var r : results) {
            assertNotNull(r.riskLevel(), "RiskLevel should never be null");
        }
    }

    @Test
    void riskLevelIsOneOfValidValues() {
        var engine = new ForecastEngine();
        var results = engine.forecast(makeReturns(randomReturns(60, 2, 42, 0.02)),
                7, List.of("A", "B"));
        for (var r : results) {
            RiskLevel rl = r.riskLevel();
            assertTrue(rl == RiskLevel.LOW || rl == RiskLevel.MEDIUM
                    || rl == RiskLevel.HIGH || rl == RiskLevel.EXTREME,
                    "RiskLevel should be one of the defined values: " + rl);
        }
    }

    @Test
    void lowVolAssetHasLowerRiskLevel() {
        double[][] lowData = randomReturns(100, 1, 42, 0.005);
        double[][] highData = randomReturns(100, 1, 42, 0.08);
        var engine = new ForecastEngine();
        var lowR = engine.forecast(makeReturns(lowData), 7, List.of("LOW")).get(0);
        var highR = engine.forecast(makeReturns(highData), 7, List.of("HIGH")).get(0);
        assertTrue(lowR.riskLevel().ordinal() <= highR.riskLevel().ordinal(),
                "Low vol asset should have lower or equal risk level");
    }

    @Test
    void riskLevelHasValidDescription() {
        for (RiskLevel rl : RiskLevel.values()) {
            assertNotNull(rl.description(), "Description should not be null: " + rl);
            assertFalse(rl.description().isBlank(), "Description should not be blank: " + rl);
        }
    }

    // ── ForecastSharpe tests ──────────────────────────────────────────────

    @Test
    void forecastSharpeIsFinite() {
        var engine = new ForecastEngine();
        var results = engine.forecast(makeReturns(randomReturns(60, 2, 42, 0.02)),
                7, List.of("A", "B"));
        for (var r : results) {
            assertTrue(Double.isFinite(r.forecastSharpe()),
                    "Forecast Sharpe should be finite: " + r.forecastSharpe());
        }
    }

    @Test
    void forecastSharpeHigherForPositiveDrift() {
        // Positive drift series
        double[][] posDrift = new double[100][1];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 100; i++)
            posDrift[i][0] = 0.001 + rand.nextGaussian() * 0.005;

        // Negative drift series
        double[][] negDrift = new double[100][1];
        var rand2 = new java.util.Random(42);
        for (int i = 0; i < 100; i++)
            negDrift[i][0] = -0.001 + rand2.nextGaussian() * 0.005;

        var engine = new ForecastEngine();
        double posSharpe = engine.forecast(makeReturns(posDrift), 7, List.of("POS")).get(0).forecastSharpe();
        double negSharpe = engine.forecast(makeReturns(negDrift), 7, List.of("NEG")).get(0).forecastSharpe();
        assertTrue(posSharpe > negSharpe,
                "Positive drift should have higher forecast Sharpe: " + posSharpe + " > " + negSharpe);
    }

    // ── Expected range tests ──────────────────────────────────────────────

    @Test
    void expectedRangeIsValid() {
        var engine = new ForecastEngine();
        var results = engine.forecast(makeReturns(randomReturns(60, 2, 42, 0.02)),
                7, List.of("A", "B"));
        for (var r : results) {
            assertTrue(Double.isFinite(r.expectedRangeLow()), "Range low should be finite");
            assertTrue(Double.isFinite(r.expectedRangeHigh()), "Range high should be finite");
            assertTrue(r.expectedRangeHigh() >= r.expectedRangeLow(),
                    "Range high should be >= range low");
        }
    }

    // ── ProbLoss tests ────────────────────────────────────────────────────

    @Test
    void probLossBetweenZeroAndOne() {
        var engine = new ForecastEngine();
        var results = engine.forecast(makeReturns(randomReturns(60, 2, 42, 0.02)),
                7, List.of("A", "B"));
        for (var r : results) {
            assertTrue(r.probLoss() >= 0.0 && r.probLoss() <= 1.0,
                    "Prob loss should be between 0 and 1: " + r.probLoss());
        }
    }

    @Test
    void probLossHigherForNegativeDrift() {
        double[][] negDrift = new double[100][1];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 100; i++)
            negDrift[i][0] = -0.005 + rand.nextGaussian() * 0.01;

        double[][] posDrift = new double[100][1];
        var rand2 = new java.util.Random(42);
        for (int i = 0; i < 100; i++)
            posDrift[i][0] = 0.005 + rand2.nextGaussian() * 0.01;

        var engine = new ForecastEngine();
        double negPL = engine.forecast(makeReturns(negDrift), 7, List.of("NEG")).get(0).probLoss();
        double posPL = engine.forecast(makeReturns(posDrift), 7, List.of("POS")).get(0).probLoss();
        assertTrue(negPL > posPL,
                "Negative drift should have higher prob of loss: " + negPL + " > " + posPL);
    }

    // ── Max drawdown estimate tests ───────────────────────────────────────

    @Test
    void maxDrawdownEstBetweenZeroAndOne() {
        var engine = new ForecastEngine();
        var results = engine.forecast(makeReturns(randomReturns(60, 2, 42, 0.02)),
                7, List.of("A", "B"));
        for (var r : results) {
            assertTrue(r.maxDrawdownEst() >= 0.0 && r.maxDrawdownEst() <= 1.0,
                    "Max DD estimate should be between 0 and 1: " + r.maxDrawdownEst());
        }
    }

    // ── Human summary tests ───────────────────────────────────────────────

    @Test
    void humanSummaryContainsAssetName() {
        var engine = new ForecastEngine();
        var results = engine.forecast(makeReturns(randomReturns(60, 1, 42, 0.02)),
                7, List.of("Bitcoin"));
        String human = results.get(0).humanSummary();
        assertTrue(human.contains("Bitcoin"),
                "Human summary should contain asset name");
    }

    @Test
    void summaryContainsSignalLabel() {
        var engine = new ForecastEngine();
        var results = engine.forecast(makeReturns(randomReturns(60, 1, 42, 0.02)),
                7, List.of("BTC"));
        String summary = results.get(0).summary();
        Signal s = results.get(0).signal();
        assertTrue(summary.contains(s.label()),
                "Summary should contain signal label");
    }

    // ── normalCDF tests ───────────────────────────────────────────────────

    @Test
    void normalCDFZeroReturnsHalf() {
        assertEquals(0.5, ForecastEngine.normalCDF(0.0), 1e-6,
                "CDF at 0 should be 0.5");
    }

    @Test
    void normalCDFApproachesOne() {
        assertTrue(ForecastEngine.normalCDF(8.0) > 0.9999,
                "CDF at +8 should approach 1");
    }

    @Test
    void normalCDFApproachesZero() {
        assertTrue(ForecastEngine.normalCDF(-8.0) < 0.0001,
                "CDF at -8 should approach 0");
    }

    @Test
    void normalCDFMonotonic() {
        for (double x = -4; x < 4; x += 0.5) {
            assertTrue(ForecastEngine.normalCDF(x + 0.5) >= ForecastEngine.normalCDF(x),
                    "CDF should be monotonically increasing at x=" + x);
        }
    }
}
