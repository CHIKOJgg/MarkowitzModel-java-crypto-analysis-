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
import org.example.model.BacktestResult;
import org.example.model.CoinData;
import org.example.model.PortfolioResult;
import org.example.service.BacktestService;
import org.example.service.DataService;
import org.example.service.PortfolioProcessor;
import org.example.service.PortfolioService;
import org.example.util.Config;
import org.example.util.FileExporter;
import org.ojalgo.matrix.MatrixR064;

import java.math.BigDecimal;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * FXML controller for MainView.fxml.
 *
 * <p>All heavy work (data fetching, optimisation, backtesting) runs on
 * background threads via {@link Task} so the UI stays responsive.
 */
public class MainController implements Initializable {

    // ── Coin list ─────────────────────────────────────────────────────────────
    @FXML private VBox coinsPane;

    // ── Model parameters ──────────────────────────────────────────────────────
    @FXML private RadioButton minRiskRadio;
    @FXML private RadioButton optimalRadio;
    @FXML private Label       targetReturnLabel;
    @FXML private Slider      targetReturnSlider;
    @FXML private Label       targetReturnValue;

    @FXML private Slider maxLongSlider;
    @FXML private Label  maxLongValue;
    @FXML private Slider maxShortSlider;
    @FXML private Label  maxShortValue;
    @FXML private CheckBox allowShortingCheck;

    @FXML private Slider alphaSlider;
    @FXML private Label  alphaValue;
    @FXML private Slider shrinkageSlider;
    @FXML private Label  shrinkageValue;
    @FXML private Slider leverageSlider;
    @FXML private Label  leverageValue;

    // ── Backtest parameters ───────────────────────────────────────────────────
    @FXML private Spinner<Integer> windowSpinner;
    @FXML private Spinner<Integer> horizonSpinner;
    @FXML private Slider           feeRateSlider;
    @FXML private Label            feeRateValue;

    // ── Action buttons ────────────────────────────────────────────────────────
    @FXML private Button runBtn;
    @FXML private Button backtestBtn;
    @FXML private Button exportBtn;

    // ── API settings ──────────────────────────────────────────────────────────
    @FXML private PasswordField apiKeyField;

    // ── Portfolio tab ─────────────────────────────────────────────────────────
    @FXML private PieChart                   weightsChart;
    @FXML private GridPane                   statsGrid;
    @FXML private TableView<WeightRow>       weightsTable;

    // ── Backtest tab ──────────────────────────────────────────────────────────
    @FXML private LineChart<Number, Number> equityChart;
    @FXML private Label finalEquityLabel;
    @FXML private Label maxDdLabel;
    @FXML private Label sharpeLabel;

    // ── Log tab ───────────────────────────────────────────────────────────────
    @FXML private TextArea logArea;

    // ── Status bar ────────────────────────────────────────────────────────────
    @FXML private ProgressBar progressBar;
    @FXML private Label       statusLabel;

    // ── App state ─────────────────────────────────────────────────────────────

    private static final List<String> ALL_COINS = List.of(
            "bitcoin", "ethereum", "solana", "hyperliquid",
            "the-open-network", "mantle", "monero", "zcash",
            "ripple", "cardano", "avalanche-2", "polkadot",
            "chainlink", "uniswap", "aave", "near","tether"
    );

    private static final Set<String> DEFAULT_COINS = Set.of(
            "bitcoin", "ethereum", "solana", "hyperliquid",
            "the-open-network", "mantle", "monero", "zcash","tether"
    );

    private final Map<String, CheckBox> coinCheckboxes = new LinkedHashMap<>();

    private List<CoinData>   lastCoinData;
    private List<String>     selectedCoins;
    private PortfolioResult  lastPortfolio;
    private BacktestResult   lastBacktest;

