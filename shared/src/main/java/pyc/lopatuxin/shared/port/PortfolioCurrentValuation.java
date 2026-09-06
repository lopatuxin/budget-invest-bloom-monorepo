package pyc.lopatuxin.shared.port;

import java.math.BigDecimal;

/** Current portfolio state: value, cost basis, unrealized pnl, size, and dividends. */
public record PortfolioCurrentValuation(
        BigDecimal totalValue,
        BigDecimal totalCost,
        BigDecimal totalPnl,
        int assetsCount,
        BigDecimal dividends12m,
        PortfolioNextDividend nextDividend
) {
}
