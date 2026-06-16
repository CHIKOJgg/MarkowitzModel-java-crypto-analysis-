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

    public List<BigDecimal> build(MatrixR064 returns) {
        MatrixUtils.assertClean(returns);
        int n = (int) returns.countColumns();

        MatrixR064 mu = alpha.predict(returns);

        List<BigDecimal> weights;
        try {
            weights = portfolio.allocate(returns, mu);
        } catch (Exception e) {
            double eq = 1.0 / n;
            return Collections.nCopies(n, BigDecimal.valueOf(eq));
        }

        if (weights == null || weights.size() != n
                || weights.stream().anyMatch(w -> w == null
                || Double.isNaN(w.doubleValue())
                || Double.isInfinite(w.doubleValue()))) {
            double eq = 1.0 / n;
            return Collections.nCopies(n, BigDecimal.valueOf(eq));
        }

        try {
            weights = risk.adjust(weights, returns);
        } catch (Exception ignored) {}

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

    public String       getId()        { return id;        }
    public AlphaModel   getAlpha()     { return alpha;     }
    public PortfolioModel getPortfolio(){ return portfolio; }
    public RiskModel    getRisk()      { return risk;      }

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

    @Override public String toString() { return id; }

    public static Builder builder(String id, AlphaModel alpha, PortfolioModel portfolio) {
        return new Builder(id, alpha, portfolio);
    }

    public static final class Builder {

        private final String         id;
        private final AlphaModel     alpha;
        private final PortfolioModel portfolio;
        private RiskModel            risk        = new PassthroughRisk();
        private final List<Constraint> constraints = new ArrayList<>();
        // Leverage stored so MarketNeutralConstraint can target the same gross.
        private double               maxLeverage = 1.0;

        private Builder(String id, AlphaModel alpha, PortfolioModel portfolio) {
            this.id        = id;
            this.alpha     = alpha;
            this.portfolio = portfolio;
        }

        public Builder risk(RiskModel rm)       { this.risk = rm; return this; }
        public Builder constraint(Constraint c) { constraints.add(c); return this; }

        public Builder leverage(double max) {
            this.maxLeverage = max;
            return constraint(new LeverageConstraint(max));
        }

        /** Market-neutral: zero net, gross = leverage (set before calling this). */
        public Builder marketNeutral() {
            return constraint(new MarketNeutralConstraint(maxLeverage));
        }

        /** Market-neutral when shorting allowed; long-only otherwise. */
        public Builder shortingPolicy(boolean allowShorting) {
            return allowShorting ? marketNeutral() : constraint(new LongOnlyConstraint());
        }

        public Builder longOnlyWhenDisabled(boolean allowShorting) {
            if (!allowShorting) constraint(new LongOnlyConstraint());
            return this;
        }

        public Builder validate(double maxLev)  { return constraint(new WeightValidator(maxLev)); }
        public Builder validate()               { return validate(10.0); }

        public Builder portfolioRiskConstraint(double maxVar, boolean useCvar) {
            if (maxVar > 0) constraint(new PortfolioRiskConstraint(maxVar, useCvar));
            return this;
        }

        public Strategy build() { return new Strategy(this); }
    }
}