package org.example.forecast;

import org.example.Defaults;
import org.example.forecast.ForecastResult.Signal;
import org.example.forecast.ForecastResult.RiskLevel;
import org.example.util.MatrixUtils;
import org.ojalgo.matrix.MatrixR064;

import java.util.ArrayList;
import java.util.List;

public class ForecastEngine {

    private final double confidence95;
    private final double confidence50;

    public ForecastEngine() {
        this.confidence95 = Defaults.Z_95;
        this.confidence50 = Defaults.Z_50;
    }

    public List<ForecastResult> forecast(MatrixR064 returns, int horizon,
                                          List<String> assetNames) {
        int cols = (int) returns.countColumns();
        int rows = (int) returns.countRows();
        List<ForecastResult> results = new ArrayList<>();

        String regime = "NORMAL";
        if (rows >= 30) {
            List<String> regimes = MatrixUtils.correlationRegime(returns, Math.min(30, rows / 3));
            regime = regimes.isEmpty() ? "NORMAL" : regimes.get(regimes.size() - 1);
        }

        for (int j = 0; j < cols; j++) {
            double[] series = extractColumn(returns, j);
            ForecastResult r = forecastAsset(series, assetNames.get(j), horizon, regime);
            results.add(r);
        }
        return results;
    }

    private ForecastResult forecastAsset(double[] series, String name, int horizon,
                                          String regime) {
        int T = series.length;

        double mu    = mean(series);
        double sigma = std(series);

        // Recent window for momentum (last ~21 trading days)
        int lookback = Math.min(21, Math.max(5, T / 4));
        double[] recent = new double[lookback];
        System.arraycopy(series, T - lookback, recent, 0, lookback);
        double recentMu = mean(recent);
        double momentum = recentMu - mu; // deviation from long-term mean

        double regimeVolMult = switch (regime) {
            case "HIGH_CORR" -> 1.3;
            case "LOW_CORR"  -> 0.8;
            default          -> 1.0;
        };

        double meanRevSpeed = 0.12;
        double alpha = Math.min(0.4, Math.max(0.05, 2.0 / (lookback + 1)));
        double ewma = series[0];
        double ewmaVar = series[0] * series[0];
        for (int i = 1; i < T; i++) {
            ewma = alpha * series[i] + (1 - alpha) * ewma;
            ewmaVar = alpha * series[i] * series[i] + (1 - alpha) * ewmaVar;
        }

        double dailyVol = Math.sqrt(Math.max(ewmaVar, 1e-12));
        double volCap = Math.max(dailyVol, sigma) * 2.5;

        // Drift: blend recent EWMA with momentum, mean-revert toward long-term mu
        double drift = 0.7 * ewma + 0.3 * momentum;
        double[] pointForecast = new double[horizon];
        for (int h = 0; h < horizon; h++) {
            double weight = Math.exp(-meanRevSpeed * (h + 1));
            double raw = (1 - weight) * mu + weight * drift;
            pointForecast[h] = Math.max(-volCap, Math.min(volCap, raw));
        }

        double[] volForecast = ewmaVolForecast(series, horizon, Defaults.EWMA_LAMBDA, regimeVolMult);

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

        double annReturn = mu * Defaults.TRADING_DAYS_PER_YEAR;
        double annVol    = sigma * Math.sqrt(Defaults.TRADING_DAYS_PER_YEAR);
        double[] acf = autocorrelation(series, Math.min(20, T / 3));
        double trend = acf.length > 1 ? acf[1] : 0;

        double cumReturn = 0;
        for (int h = 0; h < horizon; h++) cumReturn += pointForecast[h];
        Signal signal = classifySignal(cumReturn, dailyVol, horizon, momentum, ewma);
        RiskLevel riskLevel = classifyRisk(annVol);

        double forecastSharpe = annVol > 1e-12
                ? (cumReturn * Defaults.TRADING_DAYS_PER_YEAR / horizon) / annVol : 0.0;

        double expRangeLow  = lower95[horizon - 1];
        double expRangeHigh = upper95[horizon - 1];

        double cumVolEnd = 0;
        for (int k = 0; k < horizon; k++) cumVolEnd += volForecast[k] * volForecast[k];
        cumVolEnd = Math.sqrt(cumVolEnd);
        double probLoss = normalCDF(-cumReturn / (cumVolEnd > 1e-12 ? cumVolEnd : 1e-12));

        double maxDdEst = estimateMaxDrawdown(pointForecast, volForecast, horizon);

        return new ForecastResult(name, horizon,
                toList(pointForecast), toList(lower95), toList(upper95),
                toList(lower50), toList(upper50),
                annReturn, annVol, trend,
                signal, riskLevel, forecastSharpe,
                expRangeLow, expRangeHigh, probLoss, maxDdEst);
    }

