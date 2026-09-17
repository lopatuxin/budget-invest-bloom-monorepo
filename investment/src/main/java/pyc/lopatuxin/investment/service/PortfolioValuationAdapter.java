package pyc.lopatuxin.investment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pyc.lopatuxin.investment.dto.response.PortfolioSummaryDto;
import pyc.lopatuxin.investment.dto.response.UpcomingDividendDto;
import pyc.lopatuxin.shared.port.PortfolioCurrentValuation;
import pyc.lopatuxin.shared.port.PortfolioNextDividend;
import pyc.lopatuxin.shared.port.PortfolioReceivedPayout;
import pyc.lopatuxin.shared.port.PortfolioValuation;
import pyc.lopatuxin.shared.port.PortfolioValueSeries;
import pyc.lopatuxin.shared.port.PayoutKind;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * In-process implementation of the {@link PortfolioValuation} port on the investment side.
 * {@link #current} reads the same underlying data as the investment page, through the light
 * summary path ({@link PortfolioService#getPortfolioSummary}) that skips the groups/allocation/
 * recent-transactions assembly the "capital" page port does not need.
 * {@link #valueAt} delegates to {@link AnalyticsService#valueAtDates}.
 * Not wrapped in a transaction: {@link PortfolioService#getPortfolioSummary} hits the exchange
 * over the network before it is done with the database, so a transaction spanning this whole
 * adapter method would hold a Hikari connection for as long as MOEX takes to answer.
 */
@Component
@RequiredArgsConstructor
public class PortfolioValuationAdapter implements PortfolioValuation {

    private final PortfolioService portfolioService;
    private final AnalyticsService analyticsService;

    @Override
    public PortfolioCurrentValuation current(UUID userId) {
        PortfolioSummaryDto summary = portfolioService.getPortfolioSummary(userId);
        // Reuses the "today" PortfolioService already used to decide which dividends belong in
        // upcomingDividends (plan point 3), instead of calling LocalDate.now() again here: a
        // second, later "today" could disagree with the first across a midnight rollover between
        // the two calls, making effectiveDate(today) return null for an entry whose recordDate is
        // yesterday relative to the new "today" and has no paymentDate — NPE in the comparator
        // below.
        return new PortfolioCurrentValuation(
                summary.totalValue(),
                summary.totalCost(),
                summary.totalPnl(),
                summary.assetsCount(),
                summary.dividends12m(),
                findNextDividend(summary.upcomingDividends(), summary.today())
        );
    }

    @Override
    public PortfolioValueSeries valueAt(UUID userId, List<LocalDate> dates) {
        return analyticsService.valueAtDates(userId, dates);
    }

    @Override
    public List<PortfolioReceivedPayout> receivedPayouts(UUID userId) {
        return portfolioService.getReceivedPayouts(userId).stream()
                .map(d -> new PortfolioReceivedPayout(
                        DividendTiming.receivedDate(d.getRecordDate(), d.getPaymentDate()), d.getTotalAmount()))
                .toList();
    }

    private PortfolioNextDividend findNextDividend(List<UpcomingDividendDto> upcomingDividends, LocalDate today) {
        if (upcomingDividends == null || upcomingDividends.isEmpty()) {
            return null;
        }
        // Same "soonest by record date, falling back to payment date" rule PortfolioService
        // itself sorts upcomingDividends by (plan point 21) — recomputed here rather than
        // relying on the caller's list order, so this adapter's result does not depend on it.
        UpcomingDividendDto earliest = upcomingDividends.stream()
                .min(Comparator.comparing(d -> d.effectiveDate(today)))
                .orElseThrow();
        return new PortfolioNextDividend(
                earliest.getTicker(),
                earliest.getSecurityName(),
                earliest.getRecordDate(),
                earliest.getPaymentDate(),
                earliest.getTotalAmount(),
                earliest.getCurrency(),
                toPortSharedKind(earliest.getKind())
        );
    }

    // investment's own entity.enums.PayoutKind cannot cross into shared (module boundary rule —
    // shared must not depend on investment), so the port carries its own mirror enum with the
    // same two values; this is the one place that translates between them.
    private PayoutKind toPortSharedKind(pyc.lopatuxin.investment.entity.enums.PayoutKind kind) {
        if (kind == null) {
            return null;
        }
        return kind == pyc.lopatuxin.investment.entity.enums.PayoutKind.COUPON ? PayoutKind.COUPON : PayoutKind.DIVIDEND;
    }
}
