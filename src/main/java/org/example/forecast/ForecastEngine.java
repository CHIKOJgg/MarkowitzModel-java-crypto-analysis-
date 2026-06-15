package org.example.forecast;

import org.example.Defaults;
import org.ojalgo.matrix.MatrixR064;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-model return forecasting engine.
 *
 * <p>Generates point forecasts with confidence intervals using multiple
 * methods: EWMA, HARCH (heteroscedastic AR), and Monte Carlo simulation.
 */
public class ForecastEngine {

    private final double confidence95;
    private final double confidence50;

    public ForecastEngine() {
        this.confidence95 = Defaults.Z_95;
        this.confidence50 = Defaults.Z_50;
    }

    /**
     * Forecast returns for all assets.
     *
     * @param returns  [T x n] historical return matrix
     * @param horizon  forecast horizon (steps ahead)
     * @param assetNames asset names for labeling
     * @return list of forecast results, one per asset
     */
    public List<ForecastResult> forecast(MatrixR064 returns, int horizon,
                                          List<String> assetNames) {
        int cols = (int) returns.countColumns();
        List<ForecastResult> results = new ArrayList<>();

        for (int j = 0; j < cols; j++) {
            double[] series = extractColumn(returns, j);
            ForecastResult r = forecastAsset(series, assetNames.get(j), horizon);
            results.add(r);
        }
        return results;
    }

    private ForecastResult forecastAsset(double[] series, String name, int horizon) {
        int T = series.length;

        // Estimate parameters
        double mu    = mean(series);
        double sigma = std(series);
        double[] acf = autocorrelation(series, Math.min(20, T / 3));

        // Method 1: EWMA forecast (mean-reverting)
        double[] ewmaForecast = ewmaForecast(series, horizon);

        // Method 2: AR(1) forecast
        double[] ar1Forecast = ar1Forecast(series, horizon);

        // Method 3: Ensemble (average of methods)
        double[] pointForecast = new double[horizon];
        for (int h = 0; h < horizon; h++) {
            pointForecast[h] = 0.5 * ewmaForecast[h] + 0.5 * ar1Forecast[h];
        }

        // Volatility forecast (EWMA)
        double[] volForecast = ewmaVolForecast(series, horizon, Defaults.EWMA_LAMBDA);

        // Confidence intervals
        double[] lower95 = new double[horizon];
        double[] upper95 = new double[horizon];
        double[] lower50 = new double[horizon];
        double[] upper50 = new double[horizon];

        for (int h = 0; h < horizon; h++) {
            double cumVol = 0;
            for (int k = 0; k <= h; k++) cumVol += volForecast[k] * volForecast[k];
            cumVol = Math.sqrt(cumVol);

            lower95[h] = pointForecast[h] - confidence95 * cumVol;
            upper95[h] = pointForecast[h] + confidence95 * cumVol;
            lower50[h] = pointForecast[h] - confidence50 * cumVol;
            upper50[h] = pointForecast[h] + confidence50 * cumVol;
        }

        // Annualized metrics
        double annReturn = mu * Defaults.TRADING_DAYS_PER_YEAR;
        double annVol    = sigma * Math.sqrt(Defaults.TRADING_DAYS_PER_YEAR);
        double trend     = acf.length > 1 ? acf[1] : 0;

        return new ForecastResult(name, horizon,
                toList(pointForecast), toList(lower95), toList(upper95),
                toList(lower50), toList(upper50),
                annReturn, annVol, trend);
    }

    // ── Forecasting methods ──────────────────────────────────────────────────

    private double[] ewmaForecast(double[] series, int horizon) {
        double alpha = Defaults.FORECAST_ALPHA;
        double ewma = series[0];
        for (int i = 1; i < series.length; i++) {
            ewma = alpha * series[i] + (1 - alpha) * ewma;
        }
        // Mean-reverting forecast toward long-term mean
        double longTermMean = mean(series);
        double[] forecast = new double[horizon];
        for (int h = 0; h < horizon; h++) {
            double weight = Math.pow(Defaults.FORECAST_PHI_MAX, h + 1);
            forecast[h] = weight * ewma + (1 - weight) * longTermMean;
        }
        return forecast;
    }

    private double[] ar1Forecast(double[] series, int horizon) {
        int T = series.length;
        double mu = mean(series);

        // Estimate AR(1) coefficient: phi = Cov(r_t, r_{t-1}) / Var(r_{t-1})
        double sumXY = 0, sumX2 = 0;
        for (int t = 1; t < T; t++) {
            double x = series[t - 1] - mu;
            double y = series[t] - mu;
            sumXY += x * y;
            sumX2 += x * x;
        }
        double phi = sumX2 > 1e-15 ? sumXY / sumX2 : 0.0;
        phi = Math.max(-Defaults.FORECAST_PHI_MAX, Math.min(Defaults.FORECAST_PHI_MAX, phi)); // stationarity

        // Forecast: r_{t+h} = mu + phi^h * (r_t - mu)
        double lastDev = series[T - 1] - mu;
        double[] forecast = new double[horizon];
        for (int h = 0; h < horizon; h++) {
            forecast[h] = mu + Math.pow(phi, h + 1) * lastDev;
        }
        return forecast;
    }

    private double[] ewmaVolForecast(double[] series, int horizon, double lambda) {
        int T = series.length;
        double[] vol = new double[T];
        vol[0] = series[0] * series[0];
        for (int t = 1; t < T; t++) {
            vol[t] = (1 - lambda) * series[t] * series[t] + lambda * vol[t - 1];
        }

        double lastVol = Math.sqrt(vol[T - 1]);
        double longTermVol = std(series);
        double[] forecast = new double[horizon];
        for (int h = 0; h < horizon; h++) {
            double weight = Math.pow(Defaults.FORECAST_VOL_DECAY, h + 1);
            forecast[h] = weight * lastVol + (1 - weight) * longTermVol;
        }
        return forecast;
    }

    // ── Utility ──────────────────────────────────────────────────────────────

    private double[] extractColumn(MatrixR064 m, int col) {
        int rows = (int) m.countRows();
        double[] out = new double[rows];
        for (int i = 0; i < rows; i++) out[i] = m.get(i, col);
        return out;
    }

    private double mean(double[] x) {
        double sum = 0;
        for (double v : x) sum += v;
        return sum / x.length;
    }

    private double std(double[] x) {
        double m = mean(x);
        double sumSq = 0;
        for (double v : x) sumSq += (v - m) * (v - m);
        return Math.sqrt(sumSq / (x.length > 1 ? x.length - 1 : x.length));
    }

    private double[] autocorrelation(double[] x, int maxLag) {
        double m = mean(x);
        double var = 0;
        for (double v : x) var += (v - m) * (v - m);
        var /= x.length;
        if (var < 1e-15) return new double[]{1.0};

        double[] acf = new double[maxLag + 1];
        acf[0] = 1.0;
        for (int lag = 1; lag <= maxLag; lag++) {
            double sum = 0;
            for (int t = lag; t < x.length; t++) {
                sum += (x[t] - m) * (x[t - lag] - m);
            }
            acf[lag] = sum / (x.length * var);
        }
        return acf;
    }

    private List<Double> toList(double[] arr) {
        List<Double> list = new ArrayList<>(arr.length);
        for (double v : arr) list.add(v);
        return list;
    }
}
