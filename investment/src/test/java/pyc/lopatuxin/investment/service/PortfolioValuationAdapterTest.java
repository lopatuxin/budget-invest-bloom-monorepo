package pyc.lopatuxin.investment.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.investment.dto.response.PortfolioSummaryDto;
import pyc.lopatuxin.investment.dto.response.UpcomingDividendDto;
import pyc.lopatuxin.shared.port.PortfolioCurrentValuation;
import pyc.lopatuxin.shared.port.PortfolioNextDividend;
import pyc.lopatuxin.shared.port.PortfolioValueAt;
import pyc.lopatuxin.shared.port.PortfolioValueSeries;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PortfolioValuationAdapterTest")
class PortfolioValuationAdapterTest {

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private AnalyticsService analyticsService;

    @InjectMocks
    private PortfolioValuationAdapter adapter;

    private UUID userId;

    // Stand-in for the "today" PortfolioService would have used to build upcomingDividends —
    // any fixed date earlier than every dividend fixture's recordDate below works, since these
    // tests are not about the date boundary itself (see
    // current_recordDatesPastRealClock_usesSummaryTodayNotRealNow_noNpe below for that).
    private static final LocalDate SUMMARY_TODAY = LocalDate.of(2026, 1, 1);

    @Test
    @DisplayName("current — совпадает с getPortfolioSummary по стоимости, себестоимости, прибыли, числу бумаг и дивидендам")
    void current_matchesGetPortfolioSummary() {
        userId = UUID.randomUUID();
        UpcomingDividendDto later = buildDividend("SBER", "Сбербанк", LocalDate.of(2026, 11, 1), new BigDecimal("1000.00"));
        UpcomingDividendDto earliest = buildDividend("LKOH", "ЛУКОЙЛ", LocalDate.of(2026, 10, 3), new BigDecimal("4800.00"));
        PortfolioSummaryDto summary = new PortfolioSummaryDto(
                new BigDecimal("865400.00"), new BigDecimal("770100.00"), new BigDecimal("95300.00"),
                19, new BigDecimal("38200.00"), List.of(later, earliest), SUMMARY_TODAY);
        when(portfolioService.getPortfolioSummary(userId)).thenReturn(summary);

        PortfolioCurrentValuation result = adapter.current(userId);

        assertThat(result.totalValue()).isEqualByComparingTo("865400.00");
        assertThat(result.totalCost()).isEqualByComparingTo("770100.00");
        assertThat(result.totalPnl()).isEqualByComparingTo("95300.00");
        assertThat(result.assetsCount()).isEqualTo(19);
        assertThat(result.dividends12m()).isEqualByComparingTo("38200.00");
        assertThat(result.nextDividend()).isEqualTo(
                new PortfolioNextDividend("LKOH", "ЛУКОЙЛ", LocalDate.of(2026, 10, 3), null, new BigDecimal("4800.00"), "RUB", null));
    }

