package pyc.lopatuxin.investment.dto.response;

import java.math.BigDecimal;

/**
 * The portfolio-level totals {@link pyc.lopatuxin.investment.service.PortfolioGroupingService}
 * derives from an enriched position list, without the groups/allocation breakdown — the light
 * path for callers (e.g. {@link pyc.lopatuxin.investment.service.PortfolioValuationAdapter})
 * that only need the totals, not the /investments page's nested structure.
 */
public record PortfolioTotals(
        BigDecimal totalValue,
        BigDecimal totalCost,
        BigDecimal totalPnl,
        BigDecimal totalPnlPercent,
        BigDecimal dailyPnl,
        BigDecimal dailyPnlPercent,
        int unpricedCount
) {
}
