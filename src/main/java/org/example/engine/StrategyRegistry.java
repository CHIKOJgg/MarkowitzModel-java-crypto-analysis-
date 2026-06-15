package org.example.engine;

import org.example.Defaults;
import org.example.alpha.*;
import org.example.portfolio.*;
import org.example.risk.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.example.Defaults.SHORT_PENALTY;

/**
 * Factory that constructs all named strategies from UI parameters.
 *
 * <p>Adding a new strategy = add one entry to {@link #buildAll}.
 */
public class StrategyRegistry {

    /** Rolling window for the simple RiskParityPortfolio (in days). */
    private static final int RP_RISK_PARITY_WINDOW = 60;

    public static final String EWMA_MARKOWITZ    = "EWMA + Markowitz";
    public static final String MOMENTUM_MARKOWITZ= "Momentum + Markowitz";
    public static final String REVERSION_RISKPAR = "MeanReversion + RiskParity";
    public static final String BLENDED_MARKOWITZ = "Blended Alpha + Markowitz";
    public static final String EWMA_RISKPARITY   = "EWMA + RiskParity";
    public static final String MOMENTUM_EQUAL    = "Momentum + Equal Weight";
    public static final String EWMA_EQUAL        = "EWMA + Equal Weight";
    public static final String VOLADJ_MOMENTUM   = "VolAdj(Momentum) + Markowitz";
    public static final String VOLADJ_EWMA       = "VolAdj(EWMA) + Markowitz";
    public static final String BLACK_LITTERMAN   = "Black-Litterman";
    public static final String CVAR_OPTIMIZER    = "CVaR Optimizer";
    public static final String RSI_MARKOWITZ     = "RSI + Markowitz";
    public static final String BLENDED_BL        = "Blended + Black-Litterman";
    public static final String TRUE_RISK_PARITY  = "True Risk Parity (ERC)";
    public static final String HRP_MOMENTUM      = "HRP + Momentum";
    public static final String MACD_MARKOWITZ    = "MACD + Markowitz";
    public static final String BOLLINGER_MOMENTUM= "Bollinger + Momentum";
    public static final String VOLUME_EWMA       = "Volume-Weighted + EWMA";
    public static final String REGIME_VOL_MOMENTUM = "RegimeVol + Momentum";
    public static final String MDR_MOMENTUM        = "MDR + Momentum";
    public static final String ENSEMBLE_MARKOWITZ  = "Ensemble + Markowitz";
    public static final String VAR_SCALED_MOMENTUM = "VaR Scaled + Momentum";
    public static final String DDOWN_PROTECTED_EWMA = "Drawdown Protected + EWMA";
    public static final String SEASONALITY_EWMA    = "Seasonality + EWMA";

