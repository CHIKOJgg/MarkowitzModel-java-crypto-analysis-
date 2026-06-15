package org.example.forecast;

import java.util.List;

/**
 * Enhanced forecast result with point estimates, confidence intervals,
 * directional signals, risk classification, and loss probability.
 */
public record ForecastResult(
        String       assetName,
        int          horizon,
        List<Double> pointForecast,
        List<Double> lower95,
        List<Double> upper95,
        List<Double> lower50,
        List<Double> upper50,
        double       annualizedReturn,
        double       annualizedVol,
        double       trendStrength,
        Signal       signal,
        RiskLevel    riskLevel,
        double       forecastSharpe,
        double       expectedRangeLow,
        double       expectedRangeHigh,
        double       probLoss,
        double       maxDrawdownEst
) {
    public enum Signal {
        STRONG_BUY("Strong Buy", "#00e676"),
        BUY("Buy", "#4caf50"),
        HOLD("Hold", "#ffab00"),
        SELL("Sell", "#ff5722"),
        STRONG_SELL("Strong Sell", "#d50000");

        private final String label;
        private final String color;
        Signal(String label, String color) { this.label = label; this.color = color; }
        public String label()  { return label; }
        public String color()  { return color; }
    }

    public enum RiskLevel {
        LOW("Low", "#4caf50", "Conservative"),
        MEDIUM("Medium", "#ffab00", "Moderate"),
        HIGH("High", "#ff5722", "Aggressive"),
        EXTREME("Extreme", "#d50000", "Very Aggressive");

        private final String label;
        private final String color;
        private final String description;
        RiskLevel(String label, String color, String description) {
            this.label = label; this.color = color; this.description = description;
        }
        public String label()       { return label; }
        public String color()       { return color; }
        public String description() { return description; }
    }

    public String summary() {
        return String.format("%-15s  AnnRet=%+.2f%%  AnnVol=%.2f%%  Signal=%s  Risk=%s  P(Loss)=%.1f%%",
                assetName, annualizedReturn * 100, annualizedVol * 100,
                signal.label(), riskLevel.label(), probLoss * 100);
    }

    public String humanSummary() {
        double cumPct = cumulativeReturnPct().get(cumulativeReturnPct().size() - 1);
        String dir = cumPct >= 0 ? "up" : "down";
        return switch (signal) {
            case STRONG_BUY  -> String.format("%s: strong %sward trend (+%.1f%% over %d days).",
                    assetName, dir, Math.abs(cumPct), horizon);
            case BUY         -> String.format("%s: moderate %sside expected (%+.1f%% over %d days).",
                    assetName, dir, cumPct, horizon);
            case HOLD        -> String.format("%s: no clear trend (%+.1f%% over %d days); wait for confirmation.",
                    assetName, cumPct, horizon);
            case SELL        -> String.format("%s: declining trend likely (%+.1f%% over %d days); reduce exposure.",
                    assetName, cumPct, horizon);
            case STRONG_SELL -> String.format("%s: strong downward pressure (%+.1f%% over %d days); exit or avoid.",
                    assetName, cumPct, horizon);
        };
    }

    /** Geometric cumulative return path in percent (day 1 … horizon). */
    public List<Double> cumulativeReturnPct() {
        List<Double> cum = new java.util.ArrayList<>(pointForecast.size());
        double wealth = 1.0;
        for (double r : pointForecast) {
            wealth *= (1.0 + r);
            cum.add((wealth - 1.0) * 100.0);
        }
        return cum;
    }
}
