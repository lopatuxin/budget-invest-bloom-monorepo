package pyc.lopatuxin.investment.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything {@link pyc.lopatuxin.investment.service.PortfolioGroupingService} derives
 * from a flat list of positions: the enriched flat list itself (same order as the input,
 * kept for the security page and older tests), the allocation breakdown and the nested
 * type/sector groups, plus the portfolio-level totals that feed {@link PortfolioOverviewDto}.
 */
public record PortfolioGroupingResult(
        List<PositionResponseDto> positions,
        PortfolioAllocationDto allocation,
        List<PositionGroupDto> groups,
        BigDecimal totalValue,
        BigDecimal totalCost,
        BigDecimal totalPnl,
        BigDecimal totalPnlPercent,
        BigDecimal dailyPnl,
        BigDecimal dailyPnlPercent,
        int assetsCount,
        int sectorsCount,
        int unpricedCount
) {
}
