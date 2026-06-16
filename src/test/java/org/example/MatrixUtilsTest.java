package org.example;

import org.example.util.MatrixUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MatrixUtilsTest {

    @Test
    void shrinkDiagonalPreservesOffDiagonal() {
        double[][] data = {{1.0, 0.5}, {0.5, 2.0}};
        var cov = org.ojalgo.matrix.MatrixR064.FACTORY.rows(data);
        var shrunk = MatrixUtils.shrink(cov, 0.7);

        // off-diagonal should be lambda * original
        assertEquals(0.7 * 0.5, shrunk.get(0, 1), 1e-10);
        assertEquals(0.7 * 0.5, shrunk.get(1, 0), 1e-10);
        // diagonal should be lambda * original + (1-lambda) * mean(diag)
        double muTarget = (1.0 + 2.0) / 2.0;
        assertEquals(0.7 * 1.0 + 0.3 * muTarget, shrunk.get(0, 0), 1e-10);
        assertEquals(0.7 * 2.0 + 0.3 * muTarget, shrunk.get(1, 1), 1e-10);
    }

    @Test
    void centerColumnsProducesZeroMean() {
        double[][] data = {{1, 4}, {2, 5}, {3, 6}};
        var m = org.ojalgo.matrix.MatrixR064.FACTORY.rows(data);
        var centered = MatrixUtils.centerColumns(m);

        for (int j = 0; j < 2; j++) {
            double mean = 0;
            for (int i = 0; i < 3; i++) mean += centered.get(i, j);
            mean /= 3;
            assertEquals(0.0, mean, 1e-10, "Column " + j + " should have zero mean");
        }
    }

    @Test
    void covarianceMatrixIsSymmetric() {
        double[][] data = {{0.01, 0.02}, {-0.01, 0.03}, {0.02, -0.01}, {-0.02, 0.01}};
        var returns = org.ojalgo.matrix.MatrixR064.FACTORY.rows(data);
        var mu = org.ojalgo.matrix.MatrixR064.FACTORY.rows(new double[][]{{0.005, 0.01}});
        var cov = MatrixUtils.covarianceMatrix(returns, mu, 0.5);

        assertEquals(cov.get(0, 1), cov.get(1, 0), 1e-10, "Cov matrix should be symmetric");
    }

    @Test
    void sliceRowsExtractsCorrectWindow() {
        double[][] data = {{1}, {2}, {3}, {4}, {5}};
        var m = org.ojalgo.matrix.MatrixR064.FACTORY.rows(data);
        var sliced = MatrixUtils.sliceRows(m, 1, 4);

        assertEquals(3, (int) sliced.countRows());
        assertEquals(2.0, sliced.get(0, 0), 1e-10);
        assertEquals(4.0, sliced.get(2, 0), 1e-10);
    }

    @Test
    void assertCleanPassesForValidMatrix() {
        double[][] data = {{1.0, 2.0}, {3.0, 4.0}};
        var m = org.ojalgo.matrix.MatrixR064.FACTORY.rows(data);
        assertDoesNotThrow(() -> MatrixUtils.assertClean(m));
    }

    @Test
    void assertCleanThrowsForNaN() {
        double[][] data = {{1.0, Double.NaN}, {3.0, 4.0}};
        var m = org.ojalgo.matrix.MatrixR064.FACTORY.rows(data);
        assertThrows(IllegalStateException.class, () -> MatrixUtils.assertClean(m));
    }

    @Test
    void ewmaCovarianceIsSymmetric() {
        double[][] data = {{0.01, 0.02}, {-0.01, 0.03}, {0.02, -0.01}, {-0.02, 0.01}};
        var returns = org.ojalgo.matrix.MatrixR064.FACTORY.rows(data);
        var cov = MatrixUtils.ewmaCovariance(returns, 0.94);

        assertEquals(cov.get(0, 1), cov.get(1, 0), 1e-10, "EWMA cov should be symmetric");
    }

    @Test
    void ewmaCovarianceGivesMoreWeightToRecent() {
        // Asset 0: stable low vol, then sudden high vol
        double[][] data = {
            {0.01, 0.01},
            {0.01, 0.01},
            {0.01, 0.01},
            {0.10, 0.01}  // spike
        };
        var returns = org.ojalgo.matrix.MatrixR064.FACTORY.rows(data);

        // Low lambda = fast adaptation (more weight on recent)
        var ewmaFast = MatrixUtils.ewmaCovariance(returns, 0.3);
        // High lambda = slow adaptation (more weight on history)
        var ewmaSlow = MatrixUtils.ewmaCovariance(returns, 0.99);

        // Fast EWMA should react more to the spike than slow EWMA
        double fastVar0 = ewmaFast.get(0, 0);
        double slowVar0 = ewmaSlow.get(0, 0);
        assertTrue(fastVar0 > slowVar0,
            "Fast EWMA should react more to recent spike: fast=" + fastVar0 + " > slow=" + slowVar0);
    }

    @Test
    void ewmaCovarianceWithLambda1FreezesAtWarmupEstimate() {
        double[][] data = {{0.01, 0.02}, {0.03, 0.04}};
        var returns = org.ojalgo.matrix.MatrixR064.FACTORY.rows(data);
        var cov = MatrixUtils.ewmaCovariance(returns, 1.0);

        // With lambda=1, EWMA stays at the warmup sample covariance
        double mu0 = (0.01 + 0.03) / 2;
        double mu1 = (0.02 + 0.04) / 2;
        double var0 = ((0.01 - mu0) * (0.01 - mu0) + (0.03 - mu0) * (0.03 - mu0)) / 1;
        double cov01 = ((0.01 - mu0) * (0.02 - mu1) + (0.03 - mu0) * (0.04 - mu1)) / 1;
        assertEquals(var0, cov.get(0, 0), 1e-10);
        assertEquals(cov01, cov.get(0, 1), 1e-10);
    }

    @Test
    void ledoitWolfLambdaIsBetween0And1() {
        double[][] data = {
            {0.01, 0.02}, {-0.01, 0.03}, {0.02, -0.01}, {-0.02, 0.01},
            {0.015, 0.025}, {-0.015, 0.02}, {0.005, -0.005}, {-0.005, 0.015}
        };
        var returns = org.ojalgo.matrix.MatrixR064.FACTORY.rows(data);
        double lambda = MatrixUtils.ledoitWolfLambda(returns);

        assertTrue(lambda >= 0 && lambda <= 1,
            "Ledoit-Wolf lambda should be in [0,1]: " + lambda);
    }

    @Test
    void ledoitWolfCovarianceIsSymmetric() {
        double[][] data = {
            {0.01, 0.02, 0.03}, {-0.01, 0.03, -0.02}, {0.02, -0.01, 0.01},
            {-0.02, 0.01, -0.01}, {0.015, 0.025, 0.02}, {-0.015, 0.02, -0.03}
        };
        var returns = org.ojalgo.matrix.MatrixR064.FACTORY.rows(data);
        var cov = MatrixUtils.ledoitWolfCovariance(returns);

        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                assertEquals(cov.get(i, j), cov.get(j, i), 1e-10);
    }

    @Test
    void ledoitWolfShrinksTowardTarget() {
        // With very few observations, sample cov is noisy, LW should shrink more
        double[][] data = {
            {0.05, -0.05}, {-0.05, 0.05}
        };
        var returns = org.ojalgo.matrix.MatrixR064.FACTORY.rows(data);
        double lambda = MatrixUtils.ledoitWolfLambda(returns);

        // With only 2 obs and 2 assets, lambda should be less than 1 (some shrinkage)
        assertTrue(lambda < 1.0,
            "With limited data, LW should apply some shrinkage: " + lambda);
    }
}
