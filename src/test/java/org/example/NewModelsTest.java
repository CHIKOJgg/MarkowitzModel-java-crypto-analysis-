package org.example;

import org.example.alpha.BollingerBandAlpha;
import org.example.alpha.MACDAlpha;
import org.example.alpha.VolumeWeightedAlpha;
import org.example.portfolio.HierarchicalRiskParityPortfolio;
import org.example.risk.RegimeAwareVolScaling;
import org.junit.jupiter.api.Test;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NewModelsTest {

    private MatrixR064 makeReturns(double[][] data) {
        return MatrixR064.FACTORY.rows(data);
    }

    // ── MACDAlpha ──────────────────────────────────────────────────────────

    @Test
    void macdOutputIsOneRow() {
        double[][] data = new double[60][3];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 60; i++)
            for (int j = 0; j < 3; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var alpha = new MACDAlpha();
        var result = alpha.predict(makeReturns(data));

        assertEquals(1, (int) result.countRows(), "MACD output should be 1 row");
        assertEquals(3, (int) result.countColumns(), "MACD output columns should match assets");
    }

    @Test
    void macdSignalChangesWithDifferentParameters() {
        double[][] data = new double[100][2];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 100; i++)
            for (int j = 0; j < 2; j++)
                data[i][j] = rand.nextGaussian() * 0.02 + 0.001;

        var returns = makeReturns(data);
        var alpha1 = new MACDAlpha(8, 21, 5);
        var alpha2 = new MACDAlpha(12, 26, 9);

        var r1 = alpha1.predict(returns);
        var r2 = alpha2.predict(returns);

        boolean differs = false;
        for (int j = 0; j < 2; j++) {
            if (Math.abs(r1.get(0, j) - r2.get(0, j)) > 1e-10) {
                differs = true;
                break;
            }
        }
        assertTrue(differs, "Different MACD parameters should produce different signals");
    }

    @Test
    void macdWithConstantReturns() {
        double[][] data = new double[60][2];
        for (int i = 0; i < 60; i++) {
            data[i][0] = 0.01;
            data[i][1] = 0.01;
        }

        var alpha = new MACDAlpha();
        var result = alpha.predict(makeReturns(data));

        assertEquals(1, (int) result.countRows());
        assertEquals(2, (int) result.countColumns());
        for (int j = 0; j < 2; j++) {
            assertTrue(Double.isFinite(result.get(0, j)),
                    "MACD with constant returns should produce finite output");
        }
    }

    // ── BollingerBandAlpha ─────────────────────────────────────────────────

    @Test
    void bollingerOutputDimensions() {
        double[][] data = new double[60][4];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 60; i++)
            for (int j = 0; j < 4; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var alpha = new BollingerBandAlpha();
        var result = alpha.predict(makeReturns(data));

        assertEquals(1, (int) result.countRows());
        assertEquals(4, (int) result.countColumns());
    }

    @Test
    void bollingerWithTrendingVsRangingData() {
        double[][] trending = new double[60][1];
        double[][] ranging  = new double[60][1];
        var rand = new java.util.Random(42);

        for (int i = 0; i < 60; i++) {
            trending[i][0] = 0.001 * (i + 1); // uptrend
            ranging[i][0]  = rand.nextGaussian() * 0.001; // ranging
        }

        var alpha = new BollingerBandAlpha();
        var trendResult = alpha.predict(makeReturns(trending));
        var rangeResult = alpha.predict(makeReturns(ranging));

        assertTrue(Double.isFinite(trendResult.get(0, 0)));
        assertTrue(Double.isFinite(rangeResult.get(0, 0)));
    }

    @Test
    void bollingerSignalIsBounded() {
        double[][] data = new double[60][3];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 60; i++)
            for (int j = 0; j < 3; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var alpha = new BollingerBandAlpha();
        var result = alpha.predict(makeReturns(data));

        for (int j = 0; j < 3; j++) {
            assertTrue(result.get(0, j) >= -1.0 && result.get(0, j) <= 1.0,
                    "Bollinger signal should be in [-1, 1]: " + result.get(0, j));
        }
    }

    // ── VolumeWeightedAlpha ────────────────────────────────────────────────

    @Test
    void volumeWeightedOutputDimensions() {
        double[][] data = new double[60][3];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 60; i++)
            for (int j = 0; j < 3; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var alpha = new VolumeWeightedAlpha();
        var result = alpha.predict(makeReturns(data));

        assertEquals(1, (int) result.countRows());
        assertEquals(3, (int) result.countColumns());
    }

    @Test
    void volumeWeightedWithUniformVsVariableVolume() {
        // Uniform volume: all returns have same magnitude
        double[][] uniform = new double[60][1];
        for (int i = 0; i < 60; i++) {
            uniform[i][0] = (i % 2 == 0) ? 0.02 : -0.02;
        }

        // Variable volume: mixed magnitudes
        double[][] variable = new double[60][1];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 60; i++) {
            variable[i][0] = rand.nextGaussian() * 0.02;
        }

        var alpha = new VolumeWeightedAlpha();
        var uniformResult  = alpha.predict(makeReturns(uniform));
        var variableResult = alpha.predict(makeReturns(variable));

        assertTrue(Double.isFinite(uniformResult.get(0, 0)));
        assertTrue(Double.isFinite(variableResult.get(0, 0)));
    }

    @Test
    void volumeWeightedSignalIsBounded() {
        double[][] data = new double[60][2];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 60; i++)
            for (int j = 0; j < 2; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var alpha = new VolumeWeightedAlpha();
        var result = alpha.predict(makeReturns(data));

        for (int j = 0; j < 2; j++) {
            assertTrue(result.get(0, j) >= -1.0 && result.get(0, j) <= 1.0,
                    "VolumeWeighted signal should be in [-1, 1]: " + result.get(0, j));
        }
    }

    // ── RegimeAwareVolScaling ──────────────────────────────────────────────

    @Test
    void regimeAwareAdjustsWeights() {
        double[][] data = new double[100][3];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 100; i++)
            for (int j = 0; j < 3; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        var riskModel = new RegimeAwareVolScaling(0.015);
        List<BigDecimal> weights = List.of(
                BigDecimal.valueOf(0.4),
                BigDecimal.valueOf(0.3),
                BigDecimal.valueOf(0.3)
        );

        var adjusted = riskModel.adjust(weights, returns);
        assertNotNull(adjusted, "Adjusted weights should not be null");
        assertEquals(3, adjusted.size(), "Should preserve number of assets");
    }

    @Test
    void regimeAwareWithHighCorrelation() {
        // Highly correlated assets
        double[][] data = new double[100][3];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 100; i++) {
            double r = rand.nextGaussian() * 0.02;
            data[i][0] = r;
            data[i][1] = r + rand.nextGaussian() * 0.001;
            data[i][2] = r + rand.nextGaussian() * 0.001;
        }

        var returns = makeReturns(data);
        var riskModel = new RegimeAwareVolScaling(0.015);
        List<BigDecimal> weights = List.of(
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(0.25),
                BigDecimal.valueOf(0.25)
        );

        var adjusted = riskModel.adjust(weights, returns);
        assertNotNull(adjusted);
        for (var w : adjusted) {
            assertTrue(Double.isFinite(w.doubleValue()),
                    "Adjusted weight should be finite: " + w);
        }
    }

    @Test
    void regimeAwareWithLowCorrelation() {
        // Low correlation assets
        double[][] data = new double[100][3];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 100; i++) {
            data[i][0] = rand.nextGaussian() * 0.02;
            data[i][1] = rand.nextGaussian() * 0.02;
            data[i][2] = rand.nextGaussian() * 0.02;
        }

        var returns = makeReturns(data);
        var riskModel = new RegimeAwareVolScaling(0.015);
        List<BigDecimal> weights = List.of(
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(0.25),
                BigDecimal.valueOf(0.25)
        );

        var adjusted = riskModel.adjust(weights, returns);
        assertNotNull(adjusted);
        for (var w : adjusted) {
            assertTrue(Double.isFinite(w.doubleValue()));
        }
    }

    // ── HierarchicalRiskParityPortfolio ────────────────────────────────────

    @Test
    void hrpWeightsSumToOne() {
        double[][] data = new double[100][4];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 100; i++)
            for (int j = 0; j < 4; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        var mu = MatrixR064.FACTORY.rows(new double[][]{{0.01, 0.02, 0.015, 0.005}});
        var portfolio = new HierarchicalRiskParityPortfolio();
        var weights = portfolio.allocate(returns, mu);

        double sum = weights.stream().mapToDouble(BigDecimal::doubleValue).sum();
        assertEquals(1.0, sum, 1e-6, "HRP weights should sum to ~1.0");
    }

    @Test
    void hrpWeightsAreAllPositive() {
        double[][] data = new double[100][3];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 100; i++)
            for (int j = 0; j < 3; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        var mu = MatrixR064.FACTORY.rows(new double[][]{{0.01, 0.02, 0.015}});
        var portfolio = new HierarchicalRiskParityPortfolio();
        var weights = portfolio.allocate(returns, mu);

        for (var w : weights) {
            assertTrue(w.doubleValue() >= 0,
                    "HRP weights should be non-negative (long-only): " + w);
        }
    }

    @Test
    void hrpWithDifferentNumberOfAssets() {
        for (int n : new int[]{2, 3, 5, 8}) {
            double[][] data = new double[100][n];
            var rand = new java.util.Random(42);
            for (int i = 0; i < 100; i++)
                for (int j = 0; j < n; j++)
                    data[i][j] = rand.nextGaussian() * 0.02;

            var returns = makeReturns(data);
            double[] muVals = new double[n];
            for (int j = 0; j < n; j++) muVals[j] = 0.01;
            var mu = MatrixR064.FACTORY.rows(new double[][]{muVals});
            var portfolio = new HierarchicalRiskParityPortfolio();
            var weights = portfolio.allocate(returns, mu);

            assertEquals(n, weights.size(), "Should have " + n + " weights");
            double sum = weights.stream().mapToDouble(BigDecimal::doubleValue).sum();
            assertEquals(1.0, sum, 1e-6, "Weights should sum to 1 for n=" + n);
            for (var w : weights) {
                assertTrue(w.doubleValue() >= 0, "Weights should be non-negative");
            }
        }
    }
}
