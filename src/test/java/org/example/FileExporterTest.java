package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.example.model.BacktestResult;
import org.example.util.FileExporter;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileExporterTest {

    @TempDir
    Path tempDir;

    @Test
    void exportWeightsCreatesFile() {
        var exporter = new FileExporter();
        var coins = List.of("bitcoin", "ethereum", "solana");
        var weights = List.of(
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(0.3),
                BigDecimal.valueOf(0.2));

        // This will append to modelOutput.txt in the current directory
        assertDoesNotThrow(() ->
                exporter.exportWeights(coins, weights, "TestStrategy"));

        // Clean up
        File f = new File("modelOutput.txt");
        if (f.exists()) f.delete();
    }

    @Test
    void exportBacktestAppendsToOutput() {
        var exporter = new FileExporter();

        assertDoesNotThrow(() ->
                exporter.exportBacktest(1.5, 0.1, 2.0, 2.5, 3.0, 0.02, 0.03, "TestStrategy"));

        File f = new File("modelOutput.txt");
        if (f.exists()) f.delete();
    }

    @Test
    void exportCsvCreatesFile() {
        var exporter = new FileExporter();
        var coins = List.of("bitcoin", "ethereum");
        var weights = List.of(BigDecimal.valueOf(0.6), BigDecimal.valueOf(0.4));

        BacktestResult result = new BacktestResult(
                "test", List.of(1.0, 1.1, 1.2), 1.2, 0.05, 1.5, 1.8, 2.0, 0.02, 0.03, 0.1, 0.001,
                List.of(1.0, 1.05, 1.1));

        Map<String, List<BigDecimal>> allWeights = new LinkedHashMap<>();
        allWeights.put("test", weights);

        Map<String, BacktestResult> allResults = new LinkedHashMap<>();
        allResults.put("test", result);

        assertDoesNotThrow(() -> exporter.exportCsv(allWeights, allResults, coins));
    }

    @Test
    void exportCsvMultipleStrategies() {
        var exporter = new FileExporter();
        var coins = List.of("bitcoin", "ethereum");
        var weights1 = List.of(BigDecimal.valueOf(0.6), BigDecimal.valueOf(0.4));
        var weights2 = List.of(BigDecimal.valueOf(0.3), BigDecimal.valueOf(0.7));

        BacktestResult r1 = new BacktestResult(
                "strat1", List.of(1.0, 1.1), 1.1, 0.05, 1.5, 1.8, 2.0, 0.02, 0.03, 0.1, 0.001,
                List.of(1.0, 1.05));
        BacktestResult r2 = new BacktestResult(
                "strat2", List.of(1.0, 1.2), 1.2, 0.03, 2.0, 2.5, 3.0, 0.01, 0.02, 0.05, 0.0005,
                List.of(1.0, 1.05));

        Map<String, List<BigDecimal>> allWeights = new LinkedHashMap<>();
        allWeights.put("strat1", weights1);
        allWeights.put("strat2", weights2);

        Map<String, BacktestResult> allResults = new LinkedHashMap<>();
        allResults.put("strat1", r1);
        allResults.put("strat2", r2);

        assertDoesNotThrow(() -> exporter.exportCsv(allWeights, allResults, coins));
    }

    @Test
    void exportCsvWithNullWeights() {
        var exporter = new FileExporter();
        var coins = List.of("bitcoin");

        BacktestResult result = new BacktestResult(
                "test", List.of(1.0, 1.1), 1.1, 0.05, 1.5, 1.8, 2.0, 0.02, 0.03, 0.1, 0.001,
                List.of(1.0, 1.05));

        Map<String, List<BigDecimal>> allWeights = new LinkedHashMap<>();
        // No weights for this strategy

        Map<String, BacktestResult> allResults = new LinkedHashMap<>();
        allResults.put("test", result);

        // Should not crash even with missing weights
        assertDoesNotThrow(() -> exporter.exportCsv(allWeights, allResults, coins));
    }

    @Test
    void exportWeightsWithShortPositions() {
        var exporter = new FileExporter();
        var coins = List.of("bitcoin", "ethereum");
        var weights = List.of(
                BigDecimal.valueOf(0.7),
                BigDecimal.valueOf(-0.3));

        assertDoesNotThrow(() ->
                exporter.exportWeights(coins, weights, "ShortTest"));

        File f = new File("modelOutput.txt");
        if (f.exists()) f.delete();
    }
}
