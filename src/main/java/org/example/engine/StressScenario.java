package org.example.engine;

/**
 * Immutable definition of a historical or custom stress scenario.
 *
 * @param name            short label (e.g. "FTX Collapse")
 * @param description     human-readable explanation
 * @param shockMagnitude  fractional price drop (positive = loss, e.g. 0.25 = −25 %)
 * @param durationDays    number of trading days the shock is applied over
 */
public record StressScenario(String name, String description,
                              double shockMagnitude, int durationDays) {

    /** May 2022 crash: −30 % BTC over 5 days. */
    public static StressScenario may2022Crash() {
        return new StressScenario("May 2022 Crash",
                "Bitcoin dropped ~30% in 5 days during Terra/Luna contagion",
                0.30, 5);
    }

    /** FTX collapse: −25 % altcoins over 3 days. */
    public static StressScenario ftxCollapse() {
        return new StressScenario("FTX Collapse",
                "Alts dropped ~25% in 3 days after FTX insolvency",
                0.25, 3);
    }

    /** COVID crash: −40 % across all assets over 5 days. */
    public static StressScenario covidCrash() {
        return new StressScenario("COVID Crash",
                "Broad market sell-off: ~40% drawdown across all assets in 5 days",
                0.40, 5);
    }

    /** Luna/UST collapse: −20 % across all assets over 7 days. */
    public static StressScenario lunaCollapse() {
        return new StressScenario("Luna Collapse",
                "UST de-peg triggered ~20% drop across crypto over 7 days",
                0.20, 7);
    }

    /** Oct 10 2025 crash: −35 % broad market over 4 days. */
    public static StressScenario oct2025Crash() {
        return new StressScenario("Oct 10 2025 Crash",
                "Broad crypto sell-off: ~35% drawdown across all assets in 4 days",
                0.35, 4);
    }

    /** Create an ad-hoc custom scenario. */
    public static StressScenario custom(String name, double shock, int days) {
        return new StressScenario(name, "Custom scenario: " + name, shock, days);
    }
}
