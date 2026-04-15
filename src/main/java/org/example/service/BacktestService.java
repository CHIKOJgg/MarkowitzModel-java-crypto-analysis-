package org.example.service;

import org.example.model.BacktestResult;
import org.example.model.PortfolioResult;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class BacktestService {

    private final int window;
    private final int horizon;
    private final double feeRate;
    private final PortfolioService portfolioService;

    public BacktestService(int window, int horizon, double feeRate, PortfolioService ps) {
        this.window = window;
        this.horizon = horizon;
        this.feeRate = feeRate;
        this.portfolioService = ps;
    }

    public BacktestResult run(MatrixR064 returns, double targetReturn,
                              Consumer<String> progress) {

        double equity = 1.0;
        List<Double> equityCurve = new ArrayList<>();
        List<BigDecimal> prevWeights = null;

        int totalSteps = (int) returns.countRows() - window - horizon;

        for (int t = window; t < returns.countRows() - horizon; t++) {

            MatrixR064 train = returns.rows(t - window, t);
            MatrixR064 test = returns.rows(t, t + horizon);

            PortfolioResult result = portfolioService.buildOptimal(train, targetReturn);

            if (prevWeights != null) {
                double turnover = computeTurnover(prevWeights, result.weights());
                double cost = turnover * feeRate;
                equity *= (1.0 - cost);
            }

            double pnl = simulate(test, result.weights());

            equity *= (1.0 + pnl);
            equityCurve.add(equity);

            prevWeights = result.weights();

            if (progress != null) {
                int step = t - window + 1;
                progress.accept("Step " + step + "/" + totalSteps + " | Equity: " + equity);
            }
        }

        return computeStats(equityCurve);
    }

    private double simulate(MatrixR064 testReturns, List<BigDecimal> weights) {

        double equity = 1.0;

        for (int d = 0; d < testReturns.countRows(); d++) {

            double dailyReturn = 0;

            for (int a = 0; a < testReturns.countColumns(); a++) {
                dailyReturn += testReturns.get(d, a) * weights.get(a).doubleValue();
            }

            equity *= (1.0 + dailyReturn);
        }

        return equity - 1.0;
    }

    private double computeTurnover(List<BigDecimal> oldW, List<BigDecimal> newW) {
        double t = 0;
        for (int i = 0; i < oldW.size(); i++) {
            t += Math.abs(newW.get(i).doubleValue() - oldW.get(i).doubleValue());
        }
        return t;
    }


    private BacktestResult computeStats(List<Double> curve) {

        if (curve.isEmpty()) return new BacktestResult(curve, 1.0, 0.0, 0.0);

        double finalEq = curve.get(curve.size() - 1);

        double maxDD = 0;
        double peak = curve.get(0);

        for (double v : curve) {
            if (v > peak) peak = v;
            maxDD = Math.max(maxDD, (peak - v) / peak);
        }

        List<Double> rets = new ArrayList<>();
        for (int i = 1; i < curve.size(); i++) {
            rets.add(curve.get(i) / curve.get(i - 1) - 1);
        }

        double mean = rets.stream().mapToDouble(x -> x).average().orElse(0);
        double std = Math.sqrt(rets.stream()
                .mapToDouble(x -> (x - mean) * (x - mean))
                .average().orElse(0));

        double periodsPerYear = 365.0 / horizon;
        double sharpe = std != 0 ? mean / std * Math.sqrt(periodsPerYear) : 0;

        return new BacktestResult(curve, finalEq, maxDD, sharpe);
    }
}