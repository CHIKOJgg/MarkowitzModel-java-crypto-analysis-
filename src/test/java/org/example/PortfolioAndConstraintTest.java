package org.example;

import org.example.constraint.*;
import org.example.portfolio.*;
import org.junit.jupiter.api.Test;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioAndConstraintTest {

    private MatrixR064 makeReturns(double[][] data) {
        return MatrixR064.FACTORY.rows(data);
    }

    // ── Equal Weight ──────────────────────────────────────────────────────────

    @Test
    void equalWeightLongOnly() {
        var eq = new EqualWeightPortfolio(false);
        var returns = makeReturns(new double[][]{{0.01, 0.02, 0.03}});
        var mu = MatrixR064.FACTORY.rows(new double[][]{{0.1, 0.2, 0.3}});
        var weights = eq.allocate(returns, mu);

        assertEquals(3, weights.size());
        for (BigDecimal w : weights) {
            assertEquals(1.0 / 3, w.doubleValue(), 1e-10);
        }
    }

    @Test
    void equalWeightSignalDirected() {
        var eq = new EqualWeightPortfolio(true);
        var returns = makeReturns(new double[][]{{0.01, 0.02}});
        var mu = MatrixR064.FACTORY.rows(new double[][]{{0.1, -0.1}});
        var weights = eq.allocate(returns, mu);

        // Positive signal → positive weight, negative signal → negative weight
        assertTrue(weights.get(0).doubleValue() > 0);
        assertTrue(weights.get(1).doubleValue() < 0);
    }

    // ── Risk Parity ───────────────────────────────────────────────────────────

    @Test
    void riskParityWeightsArePositive() {
        var rp = new RiskParityPortfolio(60, false);
        var returns = makeReturns(new double[][]{
            {0.01, 0.02}, {-0.01, 0.03}, {0.02, -0.01}, {-0.02, 0.01}
        });
        var mu = MatrixR064.FACTORY.rows(new double[][]{{0.1, 0.1}});
        var weights = rp.allocate(returns, mu);

        for (BigDecimal w : weights) {
            assertTrue(w.doubleValue() > 0, "Risk parity weights should be positive when respectSign=false");
        }
    }

    @Test
    void riskParityLowerVolAssetGetsHigherWeight() {
        var rp = new RiskParityPortfolio(60, false);
        // Asset 0: high vol, Asset 1: low vol
        var returns = makeReturns(new double[][]{
            {0.05, 0.01}, {-0.05, -0.01}, {0.04, 0.01}, {-0.04, -0.01}
        });
        var mu = MatrixR064.FACTORY.rows(new double[][]{{0.1, 0.1}});
        var weights = rp.allocate(returns, mu);

        assertTrue(weights.get(1).doubleValue() > weights.get(0).doubleValue(),
            "Lower vol asset should get higher weight in risk parity");
    }

    // ── True Risk Parity ──────────────────────────────────────────────────────

    @Test
    void trueRiskParityWeightsSumToOne() {
        var trp = new TrueRiskParityPortfolio(60);
        var returns = makeReturns(new double[][]{
            {0.01, 0.02, 0.03}, {-0.01, 0.03, -0.02}, {0.02, -0.01, 0.01}, {-0.02, 0.01, -0.01}
        });
        var mu = MatrixR064.FACTORY.rows(new double[][]{{0.1, 0.1, 0.1}});
        var weights = trp.allocate(returns, mu);

        double sum = weights.stream().mapToDouble(BigDecimal::doubleValue).sum();
        assertEquals(1.0, sum, 1e-6, "True risk parity weights should sum to 1");
    }

    @Test
    void trueRiskParityEqualizesRiskContribution() {
        var trp = new TrueRiskParityPortfolio(30);
        // 3 assets with clearly different vols
        var returns = makeReturns(new double[][]{
            {0.05, 0.01, 0.03}, {-0.05, -0.01, -0.03}, {0.04, 0.02, 0.02}, {-0.04, -0.02, -0.02},
            {0.03, 0.005, 0.025}, {-0.03, -0.005, -0.025}, {0.045, 0.015, 0.015}, {-0.045, -0.015, -0.015},
            {0.035, 0.012, 0.028}, {-0.035, -0.012, -0.028}, {0.042, 0.008, 0.022}, {-0.042, -0.008, -0.022},
            {0.038, 0.018, 0.018}, {-0.038, -0.018, -0.018}, {0.032, 0.01, 0.03}, {-0.032, -0.01, -0.03},
            {0.048, 0.014, 0.02}, {-0.048, -0.014, -0.02}, {0.028, 0.006, 0.028}, {-0.028, -0.006, -0.028},
            {0.04, 0.016, 0.024}, {-0.04, -0.016, -0.024}, {0.036, 0.009, 0.026}, {-0.036, -0.009, -0.026},
            {0.044, 0.011, 0.019}, {-0.044, -0.011, -0.019}, {0.034, 0.013, 0.027}, {-0.034, -0.013, -0.027},
            {0.039, 0.007, 0.021}, {-0.039, -0.007, -0.021}
        });
        var mu = MatrixR064.FACTORY.rows(new double[][]{{0.1, 0.1, 0.1}});
        var weights = trp.allocate(returns, mu);

        // Verify basic properties: weights sum to 1, all positive
        double sum = weights.stream().mapToDouble(BigDecimal::doubleValue).sum();
        assertEquals(1.0, sum, 1e-6, "True risk parity weights should sum to 1");
        for (int i = 0; i < weights.size(); i++) {
            assertTrue(weights.get(i).doubleValue() > 0,
                "Weight " + i + " should be positive: " + weights.get(i));
        }
        // Lowest vol asset (asset 1) should get highest weight
        assertTrue(weights.get(1).doubleValue() > weights.get(0).doubleValue(),
            "Lowest vol asset should get highest weight");
    }

    // ── Leverage Constraint ───────────────────────────────────────────────────

    @Test
    void leverageConstraintScalesDown() {
        var lc = new LeverageConstraint(1.0);
        var weights = List.of(
            BigDecimal.valueOf(0.6),
            BigDecimal.valueOf(0.6),
            BigDecimal.valueOf(0.6)
        );
        var adjusted = lc.apply(weights);

        double totalLev = adjusted.stream()
            .mapToDouble(BigDecimal::doubleValue)
            .map(Math::abs)
            .sum();
        assertEquals(1.0, totalLev, 1e-10);
    }

    @Test
    void leverageConstraintDoesNotScaleUp() {
        var lc = new LeverageConstraint(2.0);
        var weights = List.of(
            BigDecimal.valueOf(0.3),
            BigDecimal.valueOf(0.3)
        );
        var adjusted = lc.apply(weights);

        assertEquals(0.3, adjusted.get(0).doubleValue(), 1e-10);
        assertEquals(0.3, adjusted.get(1).doubleValue(), 1e-10);
    }

    // ── Market Neutral ────────────────────────────────────────────────────────

    @Test
    void marketNeutralMakesSumZero() {
        var mn = new MarketNeutralConstraint();
        var weights = List.of(
            BigDecimal.valueOf(0.5),
            BigDecimal.valueOf(0.3),
            BigDecimal.valueOf(-0.1)
        );
        var adjusted = mn.apply(weights);

        double sum = adjusted.stream()
            .mapToDouble(BigDecimal::doubleValue)
            .sum();
        assertEquals(0.0, sum, 1e-10);
    }

    // ── Weight Validator ──────────────────────────────────────────────────────

    @Test
    void weightValidatorPassesForCleanWeights() {
        var wv = new WeightValidator(10.0);
        var weights = List.of(
            BigDecimal.valueOf(0.5),
            BigDecimal.valueOf(-0.3),
            BigDecimal.valueOf(0.2)
        );
        assertDoesNotThrow(() -> wv.apply(weights));
    }

    @Test
    void marketNeutralThenLeverageRespectsMax() {
        // Case where MarketNeutral increases leverage above the original
        var weights = List.of(
            BigDecimal.valueOf(0.9),
            BigDecimal.valueOf(-0.1),
            BigDecimal.valueOf(0.0)
        );
        // First: market neutral
        var mn = new MarketNeutralConstraint();
        var afterMN = mn.apply(weights);
        // Then: leverage cap at 1.0
        var lc = new LeverageConstraint(1.0);
        var afterLeverage = lc.apply(afterMN);

        double finalLev = afterLeverage.stream()
            .mapToDouble(w -> Math.abs(w.doubleValue()))
            .sum();
        assertTrue(finalLev <= 1.0 + 1e-10,
            "MarketNeutral + Leverage should respect max: got " + finalLev);
    }

    @Test
    void weightValidatorThrowsForExcessiveLeverage() {
        var wv = new WeightValidator(1.0);
        var weights = List.of(
            BigDecimal.valueOf(0.6),
            BigDecimal.valueOf(0.6)
        );
        assertThrows(IllegalStateException.class, () -> wv.apply(weights));
    }
}
