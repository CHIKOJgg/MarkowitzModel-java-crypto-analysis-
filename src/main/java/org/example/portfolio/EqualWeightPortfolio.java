package org.example.portfolio;

import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Equal-weight (1/N) portfolio.
 *
 * <p>In <b>long-only</b> mode: w_i = 1/n for all assets.
 *
 * <p>In <b>signal-weighted</b> mode: sign of weight follows the alpha signal,
 * magnitude is equal. Positive signals → long, negative → short.
 */
public class EqualWeightPortfolio implements PortfolioModel {

    private final boolean signalDirected;   // use alpha sign to flip weights

    public EqualWeightPortfolio(boolean signalDirected) {
        this.signalDirected = signalDirected;
    }

    public EqualWeightPortfolio() { this(false); }

    // ── PortfolioModel ────────────────────────────────────────────────────────

    @Override
    public List<BigDecimal> allocate(MatrixR064 returns, MatrixR064 mu) {
        int cols = (int) returns.countColumns();
        double baseWeight = 1.0 / cols;

        List<BigDecimal> weights = new ArrayList<>(cols);
        for (int j = 0; j < cols; j++) {
            double w = baseWeight;
            if (signalDirected && mu.get(0, j) < 0) {
                w = -baseWeight;  // symmetric short
            }
            weights.add(BigDecimal.valueOf(w));
        }
        return weights;
    }

    @Override
    public String name() {
        return signalDirected ? "Equal Weight (Signal-Directed)" : "Equal Weight (1/N)";
    }
}