    /**
     * Build all strategies configured with the given parameters.
     *
     * @return ordered map of name → Strategy (preserves insertion order for UI)
     */
    public static Map<String, Strategy> buildAll(Params p) {
        Map<String, Strategy> map = new LinkedHashMap<>();

        // ── 1. EWMA + Markowitz (Optimal Sharpe) ─────────────────────────────
        map.put(EWMA_MARKOWITZ, Strategy
                .builder(EWMA_MARKOWITZ,
                         new EWMAAlpha(p.ewmaAlpha, Defaults.SHORT_PENALTY),
                         new MarkowitzPortfolio(p.maxLong, p.maxShort,
                                                p.shrinkage, p.allowShorting,
                                                p.targetReturn,
                                                p.useEwmaCov, p.ewmaLambda))
                .risk(p.useVolScaling ? new VolatilityScalingRisk(p.targetVol, Defaults.VOL_SCALE_MAX_LEVERAGE)
                                      : new PassthroughRisk())
                .shortingPolicy(p.allowShorting())
                .leverage(p.maxLeverage)
                .validate()
                .portfolioRiskConstraint(p.maxPortfolioVar, false)
                .build());

        // ── 2. Momentum + Markowitz ───────────────────────────────────────────
        map.put(MOMENTUM_MARKOWITZ, Strategy
                .builder(MOMENTUM_MARKOWITZ,
                         new MomentumAlpha(p.momentumLookback, Defaults.SHORT_PENALTY),
                         new MarkowitzPortfolio(p.maxLong, p.maxShort,
                                                p.shrinkage, p.allowShorting,
                                                p.targetReturn,
                                                p.useEwmaCov, p.ewmaLambda))
                .risk(p.useVolScaling ? new VolatilityScalingRisk(p.targetVol, Defaults.VOL_SCALE_MAX_LEVERAGE)
                                      : new PassthroughRisk())
                .shortingPolicy(p.allowShorting())
                .leverage(p.maxLeverage)
                .validate()
                .portfolioRiskConstraint(p.maxPortfolioVar, false)
                .build());

        // ── 3. MeanReversion + RiskParity ────────────────────────────────────
        map.put(REVERSION_RISKPAR, Strategy
                .builder(REVERSION_RISKPAR,
                         new MeanReversionAlpha(Defaults.MEAN_REVERSION_WINDOW, 0.01),
                         new RiskParityPortfolio(RP_RISK_PARITY_WINDOW, p.allowShorting()))
                .longOnlyWhenDisabled(p.allowShorting())
                .leverage(p.maxLeverage)
                .validate()
                .portfolioRiskConstraint(p.maxPortfolioVar, false)
                .build());

        // ── 4. Blended (50% EWMA + 50% Momentum) + Markowitz ─────────────────
        map.put(BLENDED_MARKOWITZ, Strategy
                .builder(BLENDED_MARKOWITZ,
                         BlendedAlpha.equalWeight(
                                 new EWMAAlpha(p.ewmaAlpha, Defaults.SHORT_PENALTY),
                                 new MomentumAlpha(p.momentumLookback, Defaults.SHORT_PENALTY)),
                         new MarkowitzPortfolio(p.maxLong, p.maxShort,
                                                p.shrinkage, p.allowShorting,
                                                p.targetReturn,
                                                p.useEwmaCov, p.ewmaLambda))
                .shortingPolicy(p.allowShorting())
                .leverage(p.maxLeverage)
                .validate()
                .portfolioRiskConstraint(p.maxPortfolioVar, false)
                .build());

        // ── 5. EWMA + RiskParity ──────────────────────────────────────────────
        map.put(EWMA_RISKPARITY, Strategy
                .builder(EWMA_RISKPARITY,
                         new EWMAAlpha(p.ewmaAlpha, Defaults.SHORT_PENALTY),
                         new RiskParityPortfolio(RP_RISK_PARITY_WINDOW, p.allowShorting()))
                .longOnlyWhenDisabled(p.allowShorting())
                .leverage(p.maxLeverage)
                .validate()
                .portfolioRiskConstraint(p.maxPortfolioVar, false)
                .build());

        // ── 6. Momentum + Equal Weight ────────────────────────────────────────
        map.put(MOMENTUM_EQUAL, Strategy
                .builder(MOMENTUM_EQUAL,
                         new MomentumAlpha(p.momentumLookback, Defaults.SHORT_PENALTY),
                         new EqualWeightPortfolio(p.allowShorting()))
                .longOnlyWhenDisabled(p.allowShorting())
                .leverage(p.maxLeverage)
                .validate()
                .portfolioRiskConstraint(p.maxPortfolioVar, false)
                .build());

        // ── 7. EWMA + Equal Weight (baseline) ────────────────────────────────
        map.put(EWMA_EQUAL, Strategy
                .builder(EWMA_EQUAL,
                         new EWMAAlpha(p.ewmaAlpha, Defaults.SHORT_PENALTY),
                         new EqualWeightPortfolio(false))
                .longOnlyWhenDisabled(p.allowShorting())
                .leverage(1.0)
                .validate()
                .portfolioRiskConstraint(p.maxPortfolioVar, false)
                .build());

        // ── 8. Vol-Adjusted Momentum + Markowitz ─────────────────────────────
        map.put(VOLADJ_MOMENTUM, Strategy
                .builder(VOLADJ_MOMENTUM,
                         new VolAdjustedAlpha(new MomentumAlpha(p.momentumLookback, Defaults.SHORT_PENALTY)),
                         new MarkowitzPortfolio(p.maxLong, p.maxShort,
                                                p.shrinkage, p.allowShorting,
                                                p.targetReturn,
                                                p.useEwmaCov, p.ewmaLambda))
                .risk(p.useVolScaling ? new VolatilityScalingRisk(p.targetVol, Defaults.VOL_SCALE_MAX_LEVERAGE)
                                      : new PassthroughRisk())
                .shortingPolicy(p.allowShorting())
                .leverage(p.maxLeverage)
                .validate()
                .portfolioRiskConstraint(p.maxPortfolioVar, false)
                .build());

        // ── 9. Vol-Adjusted EWMA + Markowitz ─────────────────────────────────
        map.put(VOLADJ_EWMA, Strategy
                .builder(VOLADJ_EWMA,
                         new VolAdjustedAlpha(new EWMAAlpha(p.ewmaAlpha, Defaults.SHORT_PENALTY)),
                         new MarkowitzPortfolio(p.maxLong, p.maxShort,
                                                p.shrinkage, p.allowShorting,
                                                p.targetReturn,
                                                p.useEwmaCov, p.ewmaLambda))
                .risk(p.useVolScaling ? new VolatilityScalingRisk(p.targetVol, Defaults.VOL_SCALE_MAX_LEVERAGE)
                                      : new PassthroughRisk())
                .shortingPolicy(p.allowShorting())
                .leverage(p.maxLeverage)
                .validate()
                .portfolioRiskConstraint(p.maxPortfolioVar, false)
                .build());

        // ── 10. Black-Litterman ───────────────────────────────────────────────
        map.put(BLACK_LITTERMAN, Strategy
                .builder(BLACK_LITTERMAN,
                         new EWMAAlpha(p.ewmaAlpha, Defaults.SHORT_PENALTY),
                         new BlackLittermanPortfolio(Defaults.BL_TAU, Defaults.BL_RISK_AVERSION,
                                                     p.maxLong, p.maxShort,
                                                     p.allowShorting))
                .shortingPolicy(p.allowShorting())
                .risk(p.useVolScaling ? new VolatilityScalingRisk(p.targetVol, Defaults.VOL_SCALE_MAX_LEVERAGE)
                                      : new PassthroughRisk())
                .leverage(p.maxLeverage)
                .validate()
                .portfolioRiskConstraint(p.maxPortfolioVar, false)
                .build());

        // ── 11. CVaR Optimizer ────────────────────────────────────────────────
        map.put(CVAR_OPTIMIZER, Strategy
                .builder(CVAR_OPTIMIZER,
                         new MomentumAlpha(p.momentumLookback, Defaults.SHORT_PENALTY),
                         new CvarPortfolio(Defaults.CVAR_CONFIDENCE, Defaults.CVAR_MAX_ITER, p.maxLong))
                .longOnlyWhenDisabled(p.allowShorting())
                .leverage(p.maxLeverage)
                .validate()
                .portfolioRiskConstraint(p.maxPortfolioVar, false)
                .build());

        // ── 12. RSI + Markowitz ───────────────────────────────────────────────
        map.put(RSI_MARKOWITZ, Strategy
                .builder(RSI_MARKOWITZ,
                         new RSIAlpha(Defaults.RSI_PERIOD, Defaults.SHORT_PENALTY),
                         new MarkowitzPortfolio(p.maxLong, p.maxShort,
                                                p.shrinkage, p.allowShorting,
                                                p.targetReturn,
                                                p.useEwmaCov, p.ewmaLambda))
                .risk(p.useVolScaling ? new VolatilityScalingRisk(p.targetVol, Defaults.VOL_SCALE_MAX_LEVERAGE)
                                      : new PassthroughRisk())
                .shortingPolicy(p.allowShorting())
                .leverage(p.maxLeverage)
                .validate()
                .portfolioRiskConstraint(p.maxPortfolioVar, false)
                .build());

        // ── 13. Blended Alpha + Black-Litterman ───────────────────────────────
        map.put(BLENDED_BL, Strategy
                .builder(BLENDED_BL,
                         BlendedAlpha.equalWeight(
                                 new EWMAAlpha(p.ewmaAlpha, Defaults.SHORT_PENALTY),
                                 new MomentumAlpha(p.momentumLookback, Defaults.SHORT_PENALTY),
                                 new RSIAlpha(Defaults.RSI_PERIOD, Defaults.SHORT_PENALTY)),
                         new BlackLittermanPortfolio(Defaults.BL_TAU, Defaults.BL_RISK_AVERSION,
                                                     p.maxLong, p.maxShort,
                                                     p.allowShorting))
                .shortingPolicy(p.allowShorting())
                .risk(p.useVolScaling ? new VolatilityScalingRisk(p.targetVol, Defaults.VOL_SCALE_MAX_LEVERAGE)
                                      : new PassthroughRisk())
                .leverage(p.maxLeverage)
                .validate()
                .portfolioRiskConstraint(p.maxPortfolioVar, false)
                .build());

        // ── 14. True Risk Parity (ERC) ───────────────────────────────────────
        map.put(TRUE_RISK_PARITY, Strategy
                .builder(TRUE_RISK_PARITY,
                         new EWMAAlpha(p.ewmaAlpha, Defaults.SHORT_PENALTY),
                         new TrueRiskParityPortfolio(Defaults.RP_WINDOW))
                .longOnlyWhenDisabled(p.allowShorting())
                .leverage(p.maxLeverage)
                .validate()
                .portfolioRiskConstraint(p.maxPortfolioVar, false)
                .build());

        // ── 15. HRP + Momentum ──────────────────────────────────────────────
        map.put(HRP_MOMENTUM, Strategy
                .builder(HRP_MOMENTUM,
                         new MomentumAlpha(p.momentumLookback, Defaults.SHORT_PENALTY),
                         new HierarchicalRiskParityPortfolio())
                .longOnlyWhenDisabled(p.allowShorting())
                .leverage(p.maxLeverage)
                .validate()
                .portfolioRiskConstraint(p.maxPortfolioVar, false)
                .build());

        // ── 16. MACD + Markowitz ────────────────────────────────────────────
        map.put(MACD_MARKOWITZ, Strategy
                .builder(MACD_MARKOWITZ,
                         new MACDAlpha(12, 26, 9),
                         new MarkowitzPortfolio(p.maxLong, p.maxShort,
                                                p.shrinkage, p.allowShorting,
                                                p.targetReturn,
                                                p.useEwmaCov, p.ewmaLambda))
                .risk(p.useVolScaling ? new VolatilityScalingRisk(p.targetVol, Defaults.VOL_SCALE_MAX_LEVERAGE)
                                      : new PassthroughRisk())
                .shortingPolicy(p.allowShorting())
                .leverage(p.maxLeverage)
                .validate()
                .portfolioRiskConstraint(p.maxPortfolioVar, false)
                .build());

        // ── 17. Bollinger + Momentum ────────────────────────────────────────
        map.put(BOLLINGER_MOMENTUM, Strategy
                .builder(BOLLINGER_MOMENTUM,
                         new BollingerBandAlpha(20, 2.0),
                         new MarkowitzPortfolio(p.maxLong, p.maxShort,
                                                p.shrinkage, p.allowShorting,
                                                p.targetReturn,
                                                p.useEwmaCov, p.ewmaLambda))
                .risk(p.useVolScaling ? new VolatilityScalingRisk(p.targetVol, Defaults.VOL_SCALE_MAX_LEVERAGE)
                                      : new PassthroughRisk())
                .shortingPolicy(p.allowShorting())
                .leverage(p.maxLeverage)
                .validate()
                .portfolioRiskConstraint(p.maxPortfolioVar, false)
                .build());

        // ── 18. Volume-Weighted + EWMA ─────────────────────────────────────
        map.put(VOLUME_EWMA, Strategy
                .builder(VOLUME_EWMA,
                         new VolumeWeightedAlpha(),
                         new MarkowitzPortfolio(p.maxLong, p.maxShort,
                                                p.shrinkage, p.allowShorting,
                                                p.targetReturn,
                                                p.useEwmaCov, p.ewmaLambda))
                .risk(p.useVolScaling ? new VolatilityScalingRisk(p.targetVol, Defaults.VOL_SCALE_MAX_LEVERAGE)
                                      : new PassthroughRisk())
                .shortingPolicy(p.allowShorting())
                .leverage(p.maxLeverage)
                .validate()
                .portfolioRiskConstraint(p.maxPortfolioVar, false)
                .build());

        // ── 19. Regime-Aware Vol + Momentum ─────────────────────────────────
        map.put(REGIME_VOL_MOMENTUM, Strategy
                .builder(REGIME_VOL_MOMENTUM,
                         new MomentumAlpha(p.momentumLookback, Defaults.SHORT_PENALTY),
                         new MarkowitzPortfolio(p.maxLong, p.maxShort,
                                                p.shrinkage, p.allowShorting,
                                                p.targetReturn,
                                                p.useEwmaCov, p.ewmaLambda))
                .risk(new RegimeAwareVolScaling(p.targetVol, Defaults.VOL_SCALE_MAX_LEVERAGE, 30))
                .shortingPolicy(p.allowShorting())
                .leverage(p.maxLeverage)
                .validate()
                .portfolioRiskConstraint(p.maxPortfolioVar, false)
                .build());

        // ── 20. Max Diversification (MDR) + Momentum ─────────────────────────
        map.put(MDR_MOMENTUM, Strategy
                .builder(MDR_MOMENTUM,
                         new MomentumAlpha(p.momentumLookback, SHORT_PENALTY),
                         new MaxDiversificationPortfolio(p.maxLong, p.maxShort, p.maxLeverage))
                .longOnlyWhenDisabled(p.allowShorting())
                .leverage(p.maxLeverage)
                .validate()
                .portfolioRiskConstraint(p.maxPortfolioVar, false)
                .build());

        // ── 21. Ensemble Alpha + Markowitz ───────────────────────────────────
        map.put(ENSEMBLE_MARKOWITZ, Strategy
                .builder(ENSEMBLE_MARKOWITZ,
                         new EnsembleAlpha(java.util.List.of(
                                 new EWMAAlpha(p.ewmaAlpha, SHORT_PENALTY),
                                 new MomentumAlpha(p.momentumLookback, SHORT_PENALTY),
                                 new RSIAlpha(Defaults.RSI_PERIOD, SHORT_PENALTY))),
                         new MarkowitzPortfolio(p.maxLong, p.maxShort,
                                                p.shrinkage, p.allowShorting,
                                                p.targetReturn,
                                                p.useEwmaCov, p.ewmaLambda))
                .risk(p.useVolScaling ? new VolatilityScalingRisk(p.targetVol, Defaults.VOL_SCALE_MAX_LEVERAGE)
                                      : new PassthroughRisk())
                .shortingPolicy(p.allowShorting())
                .leverage(p.maxLeverage)
                .validate()
                .portfolioRiskConstraint(p.maxPortfolioVar, false)
                .build());

        // ── 22. VaR Scaled + Momentum ───────────────────────────────────────
        map.put(VAR_SCALED_MOMENTUM, Strategy
                .builder(VAR_SCALED_MOMENTUM,
                         new MomentumAlpha(p.momentumLookback, SHORT_PENALTY),
                         new MarkowitzPortfolio(p.maxLong, p.maxShort,
                                                p.shrinkage, p.allowShorting,
                                                p.targetReturn,
                                                p.useEwmaCov, p.ewmaLambda))
                .risk(new VaRBasedRiskScaling(p.targetVol * 1.645, Defaults.VOL_SCALE_MAX_LEVERAGE))
                .shortingPolicy(p.allowShorting())
                .leverage(p.maxLeverage)
                .validate()
                .portfolioRiskConstraint(p.maxPortfolioVar, false)
                .build());

        // ── 23. Drawdown Protected + EWMA ───────────────────────────────────
        map.put(DDOWN_PROTECTED_EWMA, Strategy
                .builder(DDOWN_PROTECTED_EWMA,
                         new EWMAAlpha(p.ewmaAlpha, SHORT_PENALTY),
                         new MarkowitzPortfolio(p.maxLong, p.maxShort,
                                                p.shrinkage, p.allowShorting,
                                                p.targetReturn,
                                                p.useEwmaCov, p.ewmaLambda))
                .risk(new DrawdownBasedRiskScaling(2.0, 0.2))
                .shortingPolicy(p.allowShorting())
                .leverage(p.maxLeverage)
                .validate()
                .portfolioRiskConstraint(p.maxPortfolioVar, false)
                .build());

        // ── 24. Seasonality + EWMA -- blended alpha ─────────────────────────
        map.put(SEASONALITY_EWMA, Strategy
                .builder(SEASONALITY_EWMA,
                         BlendedAlpha.equalWeight(
                                 new SeasonalityAlpha(),
                                 new EWMAAlpha(p.ewmaAlpha, SHORT_PENALTY)),
                         new MarkowitzPortfolio(p.maxLong, p.maxShort,
                                                p.shrinkage, p.allowShorting,
                                                p.targetReturn,
                                                p.useEwmaCov, p.ewmaLambda))
                .risk(p.useVolScaling ? new VolatilityScalingRisk(p.targetVol, Defaults.VOL_SCALE_MAX_LEVERAGE)
                                      : new PassthroughRisk())
                .shortingPolicy(p.allowShorting())
                .leverage(p.maxLeverage)
                .validate()
                .portfolioRiskConstraint(p.maxPortfolioVar, false)
                .build());

        return map;
    }

