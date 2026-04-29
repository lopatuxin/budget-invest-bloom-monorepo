package pyc.lopatuxin.investment.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

@Data
public class ProjectionRequestDto {

    @Min(1) @Max(600)
    private int horizonMonths = 120;

    @DecimalMin("0")
    private BigDecimal monthlyDeposit = BigDecimal.ZERO;

    @DecimalMin("0") @DecimalMax("1")
    private BigDecimal withdrawalRatePerYear = BigDecimal.ZERO;

    @Min(1) @Max(20)
    private int lookbackYears = 3;

    // ticker -> annual return override (e.g. 0.12 for 12%)
    private Map<String, BigDecimal> overrides = Map.of();

    public Map<String, BigDecimal> getOverrides() {
        return overrides != null ? overrides : Collections.emptyMap();
    }
}