    @Test
    @DisplayName("current — валюта ближайшего дивиденда пробрасывается в порт")
    void current_nextDividendCurrency_isPropagated() {
        userId = UUID.randomUUID();
        UpcomingDividendDto dividend = UpcomingDividendDto.builder()
                .ticker("AAPL").securityName("Apple")
                .recordDate(LocalDate.of(2026, 10, 3))
                .totalAmount(new BigDecimal("20.00"))
                .currency("USD").build();
        PortfolioSummaryDto summary = new PortfolioSummaryDto(
                new BigDecimal("865400.00"), new BigDecimal("770100.00"), new BigDecimal("95300.00"),
                19, new BigDecimal("38200.00"), List.of(dividend), SUMMARY_TODAY);
        when(portfolioService.getPortfolioSummary(userId)).thenReturn(summary);

        PortfolioCurrentValuation result = adapter.current(userId);

        assertThat(result.nextDividend().currency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("current — у ближайшего дивиденда есть дата выплаты → пробрасывается в порт")
    void current_nextDividendHasPaymentDate_isPropagated() {
        userId = UUID.randomUUID();
        UpcomingDividendDto dividend = UpcomingDividendDto.builder()
                .ticker("LKOH").securityName("ЛУКОЙЛ")
                .recordDate(LocalDate.of(2026, 10, 3)).paymentDate(LocalDate.of(2026, 10, 20))
                .totalAmount(new BigDecimal("4800.00")).build();
        PortfolioSummaryDto summary = new PortfolioSummaryDto(
                new BigDecimal("865400.00"), new BigDecimal("770100.00"), new BigDecimal("95300.00"),
                19, new BigDecimal("38200.00"), List.of(dividend), SUMMARY_TODAY);
        when(portfolioService.getPortfolioSummary(userId)).thenReturn(summary);

        PortfolioCurrentValuation result = adapter.current(userId);

        assertThat(result.nextDividend().paymentDate()).isEqualTo(LocalDate.of(2026, 10, 20));
    }

    @Test
    @DisplayName("current — recordDate обеих выплат уже в прошлом относительно реальных часов → используется today из summary, а не LocalDate.now(), без NPE")
    void current_recordDatesPastRealClock_usesSummaryTodayNotRealNow_noNpe() {
        // Reproduces the midnight-rollover bug: PortfolioService computes its own "today" (here
        // stood in for by a date far in the past relative to the real clock) and only includes
        // dividends whose effectiveDate(that today) is non-null. If the adapter recomputed
        // LocalDate.now() itself instead of reusing summary.today(), both entries below (no
        // paymentDate, recordDate before the real "now") would resolve effectiveDate(realNow) to
        // null and NPE in the comparator.
        userId = UUID.randomUUID();
        LocalDate summaryToday = LocalDate.of(2020, 1, 10);
        UpcomingDividendDto earliest = buildDividend("LKOH", "ЛУКОЙЛ", summaryToday, new BigDecimal("4800.00"));
        UpcomingDividendDto later = buildDividend("SBER", "Сбербанк", summaryToday.plusDays(5), new BigDecimal("1000.00"));
        PortfolioSummaryDto summary = new PortfolioSummaryDto(
                new BigDecimal("865400.00"), new BigDecimal("770100.00"), new BigDecimal("95300.00"),
                19, new BigDecimal("38200.00"), List.of(later, earliest), summaryToday);
        when(portfolioService.getPortfolioSummary(userId)).thenReturn(summary);

        PortfolioCurrentValuation result = adapter.current(userId);

        assertThat(result.nextDividend().ticker()).isEqualTo("LKOH");
    }

    @Test
    @DisplayName("current — две и более предстоящих выплат: выбирается ближайшая по дате отсечки, без NPE")
    void current_multipleUpcomingDividends_picksEarliestByRecordDate() {
        userId = UUID.randomUUID();
        UpcomingDividendDto later = buildDividend("SBER", "Сбербанк", LocalDate.of(2026, 11, 1), new BigDecimal("1000.00"));
        UpcomingDividendDto earliest = buildDividend("LKOH", "ЛУКОЙЛ", LocalDate.of(2026, 10, 3), new BigDecimal("4800.00"));
        UpcomingDividendDto middle = buildDividend("GAZP", "Газпром", LocalDate.of(2026, 10, 20), new BigDecimal("2000.00"));
        PortfolioSummaryDto summary = new PortfolioSummaryDto(
                new BigDecimal("865400.00"), new BigDecimal("770100.00"), new BigDecimal("95300.00"),
                19, new BigDecimal("38200.00"), List.of(later, earliest, middle), SUMMARY_TODAY);
        when(portfolioService.getPortfolioSummary(userId)).thenReturn(summary);

        PortfolioCurrentValuation result = adapter.current(userId);

        assertThat(result.nextDividend()).isEqualTo(
                new PortfolioNextDividend("LKOH", "ЛУКОЙЛ", LocalDate.of(2026, 10, 3), null, new BigDecimal("4800.00"), "RUB", null));
    }

    @Test
    @DisplayName("current — без позиций и без предстоящих дивидендов: nextDividend = null")
    void current_noPositions_nextDividendNull() {
        userId = UUID.randomUUID();
        PortfolioSummaryDto summary = new PortfolioSummaryDto(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO, List.of(), SUMMARY_TODAY);
        when(portfolioService.getPortfolioSummary(userId)).thenReturn(summary);

        PortfolioCurrentValuation result = adapter.current(userId);

        assertThat(result.totalValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.assetsCount()).isZero();
        assertThat(result.nextDividend()).isNull();
    }

    @Test
    @DisplayName("current — totalAmount уже после налога и по количеству на нужную дату — адаптер передаёт его как есть, не пересчитывая")
    void current_nextDividendTotalAmount_alreadyNetOfTax_passedThroughUnchanged() {
        // PortfolioService.getPortfolioSummary already applies the quantity-on-record-date rule
        // and the tax deduction (see PortfolioService/DividendTaxCalculator) before this DTO
        // reaches the adapter — this test only pins down that the adapter does not touch the
        // number again (no second tax subtraction, no re-derivation of quantity).
        userId = UUID.randomUUID();
        UpcomingDividendDto netDividend = UpcomingDividendDto.builder()
                .ticker("VTBR").securityName("ВТБ")
                .recordDate(LocalDate.of(2026, 10, 3))
                .amountPerShare(new BigDecimal("9.71"))
                .quantity(new BigDecimal("33"))
                .totalAmount(new BigDecimal("279.43"))
                .currency("RUB").build();
        PortfolioSummaryDto summary = new PortfolioSummaryDto(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 1, BigDecimal.ZERO, List.of(netDividend), SUMMARY_TODAY);
        when(portfolioService.getPortfolioSummary(userId)).thenReturn(summary);

        PortfolioCurrentValuation result = adapter.current(userId);

        assertThat(result.nextDividend().totalAmount()).isEqualByComparingTo("279.43");
    }

    @Test
    @DisplayName("valueAt — делегирует в AnalyticsService.valueAtDates и возвращает его результат как есть")
    void valueAt_delegatesToAnalyticsService() {
        userId = UUID.randomUUID();
        List<LocalDate> dates = List.of(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 6));
        PortfolioValueSeries expected = new PortfolioValueSeries(
                List.of(new PortfolioValueAt(dates.get(0), new BigDecimal("685400.00")),
                        new PortfolioValueAt(dates.get(1), new BigDecimal("865400.00"))),
                true, false, List.of());
        when(analyticsService.valueAtDates(userId, dates)).thenReturn(expected);

        PortfolioValueSeries result = adapter.valueAt(userId, dates);

        assertThat(result).isSameAs(expected);
        verify(analyticsService).valueAtDates(userId, dates);
    }

    private UpcomingDividendDto buildDividend(String ticker, String securityName, LocalDate recordDate, BigDecimal totalAmount) {
        return UpcomingDividendDto.builder()
                .ticker(ticker)
                .securityName(securityName)
                .recordDate(recordDate)
                .totalAmount(totalAmount)
                .currency("RUB")
                .build();
    }
}
