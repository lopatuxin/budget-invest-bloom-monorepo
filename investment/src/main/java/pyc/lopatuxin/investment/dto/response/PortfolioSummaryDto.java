package pyc.lopatuxin.investment.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Lightweight portfolio snapshot for {@link pyc.lopatuxin.investment.service.PortfolioValuationAdapter}:
 * just the totals and upcoming dividends the "capital" page port needs, without the
 * groups/allocation/recent transactions the full /investments page ({@link PortfolioPageResponseDto})
 * builds.
 * {@code today} is the date {@link pyc.lopatuxin.investment.service.PortfolioService} used to
 * build {@code upcomingDividends} — the caller reuses it instead of computing its own, so a
 * midnight rollover between the two calls cannot make {@link UpcomingDividendDto#effectiveDate}
 * disagree with the filter that decided which dividends are even in this list.
 */
public record PortfolioSummaryDto(
        BigDecimal totalValue,
        BigDecimal totalCost,
        BigDecimal totalPnl,
        int assetsCount,
        BigDecimal dividends12m,
        List<UpcomingDividendDto> upcomingDividends,
        LocalDate today
) {
}
