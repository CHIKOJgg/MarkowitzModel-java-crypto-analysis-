package org.example.engine;

import org.example.Defaults;

/**
 * Predefined strategy presets that configure all model parameters
 * for different risk profiles. Users can select a preset without
 * needing to understand individual parameters.
 */
public enum StrategyPreset {

    CONSERVATIVE("Conservative", "#4caf50",
            "Low risk, capital preservation focus. Lower leverage, tighter constraints, and conservative alpha signals.",
            15.0, 5.0, 0.05, 0.95, 1.1, 0.3, 15,
            false, 1.0, false, Defaults.EWMA_LAMBDA, true, 2.0),

    BALANCED("Balanced", "#ffab00",
            "Moderate risk-return tradeoff. Diversified approach with balanced constraints and standard parameters.",
            20.0, 15.0, 0.10, 0.90, 1.3, 0.5, 20,
            true, 1.5, false, Defaults.EWMA_LAMBDA, true, 3.0),

    AGGRESSIVE("Aggressive", "#ff5722",
            "High risk, maximum return potential. Wider constraints, higher leverage, and aggressive alpha signals.",
            35.0, 25.0, 0.20, 0.80, 2.0, 1.0, 30,
            true, 2.5, true, 0.90, false, 5.0);

    private final String label;
    private final String color;
    private final String description;
    private final double maxLong;
    private final double maxShort;
    private final double alpha;
    private final double shrinkage;
    private final double leverage;
    private final double targetReturn;
    private final int    momentumLookback;
    private final boolean volScaling;
    private final double targetVol;
    private final boolean ewmaCov;
    private final double ewmaLambda;
    private final boolean portfolioVaR;
    private final double maxVaR;

    StrategyPreset(String label, String color, String description,
                   double maxLong, double maxShort, double alpha,
                   double shrinkage, double leverage, double targetReturn,
                   int momentumLookback, boolean volScaling, double targetVol,
                   boolean ewmaCov, double ewmaLambda, boolean portfolioVaR, double maxVaR) {
        this.label = label;
        this.color = color;
        this.description = description;
        this.maxLong = maxLong;
        this.maxShort = maxShort;
        this.alpha = alpha;
        this.shrinkage = shrinkage;
        this.leverage = leverage;
        this.targetReturn = targetReturn;
        this.momentumLookback = momentumLookback;
        this.volScaling = volScaling;
        this.targetVol = targetVol;
        this.ewmaCov = ewmaCov;
        this.ewmaLambda = ewmaLambda;
        this.portfolioVaR = portfolioVaR;
        this.maxVaR = maxVaR;
    }

    public String label()                { return label; }
    public String color()                { return color; }
    public String description()          { return description; }
    public double maxLong()              { return maxLong; }
    public double maxShort()             { return maxShort; }
    public double alpha()                { return alpha; }
    public double shrinkage()            { return shrinkage; }
    public double leverage()             { return leverage; }
    public double targetReturn()         { return targetReturn; }
    public int    momentumLookback()     { return momentumLookback; }
    public boolean volScaling()          { return volScaling; }
    public double targetVol()            { return targetVol; }
    public boolean ewmaCov()             { return ewmaCov; }
    public double ewmaLambda()           { return ewmaLambda; }
    public boolean portfolioVaR()        { return portfolioVaR; }
    public double maxVaR()               { return maxVaR; }

    /**
     * Build a StrategyRegistry.Params from this preset.
     */
    public StrategyRegistry.Params toParams() {
        return new StrategyRegistry.Params(
                maxLong / 100.0,
                -maxShort / 100.0,
                alpha,
                shrinkage,
                leverage,
                false,
                targetReturn / 100.0,
                momentumLookback,
                volScaling,
                targetVol / 100.0,
                ewmaCov,
                ewmaLambda,
                portfolioVaR,
                maxVaR / 100.0
        );
    }

    @Override
    public String toString() {
        return label;
    }
}
