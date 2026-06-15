package org.example.constraint;

import org.ojalgo.matrix.MatrixR064;

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
     * Apply the constraint with full returns context.
     *
     * <p>Default implementation ignores returns for backward compatibility.
     *
     * @param weights current portfolio weights (one per asset)
     * @param returns [days × assets] training returns matrix (may be ignored)
     * @return adjusted weights (same size)
     */
    default List<BigDecimal> apply(List<BigDecimal> weights, MatrixR064 returns) {
        return apply(weights);
    }

    /**
     * Apply the constraint (backward-compatible, no returns context).
     *
     * @param weights mutable input weights (one per asset)
     * @return adjusted weights (same size)
     */
    List<BigDecimal> apply(List<BigDecimal> weights);

    /** Short description shown in tooltips / logs. */
    String describe();
}
