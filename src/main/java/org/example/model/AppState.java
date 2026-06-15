package org.example.model;

import java.util.List;

public record AppState(
        List<String> selectedCoins,
        List<String> selectedStrategies,
        double targetReturn,
        double maxLong,
        double maxShort,
        boolean allowShorting,
        double ewmaAlpha,
        double shrinkage,
        boolean ewmaCov,
        double ewmaLambda,
        double riskFreeRate,
        double leverage,
        int    momentumLookback,
        boolean volScaling,
        double targetVol,
        boolean portfolioVaR,
        double maxVaR,
        int    window,
        int    horizon,
        double feeRate,
        double maxTurnover,
        int    rebalanceFreq,
        boolean zeroCost,
        String  timeframe,
        String  apiKey
) {
    public static AppState defaults() {
        return new AppState(
                List.of("bitcoin","ethereum","solana","hyperliquid",
                        "the-open-network","mantle","monero","zcash"),
                List.of(),
                0.5, 20.0, 15.0, true, 0.10, 0.90, false, 0.94,
                4.0, 1.3, 20, false, 1.5, false, 3.0,
                60, 7, 0.1, 0.0, 1, false, "DAILY", "");
    }
}