    /** All strategy names, in order. */
    public static List<String> allNames() {
        return List.of(EWMA_MARKOWITZ, MOMENTUM_MARKOWITZ, REVERSION_RISKPAR,
                       BLENDED_MARKOWITZ, EWMA_RISKPARITY, MOMENTUM_EQUAL, EWMA_EQUAL,
                       VOLADJ_MOMENTUM, VOLADJ_EWMA, BLACK_LITTERMAN, CVAR_OPTIMIZER,
                       RSI_MARKOWITZ, BLENDED_BL, TRUE_RISK_PARITY,
                       HRP_MOMENTUM, MACD_MARKOWITZ, BOLLINGER_MOMENTUM,
                       VOLUME_EWMA, REGIME_VOL_MOMENTUM,
                       MDR_MOMENTUM, ENSEMBLE_MARKOWITZ,
                       VAR_SCALED_MOMENTUM, DDOWN_PROTECTED_EWMA, SEASONALITY_EWMA);
    }

    // ── Parameter bag ─────────────────────────────────────────────────────────

    /** All tunable parameters passed from the UI. */
    public record Params(
            double  maxLong,
            double  maxShort,
            double  ewmaAlpha,
            double  shrinkage,
            double  maxLeverage,
            boolean allowShorting,
            double  targetReturn,
            int     momentumLookback,
            boolean useVolScaling,
            double  targetVol,
            boolean useEwmaCov,
            double  ewmaLambda,
            boolean usePortfolioRiskConstraint,
            double  maxPortfolioVar
    ) {}
}
