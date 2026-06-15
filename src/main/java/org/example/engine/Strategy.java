package org.example.engine;

import org.example.alpha.AlphaModel;
import org.example.constraint.Constraint;
import org.example.constraint.LeverageConstraint;
import org.example.constraint.LongOnlyConstraint;
import org.example.constraint.MarketNeutralConstraint;
import org.example.constraint.PortfolioRiskConstraint;
import org.example.constraint.WeightValidator;
import org.example.portfolio.PortfolioModel;
import org.example.risk.PassthroughRisk;
import org.example.risk.RiskModel;
import org.example.util.MatrixUtils;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A complete investable strategy assembled from pluggable components:
 *
 * <pre>
 *   Alpha → Portfolio → Risk → [Constraints] → Weights
 * </pre>
 *
 * <p>Use the {@link Builder} for ergonomic construction.
 */
public class Strategy {

    private final String         id;
    private final AlphaModel     alpha;
    private final PortfolioModel portfolio;
    private final RiskModel      risk;
    private final List<Constraint> constraints;

    private Strategy(Builder b) {
        this.id          = b.id;
        this.alpha       = b.alpha;
        this.portfolio   = b.portfolio;
        this.risk        = b.risk;
        this.constraints = List.copyOf(b.constraints);
    }

    // ── Core pipeline ─────────────────────────────────────────────────────────

    /**
     * Run the full signal → weights pipeline on the given training window.
     *
     * @param returns [days × assets] training data
     * @return final portfolio weights (one per asset)
     */
    public List<BigDecimal> build(MatrixR064 returns) {
        MatrixUtils.assertClean(returns);

        int n = (int) returns.countColumns();

        // 1. Alpha: predict expected returns
        MatrixR064 mu = alpha.predict(returns);

        // 2. Portfolio: allocate
        List<BigDecimal> weights;
        try {
            weights = portfolio.allocate(returns, mu);
        } catch (Exception e) {
            // Solver failed — return equal-weight fallback
            double eq = 1.0 / n;
            return Collections.nCopies(n, BigDecimal.valueOf(eq));
        }

        // Guard against null/partial results from the solver
        if (weights == null || weights.size() != n
                || weights.stream().anyMatch(w -> w == null
                        || Double.isNaN(w.doubleValue())
                        || Double.isInfinite(w.doubleValue()))) {
            double eq = 1.0 / n;
            return Collections.nCopies(n, BigDecimal.valueOf(eq));
        }

        // 3. Risk model: vol-target or pass-through
        try {
            weights = risk.adjust(weights, returns);
        } catch (Exception ignored) {}

        // 4. Constraint chain (order matters)
        for (Constraint c : constraints) {
            try {
                weights = c.apply(weights, returns);
            } catch (Exception e) {
                double eq = 1.0 / n;
                return Collections.nCopies(n, BigDecimal.valueOf(eq));
            }
        }

        return weights;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String       getId()        { return id;        }
    public AlphaModel   getAlpha()     { return alpha;     }
    public PortfolioModel getPortfolio(){ return portfolio; }
    public RiskModel    getRisk()      { return risk;      }

    /** Full human-readable description of all components. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("Strategy: ").append(id).append("\n");
        sb.append("  Alpha     : ").append(alpha.name()).append("\n");
        sb.append("  Portfolio : ").append(portfolio.name()).append("\n");
        sb.append("  Risk      : ").append(risk.describe()).append("\n");
        if (!constraints.isEmpty()) {
            sb.append("  Constraints:\n");
            constraints.forEach(c -> sb.append("    • ").append(c.describe()).append("\n"));
        }
        return sb.toString();
    }

    @Override
    public String toString() { return id; }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder(String id, AlphaModel alpha, PortfolioModel portfolio) {
        return new Builder(id, alpha, portfolio);
    }

    public static final class Builder {

        private final String         id;
        private final AlphaModel     alpha;
        private final PortfolioModel portfolio;
        private RiskModel            risk        = new PassthroughRisk();
        private final List<Constraint> constraints = new ArrayList<>();

        private Builder(String id, AlphaModel alpha, PortfolioModel portfolio) {
            this.id        = id;
            this.alpha     = alpha;
            this.portfolio = portfolio;
        }

        public Builder risk(RiskModel rm)             { this.risk = rm; return this; }
        public Builder constraint(Constraint c)       { constraints.add(c); return this; }
        public Builder leverage(double max)           { return constraint(new LeverageConstraint(max)); }
        public Builder marketNeutral()                { return constraint(new MarketNeutralConstraint()); }
        /** Market-neutral when shorting allowed; long-only otherwise. */
        public Builder shortingPolicy(boolean allowShorting) {
            return allowShorting ? marketNeutral() : constraint(new LongOnlyConstraint());
        }
        /** Long-only clamp only when shorting is disabled (for inherently long strategies). */
        public Builder longOnlyWhenDisabled(boolean allowShorting) {
            if (!allowShorting) constraint(new LongOnlyConstraint());
            return this;
        }
        public Builder validate(double maxLev)        { return constraint(new WeightValidator(maxLev)); }
        public Builder validate()                     { return validate(10.0); }
        public Builder portfolioRiskConstraint(double maxVar, boolean useCvar) {
            if (maxVar > 0) constraint(new PortfolioRiskConstraint(maxVar, useCvar));
            return this;
        }

        public Strategy build() { return new Strategy(this); }
    }
}
