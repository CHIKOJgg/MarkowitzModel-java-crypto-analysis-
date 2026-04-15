package org.example.engine;

import org.example.alpha.*;
import org.example.portfolio.*;
import org.example.risk.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory that constructs all named strategies from UI parameters.
 *
 * <p>Adding a new strategy = add one entry to {@link #buildAll}.
 */
public class StrategyRegistry {

    public static final String EWMA_MARKOWITZ    = "EWMA + Markowitz";
    public static final String MOMENTUM_MARKOWITZ= "Momentum + Markowitz";
    public static final String REVERSION_RISKPAR = "MeanReversion + RiskParity";
    public static final String BLENDED_MARKOWITZ = "Blended Alpha + Markowitz";
    public static final String EWMA_RISKPARITY   = "EWMA + RiskParity";
    public static final String MOMENTUM_EQUAL    = "Momentum + Equal Weight";
    public static final String EWMA_EQUAL        = "EWMA + Equal Weight";

    public static Map<String, Strategy> buildAll(Params p) {
        Map<String, Strategy> map = new LinkedHashMap<>();

        // ── 1. EWMA + Markowitz (Optimal Sharpe) ─────────────────────────────
        map.put(EWMA_MARKOWITZ, Strategy
                .builder(EWMA_MARKOWITZ,
                         new EWMAAlpha(p.ewmaAlpha, 0.02),
                         new MarkowitzPortfolio(p.maxLong, p.maxShort,
                                                p.shrinkage, p.allowShorting,
                                                p.targetReturn))
                .risk(p.useVolScaling ? new VolatilityScalingRisk(p.targetVol, 2.0)
                                      : new PassthroughRisk())
                .leverage(p.maxLeverage)
                .marketNeutral()
                .validate()
                .build());

        // ── 2. Momentum + Markowitz ───────────────────────────────────────────
        map.put(MOMENTUM_MARKOWITZ, Strategy
                .builder(MOMENTUM_MARKOWITZ,
                         new MomentumAlpha(p.momentumLookback, 0.02),
                         new MarkowitzPortfolio(p.maxLong, p.maxShort,
                                                p.shrinkage, p.allowShorting,
                                                p.targetReturn))
                .risk(p.useVolScaling ? new VolatilityScalingRisk(p.targetVol, 2.0)
                                      : new PassthroughRisk())
                .leverage(p.maxLeverage)
                .marketNeutral()
                .validate()
                .build());

        // ── 3. MeanReversion + RiskParity ────────────────────────────────────
        map.put(REVERSION_RISKPAR, Strategy
                .builder(REVERSION_RISKPAR,
                         new MeanReversionAlpha(10, 0.01),
                         new RiskParityPortfolio(60, true))
                .leverage(p.maxLeverage)
                .validate()
                .build());

        // ── 4. Blended (50% EWMA + 50% Momentum) + Markowitz ─────────────────
        map.put(BLENDED_MARKOWITZ, Strategy
                .builder(BLENDED_MARKOWITZ,
                         BlendedAlpha.equalWeight(
                                 new EWMAAlpha(p.ewmaAlpha, 0.02),
                                 new MomentumAlpha(p.momentumLookback, 0.02)),
                         new MarkowitzPortfolio(p.maxLong, p.maxShort,
                                                p.shrinkage, p.allowShorting,
                                                p.targetReturn))
                .leverage(p.maxLeverage)
                .marketNeutral()
                .validate()
                .build());

        // ── 5. EWMA + RiskParity ──────────────────────────────────────────────
        map.put(EWMA_RISKPARITY, Strategy
                .builder(EWMA_RISKPARITY,
                         new EWMAAlpha(p.ewmaAlpha, 0.02),
                         new RiskParityPortfolio(60, true))
                .leverage(p.maxLeverage)
                .validate()
                .build());

        // ── 6. Momentum + Equal Weight ────────────────────────────────────────
        map.put(MOMENTUM_EQUAL, Strategy
                .builder(MOMENTUM_EQUAL,
                         new MomentumAlpha(p.momentumLookback, 0.02),
                         new EqualWeightPortfolio(true))
                .leverage(p.maxLeverage)
                .validate()
                .build());

        // ── 7. EWMA + Equal Weight (baseline) ────────────────────────────────
        map.put(EWMA_EQUAL, Strategy
                .builder(EWMA_EQUAL,
                         new EWMAAlpha(p.ewmaAlpha, 0.02),
                         new EqualWeightPortfolio(false))
                .leverage(1.0)
                .validate()
                .build());

        return map;
    }

    /** All strategy names, in order. */
    public static List<String> allNames() {
        return List.of(EWMA_MARKOWITZ, MOMENTUM_MARKOWITZ, REVERSION_RISKPAR,
                       BLENDED_MARKOWITZ, EWMA_RISKPARITY, MOMENTUM_EQUAL, EWMA_EQUAL);
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
            double  targetVol
    ) {}
}
