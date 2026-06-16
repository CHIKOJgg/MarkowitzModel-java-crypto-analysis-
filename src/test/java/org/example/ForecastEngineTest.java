package org.example;

import org.example.forecast.ForecastEngine;
import org.example.forecast.ForecastResult;
import org.junit.jupiter.api.Test;
import org.ojalgo.matrix.MatrixR064;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ForecastEngineTest {

    private MatrixR064 makeReturns(double[][] data) {
        return MatrixR064.FACTORY.rows(data);
    }

    @Test
    void forecastReturnsCorrectNumberOfAssets() {
        double[][] data = new double[60][4];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 60; i++)
            for (int j = 0; j < 4; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        var engine = new ForecastEngine();
        var results = engine.forecast(returns, 7, List.of("A", "B", "C", "D"));

        assertEquals(4, results.size(), "Should return one result per asset");
    }

    @Test
    void forecastHorizonMatchesRequested() {
        double[][] data = new double[60][2];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 60; i++)
            for (int j = 0; j < 2; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        var engine = new ForecastEngine();
        var results = engine.forecast(returns, 14, List.of("BTC", "ETH"));

        for (var r : results) {
            assertEquals(14, r.pointForecast().size(),
                    "Point forecast horizon should match request");
            assertEquals(14, r.lower95().size(),
                    "95% CI lower should match request");
            assertEquals(14, r.upper95().size(),
                    "95% CI upper should match request");
            assertEquals(14, r.lower50().size(),
                    "50% CI lower should match request");
            assertEquals(14, r.upper50().size(),
                    "50% CI upper should match request");
        }
    }

    @Test
    void forecast95CIIsWiderThan50CI() {
        double[][] data = new double[100][3];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 100; i++)
            for (int j = 0; j < 3; j++)
                data[i][j] = rand.nextGaussian() * 0.02 + 0.001;

        var returns = makeReturns(data);
        var engine = new ForecastEngine();
        var results = engine.forecast(returns, 10, List.of("A", "B", "C"));

        for (var r : results) {
            for (int h = 0; h < 10; h++) {
                double width95 = r.upper95().get(h) - r.lower95().get(h);
                double width50 = r.upper50().get(h) - r.lower50().get(h);
                assertTrue(width95 > width50,
                        "95% CI should be wider than 50% CI at horizon " + h
                                + ": width95=" + width95 + " width50=" + width50);
            }
        }
    }

    @Test
    void forecastCIBandsAreSymmetricAroundPoint() {
        double[][] data = new double[60][2];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 60; i++)
            for (int j = 0; j < 2; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        var engine = new ForecastEngine();
        var results = engine.forecast(returns, 7, List.of("A", "B"));

        for (var r : results) {
            for (int h = 0; h < 7; h++) {
                double point = r.pointForecast().get(h);
                double lower = r.lower95().get(h);
                double upper = r.upper95().get(h);
                double mid = (lower + upper) / 2.0;
                assertTrue(point >= lower && point <= upper,
                        "Point forecast should be within 95% CI");
            }
        }
    }

    @Test
    void forecastCIWidensWithHorizon() {
        double[][] data = new double[100][2];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 100; i++)
            for (int j = 0; j < 2; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        var engine = new ForecastEngine();
        var results = engine.forecast(returns, 10, List.of("A", "B"));

        for (var r : results) {
            double prevWidth = 0;
            for (int h = 0; h < 10; h++) {
                double width = r.upper95().get(h) - r.lower95().get(h);
                assertTrue(width >= prevWidth - 1e-15,
                        "95% CI width should not decrease with horizon");
                prevWidth = width;
            }
        }
    }

    @Test
    void forecastAnnualizedVolIsPositive() {
        double[][] data = new double[60][2];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 60; i++)
            for (int j = 0; j < 2; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        var engine = new ForecastEngine();
        var results = engine.forecast(returns, 7, List.of("A", "B"));

        for (var r : results) {
            assertTrue(r.annualizedVol() > 0,
                    "Annualized vol should be positive: " + r.annualizedVol());
        }
    }

    @Test
    void forecastAssetNamesPreserved() {
        double[][] data = new double[60][3];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 60; i++)
            for (int j = 0; j < 3; j++)
                data[i][j] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        var engine = new ForecastEngine();
        var results = engine.forecast(returns, 7, List.of("Bitcoin", "Ethereum", "Solana"));

        assertEquals("Bitcoin", results.get(0).assetName());
        assertEquals("Ethereum", results.get(1).assetName());
        assertEquals("Solana", results.get(2).assetName());
    }

    @Test
    void forecastWithConstantReturnsProducesNearZeroForecasts() {
        double[][] data = new double[60][2];
        for (int i = 0; i < 60; i++) {
            data[i][0] = 0.01;
            data[i][1] = 0.01;
        }

        var returns = makeReturns(data);
        var engine = new ForecastEngine();
        var results = engine.forecast(returns, 7, List.of("A", "B"));

        for (var r : results) {
            for (int h = 0; h < 7; h++) {
                double point = r.pointForecast().get(h);
                // With constant returns, forecast should be positive and not exceed the raw mean
                assertTrue(point > 0 && point <= 0.01,
                        "Constant returns should produce positive forecasts bounded by the raw mean");
            }
        }
    }

    @Test
    void forecastWithSingleAsset() {
        double[][] data = new double[60][1];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 60; i++)
            data[i][0] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        var engine = new ForecastEngine();
        var results = engine.forecast(returns, 5, List.of("OnlyCoin"));

        assertEquals(1, results.size());
        assertEquals(5, results.get(0).pointForecast().size());
    }

    @Test
    void forecastWithHighVolProducesWiderCI() {
        // Low vol asset
        double[][] lowVol = new double[60][1];
        var rand1 = new java.util.Random(42);
        for (int i = 0; i < 60; i++)
            lowVol[i][0] = rand1.nextGaussian() * 0.005;

        // High vol asset
        double[][] highVol = new double[60][1];
        var rand2 = new java.util.Random(42);
        for (int i = 0; i < 60; i++)
            highVol[i][0] = rand2.nextGaussian() * 0.05;

        var engine = new ForecastEngine();
        var lowResult = engine.forecast(makeReturns(lowVol), 7, List.of("LOW")).get(0);
        var highResult = engine.forecast(makeReturns(highVol), 7, List.of("HIGH")).get(0);

        double lowWidth = lowResult.upper95().get(6) - lowResult.lower95().get(6);
        double highWidth = highResult.upper95().get(6) - highResult.lower95().get(6);

        assertTrue(highWidth > lowWidth,
                "High vol asset should have wider CI: " + highWidth + " > " + lowWidth);
    }

    @Test
    void forecastSummaryContainsAssetName() {
        double[][] data = new double[60][1];
        var rand = new java.util.Random(42);
        for (int i = 0; i < 60; i++)
            data[i][0] = rand.nextGaussian() * 0.02;

        var returns = makeReturns(data);
        var engine = new ForecastEngine();
        var results = engine.forecast(returns, 7, List.of("TestCoin"));

        String summary = results.get(0).summary();
        assertTrue(summary.contains("TestCoin"),
                "Summary should contain asset name");
    }
}
