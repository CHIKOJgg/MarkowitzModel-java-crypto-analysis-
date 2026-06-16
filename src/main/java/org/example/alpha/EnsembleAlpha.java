package org.example.alpha;

import org.example.util.MatrixUtils;
import org.ojalgo.matrix.MatrixR064;

import java.util.List;

/**
 * Dynamically weighted alpha ensemble.
 *
 * <p>Each sub-alpha's weight is proportional to its recent directional accuracy
 * (correlation between its prediction and the subsequent actual return).
 * Sub-alphas with negative correlation receive zero weight.
 */
public class EnsembleAlpha implements AlphaModel {

    private final List<AlphaModel> alphas;
    private final int evalWindow;

    /**
     * @param alphas     sub-alphas to ensemble
     * @param evalWindow number of recent periods to evaluate each sub-alpha
     */
    public EnsembleAlpha(List<AlphaModel> alphas, int evalWindow) {
        if (alphas.isEmpty()) throw new IllegalArgumentException("Need at least one sub-alpha");
        this.alphas = alphas;
        this.evalWindow = evalWindow;
    }

    public EnsembleAlpha(List<AlphaModel> alphas) {
        this(alphas, 20);
    }

    @Override
    public MatrixR064 predict(MatrixR064 returns) {
        int n = (int) returns.countColumns();

        // Evaluate each sub-alpha
        double totalWeight = 0;
        double[][] weightedSignals = new double[alphas.size()][n];
        double[] alphaWeights = new double[alphas.size()];

        for (int k = 0; k < alphas.size(); k++) {
            MatrixR064 signal = alphas.get(k).predict(returns);
            for (int j = 0; j < n; j++) weightedSignals[k][j] = signal.get(0, j);

            // Compute directional accuracy: correlation between last evalWindow
            // predictions and actual returns
            double accuracy = computeAccuracy(alphas.get(k), returns);
            alphaWeights[k] = Math.max(0, accuracy);
            totalWeight += alphaWeights[k];
        }

        // Blend signals
        double[] blended = new double[n];
        if (totalWeight > 1e-12) {
            for (int k = 0; k < alphas.size(); k++) {
                double w = alphaWeights[k] / totalWeight;
                for (int j = 0; j < n; j++) blended[j] += w * weightedSignals[k][j];
            }
        } else {
            // Equal weight fallback
            double eq = 1.0 / alphas.size();
            for (int k = 0; k < alphas.size(); k++)
                for (int j = 0; j < n; j++) blended[j] += eq * weightedSignals[k][j];
        }

        return MatrixR064.FACTORY.rows(new double[][]{blended});
    }

    private double computeAccuracy(AlphaModel alpha, MatrixR064 returns) {
        int rows = (int) returns.countRows();
        int cols = (int) returns.countColumns();
        int minTrain = Math.max(5, evalWindow);
        int start = Math.max(1, rows - minTrain - 1);
        int end = rows - 1;
        if (end <= start) return 0;

        double[] predDirs = new double[end - start];
        double[] actualDirs = new double[end - start];
        int idx = 0;
        for (int t = start; t < end; t++) {
            MatrixR064 window;
            try {
                window = MatrixUtils.sliceRows(returns, 0, t);
            } catch (Exception e) {
                continue;
            }
            if ((int) window.countRows() < 2) continue;
            MatrixR064 pred;
            try {
                pred = alpha.predict(window);
            } catch (Exception e) {
                continue;
            }
            double predDir = 0;
            for (int j = 0; j < cols; j++) predDir += pred.get(0, j);
            double actualDir = 0;
            for (int j = 0; j < cols; j++) actualDir += returns.get(t, j);
            predDirs[idx] = predDir;
            actualDirs[idx] = actualDir;
            idx++;
        }
        if (idx < 2) return 0;
        return correlation(predDirs, actualDirs, idx);
    }

    private static double correlation(double[] x, double[] y, int len) {
        double mx = 0, my = 0;
        for (int i = 0; i < len; i++) { mx += x[i]; my += y[i]; }
        mx /= len; my /= len;
        double cov = 0, vx = 0, vy = 0;
        for (int i = 0; i < len; i++) {
            double dx = x[i] - mx, dy = y[i] - my;
            cov += dx * dy; vx += dx * dx; vy += dy * dy;
        }
        double denom = Math.sqrt(vx * vy);
        return denom > 1e-12 ? cov / denom : 0;
    }

    @Override
    public String name() {
        return "Ensemble(" + String.join("+", alphas.stream().map(AlphaModel::name).toList()) + ")";
    }
}
