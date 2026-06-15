package org.example.alpha;

import org.ojalgo.matrix.MatrixR064;

public class EqualWeightAlpha implements AlphaModel {

    private final int n;

    public EqualWeightAlpha(int n) {
        this.n = n;
    }

    @Override
    public MatrixR064 predict(MatrixR064 returns) {
        double[][] mu = new double[1][n];
        for (int j = 0; j < n; j++) {
            mu[0][j] = 1.0 / n;
        }
        return MatrixR064.FACTORY.rows(mu);
    }

    @Override
    public String name() {
        return "EqualWeight";
    }
}
