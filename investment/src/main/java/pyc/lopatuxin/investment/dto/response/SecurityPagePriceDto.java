package pyc.lopatuxin.investment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Exchange snapshot for the security page header (plan point 2) — same shape as the numbers
 * {@link pyc.lopatuxin.investment.service.PortfolioGroupingService} derives for a position on the
 * portfolio page. Absent ({@code null}) when {@link pyc.lopatuxin.investment.service.market.MarketDataService}
 * has no last price for the ticker.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityPagePriceDto {

    private BigDecimal current;
    private BigDecimal previousClose;
    private BigDecimal dailyChangePercent;
    private Instant asOf;
    private boolean stale;
}
