package org.example.util;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class FileExporter {

    private static final String OUTPUT_FILE = "modelOutput.txt";
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void exportWeights(List<String> coins, List<BigDecimal> weights, String strategyName) {
        try (FileWriter w = new FileWriter(OUTPUT_FILE, true)) {
            w.write("\n=== [%s] Weights  [%s] ===\n"
                    .formatted(strategyName, LocalDateTime.now().format(TS)));
            for (int i = 0; i < coins.size(); i++) {
                BigDecimal pct = weights.get(i)
                        .multiply(new BigDecimal("100"))
                        .setScale(2, RoundingMode.HALF_UP);
                String side = weights.get(i).compareTo(BigDecimal.ZERO) >= 0 ? "LONG " : "SHORT";
                w.write("  %-22s %s  %+7.2f%%\n"
                        .formatted(coins.get(i).toUpperCase(), side, pct.doubleValue()));
            }
        } catch (Exception e) {
            throw new RuntimeException("Export weights failed", e);
        }
    }

    public void exportBacktest(double finalEquity, double maxDrawdown,
                               double sharpe, double sortino, double calmar,
                               double var95, double cvar95, String strategyName) {
        try (FileWriter w = new FileWriter(OUTPUT_FILE, true)) {
            w.write("  Backtest -> Equity=%.4f  MaxDD=%.2f%%  Sharpe=%.4f  Sortino=%.4f  Calmar=%.4f  VaR95=%.2f%%  CVaR95=%.2f%%\n"
                    .formatted(finalEquity, maxDrawdown * 100, sharpe, sortino, calmar, var95 * 100, cvar95 * 100));
        } catch (Exception e) {
            throw new RuntimeException("Export backtest failed", e);
        }
    }

    /**
     * Export all results to a CSV file for external analysis.
     */
    public void exportCsv(Map<String, List<BigDecimal>> allWeights,
                          Map<String, org.example.model.BacktestResult> allResults,
                          List<String> coins) {
        String csvFile = "portfolio_results_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";
        try (FileWriter w = new FileWriter(csvFile)) {
            // Header
            w.write("Strategy,Asset,Weight,Side,FinalEquity,MaxDrawdown,Sharpe,Sortino,Calmar,VaR95,CVaR95\n");

            for (var entry : allResults.entrySet()) {
                String name = entry.getKey();
                org.example.model.BacktestResult r = entry.getValue();
                List<BigDecimal> weights = allWeights.get(name);

                if (weights != null) {
                    for (int i = 0; i < coins.size(); i++) {
                        double wt = weights.get(i).doubleValue();
                        String side = wt >= 0 ? "LONG" : "SHORT";
                        w.write("\"%s\",\"%s\",%.6f,%s,%.6f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f\n"
                                .formatted(name, coins.get(i), wt, side,
                                        r.finalEquity(), r.maxDrawdown(),
                                        r.sharpe(), r.sortino(), r.calmar(),
                                        r.var95(), r.cvar95()));
                    }
                } else {
                    // No weights available, just write strategy summary
                    w.write("\"%s\",,, ,%.6f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f\n"
                            .formatted(name,
                                    r.finalEquity(), r.maxDrawdown(),
                                    r.sharpe(), r.sortino(), r.calmar(),
                                    r.var95(), r.cvar95()));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("CSV export failed", e);
        }
    }
}