    public double[] forecastAccuracy(MatrixR064 returns, int lookback, int horizon) {
        int T = (int) returns.countRows();
        int n = (int) returns.countColumns();
        double[] errors = new double[horizon];
        int[] counts = new int[horizon];

        for (int t = lookback; t <= T - horizon; t++) {
            MatrixR064 train = sliceRows(returns, t - lookback, t);
            double[] mu = new double[n];
            for (int j = 0; j < n; j++) {
                double[] series = extractColumn(train, j);
                mu[j] = mean(series);
            }
            for (int h = 0; h < horizon; h++) {
                if (t + h < T) {
                    for (int j = 0; j < n; j++) {
                        double actual = returns.get(t + h, j);
                        errors[h] += Math.abs(mu[j] - actual);
                        counts[h]++;
                    }
                }
            }
        }
        for (int h = 0; h < horizon; h++) {
            errors[h] = counts[h] > 0 ? errors[h] / counts[h] : 0.0;
        }
        return errors;
    }

    private Signal classifySignal(double cumReturn, double dailyVol, int horizon,
                                   double momentum, double ewma) {
        double cumVol = dailyVol * Math.sqrt(horizon);
        double tStat = cumVol > 1e-12 ? cumReturn / cumVol : 0;
        double momentumScore = momentum / (dailyVol + 1e-12);
        double composite = 0.6 * tStat + 0.4 * momentumScore;

        if (composite > 1.0)      return Signal.STRONG_BUY;
        else if (composite > 0.35) return Signal.BUY;
        else if (composite > -0.35) return Signal.HOLD;
        else if (composite > -1.0) return Signal.SELL;
        else                       return Signal.STRONG_SELL;
    }

    private RiskLevel classifyRisk(double annVol) {
        if (annVol < 0.40)      return RiskLevel.LOW;
        else if (annVol < 0.80) return RiskLevel.MEDIUM;
        else if (annVol < 1.50) return RiskLevel.HIGH;
        else                     return RiskLevel.EXTREME;
    }

    private double estimateMaxDrawdown(double[] pointForecast, double[] volForecast, int horizon) {
        int sims = 100;
        double worstDD = 0;
        java.util.Random rng = new java.util.Random(42);
        for (int s = 0; s < sims; s++) {
            double peak = 1.0, eq = 1.0, maxDD = 0;
            for (int h = 0; h < horizon; h++) {
                double z = rng.nextGaussian();
                double ret = pointForecast[h] + volForecast[h] * z;
                eq *= (1 + ret);
                if (eq > peak) peak = eq;
                double dd = (peak - eq) / peak;
                if (dd > maxDD) maxDD = dd;
            }
            if (maxDD > worstDD) worstDD = maxDD;
        }
        return worstDD;
    }

    private double[] ewmaVolForecast(double[] series, int horizon, double lambda,
                                      double regimeMult) {
        int T = series.length;
        double[] vol = new double[T];
        vol[0] = series[0] * series[0];
        for (int t = 1; t < T; t++) {
            vol[t] = (1 - lambda) * series[t] * series[t] + lambda * vol[t - 1];
        }
        double lastVol = Math.sqrt(vol[T - 1]) * regimeMult;
        double longTermVol = std(series) * regimeMult;
        double[] forecast = new double[horizon];
        for (int h = 0; h < horizon; h++) {
            double weight = Math.pow(Defaults.FORECAST_VOL_DECAY, h + 1);
            forecast[h] = weight * lastVol + (1 - weight) * longTermVol;
        }
        return forecast;
    }

    private double[] extractColumn(MatrixR064 m, int col) {
        int rows = (int) m.countRows();
        double[] out = new double[rows];
        for (int i = 0; i < rows; i++) out[i] = m.get(i, col);
        return out;
    }

    private MatrixR064 sliceRows(MatrixR064 m, int from, int to) {
        int rows = to - from;
        int cols = (int) m.countColumns();
        double[][] data = new double[rows][cols];
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                data[r][c] = m.get(from + r, c);
        return MatrixR064.FACTORY.rows(data);
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

    public static double normalCDF(double x) {
        if (x < -8) return 0.0;
        if (x > 8)  return 1.0;
        double a1 =  0.254829592, a2 = -0.284496736, a3 =  1.421413741;
        double a4 = -1.453152027, a5 =  1.061405429, p  =  0.3275911;
        double sign = x < 0 ? -1 : 1;
        double t = 1.0 / (1.0 + p * Math.abs(x));
        double y = 1.0 - (((((a5*t + a4)*t) + a3)*t + a2)*t + a1) * t * Math.exp(-x*x/2);
        return 0.5 * (1.0 + sign * y);
    }

    private List<Double> toList(double[] arr) {
        List<Double> list = new ArrayList<>(arr.length);
        for (double v : arr) list.add(v);
        return list;
    }
}
