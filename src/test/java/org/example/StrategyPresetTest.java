package org.example;

import org.example.engine.StrategyPreset;
import org.example.engine.StrategyRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StrategyPresetTest {

    @Test
    void allPresetsHaveValidLabels() {
        for (StrategyPreset p : StrategyPreset.values()) {
            assertNotNull(p.label(), "Label should not be null");
            assertFalse(p.label().isBlank(), "Label should not be blank");
        }
    }

    @Test
    void allPresetsHaveValidColors() {
        for (StrategyPreset p : StrategyPreset.values()) {
            assertNotNull(p.color(), "Color should not be null");
            assertTrue(p.color().startsWith("#"), "Color should start with #");
            assertEquals(7, p.color().length(), "Color should be 7 chars: " + p.label());
        }
    }

    @Test
    void allPresetsHaveDescriptions() {
        for (StrategyPreset p : StrategyPreset.values()) {
            assertNotNull(p.description(), "Description should not be null");
            assertTrue(p.description().length() > 20,
                    "Description should be meaningful: " + p.label());
        }
    }

    @Test
    void maxLongValuesArePositive() {
        for (StrategyPreset p : StrategyPreset.values()) {
            assertTrue(p.maxLong() > 0, "Max long should be positive: " + p.label());
        }
    }

    @Test
    void maxShortValuesArePositive() {
        for (StrategyPreset p : StrategyPreset.values()) {
            assertTrue(p.maxShort() >= 0, "Max short should be non-negative: " + p.label());
        }
    }

    @Test
    void alphaValuesArePositive() {
        for (StrategyPreset p : StrategyPreset.values()) {
            assertTrue(p.alpha() > 0, "Alpha should be positive: " + p.label());
        }
    }

    @Test
    void shrinkageBetweenZeroAndOne() {
        for (StrategyPreset p : StrategyPreset.values()) {
            assertTrue(p.shrinkage() > 0 && p.shrinkage() <= 1.0,
                    "Shrinkage should be in (0, 1]: " + p.label());
        }
    }

    @Test
    void leverageGreaterThanOne() {
        for (StrategyPreset p : StrategyPreset.values()) {
            assertTrue(p.leverage() >= 1.0,
                    "Leverage should be >= 1.0: " + p.label());
        }
    }

    @Test
    void targetReturnIsPositive() {
        for (StrategyPreset p : StrategyPreset.values()) {
            assertTrue(p.targetReturn() > 0,
                    "Target return should be positive: " + p.label());
        }
    }

    @Test
    void momentumLookbackIsPositive() {
        for (StrategyPreset p : StrategyPreset.values()) {
            assertTrue(p.momentumLookback() > 0,
                    "Momentum lookback should be positive: " + p.label());
        }
    }

    @Test
    void ewmaLambdaBetweenZeroAndOne() {
        for (StrategyPreset p : StrategyPreset.values()) {
            assertTrue(p.ewmaLambda() > 0 && p.ewmaLambda() < 1.0,
                    "EWMA lambda should be in (0, 1): " + p.label());
        }
    }

    @Test
    void conservativeLessAggressiveThanBalanced() {
        StrategyPreset c = StrategyPreset.CONSERVATIVE;
        StrategyPreset b = StrategyPreset.BALANCED;
        assertTrue(c.maxLong() <= b.maxLong(),
                "Conservative maxLong should be <= Balanced");
        assertTrue(c.leverage() <= b.leverage(),
                "Conservative leverage should be <= Balanced");
        assertTrue(c.alpha() <= b.alpha(),
                "Conservative alpha should be <= Balanced");
    }

    @Test
    void balancedLessAggressiveThanAggressive() {
        StrategyPreset b = StrategyPreset.BALANCED;
        StrategyPreset a = StrategyPreset.AGGRESSIVE;
        assertTrue(b.maxLong() <= a.maxLong(),
                "Balanced maxLong should be <= Aggressive");
        assertTrue(b.leverage() <= a.leverage(),
                "Balanced leverage should should be <= Aggressive");
        assertTrue(b.alpha() <= a.alpha(),
                "Balanced alpha should be <= Aggressive");
    }

    @Test
    void toParamsReturnsNonNull() {
        for (StrategyPreset p : StrategyPreset.values()) {
            StrategyRegistry.Params params = p.toParams();
            assertNotNull(params, "toParams should not return null: " + p.label());
        }
    }

    @Test
    void toParamsHasCorrectMaxLong() {
        StrategyPreset p = StrategyPreset.CONSERVATIVE;
        StrategyRegistry.Params params = p.toParams();
        assertEquals(p.maxLong() / 100.0, params.maxLong(), 1e-10,
                "Params maxLong should match preset");
    }

    @Test
    void toParamsHasCorrectAlpha() {
        StrategyPreset p = StrategyPreset.AGGRESSIVE;
        StrategyRegistry.Params params = p.toParams();
        assertEquals(p.alpha(), params.ewmaAlpha(), 1e-10,
                "Params ewmaAlpha should match preset");
    }

    @Test
    void toParamsHasCorrectLeverage() {
        StrategyPreset p = StrategyPreset.BALANCED;
        StrategyRegistry.Params params = p.toParams();
        assertEquals(p.leverage(), params.maxLeverage(), 1e-10,
                "Params maxLeverage should match preset");
    }

    @Test
    void toParamsHasCorrectShrinkage() {
        for (StrategyPreset p : StrategyPreset.values()) {
            StrategyRegistry.Params params = p.toParams();
            assertEquals(p.shrinkage(), params.shrinkage(), 1e-10,
                    "Params shrinkage should match preset: " + p.label());
        }
    }

    @Test
    void toParamsHasCorrectEwmaLambda() {
        for (StrategyPreset p : StrategyPreset.values()) {
            StrategyRegistry.Params params = p.toParams();
            assertEquals(p.ewmaLambda(), params.ewmaLambda(), 1e-10,
                    "Params ewmaLambda should match preset: " + p.label());
        }
    }

    @Test
    void presetsAreDistinct() {
        StrategyPreset[] values = StrategyPreset.values();
        for (int i = 0; i < values.length; i++) {
            for (int j = i + 1; j < values.length; j++) {
                assertNotEquals(values[i].label(), values[j].label(),
                        "Preset labels should be distinct");
            }
        }
    }

    @Test
    void toStringReturnsLabel() {
        for (StrategyPreset p : StrategyPreset.values()) {
            assertEquals(p.label(), p.toString(), "toString should return label");
        }
    }
}
