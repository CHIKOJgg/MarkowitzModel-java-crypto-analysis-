package org.example.constraint;

import java.math.BigDecimal;
import java.util.List;

/**
 * Post-optimisation weight transformation.
 *
 * <p>Constraints are applied in a chain after the portfolio model runs.
 * Each constraint receives the current weights and returns a (potentially
 * modified) list of the same size.
 *
 * <p>Implementations must be deterministic and must not change the size of
 * the weight list.
 */
public interface Constraint {

    /**
     * Apply the constraint.
     *
     * @param weights mutable input weights (one per asset)
     * @return adjusted weights (same size)
     */
    List<BigDecimal> apply(List<BigDecimal> weights);

    /** Short description shown in tooltips / logs. */
    String describe();
}
