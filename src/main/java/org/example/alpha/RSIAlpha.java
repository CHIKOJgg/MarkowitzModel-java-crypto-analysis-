package org.example.alpha;

import org.example.Defaults;
import org.ojalgo.matrix.MatrixR064;

/**
 * RSI (Relative Strength Index) alpha model.
 *
 * <p>RSI measures the speed and magnitude of recent price changes:
 * <pre>
 *   RS = avg_gain / avg_loss
 *   RSI = 100 - 100 / (1 + RS)
 * </pre>
 *
 * <p>Signal:
 * <ul>
 *   <li>RSI > 70 → overbought → negative signal (expect reversion)</li>
 *   <li>RSI < 30 → oversold → positive signal (expect bounce)</li>
 *   <li>Otherwise → signal proportional to (50 - RSI) / 50</li>
 * </ul>
 *
 * <p>Uses exponential smoothing for gains/losses (Wilder's smoothing).
 */
public class RSIAlpha implements AlphaModel {

    private final int    period;
    private final double shortPenalty;

    public RSIAlpha(int period, double shortPenalty) {
        this.period       = period;
        this.shortPenalty = shortPenalty;
    }

    public RSIAlpha(int period) { this(period, Defaults.SHORT_PENALTY); }
    public RSIAlpha()           { this(Defaults.RSI_PERIOD, Defaults.SHORT_PENALTY); }

    @Override
    public MatrixR064 predict(MatrixR064 returns) {
        int rows = (int) returns.countRows();
        int cols = (int) returns.countColumns();
        double[][] mu = new double[1][cols];

        for (int j = 0; j < cols; j++) {
            // Compute RSI using Wilder's smoothing
            double avgGain = 0, avgLoss = 0;

            // Initial averages over first `period` observations
            for (int i = 0; i < Math.min(period, rows); i++) {
                double change = returns.get(i, j);
                if (change > 0) avgGain += change;
                else            avgLoss -= change;
            }
            avgGain /= period;
            avgLoss /= period;

            // Smoothed averages
            for (int i = period; i < rows; i++) {
                double change = returns.get(i, j);
                double gain = change > 0 ? change : 0;
                double loss = change < 0 ? -change : 0;
                avgGain = (avgGain * (period - 1) + gain) / period;
                avgLoss = (avgLoss * (period - 1) + loss) / period;
            }

            // RSI
            double rs = avgLoss > 1e-15 ? avgGain / avgLoss : 100.0;
            double rsi = 100.0 - 100.0 / (1.0 + rs);

            // Convert RSI to alpha signal
            // RSI 50 = neutral, RSI 0 = very oversold (buy), RSI 100 = very overbought (sell)
            double signal = (50.0 - rsi) / 50.0;  // range [-1, +1]

            // Apply short penalty to negative signals
            mu[0][j] = signal < 0 ? signal - shortPenalty : signal;
        }
        return MatrixR064.FACTORY.rows(mu);
    }

    @Override
    public String name() {
        return String.format("RSI(%d)", period);
    }
}
