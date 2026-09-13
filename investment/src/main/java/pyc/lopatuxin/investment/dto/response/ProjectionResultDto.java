package pyc.lopatuxin.investment.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ProjectionResultDto {
    private BigDecimal startValue;
    private BigDecimal portfolioWeightedAnnualReturn;
    private BigDecimal monthlyReturn;
    private List<ProjectionPointDto> series;
    private List<String> pendingHistoryTickers;
    // Last point's contributed (see ProjectionPointDto.contributed) and earned = last point's
    // value minus contributedTotal — the forecast page's "Через N лет"/"Заработано" tiles.
    private BigDecimal contributedTotal;
    private BigDecimal earned;
    // Portfolio-weighted split of portfolioWeightedAnnualReturn into price growth and payout
    // yield (plan point 13) — percent, scale 1, and priceGrowthPercent + payoutYieldPercent
    // always equals portfolioWeightedAnnualReturn * 100 rounded the same way (see ProjectionService).
    private BigDecimal priceGrowthPercent;
    private BigDecimal payoutYieldPercent;
    private List<ProjectionBreakdownItemDto> breakdown;
}