    // ── Initialisation ────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        buildCoinCheckboxes();
        bindSliderLabels();
        wireModelToggle();
        buildWeightsTable();
        loadApiKeyFromConfig();
    }

    private void buildCoinCheckboxes() {
        for (String coin : ALL_COINS) {
            CheckBox cb = new CheckBox(prettyName(coin));
            cb.setSelected(DEFAULT_COINS.contains(coin));
            coinCheckboxes.put(coin, cb);
            coinsPane.getChildren().add(cb);
        }
    }

    private void bindSliderLabels() {
        targetReturnSlider.valueProperty().addListener((o, p, n) ->
                targetReturnValue.setText(String.format("%.2f%%", n.doubleValue())));
        maxLongSlider.valueProperty().addListener((o, p, n) ->
                maxLongValue.setText(String.format("%.0f%%", n.doubleValue())));
        maxShortSlider.valueProperty().addListener((o, p, n) ->
                maxShortValue.setText(String.format("-%.0f%%", n.doubleValue())));
        alphaSlider.valueProperty().addListener((o, p, n) ->
                alphaValue.setText(String.format("%.2f", n.doubleValue())));
        shrinkageSlider.valueProperty().addListener((o, p, n) ->
                shrinkageValue.setText(String.format("%.2f", n.doubleValue())));
        leverageSlider.valueProperty().addListener((o, p, n) ->
                leverageValue.setText(String.format("%.2f", n.doubleValue())));
        feeRateSlider.valueProperty().addListener((o, p, n) ->
                feeRateValue.setText(String.format("%.2f%%", n.doubleValue())));
    }

    private void wireModelToggle() {
        optimalRadio.selectedProperty().addListener((obs, old, val) -> {
            targetReturnSlider.setDisable(!val);
            targetReturnLabel.setDisable(!val);
            targetReturnValue.setDisable(!val);
        });
    }

    @SuppressWarnings("unchecked")
    private void buildWeightsTable() {
        TableColumn<WeightRow, String> coinCol = new TableColumn<>("Coin");
        coinCol.setCellValueFactory(new PropertyValueFactory<>("coin"));
        coinCol.setPrefWidth(140);

        TableColumn<WeightRow, String> wCol = new TableColumn<>("Weight");
        wCol.setCellValueFactory(new PropertyValueFactory<>("weight"));
        wCol.setPrefWidth(80);

        TableColumn<WeightRow, String> dirCol = new TableColumn<>("Side");
        dirCol.setCellValueFactory(new PropertyValueFactory<>("direction"));
        dirCol.setPrefWidth(70);

        weightsTable.getColumns().addAll(coinCol, wCol, dirCol);
    }

    private void loadApiKeyFromConfig() {
        String key = Config.get("api.key");
        if (key != null && !key.equals("YOUR_API_KEY_HERE")) {
            apiKeyField.setText(key);
        }
    }

    // ── Button handlers ───────────────────────────────────────────────────────

    @FXML
    private void onRunOptimization() {
        selectedCoins = coinCheckboxes.entrySet().stream()
                .filter(e -> e.getValue().isSelected())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (selectedCoins.size() < 2) {
            warn("Please select at least 2 coins.");
            return;
        }


        boolean  shorting    = allowShortingCheck.isSelected();
        double   maxLong     = maxLongSlider.getValue()    / 100.0;
        double   maxShort    = -maxShortSlider.getValue()  / 100.0;
        double   alpha       = alphaSlider.getValue();
        double   shrinkage   = shrinkageSlider.getValue();
        double   leverage    = leverageSlider.getValue();
        double   targetRet   = targetReturnSlider.getValue() / 100.0;
        boolean  isMinRisk   = minRiskRadio.isSelected();

        Task<PortfolioResult> task = new Task<>() {
            @Override
            protected PortfolioResult call() throws Exception {
                updateMessage("Fetching market data…");
                lastCoinData = new DataService().fetchAll(selectedCoins);

                updateMessage("Building return matrix…");
                MatrixR064 returns = new PortfolioProcessor().buildReturnMatrix(lastCoinData);

                updateMessage("Running Markowitz optimisation…");
                PortfolioService ps = new PortfolioService(
                        maxLong, maxShort, alpha, shrinkage, leverage, shorting);

                return isMinRisk ? ps.buildMinRisk(returns)
                                 : ps.buildOptimal(returns, targetRet);
            }
        };

        task.messageProperty().addListener((o, p, msg) ->
                Platform.runLater(() -> statusLabel.setText(msg)));

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            lastPortfolio = task.getValue();
            renderPortfolio(lastPortfolio);
            setIdle("Optimisation complete.");
            log("Optimisation done – Sharpe %.4f | Leverage %.2f"
                    .formatted(lastPortfolio.sharpe(), lastPortfolio.leverage()));
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            setIdle("Optimisation failed.");
            log("ERROR: " + task.getException().getMessage());
            warn("Optimisation failed:\n" + task.getException().getMessage());
        }));

        setBusy("Optimising…");
        new Thread(task, "opt-thread").start();
    }

    @FXML
    private void onRunBacktest() {
        if (lastCoinData == null) {
            warn("Run optimisation first to load coin data.");
            return;
        }

        boolean  shorting   = allowShortingCheck.isSelected();
        double   maxLong    = maxLongSlider.getValue()    / 100.0;
        double   maxShort   = -maxShortSlider.getValue()  / 100.0;
        double   alpha      = alphaSlider.getValue();
        double   shrinkage  = shrinkageSlider.getValue();
        double   leverage   = leverageSlider.getValue();
        double   targetRet  = targetReturnSlider.getValue() / 100.0;
        double   fee        = feeRateSlider.getValue()      / 100.0;
        int      window     = windowSpinner.getValue();
        int      horizon    = horizonSpinner.getValue();

        Task<BacktestResult> task = new Task<>() {
            @Override
            protected BacktestResult call() throws Exception {
                updateMessage("Building return matrix…");
                MatrixR064 returns = new PortfolioProcessor().buildReturnMatrix(lastCoinData);

                PortfolioService ps = new PortfolioService(
                        maxLong, maxShort, alpha, shrinkage, leverage, shorting);

                BacktestService bs = new BacktestService(window, horizon, fee, ps);

                return bs.run(returns, targetRet, msg -> {
                    // progress callback from backtest loop
                    updateMessage(msg);
                });
            }
        };

        task.messageProperty().addListener((o, p, msg) ->
                Platform.runLater(() -> statusLabel.setText(msg)));

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            lastBacktest = task.getValue();
            renderBacktest(lastBacktest);
            setIdle("Backtest complete.");
            log("Backtest done – Final equity %.4f | MaxDD %.2f%% | Sharpe %.4f"
                    .formatted(lastBacktest.finalEquity(),
                               lastBacktest.maxDrawdown() * 100,
                               lastBacktest.sharpe()));
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            setIdle("Backtest failed.");
            log("ERROR: " + task.getException().getMessage());
            warn("Backtest failed:\n" + task.getException().getMessage());
        }));

        setBusy("Running backtest…");
        new Thread(task, "backtest-thread").start();
    }

    @FXML
    private void onExport() {
        if (lastPortfolio == null) { warn("No portfolio to export."); return; }
        try {
            FileExporter ex = new FileExporter();
            ex.exportWeights(selectedCoins, lastPortfolio.weights());
            if (lastBacktest != null) {
                ex.exportBacktest(lastBacktest.finalEquity(),
                                  lastBacktest.maxDrawdown(),
                                  lastBacktest.sharpe());
            }
            log("Results written to modelOutput.txt");
        } catch (Exception e) {
            warn("Export failed: " + e.getMessage());
        }
    }

    @FXML
    private void onSaveApiKey() {
        String key = apiKeyField.getText().strip();
        if (key.isBlank()) { warn("API key is empty."); return; }
        Config.set("api.key", key);
        try { Config.save(); } catch (Exception ignored) {}
        log("API key saved.");
    }

    @FXML
    private void onSelectAll()  { coinCheckboxes.values().forEach(cb -> cb.setSelected(true));  }
    @FXML
    private void onSelectNone() { coinCheckboxes.values().forEach(cb -> cb.setSelected(false)); }

    // ── Render helpers ────────────────────────────────────────────────────────

    private void renderPortfolio(PortfolioResult r) {
        ObservableList<PieChart.Data>  pie   = FXCollections.observableArrayList();
        ObservableList<WeightRow>      table = FXCollections.observableArrayList();

        for (int i = 0; i < selectedCoins.size(); i++) {
            double w    = r.weights().get(i).doubleValue();
            String name = prettyName(selectedCoins.get(i));

            if (Math.abs(w) > 0.005) {
                pie.add(new PieChart.Data(name + " " + "%.1f%%".formatted(w * 100), Math.abs(w * 100)));
            }
            table.add(new WeightRow(name, "%.2f%%".formatted(w * 100), w >= 0 ? "LONG" : "SHORT"));
        }

        weightsChart.setData(pie);
        weightsTable.setItems(table);

        statsGrid.getChildren().clear();
        addStat("Expected Return", "%.4f%%".formatted(r.expectedReturn() * 100), 0);
        addStat("Volatility",      "%.4f%%".formatted(r.volatility()     * 100), 1);
        addStat("Sharpe (ann.)",   "%.4f" .formatted(r.sharpe()),               2);
        addStat("Leverage",        "%.2f" .formatted(r.leverage()),             3);
    }

    private void renderBacktest(BacktestResult r) {
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Equity");

        List<Double> curve = r.equityCurve();
        for (int i = 0; i < curve.size(); i++) {
            series.getData().add(new XYChart.Data<>(i, curve.get(i)));
        }

        equityChart.getData().clear();
        equityChart.getData().add(series);
        equityChart.setAnimated(false);

        finalEquityLabel.setText("Final Equity: %.4f".formatted(r.finalEquity()));
        maxDdLabel.setText("Max Drawdown: %.2f%%".formatted(r.maxDrawdown() * 100));
        sharpeLabel.setText("Sharpe: %.4f".formatted(r.sharpe()));
    }

    private void addStat(String label, String value, int row) {
        Label l = new Label(label + ":");
        l.getStyleClass().add("stat-label");
        Label v = new Label(value);
        v.getStyleClass().add("stat-value");
        statsGrid.add(l, 0, row);
        statsGrid.add(v, 1, row);
    }

    // ── Status helpers ────────────────────────────────────────────────────────

    private void setBusy(String msg) {
        runBtn.setDisable(true);
        backtestBtn.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(-1);
        statusLabel.setText(msg);
    }

    private void setIdle(String msg) {
        runBtn.setDisable(false);
        backtestBtn.setDisable(false);
        progressBar.setVisible(false);
        statusLabel.setText(msg);
    }

    private void log(String msg) {
        Platform.runLater(() -> logArea.appendText("[LOG] " + msg + "\n"));
    }

    private void warn(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle("Warning");
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private static String prettyName(String coinId) {
        return Arrays.stream(coinId.split("-"))
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }

    // ── Inner model class for TableView ───────────────────────────────────────

    public static final class WeightRow {
        private final String coin;
        private final String weight;
        private final String direction;

        public WeightRow(String coin, String weight, String direction) {
            this.coin      = coin;
            this.weight    = weight;
            this.direction = direction;
        }

        public String getCoin()      { return coin;      }
        public String getWeight()    { return weight;    }
        public String getDirection() { return direction; }
    }
}
