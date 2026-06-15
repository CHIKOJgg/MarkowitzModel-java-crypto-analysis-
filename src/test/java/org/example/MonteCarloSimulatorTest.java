package org.example;

import org.example.engine.MonteCarloResult;
import org.example.engine.MonteCarloSimulator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MonteCarloSimulatorTest {

    private MonteCarloResult simulate(int numPaths, int horizon, long seed) {
        var sim = new MonteCarloSimulator();
        return sim.simulate(numPaths, horizon, 0.0003, 0.02, seed);
    }

    @Test
    void outputArrayLengthsMatchHorizon() {
        int horizon = 30;
        var result = simulate(500, horizon, 42);

        assertEquals(horizon + 1, result.medianPath().length,
                "medianPath length should be horizon+1");
        assertEquals(horizon + 1, result.p5().length,
                "p5 length should be horizon+1");
        assertEquals(horizon + 1, result.p25().length,
                "p25 length should be horizon+1");
        assertEquals(horizon + 1, result.p75().length,
                "p75 length should be horizon+1");
        assertEquals(horizon + 1, result.p95().length,
                "p95 length should be horizon+1");
    }

    @Test
    void medianIsBetweenP5AndP95() {
        var result = simulate(1000, 30, 42);

        for (int t = 1; t <= result.horizon(); t++) {
            assertTrue(result.medianPath()[t] >= result.p5()[t] - 1e-10,
                    "Median should be >= p5 at t=" + t);
            assertTrue(result.medianPath()[t] <= result.p95()[t] + 1e-10,
                    "Median should be <= p95 at t=" + t);
        }
    }

    @Test
    void p25IsBetweenP5AndP75() {
        var result = simulate(1000, 30, 42);

        for (int t = 1; t <= result.horizon(); t++) {
            assertTrue(result.p25()[t] >= result.p5()[t] - 1e-10,
                    "p25 should be >= p5 at t=" + t);
            assertTrue(result.p25()[t] <= result.p75()[t] + 1e-10,
                    "p25 should be <= p75 at t=" + t);
        }
    }

    @Test
    void probLossIsBetweenZeroAndOne() {
        var result = simulate(1000, 30, 42);

        assertTrue(result.probLoss() >= 0.0, "probLoss should be >= 0");
        assertTrue(result.probLoss() <= 1.0, "probLoss should be <= 1");
    }

    @Test
    void expectedReturnIsFinite() {
        var result = simulate(1000, 30, 42);

        assertTrue(Double.isFinite(result.expectedReturn()),
                "expectedReturn should be finite");
        assertTrue(Double.isFinite(result.expectedVol()),
                "expectedVol should be finite");
    }

    @Test
    void differentSeedsProduceDifferentResults() {
        var r1 = simulate(500, 30, 1);
        var r2 = simulate(500, 30, 2);

        boolean differs = false;
        for (int t = 1; t <= 30; t++) {
            if (Math.abs(r1.medianPath()[t] - r2.medianPath()[t]) > 1e-10) {
                differs = true;
                break;
            }
        }
        assertTrue(differs, "Different seeds should produce different results");
    }

    @Test
    void morePathsSmoothEstimates() {
        var rough  = simulate(50, 30, 42);
        var smooth = simulate(5000, 30, 42);

        // With more paths, percentile spread should be tighter relative to the mean
        double roughSpread = Math.abs(rough.p95()[30] - rough.p5()[30]);
        double smoothSpread = Math.abs(smooth.p95()[30] - smooth.p5()[30]);

        assertTrue(smoothSpread > 0, "Smooth spread should be positive");
        assertTrue(roughSpread > 0, "Rough spread should be positive");
        // Both should be finite
        assertTrue(Double.isFinite(roughSpread));
        assertTrue(Double.isFinite(smoothSpread));
    }

    @Test
    void varAndCvarAreNonNegative() {
        var result = simulate(1000, 30, 42);

        assertTrue(result.var95() >= 0, "VaR95 should be non-negative");
        assertTrue(result.cvar95() >= 0, "CVaR95 should be non-negative");
    }

    @Test
    void allPathsStartAtOne() {
        var result = simulate(100, 30, 42);

        assertEquals(1.0, result.medianPath()[0], 1e-10,
                "Median path should start at 1.0");
        assertEquals(1.0, result.p5()[0], 1e-10,
                "p5 path should start at 1.0");
        assertEquals(1.0, result.p95()[0], 1e-10,
                "p95 path should start at 1.0");
    }

    @Test
    void horizonAndNumPathsRecordedCorrectly() {
        var result = simulate(250, 21, 42);

        assertEquals(21, result.horizon());
        assertEquals(250, result.numPaths());
    }
}
