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
import org.example.data.CoinGeckoProvider;
import org.example.engine.*;
import org.example.execution.SimpleExecution;
import org.example.execution.ZeroCostExecution;
import org.example.model.BacktestResult;
import org.example.model.CoinData;
import org.example.util.Config;
import org.example.util.FileExporter;
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
    private List<CoinData>            lastCoinData;
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
        loadApiKey();
        runBtn.setDisable(true);

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
        bind(leverageSlider,     leverageValue,     "%.2f");
        bind(feeRateSlider,      feeRateValue,      "%.2f%%");
        bind(targetVolSlider,    targetVolValue,    "%.1f%%");
        volScalingCheck.selectedProperty().addListener((o, p, n) -> {
            targetVolSlider.setDisable(!n);
            targetVolValue.setDisable(!n);
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
        addCol(compareTable, "Avg TO",      "avgTurnover",   80);
        addCol(compareTable, "Total Fees",  "totalFees",     90);
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

        Task<MatrixR064> task = new Task<>() {
            @Override protected MatrixR064 call() {
                updateMessage("Fetching market data…");
                lastCoinData = dataProvider.fetchAll(selectedCoins);
                updateMessage("Building return matrix…");
                return buildMatrix(lastCoinData, selectedCoins);
            }
        };
        wireTask(task, "Data ready.", matrix -> {
            returnMatrix = matrix;
            log("Matrix ready: %d days × %d assets"
                    .formatted(matrix.countRows(), matrix.countColumns()));
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
        var    exec     = zeroCostCheck.isSelected()
                          ? new ZeroCostExecution()
                          : new SimpleExecution(fee, 0.0005);

        BacktestEngine engine  = new BacktestEngine(win, hor, exec);
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
                    lastWeights.put(name, s.build(returns.rows(start, end)));
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
            ex.exportBacktest(r.finalEquity(), r.maxDrawdown(), r.sharpe(), name);
        });
        log("Exported " + lastResults.size() + " strategies → modelOutput.txt");
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
            stat("Avg Turnover",  "%.2f%%".formatted(r.avgTurnover() * 100),   3);
            stat("Fee Drag",      "%.5f".formatted(r.totalFees()),             4);
        }
    }

    private void renderEquityChart(Map<String, BacktestResult> results) {
        equityChart.getData().clear();
        equityChart.setAnimated(false);
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
            String err = task.getException().getMessage();
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
                targetVolSlider.getValue() / 100.0
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

    // ── Row model classes ─────────────────────────────────────────────────────

    public static final class WeightRow {
        private final String coin, weight, direction;
        public WeightRow(String c, String w, String d) { coin=c; weight=w; direction=d; }
        public String getCoin()      { return coin;      }
        public String getWeight()    { return weight;    }
        public String getDirection() { return direction; }
    }

    public static final class CompareRow {
        private final String strategyId, finalEquity, maxDrawdown, sharpe, avgTurnover, totalFees;
        public CompareRow(BacktestResult r) {
            strategyId  = r.strategyId();
            finalEquity = "%.4f".formatted(r.finalEquity());
            maxDrawdown = "%.2f%%".formatted(r.maxDrawdown() * 100);
            sharpe      = "%.3f".formatted(r.sharpe());
            avgTurnover = "%.2f%%".formatted(r.avgTurnover() * 100);
            totalFees   = "%.5f".formatted(r.totalFees());
        }
        public String getStrategyId()  { return strategyId;  }
        public String getFinalEquity() { return finalEquity; }
        public String getMaxDrawdown() { return maxDrawdown; }
        public String getSharpe()      { return sharpe;      }
        public String getAvgTurnover() { return avgTurnover; }
        public String getTotalFees()   { return totalFees;   }
    }
}
