package org.example.service;

import org.example.model.CoinData;
import org.ojalgo.matrix.MatrixR064;

import java.util.List;

/**
 * Converts a list of {@link CoinData} objects into an ojalgo MatrixR064 of
 * daily returns [rows = days, cols = coins], aligned to the shortest history.
 */
public class PortfolioProcessor {

    public MatrixR064 buildReturnMatrix(List<CoinData> coins) {
        int minLen = coins.stream()
                .mapToInt(c -> c.getReturnMap().get(c.getCoinName()).size())
                .min()
                .orElseThrow(() -> new IllegalArgumentException("No coin data provided"));

        int n = coins.size();
        MatrixR064.DenseReceiver builder = MatrixR064.FACTORY.makeDense(minLen, n);

        for (int col = 0; col < n; col++) {
            CoinData coin = coins.get(col);
            List<Double> rets = coin.getReturnMap().get(coin.getCoinName());
            for (int row = 0; row < minLen; row++) {
                builder.set(row, col, rets.get(row));
            }
        }

        return builder.get();
    }
}
