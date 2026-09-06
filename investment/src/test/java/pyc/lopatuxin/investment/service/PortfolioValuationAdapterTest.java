package pyc.lopatuxin.investment.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.investment.dto.response.PortfolioOverviewDto;
import pyc.lopatuxin.investment.dto.response.PortfolioPageResponseDto;
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

    @Test
    @DisplayName("current — совпадает с getPortfolioPage по стоимости, себестоимости, прибыли, числу бумаг и дивидендам")
    void current_matchesGetPortfolioPage() {
        userId = UUID.randomUUID();
        PortfolioOverviewDto overview = PortfolioOverviewDto.builder()
                .totalValue(new BigDecimal("865400.00"))
                .totalCost(new BigDecimal("770100.00"))
                .totalPnl(new BigDecimal("95300.00"))
                .assetsCount(19)
                .dividends12m(new BigDecimal("38200.00"))
                .build();
        UpcomingDividendDto later = buildDividend("SBER", "Сбербанк", LocalDate.of(2026, 11, 1), new BigDecimal("1000.00"));
        UpcomingDividendDto earliest = buildDividend("LKOH", "ЛУКОЙЛ", LocalDate.of(2026, 10, 3), new BigDecimal("4800.00"));
        PortfolioPageResponseDto page = PortfolioPageResponseDto.builder()
                .overview(overview)
                .positions(List.of())
                .upcomingDividends(List.of(later, earliest))
                .build();
        when(portfolioService.getPortfolioPage(userId)).thenReturn(page);

        PortfolioCurrentValuation result = adapter.current(userId);

        assertThat(result.totalValue()).isEqualByComparingTo("865400.00");
        assertThat(result.totalCost()).isEqualByComparingTo("770100.00");
        assertThat(result.totalPnl()).isEqualByComparingTo("95300.00");
        assertThat(result.assetsCount()).isEqualTo(19);
        assertThat(result.dividends12m()).isEqualByComparingTo("38200.00");
        assertThat(result.nextDividend()).isEqualTo(
                new PortfolioNextDividend("LKOH", "ЛУКОЙЛ", LocalDate.of(2026, 10, 3), new BigDecimal("4800.00")));
    }

    @Test
    @DisplayName("current — без позиций и без предстоящих дивидендов: nextDividend = null")
    void current_noPositions_nextDividendNull() {
        userId = UUID.randomUUID();
        PortfolioOverviewDto emptyOverview = PortfolioOverviewDto.builder()
                .totalValue(BigDecimal.ZERO)
                .totalCost(BigDecimal.ZERO)
                .totalPnl(BigDecimal.ZERO)
                .assetsCount(0)
                .dividends12m(BigDecimal.ZERO)
                .build();
        PortfolioPageResponseDto page = PortfolioPageResponseDto.builder()
                .overview(emptyOverview)
                .positions(List.of())
                .build();
        when(portfolioService.getPortfolioPage(userId)).thenReturn(page);

        PortfolioCurrentValuation result = adapter.current(userId);

        assertThat(result.totalValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.assetsCount()).isZero();
        assertThat(result.nextDividend()).isNull();
    }

    @Test
    @DisplayName("valueAt — делегирует в AnalyticsService.valueAtDates и возвращает его результат как есть")
    void valueAt_delegatesToAnalyticsService() {
        userId = UUID.randomUUID();
        List<LocalDate> dates = List.of(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 6));
        PortfolioValueSeries expected = new PortfolioValueSeries(
                List.of(new PortfolioValueAt(dates.get(0), new BigDecimal("685400.00")),
                        new PortfolioValueAt(dates.get(1), new BigDecimal("865400.00"))),
                true);
        when(analyticsService.valueAtDates(userId, dates)).thenReturn(expected);

        PortfolioValueSeries result = adapter.valueAt(userId, dates);

        assertThat(result).isSameAs(expected);
        verify(analyticsService).valueAtDates(userId, dates);
    }

    private UpcomingDividendDto buildDividend(String ticker, String securityName, LocalDate paymentDate, BigDecimal totalAmount) {
        return UpcomingDividendDto.builder()
                .ticker(ticker)
                .securityName(securityName)
                .paymentDate(paymentDate)
                .totalAmount(totalAmount)
                .build();
    }
}
