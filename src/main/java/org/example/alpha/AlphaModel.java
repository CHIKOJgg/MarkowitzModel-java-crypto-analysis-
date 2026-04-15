package org.example.alpha;

import org.ojalgo.matrix.MatrixR064;

/**
 * Generates a [1 × n] matrix of expected returns ("signals") from a
 * historical returns matrix [days × n].
 *
 * <p>Implementations are stateless and deterministic — any state (e.g. alpha
 * parameter) must be supplied via the constructor.
 */
public interface AlphaModel {

    /**
     * Predict expected returns for the next period.
     *
     * @param returns historical returns [rows=days, cols=assets]
     * @return expected returns [1 × assets]
     */
    MatrixR064 predict(MatrixR064 returns);

    /** Human-readable name shown in the UI. */
    String name();
}
