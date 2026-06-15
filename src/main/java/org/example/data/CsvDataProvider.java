package org.example.data;

import org.ojalgo.matrix.MatrixR064;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

/**
 * Reads daily price data from a CSV file and converts to returns.
 *
 * <p>Expected format (header row required):
 * <pre>
 * Date,Asset1,Asset2,Asset3,...
 * 2024-01-01,100.0,50.0,25.0,...
 * 2024-01-02,101.0,51.0,24.5,...
 * </pre>
 *
 * <p>The first column is the date (ignored internally). All remaining
 * columns must be numeric prices.
 */
public class CsvDataProvider implements MarketDataProvider {

    private final String filePath;

    /** Column headers (asset names) parsed from the CSV. */
    private List<String> headers;

    public CsvDataProvider(String filePath) {
        this.filePath = filePath;
        parseHeaders();
    }

    private void parseHeaders() {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String headerLine = br.readLine();
            if (headerLine == null) throw new IllegalArgumentException("Empty CSV file");
            String[] cols = headerLine.split(",");
            headers = new ArrayList<>();
            for (int i = 1; i < cols.length; i++) headers.add(cols[i].strip());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse CSV headers: " + filePath, e);
        }
    }

    @Override
    public MatrixR064 getReturns(List<String> assets) {
        return parseReturns(assets);
    }

    /** Parse the CSV and return [days × n] returns matrix for the given assets. */
    private MatrixR064 parseReturns(List<String> assets) {
        List<Map<String, Double>> priceRows = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String headerLine = br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split(",");
                if (parts.length < 2) continue;
                Map<String, Double> row = new HashMap<>();
                for (int i = 1; i < parts.length && i - 1 < headers.size(); i++) {
                    try {
                        row.put(headers.get(i - 1), Double.parseDouble(parts[i].strip()));
                    } catch (NumberFormatException ignored) {}
                }
                priceRows.add(row);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read CSV: " + filePath, e);
        }

        if (priceRows.size() < 2)
            throw new IllegalArgumentException("Need at least 2 price rows, got " + priceRows.size());

        int nAssets = assets.size();
        int nDays = priceRows.size() - 1;
        double[][] data = new double[nDays][nAssets];

        for (int a = 0; a < nAssets; a++) {
            String asset = assets.get(a);
            for (int d = 0; d < nDays; d++) {
                double pPrev = priceRows.get(d).getOrDefault(asset, Double.NaN);
                double pCurr = priceRows.get(d + 1).getOrDefault(asset, Double.NaN);
                if (Double.isNaN(pPrev) || Double.isNaN(pCurr) || pPrev <= 0) {
                    data[d][a] = 0;
                } else {
                    data[d][a] = (pCurr - pPrev) / pPrev;
                }
            }
        }

        return MatrixR064.FACTORY.rows(data);
    }

    public List<String> getHeaders() {
        return headers;
    }
}
