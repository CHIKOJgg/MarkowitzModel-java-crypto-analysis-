package org.example.engine;

/**
 * Immutable result of a parameter sweep produced by {@link ParameterSensitivity}.
 *
 * @param paramName     name of the swept parameter
 * @param paramValues   the tested parameter values
 * @param sharpeRatios  Sharpe ratio for each parameter value
 * @param maxDrawdowns  maximum drawdown for each parameter value
 * @param finalEquities final equity for each parameter value
 */
public record SensitivityResult(
        String paramName,
        double[] paramValues,
        double[] sharpeRatios,
        double[] maxDrawdowns,
        double[] finalEquities
) {}
