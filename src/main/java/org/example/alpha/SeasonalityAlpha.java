package org.example.alpha;

import org.ojalgo.matrix.MatrixR064;

/**
 * Generates alpha signals based on day-of-week periodicity detected from data.
 *
 * <p>Instead of relying on absolute calendar dates (which are unavailable from
 * the return matrix alone), this model detects 7-day cycles directly from the
 * return series. The last row is treated as "today"; returns are grouped into
 * 7 daily buckets going backwards. The signal is the deviation of today's
 * average return from the overall weekly average.
 */
public class SeasonalityAlpha implements AlphaModel {

    @Override
    public MatrixR064 predict(MatrixR064 returns) {
        int rows = (int) returns.countRows();
        int cols = (int) returns.countColumns();
        if (rows < 7) {
            double[] neutral = new double[cols];
            return MatrixR064.FACTORY.rows(new double[][]{neutral});
        }

        // Group returns by day-of-week position (bucket 0 = most recent day)
        double[] dowSum = new double[7];
        int[] dowCount = new int[7];
        for (int i = 0; i < rows; i++) {
            int dow = i % 7;
            for (int j = 0; j < cols; j++) {
                dowSum[dow] += returns.get(rows - 1 - i, j);
                dowCount[dow]++;
            }
        }

        double[] dowAvg = new double[7];
        double crossAvg = 0;
        int validDays = 0;
        for (int d = 0; d < 7; d++) {
            if (dowCount[d] > 0) {
                dowAvg[d] = dowSum[d] / dowCount[d];
                crossAvg += dowAvg[d];
                validDays++;
            }
        }
        crossAvg /= validDays;

        // Signal = today's day-of-week effect vs weekly average, capped
        double raw = dowAvg[0] - crossAvg;
        double signal = Math.max(-0.01, Math.min(0.01, raw));

        double[] out = new double[cols];
        for (int j = 0; j < cols; j++) out[j] = signal;
        return MatrixR064.FACTORY.rows(new double[][]{out});
    }

    @Override
    public String name() {
        return "Seasonality";
    }
}
