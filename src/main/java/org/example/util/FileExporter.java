package org.example.util;

import java.io.FileWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
                               double sharpe, String strategyName) {
        try (FileWriter w = new FileWriter(OUTPUT_FILE, true)) {
            w.write("  Backtest → Equity=%.4f  MaxDD=%.2f%%  Sharpe=%.4f\n"
                    .formatted(finalEquity, maxDrawdown * 100, sharpe));
        } catch (Exception e) {
            throw new RuntimeException("Export backtest failed", e);
        }
    }
}
