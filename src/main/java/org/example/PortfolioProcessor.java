package org.example;

import org.ojalgo.matrix.MatrixR064;
import java.util.List;

public class PortfolioProcessor {

    public MatrixR064 buildReturnMatrix(List<CoinData> allCoins) {
        int minLength = allCoins.stream()
                .mapToInt(c -> c.getReturnMap().get(c.getCoinName()).size())
                .min()
                .orElse(0);

        int numCoins = allCoins.size();
        MatrixR064.DenseReceiver builder = MatrixR064.FACTORY.newDenseBuilder(minLength, numCoins);

        for (int col = 0; col < numCoins; col++) {
            CoinData currentCoin = allCoins.get(col);
            List<Double> returns = currentCoin.getReturnMap().get(currentCoin.getCoinName());

            for (int row = 0; row < minLength; row++) {
                builder.set(row, col, returns.get(row));
            }
        }

        return builder.get();
    }
}