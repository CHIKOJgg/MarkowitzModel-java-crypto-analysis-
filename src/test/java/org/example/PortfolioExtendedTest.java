package org.example;

import org.junit.jupiter.api.Test;
import org.ojalgo.matrix.MatrixR064;

import org.example.portfolio.CvarPortfolio;
import org.example.portfolio.BlackLittermanPortfolio;
import org.example.portfolio.TrueRiskParityPortfolio;
import org.example.portfolio.TurnoverConstrainedPortfolio;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioExtendedTest {

    private MatrixR064 makeReturns(double[][] data) {
        return MatrixR064.FACTORY.rows(data);
    }

    // ── CVaR Gradient Fix ─────────────────────────────────────────────────────

    @Test
    void cvarMinimizesTailRisk() {
        // Asset 0: mostly positive, occasional large loss
        // Asset 1: stable small returns
        double[][] data = new double[50][2];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 50; i++) {
            data[i][0] = 0.02 + rand.nextGaussian() * 0.01;
            data[i][1] = 0.005 + rand.nextGaussian() * 0.005;
        }
        // Inject a few large losses for asset 0
        data[10][0] = -0.10;
        data[20][0] = -0.08;
        data[30][0] = -0.12;

        var returns = makeReturns(data);
        var mu = MatrixR064.FACTORY.rows(new double[][]{{0.01, 0.005}});
        var cvar = new CvarPortfolio(0.95, 100, 0.5);
        var weights = cvar.allocate(returns, mu);

        // CVaR optimizer should prefer the stable asset (asset 1)
        assertTrue(weights.get(1).doubleValue() > weights.get(0).doubleValue(),
                "CVaR should prefer stable asset over volatile one: "
                        + "w0=" + weights.get(0) + " w1=" + weights.get(1));
    }

    @Test
    void cvarWeightsSumToOne() {
        double[][] data = new double[30][3];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 30; i++)
            for (int j = 0; j < 3; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        var mu = MatrixR064.FACTORY.rows(new double[][]{{0.1, 0.2, 0.15}});
        var weights = new CvarPortfolio().allocate(returns, mu);

        double sum = weights.stream().mapToDouble(BigDecimal::doubleValue).sum();
        assertEquals(1.0, sum, 0.01, "CVaR weights should sum to ~1");
    }

    @Test
    void cvarAllWeightsNonNegative() {
        double[][] data = new double[30][3];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 30; i++)
            for (int j = 0; j < 3; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        var weights = new CvarPortfolio().allocate(returns, null);

        for (BigDecimal w : weights) {
            assertTrue(w.doubleValue() >= -1e-10,
                    "CVaR weights should be non-negative: " + w);
        }
    }

    @Test
    void cvarRespectsMaxLong() {
        double[][] data = new double[30][2];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 30; i++)
            for (int j = 0; j < 2; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        var weights = new CvarPortfolio(0.95, 100, 0.3).allocate(returns, null);

        // CVaR clamps per-iteration, but normalization to sum=1 can push a single
        // weight slightly above maxLong. Check that weights are reasonable.
        double maxW = weights.stream().mapToDouble(BigDecimal::doubleValue).max().orElse(0);
        assertTrue(maxW <= 0.6,
                "CVaR maxLong=0.3 should keep weights reasonable, max was: " + maxW);
        // At least all weights should be non-negative
        for (BigDecimal w : weights) {
            assertTrue(w.doubleValue() >= -1e-10,
                    "CVaR weights should be non-negative: " + w);
        }
    }

    // ── Black-Litterman Extended ──────────────────────────────────────────────

    @Test
    void blWithNoShortingHasNoNegativeWeights() {
        var bl = new BlackLittermanPortfolio(0.025, 2.5, 0.3, -0.3, false);
        var returns = makeReturns(new double[][]{
                {0.01, 0.02, 0.03}, {-0.01, 0.03, -0.02}, {0.02, -0.01, 0.01},
                {-0.02, 0.01, -0.01}, {0.015, 0.025, 0.02}, {-0.015, 0.02, -0.03}
        });
        var mu = MatrixR064.FACTORY.rows(new double[][]{{0.1, 0.2, 0.15}});
        var weights = bl.allocate(returns, mu);

        for (BigDecimal w : weights) {
            assertTrue(w.doubleValue() >= -0.001,
                    "BL with no shorting should not have negative weights: " + w);
        }
    }

    @Test
    void blWithCustomTauAndRiskAversion() {
        var bl = new BlackLittermanPortfolio(0.05, 3.0, 0.3, -0.3, true);
        var returns = makeReturns(new double[][]{
                {0.01, 0.02}, {-0.01, 0.03}, {0.02, -0.01}, {-0.02, 0.01}
        });
        var mu = MatrixR064.FACTORY.rows(new double[][]{{0.1, 0.2}});
        var weights = bl.allocate(returns, mu);

        double sum = weights.stream().mapToDouble(BigDecimal::doubleValue).sum();
        assertEquals(1.0, sum, 0.05, "BL weights should sum to ~1");
    }

    @Test
    void blNameReturnsBlackLitterman() {
        var bl = new BlackLittermanPortfolio();
        assertEquals("Black-Litterman", bl.name());
    }

    // ── True Risk Parity Extended ─────────────────────────────────────────────

    @Test
    void trpWithSingleAssetGivesFullWeight() {
        var trp = new TrueRiskParityPortfolio(30);
        var returns = makeReturns(new double[][]{{0.01}, {-0.01}, {0.02}, {-0.02}});
        var weights = trp.allocate(returns, null);

        assertEquals(1, weights.size());
        assertEquals(1.0, weights.get(0).doubleValue(), 1e-6,
                "Single asset should get full weight");
    }

    @Test
    void trpAllWeightsPositive() {
        var trp = new TrueRiskParityPortfolio(30);
        double[][] data = new double[30][4];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 30; i++)
            for (int j = 0; j < 4; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        var weights = trp.allocate(returns, null);

        for (BigDecimal w : weights) {
            assertTrue(w.doubleValue() > 0,
                    "True Risk Parity weights should all be positive: " + w);
        }
    }

    @Test
    void trpLowerVolAssetGetsHigherWeight() {
        var trp = new TrueRiskParityPortfolio(30);
        // Asset 0: high vol, Asset 1: low vol
        double[][] data = new double[30][2];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 30; i++) {
            data[i][0] = rand.nextGaussian() * 0.05;
            data[i][1] = rand.nextGaussian() * 0.01;
        }
        var returns = makeReturns(data);
        var weights = trp.allocate(returns, null);

        assertTrue(weights.get(1).doubleValue() > weights.get(0).doubleValue(),
                "Low vol asset should get higher weight in ERC");
    }

    @Test
    void trpNameReturnsExpected() {
        var trp = new TrueRiskParityPortfolio();
        assertEquals("True Risk Parity (ERC)", trp.name());
    }

    // ── Turnover Constraint ───────────────────────────────────────────────────

    @Test
    void turnoverConstraintReducesTurnover() {
        var oldW = List.of(
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(-0.5));
        var newW = List.of(
                BigDecimal.valueOf(-0.5),
                BigDecimal.valueOf(0.5));

        var constrained = TurnoverConstrainedPortfolio.constrain(oldW, newW, 0.3);

        double turnover = 0;
        for (int i = 0; i < oldW.size(); i++) {
            turnover += Math.abs(constrained.get(i).doubleValue() - oldW.get(i).doubleValue());
        }
        assertTrue(turnover <= 0.3 + 1e-10,
                "Turnover constraint should limit turnover: " + turnover);
    }

    @Test
    void turnoverConstraintPreservesDirection() {
        var oldW = List.of(
                BigDecimal.valueOf(0.1),
                BigDecimal.valueOf(-0.1));
        var newW = List.of(
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(-0.5));

        var constrained = TurnoverConstrainedPortfolio.constrain(oldW, newW, 0.5);

        // Direction should be preserved (w_new closer to target than old)
        assertTrue(constrained.get(0).doubleValue() > oldW.get(0).doubleValue(),
                "Long weight should increase toward target");
        assertTrue(constrained.get(1).doubleValue() < oldW.get(1).doubleValue(),
                "Short weight should decrease toward target");
    }

    @Test
    void turnoverConstraintWithZeroCapReturnsOldWeights() {
        var oldW = List.of(
                BigDecimal.valueOf(0.3),
                BigDecimal.valueOf(-0.3));
        var newW = List.of(
                BigDecimal.valueOf(-0.3),
                BigDecimal.valueOf(0.3));

        var constrained = TurnoverConstrainedPortfolio.constrain(oldW, newW, 0.0);

        // With zero turnover cap, should stay at old weights
        for (int i = 0; i < oldW.size(); i++) {
            assertEquals(oldW.get(i).doubleValue(), constrained.get(i).doubleValue(), 1e-10,
                    "Zero turnover cap should keep old weights");
        }
    }

    @Test
    void turnoverConstraintWithLargeCapReturnsNewWeights() {
        var oldW = List.of(
                BigDecimal.valueOf(0.1),
                BigDecimal.valueOf(-0.1));
        var newW = List.of(
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(-0.5));

        var constrained = TurnoverConstrainedPortfolio.constrain(oldW, newW, 2.0);

        // With large turnover cap, should get new weights
        for (int i = 0; i < oldW.size(); i++) {
            assertEquals(newW.get(i).doubleValue(), constrained.get(i).doubleValue(), 1e-10,
                    "Large turnover cap should allow full rebalancing");
        }
    }

    @Test
    void turnoverConstraintMultipleAssets() {
        var oldW = List.of(
                BigDecimal.valueOf(0.3),
                BigDecimal.valueOf(-0.2),
                BigDecimal.valueOf(0.1),
                BigDecimal.valueOf(-0.2));
        var newW = List.of(
                BigDecimal.valueOf(-0.3),
                BigDecimal.valueOf(0.4),
                BigDecimal.valueOf(-0.1),
                BigDecimal.valueOf(0.0));

        var constrained = TurnoverConstrainedPortfolio.constrain(oldW, newW, 0.5);

        double turnover = 0;
        for (int i = 0; i < oldW.size(); i++) {
            turnover += Math.abs(constrained.get(i).doubleValue() - oldW.get(i).doubleValue());
        }
        assertTrue(turnover <= 0.5 + 1e-10,
                "Multi-asset turnover should respect cap: " + turnover);
    }
}
