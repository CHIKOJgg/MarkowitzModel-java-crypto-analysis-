package org.example.service;

import org.example.model.PortfolioResult;
import org.ojalgo.data.domain.finance.portfolio.MarkowitzModel;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.List;

public class PortfolioService {

    private final double maxLong;
    private final double maxShort;
    private final double ewmaAlpha;
    private final double shrinkageLambda;
    private final double maxLeverage;
    private final boolean allowShorting;

    public PortfolioService(double maxLong, double maxShort,
                            double ewmaAlpha, double shrinkageLambda,
                            double maxLeverage, boolean allowShorting) {
        this.maxLong = maxLong;
        this.maxShort = maxShort;
        this.ewmaAlpha = ewmaAlpha;
        this.shrinkageLambda = shrinkageLambda;
        this.maxLeverage = maxLeverage;
        this.allowShorting = allowShorting;
    }

    // ───────────────────────────────

    public PortfolioResult buildMinRisk(MatrixR064 returns) {

        int n = (int) returns.countColumns();


        MatrixR064.DenseReceiver receiver =
                MatrixR064.FACTORY.makeDense(1, n);

        for (int i = 0; i < n; i++) {
            receiver.set(0, i, 1.0);
        }

        MatrixR064 mu = receiver.get();

        MatrixR064 cov = buildCov(returns);

        MarkowitzModel model = createModel(cov, mu, n, null);

        List<BigDecimal> weights = postProcess(model.getWeights());

        return wrapResult(mu, cov, weights);
    }

    public PortfolioResult buildOptimal(MatrixR064 returns, double targetReturn) {

        MatrixR064 mu = computeEWMA(returns, ewmaAlpha);
        mu = applyShortPenalty(mu, 0.02);

        MatrixR064 cov = buildCov(returns);

        MarkowitzModel model = createModel(
                cov,
                mu,
                (int) returns.countColumns(),
                BigDecimal.valueOf(targetReturn)
        );

        List<BigDecimal> weights = postProcess(model.getWeights());

        return wrapResult(mu, cov, weights);
    }

    // ───────────────────────────────

    private MatrixR064 buildCov(MatrixR064 returns) {
        MatrixR064 centered = subtractColMeans(returns);

        MatrixR064 cov = centered.transpose()
                .multiply(centered)
                .divide(returns.countRows() - 1);

        return shrink(cov, shrinkageLambda);
    }

    MatrixR064 computeEWMA(MatrixR064 returns, double alpha) {
        int rows = (int) returns.countRows();
        int cols = (int) returns.countColumns();

        double[][] result = new double[1][cols];

        for (int j = 0; j < cols; j++) {
            double ewma = 0;
            for (int i = 0; i < rows; i++) {
                ewma = alpha * returns.get(i, j) + (1 - alpha) * ewma;
            }
            result[0][j] = ewma;
        }

        return MatrixR064.FACTORY.rows(result);
    }

    MatrixR064 applyShortPenalty(MatrixR064 mu, double penalty) {
        int cols = (int) mu.countColumns();

        double[][] result = new double[1][cols];

        for (int i = 0; i < cols; i++) {
            double v = mu.get(0, i);
            result[0][i] = v < 0 ? v - penalty : v;
        }

        return MatrixR064.FACTORY.rows(result);
    }

    MatrixR064 shrink(MatrixR064 cov, double lambda) {
        int n = (int) cov.countRows();
        MatrixR064 eye = MatrixR064.FACTORY.makeEye(n, n);
        return cov.multiply(lambda).add(eye.multiply(1.0 - lambda));
    }

    private MatrixR064 subtractColMeans(MatrixR064 m) {
        int rows = (int) m.countRows();
        int cols = (int) m.countColumns();

        double[][] out = new double[rows][cols];

        for (int j = 0; j < cols; j++) {
            double mean = 0;

            for (int i = 0; i < rows; i++) {
                mean += m.get(i, j);
            }

            mean /= rows;

            for (int i = 0; i < rows; i++) {
                out[i][j] = m.get(i, j) - mean;
            }
        }

        return MatrixR064.FACTORY.rows(out);
    }

    private MarkowitzModel createModel(MatrixR064 cov, MatrixR064 mu,
                                       int n, BigDecimal targetReturn) {

        MarkowitzModel model = new MarkowitzModel(cov, mu);

        model.setShortingAllowed(allowShorting);

        if (targetReturn != null) {
            model.setTargetReturn(targetReturn);
        }

        for (int i = 0; i < n; i++) {
            model.setUpperLimit(i, BigDecimal.valueOf(maxLong));

            if (allowShorting) {
                model.setLowerLimit(i, BigDecimal.valueOf(maxShort));
            }
        }

        return model;
    }

    private List<BigDecimal> postProcess(List<BigDecimal> weights) {
        weights = normalizeWeights(weights, maxLeverage);

        if (allowShorting) {
            weights = makeMarketNeutral(weights);
        }

        return weights;
    }

    private List<BigDecimal> normalizeWeights(List<BigDecimal> weights, double lev) {
        double total = weights.stream().mapToDouble(w -> Math.abs(w.doubleValue())).sum();

        if (total <= lev) return weights;

        double scale = lev / total;

        return weights.stream()
                .map(w -> w.multiply(BigDecimal.valueOf(scale)))
                .toList();
    }

    private List<BigDecimal> makeMarketNeutral(List<BigDecimal> weights) {
        double sum = weights.stream().mapToDouble(BigDecimal::doubleValue).sum();
        double adj = sum / weights.size();

        return weights.stream()
                .map(w -> w.subtract(BigDecimal.valueOf(adj)))
                .toList();
    }

    // 🔥 ГЛАВНЫЙ ФИКС
    private static PortfolioResult wrapResult(MatrixR064 mu, MatrixR064 cov, List<BigDecimal> weights) {

        int n = weights.size();

        double expectedReturn = 0;
        for (int i = 0; i < n; i++) {
            expectedReturn += weights.get(i).doubleValue() * mu.get(0, i);
        }

        double variance = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                variance += weights.get(i).doubleValue()
                        * cov.get(i, j)
                        * weights.get(j).doubleValue();
            }
        }

        double volatility = Math.sqrt(Math.max(variance, 0));
        double sharpe = volatility != 0 ? expectedReturn / volatility * Math.sqrt(365) : 0;

        double leverage = weights.stream()
                .mapToDouble(w -> Math.abs(w.doubleValue()))
                .sum();

        return new PortfolioResult(weights, expectedReturn, volatility, sharpe, leverage);
    }
}