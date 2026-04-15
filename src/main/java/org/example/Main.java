//package org.example;
//
//import org.ojalgo.data.domain.finance.portfolio.MarkowitzModel;
//import org.ojalgo.function.aggregator.Aggregator;
//import org.ojalgo.matrix.MatrixR064;
//
//import java.math.BigDecimal;
//import java.math.RoundingMode;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.stream.IntStream;
//
//public class Main {
//
//    private static final double MAX_TOKEN_SHARE = 0.2;
//    private static final double MAX_LONG = 0.2;
//    private static final double MAX_SHORT = -0.15;
//    private static final List<String> coins = List.of(
//            "bitcoin"
//            , "ethereum"
//            , "solana"
//            , "hyperliquid"
//            , "the-open-network"
//            , "mantle"
//            , "monero"
//           // , "tether"
//
//            ,"zcash"
//    );
//    static void printStats(List<Double> equityCurve) {
//
//        double finalEquity = equityCurve.get(equityCurve.size() - 1);
//
//        double maxDrawdown = 0;
//        double peak = equityCurve.get(0);
//
//        for (double val : equityCurve) {
//            if (val > peak) peak = val;
//
//            double dd = (peak - val) / peak;
//            if (dd > maxDrawdown) maxDrawdown = dd;
//        }
//
//        // доходности
//        List<Double> returns = new ArrayList<>();
//        for (int i = 1; i < equityCurve.size(); i++) {
//            returns.add(equityCurve.get(i) / equityCurve.get(i - 1) - 1);
//        }
//
//        double mean = returns.stream().mapToDouble(x -> x).average().orElse(0);
//        double std = Math.sqrt(
//                returns.stream().mapToDouble(x -> Math.pow(x - mean, 2)).average().orElse(0)
//        );
//
//        double sharpe = std != 0 ? mean / std * Math.sqrt(365) : 0;
//
//        System.out.println("\n===== BACKTEST RESULT =====");
//        System.out.println("Final Equity: " + finalEquity);
//        System.out.println("Max Drawdown: " + maxDrawdown);
//        System.out.println("Sharpe:       " + sharpe);
//    }
//    static double simulate(MatrixR064 testReturns, List<BigDecimal> weights) {
//
//        int days = (int) testReturns.countRows();
//        int assets = (int) testReturns.countColumns();
//
//        double totalReturn = 0;
//
//        for (int d = 0; d < days; d++) {
//
//            double dailyPnL = 0;
//
//            for (int a = 0; a < assets; a++) {
//                double r = testReturns.get(d, a);
//                double w = weights.get(a).doubleValue();
//
//                dailyPnL += w * r;
//            }
//
//            totalReturn += dailyPnL;
//        }
//
//        return totalReturn;
//    }
//    static PortfolioResult buildPortfolio(MatrixR064 returns) {
//
//        MatrixR064 expectedReturns = Main.computeEWMA(returns, 0.1);
//        expectedReturns = Main.applyShortPenalty(expectedReturns, 0.02);
//
//        MatrixR064 centered = returns.subtract(expectedReturns);
//
//        MatrixR064 cov = centered.transpose()
//                .multiply(centered)
//                .divide(returns.countRows() - 1);
//
//        cov = Main.shrinkCovariance(cov, 0.9);
//
//        MarkowitzModel model = new MarkowitzModel(cov, expectedReturns);
//
//        model.setShortingAllowed(true);
//        model.setTargetReturn(BigDecimal.valueOf(0.005)); // 0.5%
//
//        int n = (int) returns.countColumns();
//
//        for (int i = 0; i < n; i++) {
//            model.setUpperLimit(i, BigDecimal.valueOf(0.2));
//            model.setLowerLimit(i, BigDecimal.valueOf(-0.15));
//        }
//
//        List<BigDecimal> weights = model.getWeights();
//
//        // пост-обработка
//        weights = Main.makeMarketNeutral(weights);
//        weights = Main.normalizeWeights(weights, 1.3);
//
//        return new PortfolioResult(weights);
//    }
//    public static MatrixR064 shrinkCovariance(MatrixR064 cov, double lambda) {
//        int n = (int) cov.countRows();
//
//        MatrixR064 identity = MatrixR064.FACTORY.makeEye(n, n);
//
//        return cov.multiply(lambda)
//                .add(identity.multiply(1.0 - lambda));
//    }
//    public static List<BigDecimal> normalizeWeights(List<BigDecimal> weights, double maxLeverage) {
//
//        double leverage = weights.stream()
//                .mapToDouble(w -> Math.abs(w.doubleValue()))
//                .sum();
//
//        if (leverage <= maxLeverage) return weights;
//
//        double scale = maxLeverage / leverage;
//
//        return weights.stream()
//                .map(w -> w.multiply(BigDecimal.valueOf(scale)))
//                .toList();
//    }
//    public static MatrixR064 computeEWMA(MatrixR064 returns, double alpha) {
//        int rows = (int) returns.countRows();
//        int cols = (int) returns.countColumns();
//
//        double[][] result = new double[1][cols];
//
//        for (int j = 0; j < cols; j++) {
//            double ewma = 0;
//
//            for (int i = 0; i < rows; i++) {
//                double r = returns.get(i, j);
//                ewma = alpha * r + (1 - alpha) * ewma;
//            }
//
//            result[0][j] = ewma;
//        }
//
//        return MatrixR064.FACTORY.rows(result);
//    }
//    public static MatrixR064 applyShortPenalty(MatrixR064 expectedReturns, double penalty) {
//
//        int cols = (int) expectedReturns.countColumns();
//        double[][] adjusted = new double[1][cols];
//
//        for (int i = 0; i < cols; i++) {
//            double mu = expectedReturns.get(0, i);
//
//            if (mu < 0) {
//                mu -= penalty;
//            }
//
//            adjusted[0][i] = mu;
//        }
//
//        return MatrixR064.FACTORY.rows(adjusted);
//    }
//    public static List<BigDecimal> makeMarketNeutral(List<BigDecimal> weights) {
//
//        double sum = weights.stream()
//                .mapToDouble(BigDecimal::doubleValue)
//                .sum();
//
//        double adjustment = sum / weights.size();
//
//        return weights.stream()
//                .map(w -> w.subtract(BigDecimal.valueOf(adjustment)))
//                .toList();
//    }
//    public static void main(String[] args) {
//
//        DataFecther dataFetcher = new DataFecther();
//        ArrayList<CoinData> data = dataFetcher.fetchAllCoins(coins);
//
//        PortfolioProcessor processor = new PortfolioProcessor();
//        MatrixR064 returnMatrix = processor.buildReturnMatrix(data);
//        MatrixR064 expectedReturns = computeEWMA(returnMatrix, 0.1);
//        expectedReturns = applyShortPenalty(expectedReturns, 0.02);
//        MatrixR064 centered = returnMatrix.subtract(expectedReturns);
//        MatrixR064 covarianceMatrix = centered.transpose()
//                .multiply(centered)
//                .divide(returnMatrix.countRows() - 1);
//        covarianceMatrix = shrinkCovariance(covarianceMatrix, 0.9);
//    //min risk
//
//        MatrixR064.DenseReceiver receiver =
//                MatrixR064.FACTORY.makeDense(coins.size());
//
//        receiver.fillAll(1.0);
//
//        MatrixR064 equalReturns = receiver.get();
//
//        MarkowitzModel minRiskModel = new MarkowitzModel(covarianceMatrix, equalReturns);
//        minRiskModel.setShortingAllowed(false);
//        for (int i = 0; i < coins.size(); i++) {
//            minRiskModel.setUpperLimit(i, BigDecimal.valueOf(MAX_LONG));
//            minRiskModel.setLowerLimit(i, BigDecimal.valueOf(MAX_SHORT));
//        }
//        System.out.println("min risk");
//        printPortfolioResults(minRiskModel);
//        MarkowitzModel optimalModel = new MarkowitzModel(covarianceMatrix, expectedReturns);
//        optimalModel.setTargetReturn(BigDecimal.valueOf(0.01)); // 1% в день
//        optimalModel.setShortingAllowed(true);
//
//        for (int i = 0; i < coins.size(); i++) {
//            optimalModel.setUpperLimit(i, BigDecimal.valueOf(MAX_TOKEN_SHARE));
//        }
//
//        System.out.println("\n optimal markowitz (maximizin sharp coefficient)");
//
//        List<BigDecimal> weights = optimalModel.getWeights();
//        weights = normalizeWeights(weights, 1.3);
//        weights = makeMarketNeutral(weights);
//
//        printPortfolioResults(optimalModel);
//        BacktestEngine.runBacktest(returnMatrix);
//        FileWriterOutput writer = new FileWriterOutput();
//        writer.writeOutputData(coins, weights);
//    }
//    private static double computeTurnover(List<BigDecimal> oldW, List<BigDecimal> newW) {
//
//        double turnover = 0;
//
//        for (int i = 0; i < oldW.size(); i++) {
//            turnover += Math.abs(newW.get(i).doubleValue() - oldW.get(i).doubleValue());
//        }
//
//        return turnover;
//    }
//    private static void printWeights(List<BigDecimal> weights) {
//
//        for (int i = 0; i < weights.size(); i++) {
//            BigDecimal percent = weights.get(i)
//                    .multiply(BigDecimal.valueOf(100))
//                    .setScale(2, RoundingMode.HALF_UP);
//
//            System.out.println(coins.get(i).toUpperCase() + ": " + percent + "%");
//        }
//    }
//    private static void printPortfolioResults(MarkowitzModel model) {
//
//        List<BigDecimal> weights = model.getWeights();
//
//        for (int i = 0; i < weights.size(); i++) {
//            BigDecimal percent = weights.get(i)
//                    .multiply(BigDecimal.valueOf(100))
//                    .setScale(2, RoundingMode.HALF_UP);
//
//            System.out.println(coins.get(i).toUpperCase() + ": " + percent + "%");
//        }
//
//        double meanReturn = model.getMeanReturn();
//        double variance = model.getReturnVariance();
//        double volatility = Math.sqrt(variance);
//
//        double sharpe = volatility != 0 ? meanReturn / volatility : 0;
//        double annualizedSharpe = sharpe * Math.sqrt(365);
//
//        System.out.println();
//        System.out.println("Expected return/day: " + String.format("%.4f%%", meanReturn * 100));
//        System.out.println("Volatility:          " + String.format("%.4f%%", volatility * 100));
//        System.out.println("Sharpe (annualized): " + String.format("%.4f", annualizedSharpe));
//    }
//}