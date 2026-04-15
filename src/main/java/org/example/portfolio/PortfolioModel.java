package org.example.portfolio;

import org.ojalgo.matrix.MatrixR064;
import java.math.BigDecimal;
import java.util.List;

/**
 * Converts a signal (expected returns) + historical returns into portfolio weights.
 *
 * <p>Weights must satisfy any built-in constraints of the implementation
 * (e.g. leverage cap, long-only). Post-processing constraints are applied
 * separately via the {@link org.example.constraint.Constraint} chain.
 */
public interface PortfolioModel {

    /**
     * Allocate capital.
     *
     * @param returns         historical returns [days × assets]
     * @param expectedReturns model signal       [1    × assets]
     * @return portfolio weights, one per asset (may be negative for shorts)
     */
    List<BigDecimal> allocate(MatrixR064 returns, MatrixR064 expectedReturns);

    /** Human-readable name shown in the UI. */
    String name();
}
