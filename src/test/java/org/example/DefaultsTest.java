package org.example;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultsTest {

    @Test
    void tradingDaysPerYearIs365() {
        assertEquals(365, Defaults.TRADING_DAYS_PER_YEAR,
                "Crypto markets trade 365 days per year");
    }

    @Test
    void shortPenaltyIsNonNegative() {
        assertTrue(Defaults.SHORT_PENALTY >= 0,
                "Short penalty should be non-negative");
    }

    @Test
    void ewmaAlphaIsInValidRange() {
        assertTrue(Defaults.EWMA_ALPHA > 0 && Defaults.EWMA_ALPHA <= 1.0,
                "EWMA alpha should be in (0,1]");
    }

    @Test
    void momentumLookbackIsPositive() {
        assertTrue(Defaults.MOMENTUM_LOOKBACK > 0,
                "Momentum lookback should be positive");
    }

    @Test
    void rsiPeriodIsPositive() {
        assertTrue(Defaults.RSI_PERIOD > 0,
                "RSI period should be positive");
    }

    @Test
    void shrinkageLambdaIsInValidRange() {
        assertTrue(Defaults.SHRINKAGE_LAMBDA >= 0 && Defaults.SHRINKAGE_LAMBDA <= 1.0,
                "Shrinkage lambda should be in [0,1]");
    }

    @Test
    void ewmaLambdaForCovarianceIsInValidRange() {
        assertTrue(Defaults.EWMA_LAMBDA > 0 && Defaults.EWMA_LAMBDA < 1.0,
                "EWMA lambda for covariance should be in (0,1)");
    }

    @Test
    void maxLongIsPositiveAndReasonable() {
        assertTrue(Defaults.MAX_LONG > 0 && Defaults.MAX_LONG <= 1.0,
                "Max long should be in (0,1]");
    }

    @Test
    void maxShortIsNegative() {
        assertTrue(Defaults.MAX_SHORT < 0,
                "Max short should be negative");
    }

    @Test
    void maxLeverageIsAtLeast1() {
        assertTrue(Defaults.MAX_LEVERAGE >= 1.0,
                "Max leverage should be at least 1.0");
    }

    @Test
    void targetReturnCapIsInValidRange() {
        assertTrue(Defaults.TARGET_RETURN_CAP > 0 && Defaults.TARGET_RETURN_CAP <= 1.0,
                "Target return cap should be in (0,1]");
    }

    @Test
    void blTauIsPositive() {
        assertTrue(Defaults.BL_TAU > 0,
                "Black-Litterman tau should be positive");
    }

    @Test
    void blRiskAversionIsPositive() {
        assertTrue(Defaults.BL_RISK_AVERSION > 0,
                "Black-Litterman risk aversion should be positive");
    }

    @Test
    void cvarConfidenceIsInValidRange() {
        assertTrue(Defaults.CVAR_CONFIDENCE > 0.5 && Defaults.CVAR_CONFIDENCE < 1.0,
                "CVaR confidence should be in (0.5,1)");
    }

    @Test
    void cvarMaxIterIsPositive() {
        assertTrue(Defaults.CVAR_MAX_ITER > 0,
                "CVaR max iterations should be positive");
    }

    @Test
    void cvarMaxLongIsPositive() {
        assertTrue(Defaults.CVAR_MAX_LONG > 0,
                "CVaR max long should be positive");
    }

    @Test
    void rpMaxIterIsPositive() {
        assertTrue(Defaults.RP_MAX_ITER > 0,
                "Risk parity max iterations should be positive");
    }

    @Test
    void rpToleranceIsSmallPositive() {
        assertTrue(Defaults.RP_TOLERANCE > 0 && Defaults.RP_TOLERANCE < 1.0,
                "Risk parity tolerance should be small positive");
    }

    @Test
    void rpWindowIsPositive() {
        assertTrue(Defaults.RP_WINDOW > 0,
                "Risk parity window should be positive");
    }

    @Test
    void feeRateIsNonNegative() {
        assertTrue(Defaults.FEE_RATE >= 0,
                "Fee rate should be non-negative");
    }

    @Test
    void slippageIsNonNegative() {
        assertTrue(Defaults.SLIPPAGE >= 0,
                "Slippage should be non-negative");
    }

    @Test
    void backtestWindowIsPositive() {
        assertTrue(Defaults.BACKTEST_WINDOW > 0,
                "Backtest window should be positive");
    }

    @Test
    void backtestHorizonIsPositive() {
        assertTrue(Defaults.BACKTEST_HORIZON > 0,
                "Backtest horizon should be positive");
    }

    @Test
    void riskFreeRateIsNonNegative() {
        assertTrue(Defaults.RISK_FREE_RATE >= 0,
                "Risk-free rate should be non-negative");
    }

    @Test
    void forecastAlphaIsInValidRange() {
        assertTrue(Defaults.FORECAST_ALPHA > 0 && Defaults.FORECAST_ALPHA <= 1.0,
                "Forecast alpha should be in (0,1]");
    }

    @Test
    void forecastPhiMaxIsInValidRange() {
        assertTrue(Defaults.FORECAST_PHI_MAX > 0 && Defaults.FORECAST_PHI_MAX < 1.0,
                "Forecast phi max should be in (0,1) for stationarity");
    }

    @Test
    void forecastVolDecayIsInValidRange() {
        assertTrue(Defaults.FORECAST_VOL_DECAY > 0 && Defaults.FORECAST_VOL_DECAY < 1.0,
                "Forecast vol decay should be in (0,1)");
    }

    @Test
    void zScoresAreCorrect() {
        assertEquals(1.96, Defaults.Z_95, 0.01, "95% z-score should be ~1.96");
        assertEquals(0.674, Defaults.Z_50, 0.01, "50% z-score should be ~0.674");
    }

    @Test
    void maxRetriesIsPositive() {
        assertTrue(Defaults.MAX_RETRIES > 0,
                "Max retries should be positive");
    }

    @Test
    void retryBackoffIsPositive() {
        assertTrue(Defaults.RETRY_BACKOFF_MS > 0,
                "Retry backoff should be positive");
    }

    @Test
    void jitterRangeIsValid() {
        assertTrue(Defaults.JITTER_MIN_MS >= 0, "Jitter min should be non-negative");
        assertTrue(Defaults.JITTER_MAX_MS > Defaults.JITTER_MIN_MS,
                "Jitter max should be greater than min");
    }

    @Test
    void targetVolIsPositive() {
        assertTrue(Defaults.TARGET_VOL > 0,
                "Target vol should be positive");
    }

    @Test
    void volScaleMaxLeverageIsAtLeast1() {
        assertTrue(Defaults.VOL_SCALE_MAX_LEVERAGE >= 1.0,
                "Vol scale max leverage should be at least 1.0");
    }

    @Test
    void uiDefaultsAreReasonable() {
        assertTrue(Defaults.UI_EWMA_ALPHA > 0 && Defaults.UI_EWMA_ALPHA <= 1.0);
        assertTrue(Defaults.UI_TARGET_RETURN > 0);
        assertTrue(Defaults.UI_MAX_LONG > 0);
        assertTrue(Defaults.UI_MAX_SHORT > 0);
        assertTrue(Defaults.UI_LEVERAGE >= 1.0);
        assertTrue(Defaults.UI_TARGET_VOL > 0);
        assertTrue(Defaults.UI_FEE_RATE >= 0);
        assertTrue(Defaults.UI_RISK_FREE_RATE >= 0);
    }

    @Test
    void apiUrlIsValid() {
        assertNotNull(Defaults.API_BASE_URL);
        assertTrue(Defaults.API_BASE_URL.startsWith("https://"),
                "API URL should use HTTPS");
        assertTrue(Defaults.API_BASE_URL.contains("%s"),
                "API URL should have coin placeholder");
    }
}
