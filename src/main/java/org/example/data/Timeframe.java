package org.example.data;

/**
 * Supported data timeframes for market data fetching.
 */
public enum Timeframe {
    DAILY("daily", 365, "1d"),
    WEEKLY("daily", 365, "1w"),
    MONTHLY("daily", 365, "1M");

    private final String apiInterval;
    private final int    apiDays;
    private final String label;

    Timeframe(String apiInterval, int apiDays, String label) {
        this.apiInterval = apiInterval;
        this.apiDays     = apiDays;
        this.label       = label;
    }

    public String apiInterval() { return apiInterval; }
    public int    apiDays()     { return apiDays; }
    public String label()       { return label; }

    /**
     * Resample a daily return series to this timeframe.
     * Weekly = compound 5 daily returns, Monthly = compound ~22 daily returns.
     */
    public int resampleFactor() {
        return switch (this) {
            case DAILY   -> 1;
            case WEEKLY  -> 5;
            case MONTHLY -> 22;
        };
    }

    @Override
    public String toString() { return label; }
}
