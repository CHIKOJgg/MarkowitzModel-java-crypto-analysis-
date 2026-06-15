package org.example.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.example.Defaults;
import org.example.data.CoinGeckoProvider;
import org.example.engine.*;
import org.example.execution.SimpleExecution;
import org.example.engine.StressTestEngine;
import org.example.engine.StressScenario;
import org.example.engine.StressTestResult;
import org.example.engine.MonteCarloSimulator;
import org.example.engine.MonteCarloResult;
import org.example.engine.PerformanceAttribution;
import org.example.engine.AttributionResult;
import org.example.engine.ParameterSensitivity;
import org.example.engine.SensitivityResult;
import org.example.execution.ZeroCostExecution;
import org.example.forecast.ForecastEngine;
import org.example.forecast.ForecastResult;
import org.example.model.AppState;
import org.example.model.BacktestResult;
import org.example.model.CoinData;
import org.example.util.Config;
import org.example.util.FileExporter;
import org.example.util.LocalizationManager;
import org.example.util.MatrixUtils;
import org.ojalgo.matrix.MatrixR064;

import java.io.FileReader;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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
    @FXML private CheckBox portfolioVaRCheck;
    @FXML private Slider   maxVarSlider;
    @FXML private Label    maxVarValue;
    @FXML private Spinner<Integer> rebalanceSpinner;

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

    // ── Localization ──────────────────────────────────────────────────────────
    @FXML private ToggleButton langToggle;
    @FXML private Label titleLabel;
    @FXML private Label pipelineLabel;

    private final LocalizationManager loc = LocalizationManager.getInstance();

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
    @FXML private ComboBox<Integer> forecastHorizonCombo;

    // ── Correlation tab ───────────────────────────────────────────────────────
    @FXML private GridPane heatmapGrid;

    // ── Recommendations tab ──────────────────────────────────────────────────
    @FXML private VBox recommendationCards;
    @FXML private VBox strategySummaryBox;
    @FXML private VBox warningsBox;

    // ── Stress Test tab ─────────────────────────────────────────────────────
    @FXML private ComboBox<String> scenarioCombo;
    @FXML private Button runStressBtn;
    @FXML private VBox stressResultsBox;
    @FXML private TableView<StressRow> stressTable;

    // ── Monte Carlo tab ─────────────────────────────────────────────────────
    @FXML private Spinner<Integer> mcPathsSpinner;
    @FXML private Spinner<Integer> mcHorizonSpinner;
    @FXML private Slider mcDriftSlider;
    @FXML private Slider mcVolSlider;
    @FXML private Button runMcBtn;
    @FXML private LineChart<Number, Number> mcChart;
    @FXML private GridPane mcResultsGrid;

    // ── Attribution tab ─────────────────────────────────────────────────────
    @FXML private GridPane attributionGrid;
    @FXML private TableView<AttributionRow> attributionTable;

    // ── Sensitivity tab ─────────────────────────────────────────────────────
    @FXML private ComboBox<String> sensitivityParamCombo;
    @FXML private Button runSensitivityBtn;
    @FXML private LineChart<Number, Number> sensitivityChart;

    // ── Preset selector ──────────────────────────────────────────────────────
    @FXML private ComboBox<org.example.engine.StrategyPreset> presetCombo;
    @FXML private Label presetDescription;

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

    private static final Path STATE_PATH = Path.of("app-state.json");
    private static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder().setPrettyPrinting().create();

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        buildCoinCheckboxes();
        buildStrategyCheckboxes();
        bindSliders();
        buildWeightsTable();
        buildCompareTable();
        buildForecastTable();
        buildStressTable();
        buildAttributionTable();
        loadApiKey();
        runBtn.setDisable(true);

        // Timeframe selector
        timeframeCombo.setItems(FXCollections.observableArrayList(
                org.example.data.Timeframe.values()));
        timeframeCombo.setValue(org.example.data.Timeframe.DAILY);

        strategySelector.setItems(FXCollections.observableArrayList(StrategyRegistry.allNames()));
        strategySelector.getSelectionModel().selectedItemProperty()
                .addListener((o, p, n) -> renderPortfolioTab(n));

        // Preset selector
        presetCombo.setItems(FXCollections.observableArrayList(
                org.example.engine.StrategyPreset.values()));
        presetCombo.getSelectionModel().selectedItemProperty()
                .addListener((o, old, preset) -> {
                    if (preset != null) {
                        applyPreset(preset);
                        presetDescription.setText(preset.description());
                    }
                });

        scenarioCombo.setItems(FXCollections.observableArrayList(
                tr("scenario.may2022"), tr("scenario.ftxCollapse"),
                tr("scenario.covidCrash"), tr("scenario.lunaCollapse"),
                tr("scenario.oct2025")));
        if (scenarioCombo.getValue() == null) scenarioCombo.setValue(tr("scenario.may2022"));

        sensitivityParamCombo.setItems(FXCollections.observableArrayList(
                tr("sensitivity.shrinkage"), tr("sensitivity.ewmaAlpha"),
                tr("sensitivity.leverage"), tr("sensitivity.maxLong")));
        sensitivityParamCombo.setValue(tr("sensitivity.shrinkage"));

        // Forecast horizon selector (3 / 7 / 14 / 30 / 60 / 90 days)
        forecastHorizonCombo.setItems(FXCollections.observableArrayList(3, 7, 14, 30, 60, 90));
        forecastHorizonCombo.setValue(14);
        forecastHorizonCombo.setCellFactory(lv -> horizonCell());
        forecastHorizonCombo.setButtonCell(horizonCell());
        forecastHorizonCombo.valueProperty().addListener((o, p, n) -> {
            if (n != null && returnMatrix != null) runForecast();
        });

        allowShortingCheck.selectedProperty().addListener((o, p, n) -> {
            maxShortSlider.setDisable(!n);
            maxShortValue.setDisable(!n);
        });
        maxShortSlider.setDisable(!allowShortingCheck.isSelected());
        maxShortValue.setDisable(!allowShortingCheck.isSelected());

        configureForecastChartAxis();

        loadState();
        updateTexts();
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
            turnoverValue.setText(v < 0.001 ? tr("fmt.turnoverDisabled") : "%.0f%%".formatted(v * 100));
        });
        volScalingCheck.selectedProperty().addListener((o, p, n) -> {
            targetVolSlider.setDisable(!n);
            targetVolValue.setDisable(!n);
        });
        ewmaCovCheck.selectedProperty().addListener((o, p, n) -> {
            ewmaLambdaSlider.setDisable(!n);
            ewmaLambdaValue.setDisable(!n);
        });
        portfolioVaRCheck.selectedProperty().addListener((o, p, n) -> {
            maxVarSlider.setDisable(!n);
            maxVarValue.setDisable(!n);
        });
        bind(maxVarSlider, maxVarValue, "%.1f%%");
    }

    private static void bind(Slider s, Label l, String fmt) {
        l.setText(fmt.formatted(s.getValue()));
        s.valueProperty().addListener((o, p, n) -> l.setText(fmt.formatted(n.doubleValue())));
    }

    @SuppressWarnings("unchecked")
    private void buildWeightsTable() {
        addCol(weightsTable, tr("column.coin"),  "coin",      150);
        addCol(weightsTable, tr("column.weight"), "weight",     90);
        addCol(weightsTable, tr("column.side"),   "direction",  70);
    }

    @SuppressWarnings("unchecked")
    private void buildCompareTable() {
        addCol(compareTable, tr("column.strategy"),    "strategyId",     220);
        addCol(compareTable, tr("column.finalEquity"), "finalEquity",     90);
        addCol(compareTable, tr("column.maxDD"),       "maxDrawdown",     90);
        addCol(compareTable, tr("column.sharpe"),      "sharpe",          80);
        addCol(compareTable, tr("column.sortino"),     "sortino",         80);
        addCol(compareTable, tr("column.calmar"),      "calmar",          80);
        addCol(compareTable, tr("column.var95"),       "var95",           80);
        addCol(compareTable, tr("column.cvar95"),      "cvar95",          80);
        addCol(compareTable, tr("column.avgTO"),       "avgTurnover",     80);
        addCol(compareTable, tr("column.totalFees"),   "totalFees",       90);
        addCol(compareTable, tr("column.regimes"),     "regimeBreakdown", 100);
    }

    @SuppressWarnings("unchecked")
    private void buildForecastTable() {
        addCol(forecastTable, tr("column.asset"),       "assetName",     110);
        addCol(forecastTable, tr("column.signal"),      "signal",         80);
        addCol(forecastTable, tr("column.risk"),        "riskLevel",      70);
        addCol(forecastTable, tr("column.annRet"),      "annReturn",      80);
        addCol(forecastTable, tr("column.annVol"),      "annVol",         80);
        addCol(forecastTable, tr("column.forecastSharpe"), "forecastSharpe", 80);
        addCol(forecastTable, tr("column.day1"),        "day1",           70);
        addCol(forecastTable, tr("column.day3"),        "day3",           70);
        addCol(forecastTable, tr("column.day7"),        "day7",           70);
        addCol(forecastTable, tr("column.ci95"),        "ci95",          100);
        addCol(forecastTable, tr("column.probLoss"),    "probLoss",       70);
        addCol(forecastTable, tr("column.maxDDest"),    "maxDrawdownEst", 80);
    }

    private void buildStressTable() {
        addCol(stressTable, tr("column.scenario"),     "scenario",       150);
        addCol(stressTable, tr("column.portfolioRet"), "portfolioReturn",100);
        addCol(stressTable, tr("column.maxDD"),        "maxDD",          90);
        addCol(stressTable, tr("column.worstDay"),     "worstDay",       90);
        addCol(stressTable, tr("column.var95"),        "var95",          90);
        addCol(stressTable, tr("column.cvar95"),       "cvar95",         90);
    }

    @SuppressWarnings("unchecked")
    private void buildAttributionTable() {
        addCol(attributionTable, tr("column.asset"),       "asset",        150);
        addCol(attributionTable, tr("column.weight"),      "weight",       100);
        addCol(attributionTable, tr("column.return"),      "returnVal",    100);
        addCol(attributionTable, tr("column.contribution"),"contribution", 120);
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    private static <T> void addCol(TableView<T> tv, String title, String prop, double w) {
        TableColumn col = new TableColumn<>(title);
        col.setCellValueFactory(new PropertyValueFactory<>(prop));
        col.setPrefWidth(w);
        tv.getColumns().add(col);
    }

    private ListCell<Integer> horizonCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : tr("forecast.days", item));
            }
        };
    }

    private void configureForecastChartAxis() {
        NumberAxis yAxis = (NumberAxis) forecastChart.getYAxis();
        yAxis.setLabel(tr("chart.forecast.yAxis"));
        yAxis.setTickLabelFormatter(new javafx.util.StringConverter<>() {
            @Override public String toString(Number n) {
                return n == null ? "" : String.format("%.1f%%", n.doubleValue());
            }
            @Override public Number fromString(String s) {
                return Double.parseDouble(s.replace("%", "").trim());
            }
        });
        NumberAxis xAxis = (NumberAxis) forecastChart.getXAxis();
        xAxis.setLabel(tr("chart.forecast.xAxis"));
    }

    @FXML
    private void onForecastHorizonChanged() {
        if (returnMatrix != null) runForecast();
    }

    private void loadApiKey() {
        String k = Config.get("api.key");
        if (k != null && !k.equals("YOUR_API_KEY_HERE")) apiKeyField.setText(k);
    }

    // ── Localization ───────────────────────────────────────────────────────────
    private void updateTexts() {
        titleLabel.setText(loc.get("label.title"));
        pipelineLabel.setText(loc.get("label.pipeline"));
        langToggle.setText(loc.get(loc.isRussian() ? "lang.en" : "lang.ru"));

        // --- Безопасное обновление вкладок ---
        Scene scene = logArea.getScene();
        if (scene != null) {
            TabPane tabPane = (TabPane) scene.lookup(".result-tabs");
            if (tabPane != null && tabPane.getTabs().size() >= 10) {
                tabPane.getTabs().get(0).setText(loc.get("tab.portfolioWeights"));
                tabPane.getTabs().get(1).setText(loc.get("tab.backtestCompare"));
                tabPane.getTabs().get(2).setText(loc.get("tab.forecast"));
                tabPane.getTabs().get(3).setText(loc.get("tab.correlation"));
                tabPane.getTabs().get(4).setText(loc.get("tab.recommendations"));
                tabPane.getTabs().get(5).setText(loc.get("tab.stressTest"));
                tabPane.getTabs().get(6).setText(loc.get("tab.monteCarlo"));
                tabPane.getTabs().get(7).setText(loc.get("tab.attribution"));
                tabPane.getTabs().get(8).setText(loc.get("tab.sensitivity"));
                tabPane.getTabs().get(9).setText(loc.get("tab.log"));
            }
        }

        // --- Остальной код (безопасен, не требует сцены) ---
        weightsChart.setTitle(loc.get("chart.allocation.title"));
        equityChart.setTitle(loc.get("chart.equity.title"));
        forecastChart.setTitle(loc.get("chart.forecast.title"));
        configureForecastChartAxis();
        mcChart.setTitle(loc.get("chart.monteCarlo.title"));
        sensitivityChart.setTitle(loc.get("chart.sensitivity.title"));

        // Обновление статуса
        String current = statusLabel.getText();
        if (current.contains("Ready")) statusLabel.setText(loc.get("label.statusReady"));

        // Обновление выпадающих списков
        String prevScenario = scenarioCombo.getValue();
        scenarioCombo.setItems(FXCollections.observableArrayList(
                tr("scenario.may2022"), tr("scenario.ftxCollapse"),
                tr("scenario.covidCrash"), tr("scenario.lunaCollapse"),
                tr("scenario.oct2025")));
        if (prevScenario != null) {
            if (prevScenario.contains("May") || prevScenario.contains("Май"))
                scenarioCombo.setValue(tr("scenario.may2022"));
            else if (prevScenario.contains("FTX"))
                scenarioCombo.setValue(tr("scenario.ftxCollapse"));
            else if (prevScenario.contains("COVID"))
                scenarioCombo.setValue(tr("scenario.covidCrash"));
            else if (prevScenario.contains("Luna"))
                scenarioCombo.setValue(tr("scenario.lunaCollapse"));
            else if (prevScenario.contains("Oct") || prevScenario.contains("окт"))
                scenarioCombo.setValue(tr("scenario.oct2025"));
        }

        forecastHorizonCombo.setCellFactory(lv -> horizonCell());
        forecastHorizonCombo.setButtonCell(horizonCell());
        configureForecastChartAxis();
    }

    private String tr(String key) { return loc.get(key); }

    private String tr(String key, Object... args) { return loc.get(key, args); }

    // ── Handlers ──────────────────────────────────────────────────────────────

    @FXML
    private void onLoadCsv() {
        var chooser = new javafx.stage.FileChooser();
        chooser.setTitle(tr("filechooser.title"));
        chooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter(tr("filechooser.csvFiles"), "*.csv"));
        java.io.File file = chooser.showOpenDialog(logArea.getScene().getWindow());
        if (file == null) return;

        try {
            var csvProvider = new org.example.data.CsvDataProvider(file.getAbsolutePath());
            var csvHeaders = csvProvider.getHeaders();
            if (csvHeaders == null || csvHeaders.size() < 2) {
                warn(tr("message.csvMinColumns")); return;
            }
            selectedCoins = csvHeaders;
            returnMatrix = csvProvider.getReturns(selectedCoins);

            // Update coin checkboxes to match CSV headers
            coinCheckboxes.clear();
            coinsPane.getChildren().clear();
            for (String c : selectedCoins) {
                CheckBox cb = new CheckBox(c);
                cb.setSelected(true);
                coinCheckboxes.put(c, cb);
                coinsPane.getChildren().add(cb);
            }

            log("CSV loaded: %d assets × %d periods → %s"
                    .formatted(selectedCoins.size(), returnMatrix.countRows(), file.getName()));
            runBtn.setDisable(false);
        } catch (Exception e) {
            warn("Failed to load CSV: " + e.getMessage());
            log("CSV error: " + e);
        }
    }

    @FXML
    private void onAutoDetect() {
        if (returnMatrix == null) { warn(tr("message.loadDataFirst")); return; }
        try {
            var sd = new org.example.util.SmartDefaults(returnMatrix);
            windowSpinner.getValueFactory().setValue(sd.suggestWindow());
            horizonSpinner.getValueFactory().setValue(sd.suggestHorizon());
            targetVolSlider.setValue(sd.suggestTargetVol() * 100);
            leverageSlider.setValue(sd.suggestLeverage());
            shrinkageSlider.setValue(sd.suggestShrinkage());
            alphaSlider.setValue(sd.suggestEwmaAlpha());
            momentumLookback.getValueFactory().setValue(sd.suggestMomentumLookback());
            maxLongSlider.setValue(sd.suggestMaxLong() * 100);
            maxShortSlider.setValue(sd.suggestMaxShort() * 100);
            maxVarSlider.setValue(sd.suggestMaxVaR() * 100);
            log("Auto-detected params: vol=%.1f%%, corr=%.2f, window=%d, lev=%.1f"
                    .formatted(sd.avgVol() * 100, sd.avgCorr(), sd.suggestWindow(), sd.suggestLeverage()));
        } catch (Exception e) {
            log("Auto-detect failed: " + e.getMessage());
        }
    }

    @FXML
    private void onFetchData() {
        selectedCoins = selectedCoins();
        if (selectedCoins.size() < 2) { warn(tr("message.selectAtLeast2Coins")); return; }

        org.example.data.Timeframe tf = timeframeCombo.getValue();
        int factor = tf.resampleFactor();

        Task<MatrixR064> task = new Task<>() {
            @Override protected MatrixR064 call() {
                updateMessage(tr("task.fetching"));
                List<CoinData> coins = dataProvider.fetchAll(selectedCoins);
                updateMessage(tr("task.buildingMatrix", tf.label()));
                MatrixR064 dailyMatrix = buildMatrix(coins, selectedCoins);
                if (factor > 1) {
                    updateMessage(tr("task.resampling", tf.label()));
                    return org.example.util.MatrixUtils.resample(dailyMatrix, factor);
                }
                return dailyMatrix;
            }
        };
        wireTask(task, tr("task.dataReady", tf.label()), matrix -> {
            returnMatrix = matrix;
            log("Matrix ready: %d periods x %d assets (%s)"
                    .formatted(matrix.countRows(), matrix.countColumns(), tf.label()));
            runBtn.setDisable(false);
        });
        setBusy(tr("task.fetchingData"));
        new Thread(task, "fetch").start();
    }

    @FXML
    private void onRunStrategies() {
        if (returnMatrix == null) { warn(tr("message.fetchDataFirst")); return; }

        List<String> chosen = strategyCheckboxes.entrySet().stream()
                .filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey)
                .collect(Collectors.toList());
        if (chosen.isEmpty()) { warn(tr("message.selectAtLeast1Strategy")); return; }

        StrategyRegistry.Params params = readParams();
        Map<String, Strategy> allStrats = StrategyRegistry.buildAll(params);

        int    win      = windowSpinner.getValue();
        int    hor      = horizonSpinner.getValue();
        double fee      = feeRateSlider.getValue() / 100.0;
        double rfRate   = riskFreeRateSlider.getValue() / 100.0;
        double maxTO    = turnoverSlider.getValue();
        int    rebFreq  = rebalanceSpinner.getValue();
        var    exec     = zeroCostCheck.isSelected()
                          ? new ZeroCostExecution()
                          : new SimpleExecution(fee, Defaults.SLIPPAGE);

        BacktestEngine engine  = new BacktestEngine(win, hor, exec, rfRate, maxTO, rebFreq);
        MatrixR064     returns = returnMatrix;

        Task<Map<String, BacktestResult>> task = new Task<>() {
            @Override
            protected Map<String, BacktestResult> call() {
                // Build the filtered map
                Map<String, Strategy> filtered = new LinkedHashMap<>();
                for (String name : chosen) {
                    filtered.put(name, allStrats.get(name));
                }
                updateMessage(tr("task.backtesting", filtered.size()));
                Map<String, BacktestResult> results = engine.runAllParallel(returns, filtered,
                        (sid, msg) -> updateMessage(msg));

                // Single-step weights on the last training window
                int end   = (int) returns.countRows();
                int start = Math.max(0, end - win);
                for (String name : results.keySet()) {
                    Strategy s = allStrats.get(name);
                    lastWeights.put(name, s.build(MatrixUtils.sliceRows(returns, start, end)));
                    log(results.get(name).summary());
                }
                return results;
            }
        };
        wireTask(task, tr("task.done"), results -> {
            lastResults.clear();
            lastResults.putAll(results);
            renderEquityChart(results);
            renderCompareTable(results);
            List<String> names = new ArrayList<>(results.keySet());
            strategySelector.setItems(FXCollections.observableArrayList(names));
            if (!names.isEmpty()) {
                // Auto-rank: select best by Sharpe
                String best = names.stream()
                        .max(Comparator.comparingDouble(n -> results.get(n).sharpe()))
                        .orElse(names.get(0));
                strategySelector.getSelectionModel().select(best);
                renderPortfolioTab(best);
                log("Top strategy: " + best + " (Sharpe " + String.format("%.3f", results.get(best).sharpe()) + ")");
            }
            // Run forecast on fetched data
            runForecast();
            // Show current regime
            if (returnMatrix != null) {
                var regimes = MatrixUtils.correlationRegime(returnMatrix, Math.min(30, (int)returnMatrix.countRows()/3));
                if (!regimes.isEmpty()) {
                    String currentRegime = regimes.get(regimes.size()-1);
                    String regimeMsg = switch(currentRegime) {
                        case "HIGH_CORR" -> tr("regime.highCorrelation");
                        case "LOW_CORR"  -> tr("regime.lowCorrelation");
                        default          -> tr("regime.normal");
                    };
                    log("Current regime: " + regimeMsg);
                }
            }
            // Generate recommendations
            renderRecommendations();
            renderAttribution();
        });
        setBusy(tr("task.running"));
        new Thread(task, "strat").start();
    }

    @FXML
    private void onExport() {
        if (lastResults.isEmpty()) { warn(tr("message.runStrategiesFirst")); return; }
        FileExporter ex = new FileExporter();
        lastResults.forEach((name, r) -> {
            List<BigDecimal> w = lastWeights.get(name);
            if (w != null) ex.exportWeights(selectedCoins, w, name);
            ex.exportBacktest(r.finalEquity(), r.maxDrawdown(), r.sharpe(),
                    r.sortino(), r.calmar(), r.var95(), r.cvar95(), name);
        });
        ex.exportCsv(lastWeights, lastResults, selectedCoins);
        log(tr("task.exported", lastResults.size()));
    }

    @FXML
    private void onExportHtml() {
        if (lastResults.isEmpty()) { warn(tr("message.runStrategiesFirst")); return; }
        var exporter = new org.example.util.HtmlReportExporter();
        exporter.export(null, lastResults, lastWeights, selectedCoins, null);
        log(tr("task.htmlReport"));
    }

    @FXML
    private void onSaveApiKey() {
        String k = apiKeyField.getText().strip();
        if (k.isBlank()) { warn(tr("message.emptyKey")); return; }
        Config.set("api.key", k);
        try { Config.save(); } catch (Exception ignored) {}
        dataProvider.clearCache();
        log("API key saved, cache cleared.");
    }

    @FXML private void onSelectAllCoins()  { coinCheckboxes.values().forEach(c -> c.setSelected(true));  }
    @FXML private void onSelectNoneCoins() { coinCheckboxes.values().forEach(c -> c.setSelected(false)); }
    @FXML private void onSelectAllStrat()  { strategyCheckboxes.values().forEach(c -> c.setSelected(true));  }
    @FXML private void onSelectNoneStrat() { strategyCheckboxes.values().forEach(c -> c.setSelected(false)); }

    @FXML private void onToggleLanguage() {
        boolean ru = langToggle.isSelected();
        loc.setRussian(ru);
        langToggle.setText(ru ? "EN" : "RU");
        langToggle.setSelected(ru);
        updateTexts();
        log(ru ? "Язык переключён на Русский" : "Switched to English");
    }

    @FXML
    private void onSaveState() {
        List<String> coins = coinCheckboxes.entrySet().stream()
                .filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey).toList();
        List<String> strats = strategyCheckboxes.entrySet().stream()
                .filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey).toList();
        AppState state = new AppState(
                coins, strats,
                targetReturnSlider.getValue(), maxLongSlider.getValue(),
                maxShortSlider.getValue(), allowShortingCheck.isSelected(),
                alphaSlider.getValue(), shrinkageSlider.getValue(),
                ewmaCovCheck.isSelected(), ewmaLambdaSlider.getValue(),
                riskFreeRateSlider.getValue(), leverageSlider.getValue(),
                momentumLookback.getValue(), volScalingCheck.isSelected(),
                targetVolSlider.getValue(), portfolioVaRCheck.isSelected(),
                maxVarSlider.getValue(),
                windowSpinner.getValue(), horizonSpinner.getValue(),
                feeRateSlider.getValue(), turnoverSlider.getValue(),
                rebalanceSpinner.getValue(), zeroCostCheck.isSelected(),
                timeframeCombo.getValue().name(), apiKeyField.getText().strip());
        try (FileWriter fw = new FileWriter(STATE_PATH.toFile())) {
            GSON.toJson(state, fw);
            log(tr("task.saved", STATE_PATH.toString()));
        } catch (Exception e) {
            log("Failed to save state: " + e.getMessage());
        }
    }

    private void loadState() {
        if (!STATE_PATH.toFile().exists()) return;
        try (FileReader fr = new FileReader(STATE_PATH.toFile())) {
            AppState s = GSON.fromJson(fr, AppState.class);
            if (s == null) return;

            // Restore coin checkboxes
            if (s.selectedCoins() != null) {
                for (var e : coinCheckboxes.entrySet())
                    e.getValue().setSelected(s.selectedCoins().contains(e.getKey()));
            }
            // Restore strategy checkboxes
            if (s.selectedStrategies() != null) {
                for (var e : strategyCheckboxes.entrySet())
                    e.getValue().setSelected(s.selectedStrategies().contains(e.getKey()));
            }
            // Restore sliders & spinners
            setSlider(targetReturnSlider, s.targetReturn());
            setSlider(maxLongSlider, s.maxLong());
            setSlider(maxShortSlider, s.maxShort());
            allowShortingCheck.setSelected(s.allowShorting());
            setSlider(alphaSlider, s.ewmaAlpha());
            setSlider(shrinkageSlider, s.shrinkage());
            ewmaCovCheck.setSelected(s.ewmaCov());
            setSlider(ewmaLambdaSlider, s.ewmaLambda());
            setSlider(riskFreeRateSlider, s.riskFreeRate());
            setSlider(leverageSlider, s.leverage());
            if (s.momentumLookback() > 0) momentumLookback.getValueFactory().setValue(s.momentumLookback());
            volScalingCheck.setSelected(s.volScaling());
            setSlider(targetVolSlider, s.targetVol());
            portfolioVaRCheck.setSelected(s.portfolioVaR());
            setSlider(maxVarSlider, s.maxVaR());
            if (s.window() > 0) windowSpinner.getValueFactory().setValue(s.window());
            if (s.horizon() > 0) horizonSpinner.getValueFactory().setValue(s.horizon());
            setSlider(feeRateSlider, s.feeRate());
            setSlider(turnoverSlider, s.maxTurnover());
            if (s.rebalanceFreq() > 0) rebalanceSpinner.getValueFactory().setValue(s.rebalanceFreq());
            zeroCostCheck.setSelected(s.zeroCost());
            if (s.timeframe() != null) {
                try { timeframeCombo.setValue(org.example.data.Timeframe.valueOf(s.timeframe())); }
                catch (Exception ignored) {}
            }
            if (s.apiKey() != null && !s.apiKey().isEmpty()) apiKeyField.setText(s.apiKey());
            log(tr("task.loaded", STATE_PATH.toString()));
        } catch (Exception e) {
            log("Failed to load state: " + e.getMessage());
        }
    }

    private static void setSlider(Slider s, double v) {
        if (v >= s.getMin() && v <= s.getMax()) s.setValue(v);
    }

    @FXML
    private void onRunStressTest() {
        if (lastWeights.isEmpty() || returnMatrix == null) { warn(tr("message.runStrategiesFirst")); return; }
        String selectedStrategy = strategySelector.getValue();
        if (selectedStrategy == null) { warn(tr("message.selectStrategyFirst")); return; }
        List<BigDecimal> w = lastWeights.get(selectedStrategy);
        if (w == null) { warn(tr("message.noWeights")); return; }

        StressTestEngine engine = new StressTestEngine();
        String scenarioName = scenarioCombo.getValue();
        StressScenario scenario = StressScenario.may2022Crash();
        if (scenarioName.contains("FTX") || scenarioName.equals(tr("scenario.ftxCollapse")))
            scenario = StressScenario.ftxCollapse();
        else if (scenarioName.contains("COVID") || scenarioName.equals(tr("scenario.covidCrash")))
            scenario = StressScenario.covidCrash();
        else if (scenarioName.contains("Luna") || scenarioName.equals(tr("scenario.lunaCollapse")))
            scenario = StressScenario.lunaCollapse();
        else if (scenarioName.contains("Oct") || scenarioName.contains("2025")
                || scenarioName.equals(tr("scenario.oct2025")))
            scenario = StressScenario.oct2025Crash();
        StressTestResult result = engine.runStressTest(w, returnMatrix, scenario);
        renderStressResult(result);
    }

    @FXML
    private void onRunMonteCarlo() {
        MonteCarloSimulator sim = new MonteCarloSimulator();
        int paths = mcPathsSpinner.getValue();
        int horizon = mcHorizonSpinner.getValue();
        double drift = mcDriftSlider.getValue();
        double vol = mcVolSlider.getValue();
        MonteCarloResult result = sim.simulate(paths, horizon, drift, vol, 42);
        renderMonteCarloResult(result);
    }

    @FXML
    private void onRunSensitivity() {
        if (returnMatrix == null) { warn(tr("message.fetchDataFirst")); return; }
        ParameterSensitivity ps = new ParameterSensitivity();
        String paramName = sensitivityParamCombo.getValue();
        String key = "shrinkage";
        if (paramName.contains("EWMA") || paramName.equals(tr("sensitivity.ewmaAlpha")))
            key = "alpha";
        else if (paramName.contains("Leverage") || paramName.equals(tr("sensitivity.leverage")))
            key = "leverage";
        else if (paramName.contains("Max") || paramName.equals(tr("sensitivity.maxLong")))
            key = "maxLong";
        double[] values = switch (key) {
            case "shrinkage" -> new double[]{0.1, 0.3, 0.5, 0.7, 0.9};
            case "alpha"     -> new double[]{0.01, 0.05, 0.1, 0.2, 0.3, 0.5};
            case "leverage"  -> new double[]{1.0, 1.2, 1.5, 2.0, 2.5, 3.0};
            case "maxLong"   -> new double[]{0.05, 0.10, 0.15, 0.20, 0.30, 0.50};
            default -> new double[]{0.1, 0.5, 0.9};
        };
        SensitivityResult result = ps.sweep(
                returnMatrix, key, values,
                windowSpinner.getValue(), horizonSpinner.getValue(),
                zeroCostCheck.isSelected()
                        ? new ZeroCostExecution()
                        : new SimpleExecution(feeRateSlider.getValue() / 100.0, Defaults.SLIPPAGE),
                riskFreeRateSlider.getValue() / 100.0);
        renderSensitivityResult(result);
    }

    private void applyPreset(org.example.engine.StrategyPreset preset) {
        maxLongSlider.setValue(preset.maxLong());
        maxShortSlider.setValue(preset.maxShort());
        alphaSlider.setValue(preset.alpha());
        shrinkageSlider.setValue(preset.shrinkage());
        leverageSlider.setValue(preset.leverage());
        targetReturnSlider.setValue(preset.targetReturn());
        momentumLookback.getValueFactory().setValue(preset.momentumLookback());
        volScalingCheck.setSelected(preset.volScaling());
        targetVolSlider.setValue(preset.targetVol());
        ewmaCovCheck.setSelected(preset.ewmaCov());
        ewmaLambdaSlider.setValue(preset.ewmaLambda());
        portfolioVaRCheck.setSelected(preset.portfolioVaR());
        maxVarSlider.setValue(preset.maxVaR() * 100);
        log("Applied preset: " + preset.label());
    }

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
            rows.add(new WeightRow(label, pct(v), v >= 0 ? tr("weight.long") : tr("weight.short")));
        }
        weightsChart.setData(pie);
        weightsTable.setItems(rows);

        statsGrid.getChildren().clear();
        if (r != null) {
            stat(tr("stat.finalEquity"),  "%.4f".formatted(r.finalEquity()),           0);
            stat(tr("stat.maxDrawdown"),  "%.2f%%".formatted(r.maxDrawdown() * 100),   1);
            stat(tr("stat.sharpe"),       "%.4f".formatted(r.sharpe()),                2);
            stat(tr("stat.sortino"),      "%.4f".formatted(r.sortino()),               3);
            stat(tr("stat.calmar"),       "%.4f".formatted(r.calmar()),               4);
            stat(tr("stat.var95"),        "%.2f%%".formatted(r.var95() * 100),        5);
            stat(tr("stat.cvar95"),       "%.2f%%".formatted(r.cvar95() * 100),       6);
            stat(tr("stat.avgTurnover"),  "%.2f%%".formatted(r.avgTurnover() * 100),   7);
            stat(tr("stat.feeDrag"),      "%.5f".formatted(r.totalFees()),             8);
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
            stat(tr("stat.topRiskContrib"), sb.toString(), 9);
        }
    }

    private void renderEquityChart(Map<String, BacktestResult> results) {
        equityChart.getData().clear();
        equityChart.setAnimated(false);

        // Add benchmark curve first (dashed)
        BacktestResult firstResult = results.values().stream().findFirst().orElse(null);
        if (firstResult != null && firstResult.benchmarkCurve() != null) {
            XYChart.Series<Number, Number> bench = new XYChart.Series<>();
            bench.setName(tr("series.benchmark"));
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

        int horizon = forecastHorizonCombo.getValue() != null ? forecastHorizonCombo.getValue() : 14;
        ForecastEngine engine = new ForecastEngine();
        List<ForecastResult> forecasts = engine.forecast(returnMatrix, horizon, selectedCoins);

        // Render forecast chart (cumulative return %)
        forecastChart.getData().clear();
        forecastChart.setAnimated(false);
        int ci = 0;
        for (ForecastResult fr : forecasts) {
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName(fr.assetName());
            List<Double> cum = fr.cumulativeReturnPct();
            for (int h = 0; h < cum.size(); h++)
                series.getData().add(new XYChart.Data<>(h + 1, cum.get(h)));
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

        // Render forecast table (horizon-aware columns)
        ObservableList<ForecastRow> rows = FXCollections.observableArrayList();
        int mid = Math.max(0, (horizon - 1) / 2);
        int last = horizon - 1;
        for (ForecastResult fr : forecasts) {
            List<Double> cum = fr.cumulativeReturnPct();
            List<Double> u95   = fr.upper95();
            List<Double> l95   = fr.lower95();
            String d1 = cum.size() > 0 ? "%+.2f%%".formatted(cum.get(0)) : "—";
            String dMid = cum.size() > mid ? "%+.2f%%".formatted(cum.get(mid)) : "—";
            String dEnd = cum.size() > last ? "%+.2f%%".formatted(cum.get(last)) : "—";
            String ciStr = (u95.size() > last && l95.size() > last)
                    ? "[%.2f%%, %.2f%%]".formatted(l95.get(last) * 100, u95.get(last) * 100)
                    : "—";
            rows.add(new ForecastRow(
                    fr.assetName(),
                    fr.signal().label(),
                    fr.riskLevel().label(),
                    "%.2f%%".formatted(fr.annualizedReturn() * 100),
                    "%.2f%%".formatted(fr.annualizedVol() * 100),
                    "%.3f".formatted(fr.forecastSharpe()),
                    d1, dMid, dEnd, ciStr,
                    "%.1f%%".formatted(fr.probLoss() * 100),
                    "%.2f%%".formatted(fr.maxDrawdownEst() * 100)));
        }
        updateForecastTableHeaders(horizon, mid, last);
        forecastTable.setItems(rows);
        ForecastEngine fEng = new ForecastEngine();
        double[] accuracy = fEng.forecastAccuracy(returnMatrix, Math.min(60, (int)returnMatrix.countRows()/2), horizon);
        StringBuilder accStr = new StringBuilder("Forecast MAE: ");
        for (int h = 0; h < Math.min(accuracy.length, 7); h++) {
            accStr.append("d").append(h+1).append("=").append("%.4f".formatted(accuracy[h])).append(" ");
        }
        log(accStr.toString());
        log("Forecast generated for " + forecasts.size() + " assets (" + horizon + " days).");

        // Render correlation heatmap
        renderHeatmap();
    }

    @SuppressWarnings("unchecked")
    private void updateForecastTableHeaders(int horizon, int mid, int last) {
        if (forecastTable.getColumns().size() < 12) return;
        forecastTable.getColumns().get(6).setText(tr("forecast.colDay", 1));
        forecastTable.getColumns().get(7).setText(tr("forecast.colDay", mid + 1));
        forecastTable.getColumns().get(8).setText(tr("forecast.colDay", last + 1));
        forecastTable.getColumns().get(9).setText(tr("forecast.colCI", last + 1));
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

    // ── Recommendations ────────────────────────────────────────────────────

    private void renderRecommendations() {
        recommendationCards.getChildren().clear();
        strategySummaryBox.getChildren().clear();
        warningsBox.getChildren().clear();

        if (lastResults.isEmpty()) {
            Label empty = new Label(tr("rec.runFirst"));
            empty.setStyle("-fx-text-fill: #c8d4f0; -fx-font-style: italic;");
            recommendationCards.getChildren().add(empty);
            return;
        }

        // Best strategy by Sharpe
        BacktestResult best = lastResults.values().stream()
                .max(Comparator.comparingDouble(BacktestResult::sharpe))
                .orElse(null);
        String bestName = lastResults.entrySet().stream()
                .max(Comparator.comparingDouble(e -> e.getValue().sharpe()))
                .map(Map.Entry::getKey).orElse("—");

        if (best != null) {
            // Overall recommendation card
            VBox card = buildCard(tr("rec.overallRecommendation"),
                    tr("rec.bestStrategy") + bestName,
                    "Sharpe: %.3f  |  Max DD: %.2f%%  |  Sortino: %.3f".formatted(
                            best.sharpe(), best.maxDrawdown() * 100, best.sortino()),
                    "#e94560");
            recommendationCards.getChildren().add(card);

            // Forecast-based recommendation
            if (!lastResults.isEmpty()) {
                ForecastEngine fEngine = new ForecastEngine();
                List<ForecastResult> forecasts = fEngine.forecast(returnMatrix,
                        forecastHorizonCombo.getValue() != null ? forecastHorizonCombo.getValue() : 14,
                        selectedCoins);
                for (ForecastResult fr : forecasts) {
                    VBox fc = buildForecastCard(fr);
                    recommendationCards.getChildren().add(fc);
                }
            }
        }

        // Strategy summary
        Label sumTitle = new Label(tr("rec.performanceSummary"));
        sumTitle.setStyle("-fx-text-fill: #e94560; -fx-font-weight: bold; -fx-font-size: 13px;");
        strategySummaryBox.getChildren().add(sumTitle);

        for (Map.Entry<String, BacktestResult> e : lastResults.entrySet()) {
            BacktestResult r = e.getValue();
            String text = "%s: Sharpe=%.3f, DD=%.1f%%, Sortino=%.3f".formatted(
                    e.getKey(), r.sharpe(), r.maxDrawdown() * 100, r.sortino());
            Label l = new Label(text);
            l.setStyle("-fx-text-fill: #e8eeff; -fx-font-size: 12px; -fx-padding: 2 0;");
            strategySummaryBox.getChildren().add(l);
        }

        // Warnings
        Label warnTitle = new Label(tr("rec.warnings"));
        warnTitle.setStyle("-fx-text-fill: #ffab00; -fx-font-weight: bold; -fx-font-size: 13px;");
        warningsBox.getChildren().add(warnTitle);

        if (best != null && best.maxDrawdown() > 0.20) {
            addWarning(tr("rec.highDrawdown", best.maxDrawdown() * 100));
        }
        if (best != null && best.sharpe() < 0) {
            addWarning(tr("rec.negativeSharpe"));
        }
        if (leverageSlider.getValue() > 2.0) {
            addWarning(tr("rec.highLeverage"));
        }
        StrategyRegistry.Params p = readParams();
        if (!p.allowShorting() && maxShortSlider.getValue() > 0) {
            addWarning(tr("rec.shortingDisabled"));
        }
        if (turnoverSlider.getValue() > 0 && turnoverSlider.getValue() < 0.2) {
            addWarning(tr("rec.tightTurnover", turnoverSlider.getValue() * 100));
        }

        if (warningsBox.getChildren().size() == 1) {
            Label noWarn = new Label(tr("rec.noWarnings"));
            noWarn.setStyle("-fx-text-fill: #4caf50; -fx-font-style: italic; -fx-padding: 4 0;");
            warningsBox.getChildren().add(noWarn);
        }
    }

    private VBox buildCard(String title, String line1, String line2, String accent) {
        VBox card = new VBox(6);
        card.getStyleClass().add("recommendation-card");
        card.setStyle("-fx-border-color: " + accent + ";");
        Label t = new Label(title);
        t.setStyle("-fx-text-fill: " + accent + "; -fx-font-weight: bold; -fx-font-size: 14px;");
        Label l1 = new Label(line1);
        l1.setStyle("-fx-text-fill: #f0f4ff; -fx-font-size: 13px;");
        l1.setWrapText(true);
        Label l2 = new Label(line2);
        l2.setStyle("-fx-text-fill: #c8d4f0; -fx-font-size: 12px;");
        l2.setWrapText(true);
        card.getChildren().addAll(t, l1, l2);
        return card;
    }

    private VBox buildForecastCard(ForecastResult fr) {
        VBox card = new VBox(4);
        card.getStyleClass().add("recommendation-card");

        Label name = new Label(fr.assetName());
        name.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 13px;");

        Label signal = new Label(tr("forecast.signal") + fr.signal().label());
        signal.setStyle("-fx-text-fill: " + fr.signal().color() + "; -fx-font-weight: bold; -fx-font-size: 12px;");

        Label risk = new Label(tr("forecast.risk") + fr.riskLevel().label() + " (" + fr.riskLevel().description() + ")");
        risk.setStyle("-fx-text-fill: " + fr.riskLevel().color() + "; -fx-font-size: 12px;");

        Label range = new Label(tr("forecast.range95", fr.expectedRangeLow() * 100, fr.expectedRangeHigh() * 100));
        range.setStyle("-fx-text-fill: #d8e0f8; -fx-font-size: 12px;");

        Label pLoss = new Label(tr("forecast.probLoss") + "%.1f%%".formatted(fr.probLoss() * 100));
        pLoss.setStyle("-fx-text-fill: #d8e0f8; -fx-font-size: 12px;");

        Label fSharpe = new Label(tr("forecast.sharpe") + "%.3f".formatted(fr.forecastSharpe()));
        fSharpe.setStyle("-fx-text-fill: #d8e0f8; -fx-font-size: 12px;");

        Label advice = new Label(fr.humanSummary());
        advice.setStyle("-fx-text-fill: #b8c8e8; -fx-font-style: italic; -fx-font-size: 12px;");
        advice.setWrapText(true);

        card.getChildren().addAll(name, signal, risk, range, pLoss, fSharpe, advice);
        return card;
    }

    private void addWarning(String text) {
        Label l = new Label("⚠ " + text);
        l.setStyle("-fx-text-fill: #ffab00; -fx-font-size: 11px; -fx-padding: 2 0;");
        l.setWrapText(true);
        warningsBox.getChildren().add(l);
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

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private void log(String m) {
        String ts = LocalTime.now().format(TIME_FMT);
        Platform.runLater(() -> {
            logArea.appendText("[" + ts + "] " + m + "\n");
            logArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    private void warn(String m) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(tr("message.warningTitle")); a.setHeaderText(null); a.setContentText(m); a.showAndWait();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private List<String> selectedCoins() {
        return coinCheckboxes.entrySet().stream()
                .filter(e -> e.getValue().isSelected())
                .map(Map.Entry::getKey).collect(Collectors.toList());
    }

    private StrategyRegistry.Params readParams() {
        boolean shorting = allowShortingCheck.isSelected();
        return new StrategyRegistry.Params(
                maxLongSlider.getValue()   / 100.0,
                shorting ? -maxShortSlider.getValue() / 100.0 : 0.0,
                alphaSlider.getValue(),
                shrinkageSlider.getValue(),
                leverageSlider.getValue(),
                shorting,
                targetReturnSlider.getValue() / 100.0,
                momentumLookback.getValue(),
                volScalingCheck.isSelected(),
                targetVolSlider.getValue() / 100.0,
                ewmaCovCheck.isSelected(),
                ewmaLambdaSlider.getValue(),
                portfolioVaRCheck.isSelected(),
                maxVarSlider.getValue() / 100.0
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

    // ── Render new tabs ──────────────────────────────────────────────────────

    private void renderStressResult(StressTestResult result) {
        stressResultsBox.getChildren().clear();
        stressTable.getItems().clear();

        VBox card = new VBox(6);
        card.getStyleClass().add("stress-card");
        Label title = new Label(tr("stress.test") + result.scenarioName());
        title.setStyle("-fx-text-fill: #ff5722; -fx-font-weight: bold; -fx-font-size: 14px;");
        Label portfolioRet = new Label(tr("stress.portfolioReturn") + "%.2f%%".formatted(result.portfolioReturn() * 100));
        portfolioRet.setStyle("-fx-text-fill: #e0e0e0;");
        Label maxDD = new Label(tr("stress.maxDrawdown") + "%.2f%%".formatted(result.maxDrawdown() * 100));
        maxDD.setStyle("-fx-text-fill: #ff5722;");
        Label worstDay = new Label(tr("stress.worstDay") + "%.2f%%".formatted(result.worstDayLoss() * 100));
        worstDay.setStyle("-fx-text-fill: #e0e0e0;");
        Label var95 = new Label(tr("stress.var95") + "%.2f%%".formatted(result.var95() * 100));
        var95.setStyle("-fx-text-fill: #ffa500;");
        Label cvar95 = new Label(tr("stress.cvar95") + "%.2f%%".formatted(result.cvar95() * 100));
        cvar95.setStyle("-fx-text-fill: #ff5722;");
        card.getChildren().addAll(title, portfolioRet, maxDD, worstDay, var95, cvar95);
        stressResultsBox.getChildren().add(card);

        StressRow row = new StressRow(
                result.scenarioName(),
                "%.2f%%".formatted(result.portfolioReturn() * 100),
                "%.2f%%".formatted(result.maxDrawdown() * 100),
                "%.2f%%".formatted(result.worstDayLoss() * 100),
                "%.2f%%".formatted(result.var95() * 100),
                "%.2f%%".formatted(result.cvar95() * 100));
        stressTable.setItems(FXCollections.observableArrayList(row));
    }

    private void renderMonteCarloResult(MonteCarloResult result) {
        mcChart.getData().clear();
        mcChart.setAnimated(false);

        XYChart.Series<Number, Number> median = new XYChart.Series<>();
        median.setName("Median");
        double[] med = result.medianPath();
        for (int i = 0; i < med.length; i++)
            median.getData().add(new XYChart.Data<>(i, med[i]));
        mcChart.getData().add(median);

        XYChart.Series<Number, Number> upper = new XYChart.Series<>();
        upper.setName(tr("series.pct95"));
        double[] u = result.p95();
        for (int i = 0; i < u.length; i++)
            upper.getData().add(new XYChart.Data<>(i, u[i]));
        mcChart.getData().add(upper);

        XYChart.Series<Number, Number> lower = new XYChart.Series<>();
        lower.setName(tr("series.pct5"));
        double[] l = result.p5();
        for (int i = 0; i < l.length; i++)
            lower.getData().add(new XYChart.Data<>(i, l[i]));
        mcChart.getData().add(lower);

        mcResultsGrid.getChildren().clear();
        Label lblMean = new Label(tr("mc.mean") + "%.4f".formatted(result.expectedReturn()));
        lblMean.getStyleClass().add("stat-label");
        Label lblStd = new Label(tr("mc.stdDev") + "%.4f".formatted(result.expectedVol()));
        lblStd.getStyleClass().add("stat-label");
        Label lblProbLoss = new Label(tr("mc.probLoss") + "%.1f%%".formatted(result.probLoss() * 100));
        lblProbLoss.getStyleClass().add("stat-label");
        Label lblVaR = new Label(tr("mc.var95") + "%.4f".formatted(result.var95()));
        lblVaR.getStyleClass().add("stat-label");
        mcResultsGrid.add(lblMean, 0, 0);
        mcResultsGrid.add(lblStd, 1, 0);
        mcResultsGrid.add(lblProbLoss, 2, 0);
        mcResultsGrid.add(lblVaR, 3, 0);
    }

    private void renderSensitivityResult(SensitivityResult result) {
        sensitivityChart.getData().clear();
        sensitivityChart.setAnimated(false);

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName(tr("series.sharpeVs") + result.paramName());
        double[] vals = result.paramValues();
        double[] sharpes = result.sharpeRatios();
        for (int i = 0; i < vals.length; i++)
            series.getData().add(new XYChart.Data<>(vals[i], sharpes[i]));
        sensitivityChart.getData().add(series);
    }

    private void renderAttribution() {
        if (lastWeights.isEmpty() || returnMatrix == null || selectedCoins == null) return;

        String firstStrat = lastWeights.keySet().iterator().next();
        List<BigDecimal> weights = lastWeights.get(firstStrat);
        if (weights == null) return;

        BacktestResult result = lastResults.get(firstStrat);
        if (result == null) return;

        int n = weights.size();
        List<BigDecimal> benchWeights = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) benchWeights.add(BigDecimal.valueOf(1.0 / n));

        List<Double> rets = result.returnSeries();
        double portReturn = rets.isEmpty() ? 0.0 : rets.stream().mapToDouble(x -> x).average().orElse(0.0);
        double benchReturn = result.benchmarkCurve().isEmpty() ? 0.0
            : (result.benchmarkCurve().get(result.benchmarkCurve().size()-1) - 1.0) / Math.max(1, result.benchmarkCurve().size());

        attributionGrid.getChildren().clear();
        Label allLabel = new Label(tr("attr.allocationEffect")); allLabel.getStyleClass().add("stat-label");
        Label allVal = new Label("%.6f".formatted(portReturn - benchReturn)); allVal.getStyleClass().add("stat-value");
        Label selLabel = new Label(tr("attr.selectionEffect")); selLabel.getStyleClass().add("stat-label");
        Label selVal = new Label("%.6f".formatted(portReturn)); selVal.getStyleClass().add("stat-value");
        Label intLabel = new Label(tr("attr.interaction")); intLabel.getStyleClass().add("stat-label");
        Label intVal = new Label("%.6f".formatted(0.0)); intVal.getStyleClass().add("stat-value");
        attributionGrid.add(allLabel, 0, 0); attributionGrid.add(allVal, 1, 0);
        attributionGrid.add(selLabel, 0, 1); attributionGrid.add(selVal, 1, 1);
        attributionGrid.add(intLabel, 0, 2); attributionGrid.add(intVal, 1, 2);

        ObservableList<AttributionRow> rows = FXCollections.observableArrayList();
        for (int i = 0; i < n; i++) {
            String asset = selectedCoins.get(i);
            String wStr = "%.2f%%".formatted(weights.get(i).doubleValue() * 100);
            String retStr = "%.4f".formatted(portReturn);
            String contrib = "%.6f".formatted(weights.get(i).doubleValue() * portReturn);
            rows.add(new AttributionRow(pretty(asset), wStr, retStr, contrib));
        }
        attributionTable.setItems(rows);
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
        private final String strategyId, finalEquity, maxDrawdown, sharpe, sortino, calmar, var95, cvar95, avgTurnover, totalFees, regimeBreakdown;
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
            long highRegime = r.regimeHistory().stream().filter(x -> x.equals("HIGH_CORR")).count();
            long lowRegime = r.regimeHistory().stream().filter(x -> x.equals("LOW_CORR")).count();
            long normalRegime = r.regimeHistory().stream().filter(x -> x.equals("NORMAL")).count();
            this.regimeBreakdown = LocalizationManager.getInstance().get("regime.format", highRegime, normalRegime, lowRegime);
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
        public String getRegimeBreakdown() { return regimeBreakdown; }
    }

    public static final class StressRow {
        private final String scenario, portfolioReturn, maxDD, worstDay, var95, cvar95;
        public StressRow(String scenario, String portfolioReturn, String maxDD,
                         String worstDay, String var95, String cvar95) {
            this.scenario = scenario; this.portfolioReturn = portfolioReturn;
            this.maxDD = maxDD; this.worstDay = worstDay;
            this.var95 = var95; this.cvar95 = cvar95;
        }
        public String getScenario()       { return scenario;       }
        public String getPortfolioReturn() { return portfolioReturn; }
        public String getMaxDD()          { return maxDD;          }
        public String getWorstDay()       { return worstDay;       }
        public String getVar95()          { return var95;          }
        public String getCvar95()         { return cvar95;         }
    }

    public static final class AttributionRow {
        private final String asset, weight, returnVal, contribution;
        public AttributionRow(String asset, String weight, String returnVal, String contribution) {
            this.asset = asset; this.weight = weight;
            this.returnVal = returnVal; this.contribution = contribution;
        }
        public String getAsset()       { return asset;       }
        public String getWeight()      { return weight;      }
        public String getReturnVal()   { return returnVal;   }
        public String getContribution(){ return contribution;}
    }

    public static final class ForecastRow {
        private final String assetName, signal, riskLevel, annReturn, annVol, forecastSharpe;
        private final String day1, day3, day7, ci95, probLoss, maxDrawdownEst;
        public ForecastRow(String name, String signal, String riskLevel, String annRet,
                           String annVol, String fSharpe, String d1, String d3, String d7,
                           String ci, String pLoss, String maxDd) {
            this.assetName = name; this.signal = signal; this.riskLevel = riskLevel;
            this.annReturn = annRet; this.annVol = annVol; this.forecastSharpe = fSharpe;
            this.day1 = d1; this.day3 = d3; this.day7 = d7; this.ci95 = ci;
            this.probLoss = pLoss; this.maxDrawdownEst = maxDd;
        }
        public String getAssetName()       { return assetName;       }
        public String getSignal()          { return signal;          }
        public String getRiskLevel()       { return riskLevel;       }
        public String getAnnReturn()       { return annReturn;       }
        public String getAnnVol()          { return annVol;          }
        public String getForecastSharpe()  { return forecastSharpe;  }
        public String getDay1()            { return day1;            }
        public String getDay3()            { return day3;            }
        public String getDay7()            { return day7;            }
        public String getCi95()            { return ci95;            }
        public String getProbLoss()        { return probLoss;        }
        public String getMaxDrawdownEst()  { return maxDrawdownEst;  }
    }
}
