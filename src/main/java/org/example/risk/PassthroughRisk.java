package org.example.risk;

import org.ojalgo.matrix.MatrixR064;
import java.math.BigDecimal;
import java.util.List;

/** No-op risk model — passes weights through unchanged. */
public class PassthroughRisk implements RiskModel {

    @Override
    public List<BigDecimal> adjust(List<BigDecimal> weights, MatrixR064 returns) {
        return weights;
    }

    @Override
    public String describe() { return "None (Passthrough)"; }
}
