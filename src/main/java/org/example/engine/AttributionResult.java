package org.example.engine;

/**
 * Immutable Brinson-style performance attribution result.
 *
 * @param totalReturn        portfolio total return
 * @param benchmarkReturn    benchmark total return
 * @param excessReturn       totalReturn − benchmarkReturn
 * @param allocationEffect  asset allocation contribution
 * @param selectionEffect   security selection contribution
 * @param interactionEffect interaction (cross) contribution
 * @param assetContributions per-asset attribution (same order as portfolio)
 */
public record AttributionResult(
        double totalReturn,
        double benchmarkReturn,
        double excessReturn,
        double allocationEffect,
        double selectionEffect,
        double interactionEffect,
        double[] assetContributions
) {
    /**
     * One-line human-readable summary.
     */
    public String summary() {
        return String.format(
                "Attribution  Tot=%.2f%%  Bench=%.2f%%  Excess=%.2f%%  Alloc=%.2f%%  Select=%.2f%%  Interact=%.2f%%",
                totalReturn * 100, benchmarkReturn * 100, excessReturn * 100,
                allocationEffect * 100, selectionEffect * 100, interactionEffect * 100);
    }
}
