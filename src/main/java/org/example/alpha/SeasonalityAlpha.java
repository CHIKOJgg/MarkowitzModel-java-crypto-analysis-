package org.example.alpha;

import org.ojalgo.matrix.MatrixR064;

/**
 * Generates alpha signals based on crypto seasonality patterns.
 *
 * <p>Captures known effects:
 * <ul>
 *   <li>Weekend effect — crypto tends to be weaker on weekends</li>
 *   <li>Month-end effect — slight positive bias around month-end</li>
 *   <li>Monday effect — positive Monday bias</li>
 * </ul>
 *
 * <p>Since the exact calendar date isn't available from returns alone,
 * the signal uses position-within-the-series as a proxy, cycling
 * through a 7-day pattern.
 */
public class SeasonalityAlpha implements AlphaModel {

    private final double weekendPenalty;
    private final double monthEndBonus;

    /**
     * @param weekendPenalty  signal reduction for weekend days (e.g. 0.005)
     * @param monthEndBonus   signal boost for month-end (e.g. 0.003)
     */
    public SeasonalityAlpha(double weekendPenalty, double monthEndBonus) {
        this.weekendPenalty = weekendPenalty;
        this.monthEndBonus  = monthEndBonus;
    }

    public SeasonalityAlpha() {
        this(0.005, 0.003);
    }

    @Override
    public MatrixR064 predict(MatrixR064 returns) {
        int rows = (int) returns.countRows();
        int cols = (int) returns.countColumns();

        // Determine "day of week" from row position
        int dayOfWeek = rows % 7;  // 0=Sun, 1=Mon, ..., 6=Sat
        boolean isWeekend = dayOfWeek == 0 || dayOfWeek == 6;
        boolean isMonthEnd = rows % 30 >= 27;  // approximate last few days of month

        double baseSignal = isWeekend ? -weekendPenalty : 0.005;
        if (isMonthEnd) baseSignal += monthEndBonus;

        double[] signal = new double[cols];
        for (int j = 0; j < cols; j++) signal[j] = baseSignal;

        return MatrixR064.FACTORY.rows(new double[][]{signal});
    }

    @Override
    public String name() {
        return "Seasonality";
    }
}
