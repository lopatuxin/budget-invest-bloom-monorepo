package pyc.lopatuxin.shared.port;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The earliest upcoming dividend payment across the portfolio, or absent when none is scheduled. */
public record PortfolioNextDividend(
        String ticker,
        String securityName,
        LocalDate paymentDate,
        BigDecimal totalAmount
) {
}
