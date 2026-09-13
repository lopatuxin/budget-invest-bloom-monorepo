package pyc.lopatuxin.investment.service;

import java.time.LocalDate;

/**
 * The "received vs. upcoming" split and the "received date" rule shared by the portfolio page
 * and the security page, so the two never disagree on which bucket a dividend falls into.
 * Mirrors, in Java, the same predicates {@link pyc.lopatuxin.investment.repository.DividendRepository}
 * applies in SQL for {@code findByTickerInAndReceivedDateBetweenWithSecurity} (received) and
 * {@code findUpcomingByTickersWithSecurity} (upcoming) — needed here because
 * {@link pyc.lopatuxin.investment.service.SecurityPageService} classifies a single ticker's whole
 * dividend history in memory instead of issuing one query per bucket. Public (rather than
 * package-private) so {@link pyc.lopatuxin.investment.dto.response.UpcomingDividendDto#effectiveDate}
 * can delegate to the same rule instead of keeping its own copy.
 */
public final class DividendTiming {

    private DividendTiming() {
    }

    // "Received" — paymentDate when known, recordDate otherwise (plan point 20 of the security
    // page plan / PortfolioService.buildRecentDividends before this extraction).
    public static LocalDate receivedDate(LocalDate recordDate, LocalDate paymentDate) {
        return paymentDate != null ? paymentDate : recordDate;
    }

    // Mirrors the repository's "COALESCE(paymentDate, recordDate) < :today" received-window rule.
    public static boolean isReceived(LocalDate recordDate, LocalDate paymentDate, LocalDate today) {
        return receivedDate(recordDate, paymentDate).isBefore(today);
    }

    // Mirrors the repository's "recordDate >= :today OR paymentDate >= :today" upcoming rule.
    public static boolean isUpcoming(LocalDate recordDate, LocalDate paymentDate, LocalDate today) {
        return !recordDate.isBefore(today) || (paymentDate != null && !paymentDate.isBefore(today));
    }

    // The date an upcoming row is shown and sorted by: the record date while it has not passed
    // yet (labeled "отсечка"), the payment date once it has (labeled "выплата") — see
    // UpcomingDividendDto#effectiveDate, which delegates here, and PortfolioValuationAdapter,
    // which sorts upcoming dividends by the same rule.
    public static LocalDate upcomingEffectiveDate(LocalDate recordDate, LocalDate paymentDate, LocalDate today) {
        if (!recordDate.isBefore(today)) {
            return recordDate;
        }
        return paymentDate;
    }

    // Record date still ahead (or today) — nobody knows the future holding, so today's is used;
    // record date already passed and only payment is pending — the holding is already fixed, same
    // rule PortfolioService.upcomingQuantity and the security page's own upcoming events apply.
    public static boolean isRecordDateAhead(LocalDate recordDate, LocalDate today) {
        return !recordDate.isBefore(today);
    }
}
