package org.example.forecast;

import java.util.List;

/**
 * Forecast result with point estimates and confidence intervals.
 */
public record ForecastResult(
        String      assetName,
        int         horizon,
        List<Double> pointForecast,
        List<Double> lower95,
        List<Double> upper95,
        List<Double> lower50,
        List<Double> upper50,
        double       annualizedReturn,
        double       annualizedVol,
        double       trendStrength
) {
    public String summary() {
        return String.format("%-15s  AnnRet=%+.2f%%  AnnVol=%.2f%%  Trend=%.3f",
                assetName, annualizedReturn * 100, annualizedVol * 100, trendStrength);
    }
}
