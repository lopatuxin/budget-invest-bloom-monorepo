package pyc.lopatuxin.shared.port;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The earliest upcoming dividend payment across the portfolio, or absent when none is scheduled.
 * {@code recordDate} is the registry closing (record) date; {@code paymentDate} is the date the
 * money actually arrives, when the source (T-Invest) provided one — it may be null.
 * {@code currency} is the ISO code (upper case, e.g. {@code RUB} or {@code USD}) {@code totalAmount}
 * is denominated in — not null. {@code totalAmount} is the amount actually credited to the
 * account: the flat-rate personal-income tax already withheld for RUB payouts (a foreign-currency
 * one is unchanged, that regime is out of scope) — see the investment module's DividendTaxCalculator.
 */
public record PortfolioNextDividend(
        String ticker,
        String securityName,
        LocalDate recordDate,
        LocalDate paymentDate,
        BigDecimal totalAmount,
        String currency
) {
}
