package org.example.alpha;

import org.ojalgo.matrix.MatrixR064;

/**
 * MACD (Moving Average Convergence Divergence) alpha.
 *
 * <p>MACD line = EMA(fast) − EMA(slow), Signal = EMA(MACD),
 * Histogram = MACD − Signal.  Normalized histogram is the output signal.
 */
public class MACDAlpha implements AlphaModel {

    private final int fastPeriod;
    private final int slowPeriod;
    private final int signalPeriod;

    public MACDAlpha(int fastPeriod, int slowPeriod, int signalPeriod) {
        this.fastPeriod  = fastPeriod;
        this.slowPeriod  = slowPeriod;
        this.signalPeriod = signalPeriod;
    }

    public MACDAlpha() {
        this(12, 26, 9);
    }

    @Override
    public MatrixR064 predict(MatrixR064 returns) {
        int rows = (int) returns.countRows();
        int cols = (int) returns.countColumns();
        double[][] mu = new double[1][cols];

        double fastAlpha = 2.0 / (fastPeriod + 1);
        double slowAlpha = 2.0 / (slowPeriod + 1);
        double sigAlpha  = 2.0 / (signalPeriod + 1);

        for (int j = 0; j < cols; j++) {
            double fastEma = returns.get(0, j);
            double slowEma = returns.get(0, j);
            double macdLine;
            double signalLine = 0;
            double hist = 0;

            for (int i = 0; i < rows; i++) {
                double r = returns.get(i, j);
                fastEma = r * fastAlpha + fastEma * (1 - fastAlpha);
                slowEma = r * slowAlpha + slowEma * (1 - slowAlpha);
                macdLine = fastEma - slowEma;
                signalLine = macdLine * sigAlpha + signalLine * (1 - sigAlpha);
                hist = macdLine - signalLine;
            }

            double maxAbs = 0;
            for (int i = 0; i < rows; i++) {
                double r = returns.get(i, j);
                maxAbs = Math.max(maxAbs, Math.abs(r));
            }
            double scale = maxAbs > 1e-10 ? 1.0 / (maxAbs * rows * 0.1) : 1.0;
            mu[0][j] = Math.max(-1.0, Math.min(1.0, hist * scale));
        }
        return MatrixR064.FACTORY.rows(mu);
    }

    @Override
    public String name() {
        return String.format("MACD(%d,%d,%d)", fastPeriod, slowPeriod, signalPeriod);
    }
}
