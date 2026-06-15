package org.example.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.example.Defaults;
import org.example.data.CoinGeckoProvider;
import org.example.engine.*;
import org.example.execution.SimpleExecution;
import org.example.execution.ZeroCostExecution;
import org.example.forecast.ForecastEngine;
import org.example.forecast.ForecastResult;
import org.example.model.BacktestResult;
import org.example.model.CoinData;
import org.example.util.Config;
import org.example.util.FileExporter;
import org.example.util.MatrixUtils;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.net.URL;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MainController implements Initializable {

    // ── Coin list ─────────────────────────────────────────────────────────────
    @FXML private VBox coinsPane;

    // ── Model parameters ──────────────────────────────────────────────────────
    @FXML private ComboBox<org.example.data.Timeframe> timeframeCombo;
    @FXML private Slider  targetReturnSlider;
    @FXML private Label   targetReturnValue;
    @FXML private Slider  maxLongSlider;
    @FXML private Label   maxLongValue;
    @FXML private Slider  maxShortSlider;
    @FXML private Label   maxShortValue;
    @FXML private CheckBox allowShortingCheck;
    @FXML private Slider  alphaSlider;
    @FXML private Label   alphaValue;
    @FXML private Slider  shrinkageSlider;
    @FXML private Label   shrinkageValue;
    @FXML private CheckBox ewmaCovCheck;
    @FXML private Slider  ewmaLambdaSlider;
    @FXML private Label   ewmaLambdaValue;
    @FXML private Slider  riskFreeRateSlider;
    @FXML private Label   riskFreeRateValue;
    @FXML private Slider  leverageSlider;
    @FXML private Label   leverageValue;
    @FXML private Spinner<Integer> momentumLookback;
    @FXML private CheckBox volScalingCheck;
    @FXML private Slider   targetVolSlider;
    @FXML private Label    targetVolValue;

    // ── Strategy selection ────────────────────────────────────────────────────
    @FXML private VBox strategyPane;

    // ── Backtest parameters ───────────────────────────────────────────────────
    @FXML private Spinner<Integer> windowSpinner;
    @FXML private Spinner<Integer> horizonSpinner;
    @FXML private Slider  feeRateSlider;
    @FXML private Label   feeRateValue;
    @FXML private Slider  turnoverSlider;
    @FXML private Label   turnoverValue;
    @FXML private CheckBox zeroCostCheck;

    // ── Buttons ───────────────────────────────────────────────────────────────
    @FXML private Button fetchBtn;
    @FXML private Button runBtn;
    @FXML private Button exportBtn;
    @FXML private PasswordField apiKeyField;

    // ── Portfolio tab ─────────────────────────────────────────────────────────
    @FXML private ComboBox<String>     strategySelector;
    @FXML private PieChart             weightsChart;
    @FXML private GridPane             statsGrid;
    @FXML private TableView<WeightRow> weightsTable;

    // ── Backtest tab ──────────────────────────────────────────────────────────
    @FXML private LineChart<Number, Number> equityChart;
    @FXML private TableView<CompareRow>     compareTable;

    // ── Forecast tab ──────────────────────────────────────────────────────────
    @FXML private LineChart<Number, Number> forecastChart;
    @FXML private TableView<ForecastRow>    forecastTable;

    // ── Correlation tab ───────────────────────────────────────────────────────
    @FXML private GridPane heatmapGrid;

    // ── Log / status ──────────────────────────────────────────────────────────
    @FXML private TextArea    logArea;
    @FXML private ProgressBar progressBar;
    @FXML private Label       statusLabel;

    // ── State ─────────────────────────────────────────────────────────────────

    private static final List<String> ALL_COINS = List.of(
            "bitcoin","ethereum","solana","hyperliquid",
            "the-open-network","mantle","monero","zcash",
            "ripple","cardano","avalanche-2","polkadot",
            "chainlink","uniswap","aave","near");

    private static final Set<String> DEFAULT_COINS = Set.of(
            "bitcoin","ethereum","solana","hyperliquid",
            "the-open-network","mantle","monero","zcash");

    private static final String[] CURVE_COLORS = {
            "#e94560","#00d4ff","#7cfc00","#ffa500",
            "#ff69b4","#bf7fff","#ffff00","#00ced1" };

    private final Map<String, CheckBox> coinCheckboxes     = new LinkedHashMap<>();
    private final Map<String, CheckBox> strategyCheckboxes = new LinkedHashMap<>();

    private final CoinGeckoProvider dataProvider = new CoinGeckoProvider();

    private List<String>              selectedCoins;
    private MatrixR064                returnMatrix;

    private final Map<String, BacktestResult>   lastResults = new LinkedHashMap<>();
    private final Map<String, List<BigDecimal>> lastWeights = new LinkedHashMap<>();

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        buildCoinCheckboxes();
        buildStrategyCheckboxes();
        bindSliders();
        buildWeightsTable();
        buildCompareTable();
        buildForecastTable();
        loadApiKey();
        runBtn.setDisable(true);

        // Timeframe selector
        timeframeCombo.setItems(FXCollections.observableArrayList(
                org.example.data.Timeframe.values()));
        timeframeCombo.setValue(org.example.data.Timeframe.DAILY);

        strategySelector.setItems(FXCollections.observableArrayList(StrategyRegistry.allNames()));
        strategySelector.getSelectionModel().selectedItemProperty()
                .addListener((o, p, n) -> renderPortfolioTab(n));
    }

    private void buildCoinCheckboxes() {
        for (String c : ALL_COINS) {
            CheckBox cb = new CheckBox(pretty(c));
            cb.setSelected(DEFAULT_COINS.contains(c));
            coinCheckboxes.put(c, cb);
            coinsPane.getChildren().add(cb);
        }
    }

    private void buildStrategyCheckboxes() {
        for (String n : StrategyRegistry.allNames()) {
            CheckBox cb = new CheckBox(n);
            cb.setSelected(true);
            strategyCheckboxes.put(n, cb);
            strategyPane.getChildren().add(cb);
        }
    }

    private void bindSliders() {
        bind(targetReturnSlider, targetReturnValue, "%.2f%%");
        bind(maxLongSlider,      maxLongValue,      "%.0f%%");
        bind(maxShortSlider,     maxShortValue,     "-%.0f%%");
        bind(alphaSlider,        alphaValue,        "%.2f");
        bind(shrinkageSlider,    shrinkageValue,    "%.2f");
        bind(ewmaLambdaSlider,   ewmaLambdaValue,   "%.2f");
        bind(riskFreeRateSlider, riskFreeRateValue, "%.1f%%");
        bind(leverageSlider,     leverageValue,     "%.2f");
        bind(feeRateSlider,      feeRateValue,      "%.2f%%");
        bind(targetVolSlider,    targetVolValue,    "%.1f%%");
        turnoverSlider.valueProperty().addListener((o, p, n) -> {
            double v = n.doubleValue();
            turnoverValue.setText(v < 0.001 ? "Disabled" : "%.0f%%".formatted(v * 100));
        });
        volScalingCheck.selectedProperty().addListener((o, p, n) -> {
            targetVolSlider.setDisable(!n);
            targetVolValue.setDisable(!n);
        });
        ewmaCovCheck.selectedProperty().addListener((o, p, n) -> {
            ewmaLambdaSlider.setDisable(!n);
            ewmaLambdaValue.setDisable(!n);
        });
    }

    private static void bind(Slider s, Label l, String fmt) {
        l.setText(fmt.formatted(s.getValue()));
        s.valueProperty().addListener((o, p, n) -> l.setText(fmt.formatted(n.doubleValue())));
    }

    @SuppressWarnings("unchecked")
    private void buildWeightsTable() {
        addCol(weightsTable, "Coin",  "coin",      150);
        addCol(weightsTable, "Weight","weight",     90);
        addCol(weightsTable, "Side",  "direction",  70);
    }

    @SuppressWarnings("unchecked")
    private void buildCompareTable() {
        addCol(compareTable, "Strategy",    "strategyId",   220);
        addCol(compareTable, "Final Eq.",   "finalEquity",   90);
        addCol(compareTable, "Max DD",      "maxDrawdown",   90);
        addCol(compareTable, "Sharpe",      "sharpe",        80);
        addCol(compareTable, "Sortino",     "sortino",       80);
        addCol(compareTable, "Calmar",      "calmar",        80);
        addCol(compareTable, "VaR 95%",     "var95",         80);
        addCol(compareTable, "CVaR 95%",    "cvar95",        80);
        addCol(compareTable, "Avg TO",      "avgTurnover",   80);
        addCol(compareTable, "Total Fees",  "totalFees",     90);
    }

    @SuppressWarnings("unchecked")
    private void buildForecastTable() {
        addCol(forecastTable, "Asset",     "assetName",    120);
        addCol(forecastTable, "Ann. Ret",  "annReturn",     80);
        addCol(forecastTable, "Ann. Vol",  "annVol",        80);
        addCol(forecastTable, "Trend",     "trend",         80);
        addCol(forecastTable, "1d",        "day1",          70);
        addCol(forecastTable, "3d",        "day3",          70);
        addCol(forecastTable, "7d",        "day7",          70);
        addCol(forecastTable, "95% CI",    "ci95",         100);
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    private static <T> void addCol(TableView<T> tv, String title, String prop, double w) {
        TableColumn col = new TableColumn<>(title);
        col.setCellValueFactory(new PropertyValueFactory<>(prop));
        col.setPrefWidth(w);
        tv.getColumns().add(col);
    }

    private void loadApiKey() {
        String k = Config.get("api.key");
        if (k != null && !k.equals("YOUR_API_KEY_HERE")) apiKeyField.setText(k);
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    @FXML
    private void onFetchData() {
        selectedCoins = selectedCoins();
        if (selectedCoins.size() < 2) { warn("Select at least 2 coins."); return; }

        org.example.data.Timeframe tf = timeframeCombo.getValue();
        int factor = tf.resampleFactor();

        Task<MatrixR064> task = new Task<>() {
            @Override protected MatrixR064 call() {
                updateMessage("Fetching market data...");
                List<CoinData> coins = dataProvider.fetchAll(selectedCoins);
                updateMessage("Building return matrix (" + tf.label() + ")...");
                MatrixR064 dailyMatrix = buildMatrix(coins, selectedCoins);
                if (factor > 1) {
                    updateMessage("Resampling to " + tf.label() + "...");
                    return org.example.util.MatrixUtils.resample(dailyMatrix, factor);
                }
                return dailyMatrix;
            }
        };
        wireTask(task, "Data ready (" + tf.label() + ").", matrix -> {
            returnMatrix = matrix;
            log("Matrix ready: %d periods x %d assets (%s)"
                    .formatted(matrix.countRows(), matrix.countColumns(), tf.label()));
            runBtn.setDisable(false);
        });
        setBusy("Fetching…");
        new Thread(task, "fetch").start();
    }

    @FXML
    private void onRunStrategies() {
        if (returnMatrix == null) { warn("Fetch data first."); return; }

        List<String> chosen = strategyCheckboxes.entrySet().stream()
                .filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey)
                .collect(Collectors.toList());
        if (chosen.isEmpty()) { warn("Select at least one strategy."); return; }

        StrategyRegistry.Params params = readParams();
        Map<String, Strategy> allStrats = StrategyRegistry.buildAll(params);

        int    win      = windowSpinner.getValue();
        int    hor      = horizonSpinner.getValue();
        double fee      = feeRateSlider.getValue() / 100.0;
        double rfRate   = riskFreeRateSlider.getValue() / 100.0;
        double maxTO    = turnoverSlider.getValue();
        var    exec     = zeroCostCheck.isSelected()
                          ? new ZeroCostExecution()
                          : new SimpleExecution(fee, Defaults.SLIPPAGE);

        BacktestEngine engine  = new BacktestEngine(win, hor, exec, rfRate, maxTO);
        MatrixR064     returns = returnMatrix;

        Task<Map<String, BacktestResult>> task = new Task<>() {
            @Override
            protected Map<String, BacktestResult> call() {
                Map<String, BacktestResult> results = new LinkedHashMap<>();
                for (String name : chosen) {
                    Strategy s = allStrats.get(name);
                    updateMessage("Backtesting: " + name + "…");
                    BacktestResult r = engine.run(returns, s,
                            (sid, msg) -> updateMessage(msg));
                    results.put(name, r);

                    // Single-step weights on the last training window
                    int end   = (int) returns.countRows();
                    int start = Math.max(0, end - win);
                    // BUG FIX: returns.rows(start, end) uses ojAlgo varargs (selects
                    // two specific rows), not a range. Use MatrixUtils.sliceRows instead.
                    lastWeights.put(name, s.build(MatrixUtils.sliceRows(returns, start, end)));
                    log(r.summary());
                }
                return results;
            }
        };
        wireTask(task, "Done.", results -> {
            lastResults.clear();
            lastResults.putAll(results);
            renderEquityChart(results);
            renderCompareTable(results);
            List<String> names = new ArrayList<>(results.keySet());
            strategySelector.setItems(FXCollections.observableArrayList(names));
            if (!names.isEmpty()) {
                strategySelector.getSelectionModel().select(0);
                renderPortfolioTab(names.get(0));
            }
            // Run forecast on fetched data
            runForecast();
        });
        setBusy("Running strategies…");
        new Thread(task, "strat").start();
    }

    @FXML
    private void onExport() {
        if (lastResults.isEmpty()) { warn("Run strategies first."); return; }
        FileExporter ex = new FileExporter();
        lastResults.forEach((name, r) -> {
            List<BigDecimal> w = lastWeights.get(name);
            if (w != null) ex.exportWeights(selectedCoins, w, name);
            ex.exportBacktest(r.finalEquity(), r.maxDrawdown(), r.sharpe(),
                    r.sortino(), r.calmar(), r.var95(), r.cvar95(), name);
        });
        // Also export CSV for external analysis
        ex.exportCsv(lastWeights, lastResults, selectedCoins);
        log("Exported " + lastResults.size() + " strategies → modelOutput.txt + CSV");
    }

    @FXML
    private void onSaveApiKey() {
        String k = apiKeyField.getText().strip();
        if (k.isBlank()) { warn("Empty key."); return; }
        Config.set("api.key", k);
        try { Config.save(); } catch (Exception ignored) {}
        dataProvider.clearCache();
        log("API key saved, cache cleared.");
    }

    @FXML private void onSelectAllCoins()  { coinCheckboxes.values().forEach(c -> c.setSelected(true));  }
    @FXML private void onSelectNoneCoins() { coinCheckboxes.values().forEach(c -> c.setSelected(false)); }
    @FXML private void onSelectAllStrat()  { strategyCheckboxes.values().forEach(c -> c.setSelected(true));  }
    @FXML private void onSelectNoneStrat() { strategyCheckboxes.values().forEach(c -> c.setSelected(false)); }

    // ── Render ────────────────────────────────────────────────────────────────

    private void renderPortfolioTab(String name) {
        if (name == null) return;
        List<BigDecimal> w = lastWeights.get(name);
        BacktestResult   r = lastResults.get(name);
        if (w == null) return;

        ObservableList<PieChart.Data> pie  = FXCollections.observableArrayList();
        ObservableList<WeightRow>     rows = FXCollections.observableArrayList();

        for (int i = 0; i < selectedCoins.size(); i++) {
            double v = w.get(i).doubleValue();
            String label = pretty(selectedCoins.get(i));
            if (Math.abs(v) > 0.005)
                pie.add(new PieChart.Data(label + " " + pct(v), Math.abs(v * 100)));
            rows.add(new WeightRow(label, pct(v), v >= 0 ? "LONG" : "SHORT"));
        }
        weightsChart.setData(pie);
        weightsTable.setItems(rows);

        statsGrid.getChildren().clear();
        if (r != null) {
            stat("Final Equity",  "%.4f".formatted(r.finalEquity()),           0);
            stat("Max Drawdown",  "%.2f%%".formatted(r.maxDrawdown() * 100),   1);
            stat("Sharpe (ann.)", "%.4f".formatted(r.sharpe()),                2);
            stat("Sortino (ann.)","%.4f".formatted(r.sortino()),               3);
            stat("Calmar Ratio",  "%.4f".formatted(r.calmar()),               4);
            stat("VaR 95%",       "%.2f%%".formatted(r.var95() * 100),        5);
            stat("CVaR 95%",      "%.2f%%".formatted(r.cvar95() * 100),       6);
            stat("Avg Turnover",  "%.2f%%".formatted(r.avgTurnover() * 100),   7);
            stat("Fee Drag",      "%.5f".formatted(r.totalFees()),             8);
        }

        // Risk contribution: top 3 risk contributors
        if (w != null && returnMatrix != null) {
            double[] rc = riskContributions(w, returnMatrix);
            int[] topIdx = topIndices(rc, Math.min(3, rc.length));
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < topIdx.length; k++) {
                if (k > 0) sb.append(", ");
                sb.append(pretty(selectedCoins.get(topIdx[k])))
                  .append(" ").append("%.1f%%".formatted(rc[topIdx[k]] * 100));
            }
            stat("Top Risk Contributors", sb.toString(), 9);
        }
    }

    private void renderEquityChart(Map<String, BacktestResult> results) {
        equityChart.getData().clear();
        equityChart.setAnimated(false);

        // Add benchmark curve first (dashed)
        BacktestResult firstResult = results.values().stream().findFirst().orElse(null);
        if (firstResult != null && firstResult.benchmarkCurve() != null) {
            XYChart.Series<Number, Number> bench = new XYChart.Series<>();
            bench.setName("Benchmark (1/N B&H)");
            List<Double> curve = firstResult.benchmarkCurve();
            for (int i = 0; i < curve.size(); i++)
                bench.getData().add(new XYChart.Data<>(i, curve.get(i)));
            equityChart.getData().add(bench);
            Platform.runLater(() -> {
                try {
                    equityChart.applyCss(); equityChart.layout();
                    equityChart.getData().get(0).getNode()
                            .lookup(".chart-series-line")
                            .setStyle("-fx-stroke:#888888;-fx-stroke-width:1.5;-fx-stroke-dash-array:6 4;");
                } catch (Exception ignored) {}
            });
        }

        int ci = 0;
        for (Map.Entry<String, BacktestResult> e : results.entrySet()) {
            XYChart.Series<Number, Number> s = new XYChart.Series<>();
            s.setName(e.getKey());
            List<Double> curve = e.getValue().equityCurve();
            for (int i = 0; i < curve.size(); i++)
                s.getData().add(new XYChart.Data<>(i, curve.get(i)));
            equityChart.getData().add(s);
            String color = CURVE_COLORS[ci % CURVE_COLORS.length];
            final int idx = equityChart.getData().size() - 1;
            Platform.runLater(() -> {
                try {
                    equityChart.applyCss(); equityChart.layout();
                    equityChart.getData().get(idx).getNode()
                            .lookup(".chart-series-line")
                            .setStyle("-fx-stroke:" + color + ";-fx-stroke-width:2;");
                } catch (Exception ignored) {}
            });
            ci++;
        }
    }

    private void renderCompareTable(Map<String, BacktestResult> results) {
        ObservableList<CompareRow> rows = FXCollections.observableArrayList();
        results.values().stream()
                .sorted(Comparator.comparingDouble(BacktestResult::sharpe).reversed())
                .forEach(r -> rows.add(new CompareRow(r)));
        compareTable.setItems(rows);
    }

    // ── Forecast ─────────────────────────────────────────────────────────────

    private void runForecast() {
        if (returnMatrix == null || selectedCoins == null) return;

        int horizon = horizonSpinner.getValue();
        ForecastEngine engine = new ForecastEngine();
        List<ForecastResult> forecasts = engine.forecast(returnMatrix, horizon, selectedCoins);

        // Render forecast chart
        forecastChart.getData().clear();
        forecastChart.setAnimated(false);
        int ci = 0;
        for (ForecastResult fr : forecasts) {
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName(fr.assetName());
            List<Double> point = fr.pointForecast();
            for (int h = 0; h < point.size(); h++)
                series.getData().add(new XYChart.Data<>(h + 1, point.get(h)));
            forecastChart.getData().add(series);
            String color = CURVE_COLORS[ci % CURVE_COLORS.length];
            final int idx = forecastChart.getData().size() - 1;
            Platform.runLater(() -> {
                try {
                    forecastChart.applyCss(); forecastChart.layout();
                    forecastChart.getData().get(idx).getNode()
                            .lookup(".chart-series-line")
                            .setStyle("-fx-stroke:" + color + ";-fx-stroke-width:2;");
                } catch (Exception ignored) {}
            });
            ci++;
        }

        // Render forecast table
        ObservableList<ForecastRow> rows = FXCollections.observableArrayList();
        for (ForecastResult fr : forecasts) {
            List<Double> point = fr.pointForecast();
            List<Double> u95   = fr.upper95();
            List<Double> l95   = fr.lower95();
            String d1 = point.size() > 0 ? "%+.3f%%".formatted(point.get(0) * 100) : "—";
            String d3 = point.size() > 2 ? "%+.3f%%".formatted(point.get(2) * 100) : "—";
            String d7 = point.size() > 6 ? "%+.3f%%".formatted(point.get(6) * 100) : "—";
            String ciStr = (u95.size() > 6 && l95.size() > 6)
                    ? "[%.3f%%, %.3f%%]".formatted(l95.get(6) * 100, u95.get(6) * 100)
                    : "—";
            rows.add(new ForecastRow(fr.assetName(),
                    "%.2f%%".formatted(fr.annualizedReturn() * 100),
                    "%.2f%%".formatted(fr.annualizedVol() * 100),
                    "%.4f".formatted(fr.trendStrength()),
                    d1, d3, d7, ciStr));
        }
        forecastTable.setItems(rows);
        log("Forecast generated for " + forecasts.size() + " assets.");

        // Render correlation heatmap
        renderHeatmap();
    }

    private void renderHeatmap() {
        if (returnMatrix == null || selectedCoins == null) return;

        MatrixR064 corr = MatrixUtils.correlationMatrix(returnMatrix);
        int n = (int) corr.countColumns();

        heatmapGrid.getChildren().clear();
        heatmapGrid.getColumnConstraints().clear();
        heatmapGrid.getRowConstraints().clear();

        // Add empty top-left corner
        heatmapGrid.add(new Label(""), 0, 0);

        // Column headers
        for (int j = 0; j < n; j++) {
            Label h = new Label(shortName(selectedCoins.get(j)));
            h.setStyle("-fx-font-size:9; -fx-text-fill:#ccc; -fx-padding:2 4;");
            h.setRotate(-45);
            h.setMinWidth(50);
            heatmapGrid.add(h, j + 1, 0);
        }

        // Rows
        for (int i = 0; i < n; i++) {
            Label rowLabel = new Label(shortName(selectedCoins.get(i)));
            rowLabel.setStyle("-fx-font-size:9; -fx-text-fill:#ccc; -fx-padding:0 4;");
            rowLabel.setMinWidth(50);
            heatmapGrid.add(rowLabel, 0, i + 1);

            for (int j = 0; j < n; j++) {
                double v = corr.get(i, j);
                Label cell = new Label("%.2f".formatted(v));
                cell.setMinSize(42, 24);
                cell.setMaxSize(42, 24);
                cell.setAlignment(javafx.geometry.Pos.CENTER);
                cell.setStyle("-fx-font-size:9; -fx-background-radius:3; "
                        + "-fx-background-color:" + corrColor(v) + "; "
                        + "-fx-text-fill:" + (Math.abs(v) > 0.6 ? "#fff" : "#222") + ";");
                heatmapGrid.add(cell, j + 1, i + 1);
            }
        }
    }

    private static String corrColor(double v) {
        // Blue (negative) → White (zero) → Red (positive)
        if (v >= 0) {
            int g = (int) (255 * (1 - v));
            return String.format("#%02x%02x%02x", 255, g, g);
        } else {
            int g = (int) (255 * (1 + v));
            return String.format("#%02x%02x%02x", g, g, 255);
        }
    }

    private static String shortName(String id) {
        String[] parts = id.split("-");
        return parts[0].substring(0, Math.min(4, parts[0].length())).toUpperCase();
    }

    private void stat(String label, String value, int row) {
        Label l = new Label(label + ":"); l.getStyleClass().add("stat-label");
        Label v = new Label(value);       v.getStyleClass().add("stat-value");
        statsGrid.add(l, 0, row);
        statsGrid.add(v, 1, row);
    }

    // ── Task helpers ──────────────────────────────────────────────────────────

    private <T> void wireTask(Task<T> task, String ok, Consumer<T> onOk) {
        task.messageProperty().addListener((o, p, m) ->
                Platform.runLater(() -> statusLabel.setText(m)));
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            onOk.accept(task.getValue());
            setIdle(ok);
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            Throwable ex = task.getException();
            String err = ex != null ? ex.getMessage() : "Unknown error";
            if (err == null) err = ex != null ? ex.getClass().getSimpleName() : "Unknown error";
            setIdle("Error: " + err);
            log("ERROR: " + err);
            warn(err);
        }));
    }

    private void setBusy(String msg) {
        fetchBtn.setDisable(true); runBtn.setDisable(true);
        progressBar.setVisible(true); progressBar.setProgress(-1);
        statusLabel.setText(msg);
    }

    private void setIdle(String msg) {
        fetchBtn.setDisable(false);
        runBtn.setDisable(returnMatrix == null);
        progressBar.setVisible(false);
        statusLabel.setText(msg);
    }

    private void log(String m) { Platform.runLater(() -> logArea.appendText("[LOG] " + m + "\n")); }

    private void warn(String m) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle("Warning"); a.setHeaderText(null); a.setContentText(m); a.showAndWait();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private List<String> selectedCoins() {
        return coinCheckboxes.entrySet().stream()
                .filter(e -> e.getValue().isSelected())
                .map(Map.Entry::getKey).collect(Collectors.toList());
    }

    private StrategyRegistry.Params readParams() {
        return new StrategyRegistry.Params(
                maxLongSlider.getValue()   / 100.0,
                -maxShortSlider.getValue() / 100.0,
                alphaSlider.getValue(),
                shrinkageSlider.getValue(),
                leverageSlider.getValue(),
                allowShortingCheck.isSelected(),
                targetReturnSlider.getValue() / 100.0,
                momentumLookback.getValue(),
                volScalingCheck.isSelected(),
                targetVolSlider.getValue() / 100.0,
                ewmaCovCheck.isSelected(),
                ewmaLambdaSlider.getValue()
        );
    }

    private static MatrixR064 buildMatrix(List<CoinData> coins, List<String> order) {
        Map<String, CoinData> byName = new HashMap<>();
        coins.forEach(c -> byName.put(c.getCoinName(), c));
        int minLen = order.stream()
                .mapToInt(id -> byName.get(id).getReturnMap().get(id).size())
                .min().orElseThrow();
        MatrixR064.DenseReceiver b = MatrixR064.FACTORY.makeDense(minLen, order.size());
        for (int col = 0; col < order.size(); col++) {
            List<Double> rets = byName.get(order.get(col)).getReturnMap().get(order.get(col));
            for (int row = 0; row < minLen; row++) b.set(row, col, rets.get(row));
        }
        return b.get();
    }

    private static String pretty(String id) {
        return Arrays.stream(id.split("-"))
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }

    private static String pct(double v) { return "%+.2f%%".formatted(v * 100); }

    /**
     * Compute marginal risk contribution for each asset.
     * RC_i = w_i * (Σw)_i / σ_p
     */
    private double[] riskContributions(List<BigDecimal> weights, MatrixR064 returns) {
        int n = weights.size();
        int T = (int) returns.countRows();

        // Compute covariance Σ
        double[][] cov = new double[n][n];
        double[] mean = new double[n];
        for (int j = 0; j < n; j++) {
            double sum = 0;
            for (int t = 0; t < T; t++) sum += returns.get(t, j);
            mean[j] = sum / T;
        }
        for (int i = 0; i < n; i++) {
            for (int j = i; j < n; j++) {
                double s = 0;
                for (int t = 0; t < T; t++) {
                    s += (returns.get(t, i) - mean[i]) * (returns.get(t, j) - mean[j]);
                }
                cov[i][j] = s / (T - 1);
                cov[j][i] = cov[i][j];
            }
        }

        // Σw
        double[] sigmaW = new double[n];
        for (int i = 0; i < n; i++) {
            double s = 0;
            for (int j = 0; j < n; j++) s += cov[i][j] * weights.get(j).doubleValue();
            sigmaW[i] = s;
        }

        // σ_p = sqrt(w' Σ w)
        double portVar = 0;
        for (int i = 0; i < n; i++) portVar += weights.get(i).doubleValue() * sigmaW[i];
        double portVol = portVar > 0 ? Math.sqrt(portVar) : 1e-10;

        // RC_i = w_i * (Σw)_i / σ_p
        double[] rc = new double[n];
        for (int i = 0; i < n; i++) {
            rc[i] = weights.get(i).doubleValue() * sigmaW[i] / portVol;
        }
        return rc;
    }

    private static int[] topIndices(double[] arr, int k) {
        Integer[] idx = new Integer[arr.length];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (a, b) -> Double.compare(Math.abs(arr[b]), Math.abs(arr[a])));
        int[] result = new int[Math.min(k, idx.length)];
        for (int i = 0; i < result.length; i++) result[i] = idx[i];
        return result;
    }

    // ── Row model classes ─────────────────────────────────────────────────────

    public static final class WeightRow {
        private final String coin, weight, direction;
        public WeightRow(String c, String w, String d) { coin=c; weight=w; direction=d; }
        public String getCoin()      { return coin;      }
        public String getWeight()    { return weight;    }
        public String getDirection() { return direction; }
    }

    public static final class CompareRow {
        private final String strategyId, finalEquity, maxDrawdown, sharpe, sortino, calmar, var95, cvar95, avgTurnover, totalFees;
        public CompareRow(BacktestResult r) {
            strategyId  = r.strategyId();
            finalEquity = "%.4f".formatted(r.finalEquity());
            maxDrawdown = "%.2f%%".formatted(r.maxDrawdown() * 100);
            sharpe      = "%.3f".formatted(r.sharpe());
            sortino     = "%.3f".formatted(r.sortino());
            calmar      = "%.3f".formatted(r.calmar());
            var95       = "%.2f%%".formatted(r.var95() * 100);
            cvar95      = "%.2f%%".formatted(r.cvar95() * 100);
            avgTurnover = "%.2f%%".formatted(r.avgTurnover() * 100);
            totalFees   = "%.5f".formatted(r.totalFees());
        }
        public String getStrategyId()  { return strategyId;  }
        public String getFinalEquity() { return finalEquity; }
        public String getMaxDrawdown() { return maxDrawdown; }
        public String getSharpe()      { return sharpe;      }
        public String getSortino()     { return sortino;     }
        public String getCalmar()      { return calmar;      }
        public String getVar95()       { return var95;       }
        public String getCvar95()      { return cvar95;      }
        public String getAvgTurnover() { return avgTurnover; }
        public String getTotalFees()   { return totalFees;   }
    }

    public static final class ForecastRow {
        private final String assetName, annReturn, annVol, trend, day1, day3, day7, ci95;
        public ForecastRow(String name, String annRet, String annVol, String trend,
                           String d1, String d3, String d7, String ci) {
            this.assetName = name; this.annReturn = annRet; this.annVol = annVol;
            this.trend = trend; this.day1 = d1; this.day3 = d3; this.day7 = d7; this.ci95 = ci;
        }
        public String getAssetName() { return assetName; }
        public String getAnnReturn() { return annReturn; }
        public String getAnnVol()    { return annVol;    }
        public String getTrend()     { return trend;     }
        public String getDay1()      { return day1;      }
        public String getDay3()      { return day3;      }
        public String getDay7()      { return day7;      }
        public String getCi95()      { return ci95;      }
    }
}
