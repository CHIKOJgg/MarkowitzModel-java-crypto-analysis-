package org.example.engine;

import java.math.BigDecimal;
import java.util.List;

/**
 * Simplified Brinson-style performance attribution engine.
 *
 * <p>Decomposes total excess return into three effects:
 * <ul>
 *   <li><b>Allocation</b> — over/under-weighting assets relative to the benchmark</li>
 *   <li><b>Selection</b> — picking better/worse performing assets within the benchmark</li>
 *   <li><b>Interaction</b> — cross-term from simultaneous over-weighting and out-performance</li>
 * </ul>
 *
 * <p>Since we don't have sector groupings, each asset is treated as its own "sector."
 */
public class PerformanceAttribution {

    /**
     * Compute Brinson attribution for a portfolio versus a benchmark.
     *
     * @param weights          portfolio weights (one per asset)
     * @param returns          per-asset returns for the attribution period
     * @param benchmarkWeights benchmark (e.g. equal-weight) weights
     * @return attribution decomposition
     */
    public AttributionResult attribute(List<BigDecimal> weights,
                                       List<Double> returns,
                                       List<Double> benchmarkWeights) {
        int n = weights.size();

        double portfolioReturn  = 0.0;
        double benchmarkReturn  = 0.0;
        double allocationEffect = 0.0;
        double selectionEffect  = 0.0;
        double interactionEffect = 0.0;
        double[] assetContributions = new double[n];

        for (int i = 0; i < n; i++) {
            double wp = weights.get(i).doubleValue();
            double wb = benchmarkWeights.get(i);
            double rp = returns.get(i);
            double rb = benchmarkReturn; // running benchmark contribution

            portfolioReturn += wp * rp;
            benchmarkReturn += wb * returns.get(i);
        }

        // Recompute with full benchmark return for the formulas
        benchmarkReturn = 0.0;
        for (int i = 0; i < n; i++) {
            benchmarkReturn += benchmarkWeights.get(i) * returns.get(i);
        }

        for (int i = 0; i < n; i++) {
            double wp = weights.get(i).doubleValue();
            double wb = benchmarkWeights.get(i);
            double rp = returns.get(i);
            double rb = returns.get(i); // benchmark "sector" return = asset return itself

            double alloc  = (wp - wb) * (rb - benchmarkReturn);
            double select = wb * (rp - rb);
            double interact = (wp - wb) * (rp - rb);

            allocationEffect  += alloc;
            selectionEffect   += select;
            interactionEffect += interact;

            assetContributions[i] = alloc + select + interact;
        }

        double excessReturn = portfolioReturn - benchmarkReturn;

        return new AttributionResult(portfolioReturn, benchmarkReturn, excessReturn,
                allocationEffect, selectionEffect, interactionEffect,
                assetContributions);
    }
}
