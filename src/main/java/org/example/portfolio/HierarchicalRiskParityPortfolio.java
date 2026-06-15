package org.example.portfolio;

import org.example.util.MatrixUtils;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Hierarchical Risk Parity (HRP) by Lopez de Prado (2016).
 *
 * <p>Steps: (1) correlation distance, (2) single-linkage clustering,
 * (3) quasi-diagonalization, (4) recursive bisection allocation.
 */
public class HierarchicalRiskParityPortfolio implements PortfolioModel {

    @Override
    public List<BigDecimal> allocate(MatrixR064 returns, MatrixR064 mu) {
        int rows = (int) returns.countRows();
        int cols = (int) returns.countColumns();
        if (cols == 0) return List.of();

        MatrixR064 corr = MatrixUtils.correlationMatrix(returns);
        double[][] dist = correlationDistance(corr);
        int[] order = clusterAndOrder(dist, cols);
        double[] vols = computeVols(returns, order);
        double[] w = recursiveBisection(vols);

        List<BigDecimal> weights = new ArrayList<>(cols);
        for (int i = 0; i < cols; i++) {
            weights.add(BigDecimal.valueOf(w[i]));
        }
        return weights;
    }

    @Override
    public String name() {
        return "Hierarchical Risk Parity";
    }

    private double[][] correlationDistance(MatrixR064 corr) {
        int n = (int) corr.countRows();
        double[][] dist = new double[n][n];
        for (int i = 0; i < n; i++) {
            dist[i][i] = 0;
            for (int j = i + 1; j < n; j++) {
                double c = Math.max(-1.0, Math.min(1.0, corr.get(i, j)));
                double d = Math.sqrt(0.5 * (1.0 - c));
                dist[i][j] = d;
                dist[j][i] = d;
            }
        }
        return dist;
    }

    private int[] clusterAndOrder(double[][] dist, int n) {
        List<List<Integer>> clusters = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            clusters.add(new ArrayList<>(List.of(i)));
        }

        double[][] work = new double[n][n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(dist[i], 0, work[i], 0, n);
        }

        while (clusters.size() > 1) {
            double minDist = Double.MAX_VALUE;
            int minI = -1, minJ = -1;
            for (int i = 0; i < clusters.size(); i++) {
                for (int j = i + 1; j < clusters.size(); j++) {
                    double d = singleLinkage(work, clusters.get(i), clusters.get(j));
                    if (d < minDist) {
                        minDist = d;
                        minI = i;
                        minJ = j;
                    }
                }
            }
            List<Integer> merged = new ArrayList<>(clusters.get(minI));
            merged.addAll(clusters.get(minJ));
            clusters.remove(minJ);
            clusters.remove(minI);
            clusters.add(merged);
        }

        int[] order = new int[n];
        int idx = 0;
        for (int asset : clusters.get(0)) {
            order[idx++] = asset;
        }
        return order;
    }

    private double singleLinkage(double[][] dist, List<Integer> a, List<Integer> b) {
        double minD = Double.MAX_VALUE;
        for (int i : a) {
            for (int j : b) {
                minD = Math.min(minD, dist[i][j]);
            }
        }
        return minD;
    }

    private double[] computeVols(MatrixR064 returns, int[] order) {
        int rows = (int) returns.countRows();
        int n = order.length;
        double[] vols = new double[n];
        for (int i = 0; i < n; i++) {
            int col = order[i];
            double sum = 0, sumSq = 0;
            for (int r = 0; r < rows; r++) {
                double v = returns.get(r, col);
                sum += v;
                sumSq += v * v;
            }
            double mean = sum / rows;
            double var = Math.max(sumSq / rows - mean * mean, 1e-10);
            vols[i] = Math.sqrt(var);
        }
        return vols;
    }

    private double[] recursiveBisection(double[] vols) {
        int n = vols.length;
        double[] weights = new double[n];
        int[] all = new int[n];
        for (int i = 0; i < n; i++) all[i] = i;
        bisect(vols, weights, all, 1.0);
        return weights;
    }

    private void bisect(double[] vols, double[] weights, int[] indices, double capital) {
        if (indices.length == 1) {
            weights[indices[0]] = capital;
            return;
        }

        int mid = indices.length / 2;
        int[] left = new int[mid];
        int[] right = new int[indices.length - mid];
        System.arraycopy(indices, 0, left, 0, mid);
        System.arraycopy(indices, mid, right, 0, indices.length - mid);

        double invVolL = 1.0 / clusterVariance(vols, left);
        double invVolR = 1.0 / clusterVariance(vols, right);
        double total = invVolL + invVolR;

        double wL = invVolL / total;
        double wR = invVolR / total;

        bisect(vols, weights, left, capital * wL);
        bisect(vols, weights, right, capital * wR);
    }

    private double clusterVariance(double[] vols, int[] indices) {
        double sumInvVar = 0;
        for (int i : indices) {
            sumInvVar += 1.0 / (vols[i] * vols[i]);
        }
        return 1.0 / sumInvVar;
    }
}
