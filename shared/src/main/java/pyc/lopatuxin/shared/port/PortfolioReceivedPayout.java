package pyc.lopatuxin.shared.port;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One received dividend or coupon payout, RUB only and net of tax — the unit the "capital" page
 * sums per date to fold payout money (not a budget record) into free money and its history.
 */
public record PortfolioReceivedPayout(
        LocalDate receivedDate,
        BigDecimal netAmountRub
) {
}
