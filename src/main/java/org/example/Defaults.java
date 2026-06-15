package org.example;

/**
 * Centralized constants for the portfolio optimization framework.
 *
 * <p>All magic numbers, default parameters, and configuration values
 * are defined here to ensure consistency across the codebase.
 */
public final class Defaults {

    private Defaults() {}

    // ── Trading Calendar ──────────────────────────────────────────────────────
    /** Trading days per year for crypto (24/7 markets). */
    public static final int TRADING_DAYS_PER_YEAR = 365;

    // ── Alpha Model Defaults ──────────────────────────────────────────────────
    /** Default short penalty applied to negative alpha signals. */
    public static final double SHORT_PENALTY = 0.02;

    /** Default EWMA smoothing factor. */
    public static final double EWMA_ALPHA = 0.1;

    /** Default momentum lookback window (days). */
    public static final int MOMENTUM_LOOKBACK = 20;

    /** Default mean reversion window (days). */
    public static final int MEAN_REVERSION_WINDOW = 10;

    /** Default RSI period. */
    public static final int RSI_PERIOD = 14;

    /** Mean reversion z-score scaling factor. */
    public static final double MEAN_REVERSION_SCALE = 3.0;

    // ── Covariance / Shrinkage ────────────────────────────────────────────────
    /** Default shrinkage lambda for Ledoit-Wolf. */
    public static final double SHRINKAGE_LAMBDA = 0.9;

    /** Default EWMA decay factor for covariance. */
    public static final double EWMA_LAMBDA = 0.94;

    /** Default EWMA decay factor for volatility. */
    public static final double EWMA_VOL_LAMBDA = 0.94;

    // ── Portfolio Optimization ────────────────────────────────────────────────
    /** Maximum weight per asset (long). */
    public static final double MAX_LONG = 0.20;

    /** Maximum short weight per asset. */
    public static final double MAX_SHORT = -0.15;

    /** Maximum gross leverage. */
    public static final double MAX_LEVERAGE = 1.3;

    /** Target return cap as fraction of max feasible mu. */
    public static final double TARGET_RETURN_CAP = 0.90;

    /** Black-Litterman tau (view uncertainty scalar). */
    public static final double BL_TAU = 0.025;

    /** Black-Litterman risk aversion (delta). */
    public static final double BL_RISK_AVERSION = 2.5;

    /** CVaR confidence level. */
    public static final double CVAR_CONFIDENCE = 0.95;

    /** CVaR max iterations. */
    public static final int CVAR_MAX_ITER = 100;

    /** CVaR max weight per asset. */
    public static final double CVAR_MAX_LONG = 0.5;

    /** True Risk Parity max iterations. */
    public static final int RP_MAX_ITER = 100;

    /** True Risk Parity convergence tolerance. */
    public static final double RP_TOLERANCE = 1e-10;

    /** True Risk Parity estimation window. */
    public static final int RP_WINDOW = 60;

    /** Turnover constraint: 0 = disabled. */
    public static final double MAX_TURNOVER = 0.0;

    // ── Risk Model ────────────────────────────────────────────────────────────
    /** Default target daily volatility for vol scaling. */
    public static final double TARGET_VOL = 0.015;

    /** Maximum leverage for vol scaling. */
    public static final double VOL_SCALE_MAX_LEVERAGE = 2.0;

    // ── Execution / Transaction Costs ─────────────────────────────────────────
    /** Default one-way fee rate (0.1%). */
    public static final double FEE_RATE = 0.001;

    /** Default slippage coefficient. */
    public static final double SLIPPAGE = 0.0005;

    // ── Backtest ──────────────────────────────────────────────────────────────
    /** Default training window (days). */
    public static final int BACKTEST_WINDOW = 60;

    /** Default prediction horizon (days). */
    public static final int BACKTEST_HORIZON = 7;

    /** Default risk-free rate (annual). */
    public static final double RISK_FREE_RATE = 0.04;

    // ── Forecast ──────────────────────────────────────────────────────────────
    /** Forecast EWMA alpha. */
    public static final double FORECAST_ALPHA = 0.1;

    /** Forecast AR(1) phi clamp (stationarity). */
    public static final double FORECAST_PHI_MAX = 0.99;

    /** Forecast volatility mean-reversion speed. */
    public static final double FORECAST_VOL_DECAY = 0.97;

    /** 95% confidence z-score. */
    public static final double Z_95 = 1.96;

    /** 50% confidence z-score. */
    public static final double Z_50 = 0.674;

    // ── Data ──────────────────────────────────────────────────────────────────
    /** CoinGecko API base URL. */
    public static final String API_BASE_URL =
            "https://api.coingecko.com/api/v3/coins/%s/market_chart?vs_currency=usd&days=365&interval=daily";

    /** Max retries for API calls. */
    public static final int MAX_RETRIES = 3;

    /** Base backoff time for rate limiting (ms). */
    public static final long RETRY_BACKOFF_MS = 5000;

    /** Jitter range minimum (ms). */
    public static final int JITTER_MIN_MS = 60;

    /** Jitter range maximum (ms). */
    public static final int JITTER_MAX_MS = 280;

    // ── UI ────────────────────────────────────────────────────────────────────
    /** Default EWMA alpha for alpha model. */
    public static final double UI_EWMA_ALPHA = 0.10;

    /** Default target return for UI slider (%). */
    public static final double UI_TARGET_RETURN = 0.5;

    /** Default max long for UI slider (%). */
    public static final double UI_MAX_LONG = 20.0;

    /** Default max short for UI slider (%). */
    public static final double UI_MAX_SHORT = 15.0;

    /** Default leverage for UI slider. */
    public static final double UI_LEVERAGE = 1.3;

    /** Default target vol for UI slider (%). */
    public static final double UI_TARGET_VOL = 1.5;

    /** Default fee rate for UI slider (%). */
    public static final double UI_FEE_RATE = 0.1;

    /** Default risk-free rate for UI slider (%). */
    public static final double UI_RISK_FREE_RATE = 4.0;
}
