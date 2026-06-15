package org.example;

import org.example.alpha.*;
import org.junit.jupiter.api.Test;
import org.ojalgo.matrix.MatrixR064;

import static org.junit.jupiter.api.Assertions.*;

class AlphaModelTest {

    private MatrixR064 makeReturns(double[][] data) {
        return MatrixR064.FACTORY.rows(data);
    }

    // ── EWMA ──────────────────────────────────────────────────────────────────

    @Test
    void ewmaAlphaFollowsRecentReturns() {
        // 5 days of returns, 2 assets
        double[][] data = {
            {0.01, 0.02},
            {0.03, 0.04},
            {0.02, 0.01},
            {0.05, -0.01},
            {0.04, 0.03}
        };
        var returns = makeReturns(data);
        var ewma = new EWMAAlpha(0.3);

        var result = ewma.predict(returns);

        // Result should be a [1 x 2] matrix
        assertEquals(1, (int) result.countRows());
        assertEquals(2, (int) result.countColumns());
        // Should not be NaN
        assertFalse(Double.isNaN(result.get(0, 0)));
        assertFalse(Double.isNaN(result.get(0, 1)));
    }

    @Test
    void ewmaAlphaWithHighAlphaTracksRecentMore() {
        double[][] data = {
            {0.01, 0.01},
            {0.01, 0.01},
            {0.01, 0.01},
            {0.10, 0.01}  // sudden jump in asset 0
        };
        var returns = makeReturns(data);

        var lowAlpha = new EWMAAlpha(0.1).predict(returns);
        var highAlpha = new EWMAAlpha(0.9).predict(returns);

        // High alpha should react more to the 0.10 jump
        assertTrue(highAlpha.get(0, 0) > lowAlpha.get(0, 0),
            "High alpha EWMA should track the recent jump more");
    }

    @Test
    void ewmaAlphaShortPenaltyApplied() {
        double[][] data = {{-0.05, 0.01}};
        var returns = makeReturns(data);

        var noPenalty = new EWMAAlpha(0.5, 0.0).predict(returns);
        var withPenalty = new EWMAAlpha(0.5, 0.05).predict(returns);

        // Negative signal should be more negative with penalty
        assertTrue(withPenalty.get(0, 0) < noPenalty.get(0, 0),
            "Short penalty should make negative signals more negative");
    }

    @Test
    void ewmaAlphaInvalidAlphaThrows() {
        assertThrows(IllegalArgumentException.class, () -> new EWMAAlpha(0.0));
        assertThrows(IllegalArgumentException.class, () -> new EWMAAlpha(1.5));
        assertThrows(IllegalArgumentException.class, () -> new EWMAAlpha(-0.1));
    }

    // ── Momentum ──────────────────────────────────────────────────────────────

    @Test
    void momentumAlphaDetectsTrend() {
        // Asset 0: strong uptrend, Asset 1: flat
        double[][] data = {
            {0.01, 0.00},
            {0.02, 0.00},
            {0.03, 0.00},
            {0.04, 0.00}
        };
        var returns = makeReturns(data);
        var mom = new MomentumAlpha(4);

        var result = mom.predict(returns);

        // Asset 0 should have higher (positive) signal than asset 1
        assertTrue(result.get(0, 0) > result.get(0, 1),
            "Momentum should favor the trending asset");
    }

    @Test
    void momentumAlphaDemeansCrossSectionally() {
        double[][] data = {
            {0.05, 0.05},
            {0.05, 0.05}
        };
        var returns = makeReturns(data);
        var mom = new MomentumAlpha(2);

        var result = mom.predict(returns);

        // Cross-sectional mean subtraction: both signals should sum to ~0
        double sum = result.get(0, 0) + result.get(0, 1);
        assertEquals(0.0, sum, 1e-10,
            "Cross-sectional demeaning should make signals sum to ~0");
    }

    // ── Mean Reversion ────────────────────────────────────────────────────────

    @Test
    void meanReversionSignalIsContrarian() {
        // Last return is high positive → should get negative signal
        double[][] data = {
            {0.01, 0.01},
            {0.01, 0.01},
            {0.01, 0.01},
            {0.10, 0.01}  // asset 0 spiked
        };
        var returns = makeReturns(data);
        var mr = new MeanReversionAlpha(3);

        var result = mr.predict(returns);

        // Asset 0 spiked → contrarian signal should be negative
        assertTrue(result.get(0, 0) < 0,
            "Mean reversion should give negative signal to recent outperformer");
    }

    // ── Blended ───────────────────────────────────────────────────────────────

    @Test
    void blendedAlphaCombinesSignals() {
        double[][] data = {
            {0.01, 0.02},
            {0.03, 0.04},
            {0.02, 0.01}
        };
        var returns = makeReturns(data);

        var alpha1 = new EWMAAlpha(0.5);
        var alpha2 = new MomentumAlpha(3);
        var blend = BlendedAlpha.equalWeight(alpha1, alpha2);

        var result = blend.predict(returns);

        // Should be average of individual signals
        var sig1 = alpha1.predict(returns);
        var sig2 = alpha2.predict(returns);
        for (int j = 0; j < 2; j++) {
            double expected = (sig1.get(0, j) + sig2.get(0, j)) / 2.0;
            assertEquals(expected, result.get(0, j), 1e-10);
        }
    }

    @Test
    void blendedAlphaWeightsAreNormalized() {
        var blend = new BlendedAlpha(
            java.util.List.of(new EWMAAlpha(0.5), new MomentumAlpha(20)),
            java.util.List.of(3.0, 1.0)
        );
        String name = blend.name();
        assertTrue(name.contains("75%") && name.contains("25%"));
    }

    // ── VolAdjusted ───────────────────────────────────────────────────────────

    @Test
    void volAdjustedAlphaDividesByVol() {
        // Asset 0: high vol, Asset 1: low vol
        double[][] data = {
            {0.05, 0.01}, {-0.05, -0.01}, {0.04, 0.01}, {-0.04, -0.01},
            {0.03, 0.01}, {-0.03, -0.01}, {0.02, 0.01}, {-0.02, -0.01}
        };
        var returns = makeReturns(data);
        var base = new EWMAAlpha(0.5);

        var raw = base.predict(returns);
        var volAdj = new VolAdjustedAlpha(base, 4).predict(returns);

        // Vol-adjusted signal for high-vol asset should be smaller (divided by larger vol)
        // For low-vol asset, the ratio should be preserved or amplified
        double rawRatio = Math.abs(raw.get(0, 0)) / Math.abs(raw.get(0, 1));
        double adjRatio = Math.abs(volAdj.get(0, 0)) / Math.abs(volAdj.get(0, 1));

        // The ratio should decrease because high-vol asset gets divided by larger vol
        assertTrue(adjRatio < rawRatio || Math.abs(adjRatio - rawRatio) < 0.01,
            "Vol adjustment should reduce the relative signal of high-vol assets");
    }

    @Test
    void volAdjustedAlphaNameIncludesInner() {
        var va = new VolAdjustedAlpha(new MomentumAlpha(20));
        assertTrue(va.name().contains("Momentum"));
        assertTrue(va.name().contains("VolAdj"));
    }
}
