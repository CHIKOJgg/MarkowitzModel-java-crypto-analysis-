package org.example.execution;

import java.math.BigDecimal;
import java.util.List;

/**
 * Models transaction costs applied when rebalancing from old weights to new weights.
 *
 * <p>Returns the equity multiplier after costs (e.g. 0.999 means 0.1% cost taken out).
 */
public interface ExecutionModel {

    /**
     * @param equity    current portfolio value
     * @param oldWeights weights before rebalancing (null on first step)
     * @param newWeights weights after rebalancing
     * @return equity after costs
     */
    double applyCosts(double equity, List<BigDecimal> oldWeights, List<BigDecimal> newWeights);

    /** Human-readable description. */
    String describe();
}
