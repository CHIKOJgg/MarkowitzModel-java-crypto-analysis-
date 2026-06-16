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

    // ── Forecast model tuning ─────────────────────────────────────────────
    /**
     * Maximum fraction of the raw historical daily mean that is carried
     * forward as forward-looking drift. Historical daily means are extremely
     * noisy (their standard error is often larger than the mean itself), so
     * naively projecting them forward for a 90-day horizon produces absurd
     * cumulative numbers (e.g. +365% annualized). This caps how much of the
     * estimate we "believe" going forward; the actual weight applied is
     * scaled further by the signal-to-noise ratio of the mean estimate.
     */
    private static final double DRIFT_SHRINKAGE_MAX = 0.35;

    /** Weight of short-term momentum (recent vs. long-term mean) in the drift blend. */
    private static final double MOMENTUM_WEIGHT = 0.3;

    /** Speed at which the near-term drift converges toward the shrunk long-run drift. */
    private static final double MEAN_REV_SPEED = 0.05;

    /** Multiple of daily vol used to clamp the (already shrunk) per-day point forecast. */
    private static final double DAILY_MOVE_CAP_MULT = 2.0;

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

        // ── Shrink the drift estimates ──────────────────────────────────────
        // Standard error of the sample mean: SE = sigma / sqrt(T).
        // Signal-to-noise ratio in [0,1): how much of mu is "signal" vs noise.
        double seMu = sigma / Math.sqrt(Math.max(T, 1));
        double snr  = (mu * mu) / (mu * mu + seMu * seMu + 1e-18);
        double driftShrink = DRIFT_SHRINKAGE_MAX * snr;

        double muAdj       = mu * driftShrink;
        double momentumAdj = momentum * MOMENTUM_WEIGHT;

        double alpha = Math.min(0.4, Math.max(0.05, 2.0 / (lookback + 1)));
        double ewma = series[0];
        double ewmaVar = series[0] * series[0];
        for (int i = 1; i < T; i++) {
            ewma = alpha * series[i] + (1 - alpha) * ewma;
            ewmaVar = alpha * series[i] * series[i] + (1 - alpha) * ewmaVar;
        }
        double ewmaAdj = ewma * driftShrink;

        double dailyVol = Math.sqrt(Math.max(ewmaVar, 1e-12));
        double dailyCap = Math.max(dailyVol, sigma) * DAILY_MOVE_CAP_MULT;
        // Keep well away from -100% per day so log() below stays finite/sane.
        dailyCap = Math.min(dailyCap, 0.5);

        // Near-term drift: blend shrunk EWMA with shrunk momentum, then mean-revert
        // toward the shrunk long-run drift (muAdj) over the forecast horizon.
        double nearTermDrift = 0.5 * ewmaAdj + 0.5 * momentumAdj;

        double[] pointForecast = new double[horizon];
        for (int h = 0; h < horizon; h++) {
            double weight = Math.exp(-MEAN_REV_SPEED * (h + 1));
            double raw = (1 - weight) * muAdj + weight * nearTermDrift;
            pointForecast[h] = Math.max(-dailyCap, Math.min(dailyCap, raw));
        }

        double[] volForecast = ewmaVolForecast(series, horizon, Defaults.EWMA_LAMBDA, regimeVolMult);

        // ── Cumulative (log-space) path & confidence intervals ──────────────
        // Aggregating drift and variance in log-space keeps the cumulative
        // return distribution internally consistent: lower bounds can never
        // imply a loss worse than -100%, and the CI at day h correctly
        // reflects h days of accumulated drift + variance (not a 1-day point
        // forecast mixed with an h-day volatility, as before).
        double[] cumLogReturn = new double[horizon];
        double[] cumVar       = new double[horizon];
        double runningLog = 0;
        double runningVar = 0;
        for (int h = 0; h < horizon; h++) {
            runningLog += Math.log1p(pointForecast[h]);
            runningVar += volForecast[h] * volForecast[h];
            cumLogReturn[h] = runningLog;
            cumVar[h] = runningVar;
        }

        double[] lower95 = new double[horizon];
        double[] upper95 = new double[horizon];
        double[] lower50 = new double[horizon];
        double[] upper50 = new double[horizon];

        for (int h = 0; h < horizon; h++) {
            double sd = Math.sqrt(cumVar[h]);
            lower95[h] = Math.exp(cumLogReturn[h] - confidence95 * sd) - 1.0;
            upper95[h] = Math.exp(cumLogReturn[h] + confidence95 * sd) - 1.0;
            lower50[h] = Math.exp(cumLogReturn[h] - confidence50 * sd) - 1.0;
            upper50[h] = Math.exp(cumLogReturn[h] + confidence50 * sd) - 1.0;
        }

        // ── Historical (backward-looking) descriptive stats ─────────────────
        double annReturn = mu * Defaults.TRADING_DAYS_PER_YEAR;
        double annVol    = sigma * Math.sqrt(Defaults.TRADING_DAYS_PER_YEAR);
        double[] acf = autocorrelation(series, Math.min(20, T / 3));
        double trend = acf.length > 1 ? acf[1] : 0;

        // ── Forward-looking metrics (derived from the shrunk forecast) ──────
        double cumReturn   = Math.exp(cumLogReturn[horizon - 1]) - 1.0; // geometric, day "horizon"
        double cumVolFinal = Math.sqrt(cumVar[horizon - 1]);

        // forecastSharpe uses the *shrunk* forward drift, annualized arithmetically,
        // against the (reliable) historical annualized volatility.
        double forwardAnnReturn = muAdj * Defaults.TRADING_DAYS_PER_YEAR;
        double forecastSharpe = annVol > 1e-12 ? forwardAnnReturn / annVol : 0.0;

        Signal signal = classifySignal(cumLogReturn[horizon - 1], cumVolFinal, momentumAdj, dailyVol);
        RiskLevel riskLevel = classifyRisk(annVol);

        double expRangeLow  = lower95[horizon - 1];
        double expRangeHigh = upper95[horizon - 1];

        // P(cumulative simple return < 0) under the log-normal approximation.
        double probLoss = cumVolFinal > 1e-12
                ? normalCDF(-cumLogReturn[horizon - 1] / cumVolFinal)
                : (cumLogReturn[horizon - 1] < 0 ? 1.0 : 0.0);

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

    /**
     * Classifies the directional signal based on the *cumulative* forecast
     * (log-return over the full horizon) relative to its cumulative
     * uncertainty, with a secondary boost/penalty from short-term momentum.
     */
    private Signal classifySignal(double cumLogReturn, double cumVol,
                                  double momentumAdj, double dailyVol) {
        double tStat = cumVol > 1e-12 ? cumLogReturn / cumVol : 0;
        double momentumScore = momentumAdj / (dailyVol + 1e-12);
        double composite = 0.7 * tStat + 0.3 * momentumScore;

        if (composite > 1.0)       return Signal.STRONG_BUY;
        else if (composite > 0.35) return Signal.BUY;
        else if (composite > -0.35) return Signal.HOLD;
        else if (composite > -1.0) return Signal.SELL;
        else                        return Signal.STRONG_SELL;
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
                ret = Math.max(ret, -0.99); // never let a single step wipe out >99%
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