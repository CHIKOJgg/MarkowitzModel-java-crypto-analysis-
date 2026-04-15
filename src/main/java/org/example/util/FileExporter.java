package org.example.util;

import java.io.FileWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exports portfolio weights (and optionally backtest stats) to a text file.
 */
public class FileExporter {

    private static final String OUTPUT_FILE = "modelOutput.txt";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void exportWeights(List<String> coins, List<BigDecimal> weights) {
        try (FileWriter w = new FileWriter(OUTPUT_FILE, true)) {
            w.write("\n=== Portfolio Weights  [" + LocalDateTime.now().format(TS) + "] ===\n");
            for (int i = 0; i < coins.size(); i++) {
                BigDecimal pct = weights.get(i)
                        .multiply(new BigDecimal("100"))
                        .setScale(2, RoundingMode.HALF_UP);
                String direction = weights.get(i).compareTo(BigDecimal.ZERO) >= 0 ? "LONG " : "SHORT";
                w.write(String.format("  %-22s %s  %+7.2f%%\n",
                        coins.get(i).toUpperCase(), direction, pct.doubleValue()));
            }
        } catch (Exception e) {
            throw new RuntimeException("Export failed: " + e.getMessage(), e);
        }
    }

    public void exportBacktest(double finalEquity, double maxDrawdown, double sharpe) {
        try (FileWriter w = new FileWriter(OUTPUT_FILE, true)) {
            w.write("\n--- Backtest Stats ---\n");
            w.write(String.format("  Final Equity : %.6f\n", finalEquity));
            w.write(String.format("  Max Drawdown : %.2f%%\n", maxDrawdown * 100));
            w.write(String.format("  Sharpe (ann) : %.4f\n", sharpe));
        } catch (Exception e) {
            throw new RuntimeException("Export failed: " + e.getMessage(), e);
        }
    }
}
