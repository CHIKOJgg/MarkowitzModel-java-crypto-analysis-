package org.example.data;

import org.ojalgo.matrix.MatrixR064;
import java.util.List;

/**
 * Unified source of market returns.
 * Implementations may fetch from REST APIs, local CSV, databases, etc.
 */
public interface MarketDataProvider {

    /**
     * Return a [days × assets] matrix of daily simple returns,
     * columns ordered to match {@code assets}.
     */
    MatrixR064 getReturns(List<String> assets);
}
