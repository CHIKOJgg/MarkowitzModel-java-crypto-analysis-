package org.example;

import org.example.engine.AttributionResult;
import org.example.engine.PerformanceAttribution;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceAttributionTest {

    private final PerformanceAttribution engine = new PerformanceAttribution();

    @Test
    void equalWeightsAndEqualReturnsProduceZeroAttribution() {
        int n = 4;
        var weights = List.of(
                BigDecimal.valueOf(0.25), BigDecimal.valueOf(0.25),
                BigDecimal.valueOf(0.25), BigDecimal.valueOf(0.25));
        var benchWeights = List.of(0.25, 0.25, 0.25, 0.25);
        var returns = List.of(0.05, 0.05, 0.05, 0.05);

        var result = engine.attribute(weights, returns, benchWeights);

        assertEquals(0.0, result.allocationEffect(), 1e-10,
                "Allocation effect should be zero with equal weights/returns");
        assertEquals(0.0, result.selectionEffect(), 1e-10,
                "Selection effect should be zero with equal weights/returns");
        assertEquals(0.0, result.interactionEffect(), 1e-10,
                "Interaction effect should be zero with equal weights/returns");
        assertEquals(0.0, result.excessReturn(), 1e-10,
                "Excess return should be zero with equal weights/returns");
    }

    @Test
    void concentratedPortfolioVsDiversified() {
        // Portfolio overweight asset with high return
        var weights = List.of(
                BigDecimal.valueOf(0.7),
                BigDecimal.valueOf(0.15),
                BigDecimal.valueOf(0.15));
        var benchWeights = List.of(1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0);
        var returns = List.of(0.10, 0.02, 0.03);

        var result = engine.attribute(weights, returns, benchWeights);

        // Overweight best-performing asset → positive selection
        assertTrue(result.selectionEffect() >= 0,
                "Selection effect should be non-negative when overweighting best asset: "
                        + result.selectionEffect());
    }

    @Test
    void sumOfEffectsEqualsExcessReturn() {
        var weights = List.of(
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(0.3),
                BigDecimal.valueOf(0.2));
        var benchWeights = List.of(1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0);
        var returns = List.of(0.08, -0.02, 0.05);

        var result = engine.attribute(weights, returns, benchWeights);

        double sumEffects = result.allocationEffect()
                + result.selectionEffect()
                + result.interactionEffect();
        assertEquals(result.excessReturn(), sumEffects, 1e-10,
                "Sum of effects should equal excess return");
    }

    @Test
    void assetContributionsLengthMatchesNumAssets() {
        var weights = List.of(
                BigDecimal.valueOf(0.4),
                BigDecimal.valueOf(0.35),
                BigDecimal.valueOf(0.25));
        var benchWeights = List.of(1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0);
        var returns = List.of(0.06, 0.03, -0.01);

        var result = engine.attribute(weights, returns, benchWeights);

        assertEquals(3, result.assetContributions().length,
                "Asset contributions length should match number of assets");
    }

    @Test
    void assetContributionsSumToExcessReturn() {
        var weights = List.of(
                BigDecimal.valueOf(0.6),
                BigDecimal.valueOf(0.4));
        var benchWeights = List.of(0.5, 0.5);
        var returns = List.of(0.10, -0.05);

        var result = engine.attribute(weights, returns, benchWeights);

        double contribSum = 0;
        for (double c : result.assetContributions()) {
            contribSum += c;
        }
        assertEquals(result.excessReturn(), contribSum, 1e-10,
                "Sum of asset contributions should equal excess return");
    }

    @Test
    void concentratedVsDiversifiedGivesPositiveAllocation() {
        // Benchmark is equal-weight; portfolio is concentrated in asset 0 (best return)
        var weights = List.of(
                BigDecimal.valueOf(0.9),
                BigDecimal.valueOf(0.05),
                BigDecimal.valueOf(0.05));
        var benchWeights = List.of(1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0);
        var returns = List.of(0.10, 0.02, 0.02);

        var result = engine.attribute(weights, returns, benchWeights);

        // Overweighting the best-performing asset relative to benchmark → positive allocation
        assertTrue(result.allocationEffect() > 0,
                "Concentrated portfolio in best asset should have positive allocation: "
                        + result.allocationEffect());
    }

    @Test
    void portfolioAndBenchmarkReturnsAreCorrect() {
        var weights = List.of(
                BigDecimal.valueOf(0.6),
                BigDecimal.valueOf(0.4));
        var benchWeights = List.of(0.5, 0.5);
        var returns = List.of(0.10, -0.05);

        var result = engine.attribute(weights, returns, benchWeights);

        double expectedPortReturn = 0.6 * 0.10 + 0.4 * (-0.05);
        double expectedBenchReturn = 0.5 * 0.10 + 0.5 * (-0.05);

        assertEquals(expectedPortReturn, result.totalReturn(), 1e-10,
                "Portfolio return should match");
        assertEquals(expectedBenchReturn, result.benchmarkReturn(), 1e-10,
                "Benchmark return should match");
        assertEquals(expectedPortReturn - expectedBenchReturn, result.excessReturn(), 1e-10,
                "Excess return should be difference");
    }
}
