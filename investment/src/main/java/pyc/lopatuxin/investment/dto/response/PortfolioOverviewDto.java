package pyc.lopatuxin.investment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioOverviewDto {

    private BigDecimal totalValue;
    private BigDecimal totalCost;
    private BigDecimal totalPnl;
    private BigDecimal totalPnlPercent;
    private BigDecimal dailyPnl;
    private BigDecimal dailyPnlPercent;
    private int assetsCount;
    private int sectorsCount;
    private BigDecimal dividends12m;
    private BigDecimal dividendYieldPercent;
    private BigDecimal dividendTaxRatePercent;
    private Instant pricesAsOf;
    private boolean pricesStale;
    private int unpricedCount;
    private boolean dividendsSourceConfigured;
}
