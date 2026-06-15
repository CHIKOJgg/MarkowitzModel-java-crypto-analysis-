package org.example.execution;

import org.example.Defaults;
import java.math.BigDecimal;
import java.util.List;

/**
 * Simple execution model:  cost = feeRate × turnover + slippage × turnover²
 *
 * <ul>
 *   <li>{@code feeRate}  — one-way commission (e.g. 0.001 = 0.1 %)</li>
 *   <li>{@code slippage} — market-impact coefficient (e.g. 0.0005)</li>
 * </ul>
 *
 * Turnover = Σ |w_new_i - w_old_i|
 */
public class SimpleExecution implements ExecutionModel {

    private final double feeRate;
    private final double slippage;

    public SimpleExecution(double feeRate, double slippage) {
        this.feeRate  = feeRate;
        this.slippage = slippage;
    }

    public SimpleExecution(double feeRate) { this(feeRate, Defaults.SLIPPAGE); }
    public SimpleExecution()               { this(Defaults.FEE_RATE, Defaults.SLIPPAGE); }

    @Override
    public double applyCosts(double equity,
                             List<BigDecimal> oldWeights,
                             List<BigDecimal> newWeights) {
        if (oldWeights == null) return equity;          // first step: no rebalancing

        double turnover = 0;
        for (int i = 0; i < newWeights.size(); i++) {
            turnover += Math.abs(newWeights.get(i).doubleValue()
                               - oldWeights.get(i).doubleValue());
        }

        double costFraction = feeRate * turnover + slippage * turnover * turnover;
        return equity * (1.0 - costFraction);
    }

    @Override
    public String describe() {
        return String.format("Simple(fee=%.3f%%, slip=%.4f%%)",
                feeRate * 100, slippage * 100);
    }
}
