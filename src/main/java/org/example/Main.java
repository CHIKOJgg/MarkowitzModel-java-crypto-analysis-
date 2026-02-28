package org.example;

import org.ojalgo.data.domain.finance.portfolio.MarkowitzModel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.ojalgo.function.aggregator.Aggregator;
import org.ojalgo.matrix.MatrixR064;
import org.ojalgo.*;

    //TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {

    private static final List<String> coins = List.of("bitcoin","ethereum","solana","hyperliquid","the-open-network","mantle","monero","tether");
    public static void main(String[] args) {

        DataFecther dataFecther = new DataFecther();
        ArrayList<CoinData> responseArrayList = dataFecther.fetchAllCoins( coins);
        IntStream.range(0,1).forEach(i->
                {
                    //System.out.println(responseArrayList.get(i).getPricesMap());
                    System.out.println(responseArrayList.get(i).getReturnMap());
                   // System.out.println(responseArrayList.get(i).getPrices());
                }
        );
        PortfolioProcessor portfolioProcessor = new PortfolioProcessor();
        MatrixR064 returnMatrix = portfolioProcessor.buildReturnMatrix(responseArrayList);


        MatrixR064 expectedReturns = returnMatrix.reduceColumns(Aggregator.AVERAGE);

        MatrixR064 centered = returnMatrix.subtract(expectedReturns);
        MatrixR064 covarianceMatrix = centered.transpose().multiply(centered)
                .divide(returnMatrix.countRows() - 1);
        MarkowitzModel model = new MarkowitzModel(covarianceMatrix, expectedReturns);

        model.setShortingAllowed(false);
        for (int i = 0; i < coins.size(); i++) {
            model.setUpperLimit(i, BigDecimal.valueOf(0.33));
        }
        List<BigDecimal> weights = model.getWeights();
        System.out.println("optimal portfolio");
        for (int i = 0; i < weights.size(); i++) {
            BigDecimal percent = weights.get(i)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
            System.out.println(coins.get(i).toUpperCase() + ": " + percent + "%");
        }
        FileWriterOutput fileWriterOutput = new FileWriterOutput();
        fileWriterOutput.writeOutputData(coins, weights);
//        double portfolioReturn = model.getMeanReturn();
//        double variance = model.getReturnVariance();
//        double volatility = Math.sqrt(variance);
//
//
//        double sharpeRatio = (volatility != 0) ? (portfolioReturn / volatility) : 0;
//
//        System.out.println("Ожидаемая доходность: " + (portfolioReturn * 100) + "%");
//        System.out.println("Волатильность (Риск): " + (volatility * 100) + "%");
//        System.out.println("Коэффициент Шарпа: " + sharpeRatio);

    }
}