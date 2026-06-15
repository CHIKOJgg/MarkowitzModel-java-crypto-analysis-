package org.example;

import org.example.alpha.RSIAlpha;
import org.example.forecast.ForecastEngine;
import org.example.forecast.ForecastResult;
import org.example.portfolio.BlackLittermanPortfolio;
import org.example.portfolio.CvarPortfolio;
import org.example.util.MatrixUtils;
import org.junit.jupiter.api.Test;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NewModelsTest {

    private MatrixR064 makeReturns(double[][] data) {
        return MatrixR064.FACTORY.rows(data);
    }

    // ── RSI Alpha ─────────────────────────────────────────────────────────────

    @Test
    void rsiAlphaProducesValidRange() {
        double[][] data = new double[50][3];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 50; i++)
            for (int j = 0; j < 3; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        var rsi = new RSIAlpha(14);
        var result = rsi.predict(returns);

        assertEquals(1, (int) result.countRows());
        assertEquals(3, (int) result.countColumns());

        for (int j = 0; j < 3; j++) {
            double v = result.get(0, j);
            assertTrue(v >= -1.5 && v <= 1.5,
                "RSI signal should be in reasonable range: " + v);
        }
    }

    @Test
    void rsiAlphaDetectsOverbought() {
        // Strong uptrend → RSI should be high → negative signal
        double[][] data = new double[30][1];
        for (int i = 0; i < 30; i++) data[i][0] = 0.02; // consistent positive

        var returns = makeReturns(data);
        var rsi = new RSIAlpha(14);
        var result = rsi.predict(returns);

        assertTrue(result.get(0, 0) < 0,
            "Strong uptrend should produce negative RSI signal (overbought)");
    }

    @Test
    void rsiAlphaDetectsOversold() {
        // Strong downtrend → RSI should be low → positive signal
        double[][] data = new double[30][1];
        for (int i = 0; i < 30; i++) data[i][0] = -0.02;

        var returns = makeReturns(data);
        var rsi = new RSIAlpha(14);
        var result = rsi.predict(returns);

        assertTrue(result.get(0, 0) > 0,
            "Strong downtrend should produce positive RSI signal (oversold)");
    }

    // ── Black-Litterman ───────────────────────────────────────────────────────

    @Test
    void blackLittermanWeightsSumToOne() {
        var bl = new BlackLittermanPortfolio();
        var returns = makeReturns(new double[][]{
            {0.01, 0.02, 0.03}, {-0.01, 0.03, -0.02}, {0.02, -0.01, 0.01},
            {-0.02, 0.01, -0.01}, {0.015, 0.025, 0.02}, {-0.015, 0.02, -0.03}
        });
        var mu = MatrixR064.FACTORY.rows(new double[][]{{0.1, 0.2, 0.15}});
        var weights = bl.allocate(returns, mu);

        double sum = weights.stream().mapToDouble(BigDecimal::doubleValue).sum();
        assertEquals(1.0, sum, 0.01, "BL weights should sum to ~1");
    }

    @Test
    void blackLittermanWeightsAreValid() {
        var bl = new BlackLittermanPortfolio();
        var returns = makeReturns(new double[][]{
            {0.01, 0.02}, {-0.01, 0.03}, {0.02, -0.01}, {-0.02, 0.01},
            {0.015, 0.025}, {-0.015, 0.02}
        });
        var mu = MatrixR064.FACTORY.rows(new double[][]{{0.05, 0.3}});
        var weights = bl.allocate(returns, mu);

        // Check weights are valid (finite, reasonable range)
        for (BigDecimal w : weights) {
            assertFalse(Double.isNaN(w.doubleValue()), "Weight should not be NaN");
            assertFalse(Double.isInfinite(w.doubleValue()), "Weight should not be infinite");
            assertTrue(Math.abs(w.doubleValue()) <= 1.0,
                "Weight should be in [-1, 1]: " + w);
        }

        double sum = weights.stream().mapToDouble(BigDecimal::doubleValue).sum();
        assertEquals(1.0, sum, 0.05, "BL weights should sum to ~1");
    }

    // ── CVaR Optimizer ────────────────────────────────────────────────────────

    @Test
    void cvarWeightsAreNonNegative() {
        var cvar = new CvarPortfolio(0.95, 50, 0.5);
        var returns = makeReturns(new double[][]{
            {0.01, 0.02, -0.01}, {-0.01, 0.03, 0.02}, {0.02, -0.01, 0.01},
            {-0.02, 0.01, -0.01}, {0.015, 0.025, 0.02}, {-0.015, 0.02, -0.03}
        });
        var mu = MatrixR064.FACTORY.rows(new double[][]{{0.1, 0.2, 0.15}});
        var weights = cvar.allocate(returns, mu);

        for (BigDecimal w : weights) {
            assertTrue(w.doubleValue() >= 0,
                "CVaR weights should be non-negative (long-only): " + w);
        }
    }

    @Test
    void cvarWeightsSumToOne() {
        var cvar = new CvarPortfolio();
        var returns = makeReturns(new double[][]{
            {0.01, 0.02, 0.03}, {-0.01, 0.03, -0.02}, {0.02, -0.01, 0.01},
            {-0.02, 0.01, -0.01}, {0.015, 0.025, 0.02}, {-0.015, 0.02, -0.03}
        });
        var mu = MatrixR064.FACTORY.rows(new double[][]{{0.1, 0.2, 0.15}});
        var weights = cvar.allocate(returns, mu);

        double sum = weights.stream().mapToDouble(BigDecimal::doubleValue).sum();
        assertEquals(1.0, sum, 0.01, "CVaR weights should sum to ~1");
    }

    // ── Forecast Engine ───────────────────────────────────────────────────────

    @Test
    void forecastProducesValidResults() {
        double[][] data = new double[100][3];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 100; i++)
            for (int j = 0; j < 3; j++)
                data[i][j] = rand.nextGaussian() * 0.02 + 0.001;

        var returns = makeReturns(data);
        var engine = new ForecastEngine();
        var results = engine.forecast(returns, 10, List.of("BTC", "ETH", "SOL"));

        assertEquals(3, results.size());

        for (var r : results) {
            assertEquals(10, r.pointForecast().size());
            assertEquals(10, r.lower95().size());
            assertEquals(10, r.upper95().size());

            // 95% CI should be wider than 50% CI
            for (int h = 0; h < 10; h++) {
                double width95 = r.upper95().get(h) - r.lower95().get(h);
                double width50 = r.upper50().get(h) - r.lower50().get(h);
                assertTrue(width95 > width50,
                    "95% CI should be wider than 50% CI at horizon " + h);
            }

            // Annualized vol should be positive
            assertTrue(r.annualizedVol() > 0);
        }
    }

    // ── Matrix Utils Extensions ───────────────────────────────────────────────

    @Test
    void resampleWeeklyReducesRows() {
        double[][] data = new double[25][3];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 25; i++)
            for (int j = 0; j < 3; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        var weekly = MatrixUtils.resample(returns, 5);

        assertEquals(5, (int) weekly.countRows(), "25 daily / 5 = 5 weekly");
        assertEquals(3, (int) weekly.countColumns());
    }

    @Test
    void correlationMatrixDiagonalIsOne() {
        double[][] data = new double[20][3];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 20; i++)
            for (int j = 0; j < 3; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        var corr = MatrixUtils.correlationMatrix(returns);

        for (int i = 0; i < 3; i++) {
            assertEquals(1.0, corr.get(i, i), 1e-10,
                "Correlation diagonal should be 1");
        }
    }

    @Test
    void correlationMatrixBoundsAreMinusOneToOne() {
        double[][] data = new double[20][3];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 20; i++)
            for (int j = 0; j < 3; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        var corr = MatrixUtils.correlationMatrix(returns);

        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                assertTrue(corr.get(i, j) >= -1.0 && corr.get(i, j) <= 1.0,
                    "Correlation should be in [-1, 1]");
    }
}
