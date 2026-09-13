package pyc.lopatuxin.investment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pyc.lopatuxin.investment.entity.enums.PayoutKind;
import pyc.lopatuxin.investment.service.DividendTiming;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpcomingDividendDto {

    private String ticker;
    private String securityName;
    private LocalDate recordDate;
    private LocalDate paymentDate;
    private BigDecimal amountPerShare;
    private BigDecimal quantity;
    private BigDecimal totalAmount;
    private String currency;
    private PayoutKind kind;

    // The date this row is shown and sorted by: the record date while it has not passed yet
    // (labeled "отсечка"), the payment date once it has (labeled "выплата") — see
    // PortfolioService.buildUpcomingDividends and PortfolioValuationAdapter.findNextDividend,
    // both of which pick the soonest upcoming dividend by this same rule (plan point 21).
    // Takes "today" as an argument instead of calling LocalDate.now() itself, so a whole page
    // build (which can call this once per comparison in a sort) judges every row against the
    // same "today" rather than risking a different one for each call around a midnight rollover
    // (plan point 3). Delegates to DividendTiming so the security page's own upcoming events use
    // the identical rule instead of a second copy of it.
    public LocalDate effectiveDate(LocalDate today) {
        return DividendTiming.upcomingEffectiveDate(recordDate, paymentDate, today);
    }
}
