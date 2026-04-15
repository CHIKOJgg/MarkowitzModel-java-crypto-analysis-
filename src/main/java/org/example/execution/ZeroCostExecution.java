package org.example.execution;

import java.math.BigDecimal;
import java.util.List;

/** No-cost baseline — useful for comparing gross vs. net PnL. */
public class ZeroCostExecution implements ExecutionModel {

    @Override
    public double applyCosts(double equity,
                             List<BigDecimal> oldWeights,
                             List<BigDecimal> newWeights) {
        return equity;
    }

    @Override
    public String describe() { return "Zero Cost (Gross PnL)"; }
}
