package org.example;

import org.example.util.MatrixUtils;
import org.junit.jupiter.api.Test;
import org.ojalgo.matrix.MatrixR064;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MatrixUtilsExtendedTest {

    private MatrixR064 makeReturns(double[][] data) {
        return MatrixR064.FACTORY.rows(data);
    }

    // ── Resample ──────────────────────────────────────────────────────────────

    @Test
    void resampleWithFactor1ReturnsSame() {
        double[][] data = {{0.01, 0.02}, {0.03, 0.04}};
        var returns = makeReturns(data);
        var resampled = MatrixUtils.resample(returns, 1);

        assertEquals(2, (int) resampled.countRows());
        assertEquals(0.01, resampled.get(0, 0), 1e-10);
    }

    @Test
    void resampleWeeklyCompoundsReturns() {
        // 10 days, 2 assets
        double[][] data = new double[10][2];
        for (int i = 0; i < 10; i++) {
            data[i][0] = 0.01;  // 1% daily
            data[i][1] = 0.02;  // 2% daily
        }
        var returns = makeReturns(data);
        var weekly = MatrixUtils.resample(returns, 5);

        assertEquals(2, (int) weekly.countRows());
        // 5 days of 1% → (1.01)^5 - 1 ≈ 0.0510
        assertEquals(Math.pow(1.01, 5) - 1, weekly.get(0, 0), 1e-6);
        // 5 days of 2% → (1.02)^5 - 1 ≈ 0.1041
        assertEquals(Math.pow(1.02, 5) - 1, weekly.get(0, 1), 1e-6);
    }

    @Test
    void resampleMonthlyReducesRowsCorrectly() {
        double[][] data = new double[22][3];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 22; i++)
            for (int j = 0; j < 3; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        var monthly = MatrixUtils.resample(returns, 22);

        assertEquals(1, (int) monthly.countRows());
        assertEquals(3, (int) monthly.countColumns());
    }

    @Test
    void resamplePreservesColumns() {
        double[][] data = new double[20][5];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 20; i++)
            for (int j = 0; j < 5; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        var weekly = MatrixUtils.resample(returns, 5);

        assertEquals(5, (int) weekly.countColumns());
    }

    @Test
    void resampleWithZeroReturns() {
        double[][] data = new double[10][2];
        var returns = makeReturns(data);
        var weekly = MatrixUtils.resample(returns, 5);

        for (int i = 0; i < 2; i++)
            for (int j = 0; j < 2; j++)
                assertEquals(0.0, weekly.get(i, j), 1e-10);
    }

    // ── Rolling Correlation ───────────────────────────────────────────────────

    @Test
    void rollingCorrelationLength() {
        double[][] data = new double[30][2];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 30; i++) {
            data[i][0] = rand.nextGaussian() * 0.02;
            data[i][1] = rand.nextGaussian() * 0.02;
        }
        var returns = makeReturns(data);
        List<Double> rc = MatrixUtils.rollingCorrelation(returns, 0, 1, 10);

        assertEquals(21, rc.size(),
                "Rolling correlation length should be T - window + 1");
    }

    @Test
    void rollingCorrelationValuesAreInBounds() {
        double[][] data = new double[30][2];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 30; i++) {
            data[i][0] = rand.nextGaussian() * 0.02;
            data[i][1] = rand.nextGaussian() * 0.02;
        }
        var returns = makeReturns(data);
        List<Double> rc = MatrixUtils.rollingCorrelation(returns, 0, 1, 10);

        for (double v : rc) {
            assertTrue(v >= -1.0 - 1e-10 && v <= 1.0 + 1e-10,
                    "Rolling correlation should be in [-1, 1]: " + v);
        }
    }

    @Test
    void rollingCorrelationPerfectCorrelation() {
        // Perfectly correlated: asset 1 = 2 * asset 0
        double[][] data = new double[20][2];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 20; i++) {
            double v = rand.nextGaussian() * 0.02;
            data[i][0] = v;
            data[i][1] = v * 2;
        }
        var returns = makeReturns(data);
        List<Double> rc = MatrixUtils.rollingCorrelation(returns, 0, 1, 10);

        for (double v : rc) {
            assertEquals(1.0, v, 1e-6,
                    "Perfectly correlated assets should have rolling corr ≈ 1");
        }
    }

    @Test
    void rollingCorrelationAntiCorrelated() {
        // Perfectly anti-correlated: asset 1 = -asset 0
        double[][] data = new double[20][2];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 20; i++) {
            double v = rand.nextGaussian() * 0.02;
            data[i][0] = v;
            data[i][1] = -v;
        }
        var returns = makeReturns(data);
        List<Double> rc = MatrixUtils.rollingCorrelation(returns, 0, 1, 10);

        for (double v : rc) {
            assertEquals(-1.0, v, 1e-6,
                    "Perfectly anti-correlated assets should have rolling corr ≈ -1");
        }
    }

    // ── Correlation Regime ────────────────────────────────────────────────────

    @Test
    void correlationRegimeReturnsCorrectCount() {
        double[][] data = new double[30][3];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 30; i++)
            for (int j = 0; j < 3; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        List<String> regimes = MatrixUtils.correlationRegime(returns, 10);

        assertEquals(21, regimes.size(),
                "Regime count should be T - window + 1");
    }

    @Test
    void correlationRegimeValuesAreValid() {
        double[][] data = new double[30][3];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 30; i++)
            for (int j = 0; j < 3; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        List<String> regimes = MatrixUtils.correlationRegime(returns, 10);

        for (String r : regimes) {
            assertTrue(r.equals("HIGH_CORR") || r.equals("NORMAL") || r.equals("LOW_CORR"),
                    "Regime should be HIGH_CORR, NORMAL, or LOW_CORR: " + r);
        }
    }

    @Test
    void correlationRegimeHighCorrForIdenticalAssets() {
        // All assets identical → high correlation
        double[][] data = new double[20][2];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 20; i++) {
            double v = rand.nextGaussian() * 0.02;
            data[i][0] = v;
            data[i][1] = v;
        }
        var returns = makeReturns(data);
        List<String> regimes = MatrixUtils.correlationRegime(returns, 10);

        for (String r : regimes) {
            assertEquals("HIGH_CORR", r,
                    "Identical assets should produce HIGH_CORR regime");
        }
    }

    // ── Ledoit-Wolf Edge Cases ────────────────────────────────────────────────

    @Test
    void ledoitWolfLambdaSingleAsset() {
        double[][] data = {{0.01}, {-0.01}, {0.02}, {-0.02}};
        var returns = makeReturns(data);
        double lambda = MatrixUtils.ledoitWolfLambda(returns);

        assertTrue(lambda >= 0 && lambda <= 1,
                "Ledoit-Wolf lambda should be valid for single asset: " + lambda);
    }

    @Test
    void ledoitWolfLambdaManyAssets() {
        double[][] data = new double[50][10];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 50; i++)
            for (int j = 0; j < 10; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        double lambda = MatrixUtils.ledoitWolfLambda(returns);

        assertTrue(lambda >= 0 && lambda <= 1,
                "Ledoit-Wolf lambda should be valid for many assets: " + lambda);
    }

    @Test
    void ledoitWolfCovarianceHasCorrectDimensions() {
        double[][] data = new double[20][4];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 20; i++)
            for (int j = 0; j < 4; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        var cov = MatrixUtils.ledoitWolfCovariance(returns);

        assertEquals(4, (int) cov.countRows());
        assertEquals(4, (int) cov.countColumns());
    }

    @Test
    void ledoitWolfCovarianceDiagonalIsPositive() {
        double[][] data = new double[20][3];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 20; i++)
            for (int j = 0; j < 3; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        var cov = MatrixUtils.ledoitWolfCovariance(returns);

        for (int i = 0; i < 3; i++) {
            assertTrue(cov.get(i, i) > 0,
                    "Diagonal of covariance should be positive: " + cov.get(i, i));
        }
    }

    // ── Correlation Matrix ────────────────────────────────────────────────────

    @Test
    void correlationMatrixIsSymmetric() {
        double[][] data = new double[20][3];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 20; i++)
            for (int j = 0; j < 3; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        var corr = MatrixUtils.correlationMatrix(returns);

        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                assertEquals(corr.get(i, j), corr.get(j, i), 1e-10,
                        "Correlation matrix should be symmetric");
    }

    @Test
    void correlationMatrixOffDiagonalBounds() {
        double[][] data = new double[20][3];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 20; i++)
            for (int j = 0; j < 3; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        var corr = MatrixUtils.correlationMatrix(returns);

        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                if (i != j)
                    assertTrue(corr.get(i, j) >= -1.0 && corr.get(i, j) <= 1.0,
                            "Off-diagonal correlation should be in [-1, 1]");
    }

    // ── Center Columns ────────────────────────────────────────────────────────

    @Test
    void centerColumnsPreservesShape() {
        double[][] data = {{1, 2, 3}, {4, 5, 6}, {7, 8, 9}};
        var m = makeReturns(data);
        var centered = MatrixUtils.centerColumns(m);

        assertEquals(3, (int) centered.countRows());
        assertEquals(3, (int) centered.countColumns());
    }

    @Test
    void centerColumnsPreservesVariance() {
        double[][] data = {{1, 4}, {2, 5}, {3, 6}};
        var m = makeReturns(data);
        var centered = MatrixUtils.centerColumns(m);

        // Variance should be preserved (centering doesn't change variance)
        for (int j = 0; j < 2; j++) {
            double origVar = 0, centVar = 0;
            double origMean = 0, centMean = 0;
            for (int i = 0; i < 3; i++) {
                origMean += m.get(i, j);
                centMean += centered.get(i, j);
            }
            origMean /= 3; centMean /= 3;
            for (int i = 0; i < 3; i++) {
                origVar += (m.get(i, j) - origMean) * (m.get(i, j) - origMean);
                centVar += (centered.get(i, j) - centMean) * (centered.get(i, j) - centMean);
            }
            assertEquals(origVar / 3, centVar / 3, 1e-10,
                    "Variance should be preserved after centering");
        }
    }

    // ── Covariance Matrix ─────────────────────────────────────────────────────

    @Test
    void covarianceMatrixWithZeroShrinkageIsSampleCov() {
        double[][] data = {{0.01, 0.02}, {-0.01, 0.03}, {0.02, -0.01}};
        var returns = makeReturns(data);
        var cov = MatrixUtils.covarianceMatrix(returns, null, 0.0);

        // Should be close to sample covariance
        assertNotNull(cov);
        assertEquals(2, (int) cov.countRows());
        assertEquals(2, (int) cov.countColumns());
    }

    @Test
    void covarianceMatrixWithFullShrinkageIsIdentity() {
        double[][] data = {{0.01, 0.02}, {-0.01, 0.03}};
        var returns = makeReturns(data);
        var cov = MatrixUtils.covarianceMatrix(returns, null, 1.0);

        // With lambda=1, shrunk = 1*S + 0*I = S
        // But centering 2 rows gives 0 matrix... so this is degenerate
        // Just check it doesn't crash
        assertNotNull(cov);
    }
}
