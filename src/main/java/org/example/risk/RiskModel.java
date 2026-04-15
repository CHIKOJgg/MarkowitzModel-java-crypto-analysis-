package org.example.risk;

import org.ojalgo.matrix.MatrixR064;
import java.math.BigDecimal;
import java.util.List;

/**
 * Adjusts portfolio weights to hit a volatility target.
 *
 * <p>The risk model sits between portfolio construction and execution,
 * scaling the raw weights so the ex-ante portfolio volatility matches
 * a desired level.
 */
public interface RiskModel {

    /**
     * @param weights raw weights from the portfolio model
     * @param returns historical returns (for vol estimation)
     * @return adjusted weights
     */
    List<BigDecimal> adjust(List<BigDecimal> weights, MatrixR064 returns);

    /** Human-readable description. */
    String describe();
}
