package org.example;

import org.example.engine.StressScenario;
import org.example.engine.StressTestEngine;
import org.example.engine.StressTestResult;
import org.junit.jupiter.api.Test;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StressTestEngineTest {

    private MatrixR064 makeReturns(double[][] data) {
        return MatrixR064.FACTORY.rows(data);
    }

    private List<BigDecimal> makeWeights(int n) {
        double w = 1.0 / n;
        return java.util.stream.IntStream.range(0, n)
                .mapToObj(i -> BigDecimal.valueOf(w))
                .toList();
    }

    @Test
    void predefinedScenariosExist() {
        StressScenario may2022 = StressScenario.may2022Crash();
        StressScenario ftx     = StressScenario.ftxCollapse();
        StressScenario covid   = StressScenario.covidCrash();
        StressScenario luna    = StressScenario.lunaCollapse();
        StressScenario oct2025 = StressScenario.oct2025Crash();

        assertNotNull(may2022);
        assertNotNull(ftx);
        assertNotNull(covid);
        assertNotNull(luna);
        assertNotNull(oct2025);

        assertTrue(may2022.shockMagnitude() > 0);
        assertTrue(ftx.shockMagnitude() > 0);
        assertTrue(covid.shockMagnitude() > 0);
        assertTrue(luna.shockMagnitude() > 0);
        assertEquals(0.35, oct2025.shockMagnitude(), 1e-9);
        assertEquals(4, oct2025.durationDays());
    }

    @Test
    void oct2025StressTestReturnsValidResult() {
        double[][] data = new double[60][3];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 60; i++)
            for (int j = 0; j < 3; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var engine = new StressTestEngine();
        var result = engine.runStressTest(makeWeights(3), makeReturns(data),
                StressScenario.oct2025Crash());
        assertNotNull(result);
        assertTrue(result.portfolioReturn() <= 0);
        assertTrue(result.maxDrawdown() >= 0);
    }

    @Test
    void historicalStressTestReturnsValidResult() {
        double[][] data = new double[60][3];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 60; i++)
            for (int j = 0; j < 3; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var engine = new StressTestEngine();
        var result = engine.runStressTest(makeWeights(3), makeReturns(data),
                StressScenario.may2022Crash());

        assertNotNull(result);
        assertTrue(Double.isFinite(result.portfolioReturn()));
        assertTrue(Double.isFinite(result.maxDrawdown()));
        assertFalse(result.equityPath().isEmpty(), "Equity path should not be empty");
    }

    @Test
    void monteCarloStressTestReturnsValidResult() {
        double[][] data = new double[60][3];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 60; i++)
            for (int j = 0; j < 3; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var engine = new StressTestEngine();
        var result = engine.runMonteCarloStress(makeWeights(3), makeReturns(data),
                100, 21, 0.25);

        assertNotNull(result);
        assertTrue(Double.isFinite(result.portfolioReturn()));
        assertTrue(Double.isFinite(result.var95()));
        assertTrue(Double.isFinite(result.cvar95()));
    }

    @Test
    void worstDayLossIsNonPositive() {
        double[][] data = new double[60][3];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 60; i++)
            for (int j = 0; j < 3; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var engine = new StressTestEngine();
        var result = engine.runStressTest(makeWeights(3), makeReturns(data),
                StressScenario.covidCrash());

        // worstDayLoss is stored as positive = loss; verify it's non-negative
        assertTrue(result.worstDayLoss() >= 0,
                "worstDayLoss (positive = loss) should be >= 0: " + result.worstDayLoss());
    }

    @Test
    void equityPathLengthMatchesHistoryDays() {
        double[][] data = new double[30][2];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 30; i++)
            for (int j = 0; j < 2; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var engine = new StressTestEngine();
        var result = engine.runStressTest(makeWeights(2), makeReturns(data),
                StressScenario.ftxCollapse());

        assertEquals(30, result.equityPath().size(),
                "Equity path length should match number of days");
    }

    @Test
    void varAndCvarAreNonNegative() {
        double[][] data = new double[60][3];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 60; i++)
            for (int j = 0; j < 3; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var engine = new StressTestEngine();
        var result = engine.runStressTest(makeWeights(3), makeReturns(data),
                StressScenario.lunaCollapse());

        assertTrue(result.var95() >= 0, "VaR95 should be non-negative: " + result.var95());
        assertTrue(result.cvar95() >= 0, "CVaR95 should be non-negative: " + result.cvar95());
    }

    @Test
    void whatIfReturnsValidResult() {
        double[][] data = new double[60][2];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 60; i++)
            for (int j = 0; j < 2; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var engine = new StressTestEngine();
        var result = engine.whatIf(makeWeights(2), makeReturns(data), 0.20);

        assertNotNull(result);
        assertTrue(Double.isFinite(result.portfolioReturn()));
        assertTrue(result.maxDrawdown() >= 0);
    }
}
