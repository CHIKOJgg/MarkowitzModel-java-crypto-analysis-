package org.example.alpha;

import org.ojalgo.matrix.MatrixR064;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Blends multiple {@link AlphaModel}s with explicit weights.
 *
 * <pre>
 *   μ_blend = Σ  w_i · μ_i
 * </pre>
 *
 * <p>Weights are normalised so they sum to 1.
 *
 * <p>Example:
 * <pre>
 *   new BlendedAlpha(
 *       List.of(new EWMAAlpha(0.1), new MomentumAlpha(20)),
 *       List.of(0.5, 0.5)
 *   )
 * </pre>
 */
public class BlendedAlpha implements AlphaModel {

    private final List<AlphaModel> models;
    private final double[]         weights;  // normalised

    public BlendedAlpha(List<AlphaModel> models, List<Double> rawWeights) {
        if (models.size() != rawWeights.size())
            throw new IllegalArgumentException("models and weights must have the same size");

        this.models  = List.copyOf(models);
        double total = rawWeights.stream().mapToDouble(Double::doubleValue).sum();
        this.weights = rawWeights.stream().mapToDouble(w -> w / total).toArray();
    }

    /** Equal-weight convenience factory. */
    public static BlendedAlpha equalWeight(AlphaModel... models) {
        List<Double> equal = Arrays.stream(models).map(m -> 1.0).collect(Collectors.toList());
        return new BlendedAlpha(Arrays.asList(models), equal);
    }

    // ── AlphaModel ────────────────────────────────────────────────────────────

    @Override
    public MatrixR064 predict(MatrixR064 returns) {
        int cols = (int) returns.countColumns();
        double[][] blend = new double[1][cols];

        for (int m = 0; m < models.size(); m++) {
            MatrixR064 mu = models.get(m).predict(returns);
            double w = weights[m];
            for (int j = 0; j < cols; j++) {
                blend[0][j] += w * mu.get(0, j);
            }
        }
        return MatrixR064.FACTORY.rows(blend);
    }

    @Override
    public String name() {
        StringBuilder sb = new StringBuilder("Blend(");
        for (int i = 0; i < models.size(); i++) {
            if (i > 0) sb.append(" + ");
            sb.append(String.format("%.0f%%·%s", weights[i] * 100, models.get(i).name()));
        }
        return sb.append(")").toString();
    }
}
