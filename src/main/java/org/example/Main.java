package org.example;

import org.ojalgo.data.domain.finance.portfolio.MarkowitzModel;
import org.ojalgo.function.aggregator.Aggregator;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final List<String> coins = List.of(
            "bitcoin", "ethereum", "solana", "hyperliquid",
            "the-open-network", "mantle", "monero", "tether"
    );

    public static void main(String[] args) {

        DataFecther dataFetcher = new DataFecther();
        ArrayList<CoinData> data = dataFetcher.fetchAllCoins(coins);

        PortfolioProcessor processor = new PortfolioProcessor();
        MatrixR064 returnMatrix = processor.buildReturnMatrix(data);

        // ---- Expected Returns (Mean vector)
        MatrixR064 expectedReturns = returnMatrix.reduceColumns(Aggregator.AVERAGE);

        // ---- Covariance Matrix
        MatrixR064 centered = returnMatrix.subtract(expectedReturns);
        MatrixR064 covarianceMatrix = centered.transpose()
                .multiply(centered)
                .divide(returnMatrix.countRows() - 1);

    //min risk

        MatrixR064.DenseReceiver receiver =
                MatrixR064.FACTORY.makeDense(coins.size());

        receiver.fillAll(1.0);

        MatrixR064 equalReturns = receiver.get();

        MarkowitzModel minRiskModel = new MarkowitzModel(covarianceMatrix, equalReturns);
        minRiskModel.setShortingAllowed(false);

        for (int i = 0; i < coins.size(); i++) {
            minRiskModel.setUpperLimit(i, BigDecimal.valueOf(0.4));
        }

        System.out.println("min risk");
        printPortfolioResults(minRiskModel);

        MarkowitzModel optimalModel = new MarkowitzModel(covarianceMatrix, expectedReturns);
        optimalModel.setShortingAllowed(false);

        for (int i = 0; i < coins.size(); i++) {
            optimalModel.setUpperLimit(i, BigDecimal.valueOf(0.33));
        }

        System.out.println("\n optimal markowitz (maximizin sharp coefficient)");
        printPortfolioResults(optimalModel);

        List<BigDecimal> weights = optimalModel.getWeights();

        FileWriterOutput writer = new FileWriterOutput();
        writer.writeOutputData(coins, weights);
    }

    private static void printPortfolioResults(MarkowitzModel model) {

        List<BigDecimal> weights = model.getWeights();

        for (int i = 0; i < weights.size(); i++) {
            BigDecimal percent = weights.get(i)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);

            System.out.println(coins.get(i).toUpperCase() + ": " + percent + "%");
        }

        double meanReturn = model.getMeanReturn();
        double variance = model.getReturnVariance();
        double volatility = Math.sqrt(variance);

        double sharpe = volatility != 0 ? meanReturn / volatility : 0;
        double annualizedSharpe = sharpe * Math.sqrt(365);

        System.out.println();
        System.out.println("Expected return/day: " + String.format("%.4f%%", meanReturn * 100));
        System.out.println("Volatility:          " + String.format("%.4f%%", volatility * 100));
        System.out.println("Sharpe (annualized): " + String.format("%.4f", annualizedSharpe));
    }
}