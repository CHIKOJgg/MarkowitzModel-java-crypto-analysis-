package org.example.util;

import org.example.engine.SensitivityResult;
import org.example.model.BacktestResult;

import java.io.FileWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Generates a self-contained HTML report with all backtest results,
 * weight allocations, and sensitivity analysis.
 */
public class HtmlReportExporter {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

    public void export(String filePath,
                       Map<String, BacktestResult> results,
                       Map<String, List<BigDecimal>> weights,
                       List<String> coins,
                       SensitivityResult sensitivity) {
        String ts = LocalDateTime.now().format(TS);
        if (filePath == null) filePath = "report_" + ts + ".html";

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">")
            .append("<title>Portfolio Optimization Report</title><style>")
            .append(css())
            .append("</style></head><body>")
            .append("<div class=\"container\">")
            .append("<h1>Portfolio Optimization Report</h1>")
            .append("<p class=\"subtitle\">Generated ").append(LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("</p>");

        // Strategy comparison table
        html.append("<h2>Strategy Comparison</h2>")
            .append("<table><tr>")
            .append("<th>Strategy</th><th>Final Eq</th><th>Max DD</th>")
            .append("<th>Sharpe</th><th>Sortino</th><th>Calmar</th>")
            .append("<th>VaR95</th><th>CVaR95</th><th>Avg TO</th><th>Fees</th><th>Regimes</th>")
            .append("</tr>");

        var sorted = results.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue().sharpe(), a.getValue().sharpe()))
                .toList();

        String bestId = sorted.isEmpty() ? "" : sorted.get(0).getKey();

        for (var entry : sorted) {
            BacktestResult r = entry.getValue();
            boolean isBest = entry.getKey().equals(bestId);
            html.append("<tr").append(isBest ? " class=\"best\"" : "").append(">")
                .append("<td>").append(esc(entry.getKey())).append(isBest ? " ⭐" : "").append("</td>")
                .append(td(r.finalEquity())).append(tdPct(r.maxDrawdown()))
                .append(td(r.sharpe())).append(td(r.sortino())).append(td(r.calmar()))
                .append(tdPct(r.var95())).append(tdPct(r.cvar95()))
                .append(tdPct(r.avgTurnover())).append(td(r.totalFees()))
                .append("<td>").append(regimeBreakdown(r)).append("</td>")
                .append("</tr>");
        }
        html.append("</table>");

        // Per-strategy weight allocations
        html.append("<h2>Weight Allocations</h2>");
        for (var entry : results.entrySet()) {
            String name = entry.getKey();
            html.append("<h3>").append(esc(name)).append("</h3><table><tr>");
            for (String c : coins) html.append("<th>").append(esc(c)).append("</th>");
            html.append("</tr><tr>");
            List<BigDecimal> w = weights.get(name);
            if (w != null) {
                for (BigDecimal v : w) {
                    String cls = v.doubleValue() >= 0 ? "pos" : "neg";
                    html.append("<td class=\"").append(cls).append("\">")
                        .append(pct(v.doubleValue())).append("</td>");
                }
            }
            html.append("</tr></table>");
        }

        // Sensitivity analysis
        if (sensitivity != null) {
            html.append("<h2>Parameter Sensitivity: ").append(esc(sensitivity.paramName())).append("</h2>")
                .append("<table><tr><th>Value</th><th>Sharpe</th><th>Max DD</th><th>Final Eq</th></tr>");
            for (int i = 0; i < sensitivity.paramValues().length; i++) {
                html.append("<tr>")
                    .append(td(sensitivity.paramValues()[i]))
                    .append(td(sensitivity.sharpeRatios()[i]))
                    .append(tdPct(sensitivity.maxDrawdowns()[i]))
                    .append(td(sensitivity.finalEquities()[i]))
                    .append("</tr>");
            }
            html.append("</table>");
        }

        html.append("</div></body></html>");

        try (FileWriter fw = new FileWriter(filePath)) {
            fw.write(html.toString());
        } catch (Exception e) {
            throw new RuntimeException("Failed to write HTML report: " + filePath, e);
        }
    }

    private static String css() {
        return """
            body { font-family: 'Segoe UI', sans-serif; background: #f5f6fa; margin: 0; padding: 20px; color: #2c3e50; }
            .container { max-width: 1200px; margin: auto; background: #fff; padding: 30px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
            h1 { color: #2c3e50; margin: 0; }
            .subtitle { color: #7f8c8d; font-size: 14px; margin-top: 4px; }
            h2 { color: #2c3e50; border-bottom: 2px solid #3498db; padding-bottom: 6px; margin-top: 30px; }
            h3 { color: #34495e; margin: 16px 0 8px; }
            table { width: 100%; border-collapse: collapse; margin: 10px 0 20px; font-size: 13px; }
            th { background: #3498db; color: #fff; padding: 10px 8px; text-align: left; }
            td { padding: 8px; border-bottom: 1px solid #ecf0f1; }
            tr:hover td { background: #f8f9fa; }
            tr.best td { background: #e8f8f5; font-weight: bold; }
            .pos { color: #27ae60; }
            .neg { color: #e74c3c; }
            """;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String td(double v) {
        return "<td>" + String.format("%.4f", v) + "</td>";
    }

    private static String tdPct(double v) {
        return "<td>" + String.format("%.2f%%", v * 100) + "</td>";
    }

    private static String tdRatio(double v) {
        return "<td>" + String.format("%.4f", v) + "</td>";
    }

    private static String regimeBreakdown(BacktestResult r) {
        long high = r.regimeCount("HIGH_CORR");
        long norm = r.regimeCount("NORMAL");
        long low  = r.regimeCount("LOW_CORR");
        return "H:" + high + " N:" + norm + " L:" + low;
    }

    private static String pct(double v) {
        return String.format("%+.2f%%", v * 100);
    }
}
