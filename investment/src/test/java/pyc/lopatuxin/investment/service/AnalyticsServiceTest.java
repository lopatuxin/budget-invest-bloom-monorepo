package pyc.lopatuxin.investment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.investment.dto.response.PortfolioValuePointDto;
import pyc.lopatuxin.investment.dto.response.PricePointDto;
import pyc.lopatuxin.investment.dto.response.SeriesResponseDto;
import pyc.lopatuxin.investment.entity.Position;
import pyc.lopatuxin.investment.entity.PriceHistory;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.Transaction;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.entity.enums.TransactionType;
import pyc.lopatuxin.investment.repository.DividendRepository;
import pyc.lopatuxin.investment.repository.PositionRepository;
import pyc.lopatuxin.investment.repository.PriceHistoryRepository;
import pyc.lopatuxin.investment.repository.TransactionRepository;
import pyc.lopatuxin.investment.service.market.MarketDataService;
import pyc.lopatuxin.shared.port.PortfolioValueAt;
import pyc.lopatuxin.shared.port.PortfolioValueSeries;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsServiceTest")
class AnalyticsServiceTest {

    @Mock
    private PriceHistoryRepository priceHistoryRepository;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private MarketDataService marketDataService;

    @Mock
    private DividendRepository dividendRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    private UUID userId;
    private Security security;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        security = Security.builder()
                .ticker("SBER")
                .name("Сбербанк")
                .type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.READY)
                .build();
    }

    @Test
    @DisplayName("portfolioValueHistory — одна позиция 10 акций, 5 дней истории: value = qty × close каждый день")
    void portfolioValueHistory_singleBuy_correctValue() {
        Position position = buildPosition("SBER", "10");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));

        LocalDate base = LocalDate.of(2024, 1, 15);
        List<PriceHistory> history = buildHistoryDays("SBER", base, 5, "270.00");
        when(priceHistoryRepository.findByTickerInAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any()))
                .thenReturn(history);

        LocalDate from = base;
        LocalDate to = base.plusDays(4);
        SeriesResponseDto<PortfolioValuePointDto> result = analyticsService.portfolioValueHistory(userId, from, to);

        assertThat(result.getSeries()).hasSize(5);
        result.getSeries().forEach(p -> assertThat(p.getValue()).isEqualByComparingTo(new BigDecimal("2700.00")));
    }

    @Test
    @DisplayName("portfolioValueHistory — позиция 5 акций (после частичной продажи): value = 5 × close")
    void portfolioValueHistory_buyThenSell_quantityDecreases() {
        // After a BUY 10 + SELL 5, TransactionService stores qty=5 in Position.
        // AnalyticsService reads current position qty from positionRepository.
        Position position = buildPosition("SBER", "5");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));

        LocalDate day1 = LocalDate.of(2024, 1, 1);
        LocalDate day2 = LocalDate.of(2024, 1, 2);
        List<PriceHistory> history = List.of(
                buildPriceHistory("SBER", day1, "270.00"),
                buildPriceHistory("SBER", day2, "280.00")
        );
        when(priceHistoryRepository.findByTickerInAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any()))
                .thenReturn(history);

        SeriesResponseDto<PortfolioValuePointDto> result = analyticsService.portfolioValueHistory(userId, day1, day2);

        assertThat(result.getSeries()).hasSize(2);
        // 5 × 270 = 1350
        assertThat(result.getSeries().get(0).getValue()).isEqualByComparingTo(new BigDecimal("1350.00"));
        // 5 × 280 = 1400
        assertThat(result.getSeries().get(1).getValue()).isEqualByComparingTo(new BigDecimal("1400.00"));
    }

    @Test
    @DisplayName("portfolioValueHistory — позиции есть, история пустая, возвращает пустой список")
    void portfolioValueHistory_emptyHistory_returnsEmpty() {
        Position position = buildPosition("SBER", "10");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));
        when(priceHistoryRepository.findByTickerInAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any()))
                .thenReturn(List.of());

        SeriesResponseDto<PortfolioValuePointDto> result = analyticsService.portfolioValueHistory(
                userId, LocalDate.now().minusYears(1), LocalDate.now());

        assertThat(result.getSeries()).isEmpty();
    }

    @Test
    @DisplayName("portfolioValueHistory — forward fill: тикер без цены на день использует последнюю известную")
    void portfolioValueHistory_forwardFill() {
        Position position = buildPosition("SBER", "10");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));

        LocalDate day1 = LocalDate.of(2024, 1, 15);
        LocalDate day2 = LocalDate.of(2024, 1, 16);
        // day2 has no price — only day1 present in DB result
        List<PriceHistory> history = List.of(buildPriceHistory("SBER", day1, "270.00"));
        when(priceHistoryRepository.findByTickerInAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any()))
                .thenReturn(history);

        SeriesResponseDto<PortfolioValuePointDto> result = analyticsService.portfolioValueHistory(userId, day1, day2);

        // Only day1 has data in history, so only 1 point is produced
        assertThat(result.getSeries()).hasSize(1);
        assertThat(result.getSeries().get(0).getValue()).isEqualByComparingTo(new BigDecimal("2700.00"));
    }

    @Test
    @DisplayName("securityPriceHistory — 3 PriceHistory маппируются в 3 PricePointDto с корректными полями")
    void securityPriceHistory_mapsToPricePointDto() {
        when(marketDataService.getSecurityHistoryStatus("SBER")).thenReturn(HistoryStatus.READY);

        LocalDate base = LocalDate.of(2024, 1, 15);
        List<PriceHistory> history = buildHistoryDays("SBER", base, 3, "271.00");
        when(priceHistoryRepository.findByTickerAndTradeDateBetweenOrderByTradeDateAsc(
                anyString(), any(), any())).thenReturn(history);

        SeriesResponseDto<PricePointDto> result = analyticsService.securityPriceHistory("SBER", base, base.plusDays(2));

        assertThat(result.getSeries()).hasSize(3);
        assertThat(result.getSeries().get(0).getDate()).isEqualTo(base);
        assertThat(result.getSeries().get(0).getClose()).isEqualByComparingTo(new BigDecimal("271.00"));
        assertThat(result.getSeries().get(0).getOpen()).isEqualByComparingTo(new BigDecimal("270.00"));
        assertThat(result.getSeries().get(0).getVolume()).isEqualTo(11000L);
    }

    @Test
    @DisplayName("portfolioValueHistory — позиция с qty=0: точка не добавляется в серию")
    void portfolioValueHistory_zeroQuantity_pointNotAdded() {
        Position position = buildPosition("SBER", "0");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));

        LocalDate day = LocalDate.of(2024, 3, 1);
        List<PriceHistory> history = List.of(buildPriceHistory("SBER", day, "270.00"));
        when(priceHistoryRepository.findByTickerInAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any()))
                .thenReturn(history);

        SeriesResponseDto<PortfolioValuePointDto> result = analyticsService.portfolioValueHistory(userId, day, day);

        assertThat(result.getSeries()).isEmpty();
    }

    @Test
    @DisplayName("portfolioValueHistory — позиция с qty<0: точка не добавляется в серию")
    void portfolioValueHistory_negativeQuantity_pointNotAdded() {
        Position position = buildPosition("SBER", "-5");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));

        LocalDate day = LocalDate.of(2024, 3, 1);
        List<PriceHistory> history = List.of(buildPriceHistory("SBER", day, "270.00"));
        when(priceHistoryRepository.findByTickerInAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any()))
                .thenReturn(history);

        SeriesResponseDto<PortfolioValuePointDto> result = analyticsService.portfolioValueHistory(userId, day, day);

        assertThat(result.getSeries()).isEmpty();
    }

    @Test
    @DisplayName("portfolioValueHistory — два тикера, у одного нет цены в lastKnownClose: считается только тикер с ценой")
    void portfolioValueHistory_missingPriceForOneTicker_onlyKnownTickerCounts() {
        // SBER has price history; LKOH does not → LKOH absent from lastKnownClose → skipped in calcDayValue
        Position posSber = buildPosition("SBER", "10");
        Position posLkoh = buildPosition("LKOH", "2");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(posSber, posLkoh));

        LocalDate day = LocalDate.of(2024, 3, 1);
        List<PriceHistory> history = List.of(buildPriceHistory("SBER", day, "270.00"));
        when(priceHistoryRepository.findByTickerInAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any()))
                .thenReturn(history);

        SeriesResponseDto<PortfolioValuePointDto> result = analyticsService.portfolioValueHistory(userId, day, day);

        assertThat(result.getSeries()).hasSize(1);
        // value = 10 × 270 = 2700 (LKOH skipped — no price in lastKnownClose)
        assertThat(result.getSeries().get(0).getValue()).isEqualByComparingTo(new BigDecimal("2700.00"));
    }

    @Test
    @DisplayName("valueAtDates — нет сделок вообще: все точки 0, historyPending = false")
    void valueAtDates_noTransactions_allZero() {
        when(transactionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of());

        PortfolioValueSeries result = analyticsService.valueAtDates(
                userId, List.of(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1)));

        assertThat(result.historyPending()).isFalse();
        assertThat(result.points()).hasSize(2);
        result.points().forEach(p -> assertThat(p.value()).isEqualByComparingTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("valueAtDates — количество на дату из BUY/SELL: до первой сделки 0, после покупки полное количество, после продажи — за вычетом")
    void valueAtDates_quantityFromBuysAndSells() {
        Security sber = buildSecurity("SBER", HistoryStatus.READY);
        LocalDate buyDate = LocalDate.of(2024, 1, 10);
        LocalDate sellDate = LocalDate.of(2024, 1, 12);
        Transaction buy = buildTransaction(sber, TransactionType.BUY, "10", instantAt(buyDate, 10, 0, 0));
        Transaction sell = buildTransaction(sber, TransactionType.SELL, "3", instantAt(sellDate, 15, 0, 0));
        when(transactionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(buy, sell));
        when(priceHistoryRepository.findByTickerInAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any()))
                .thenReturn(List.of(
                        buildPriceHistory("SBER", buyDate, "100.00"),
                        buildPriceHistory("SBER", sellDate, "100.00")));

        LocalDate beforeAnyTrade = buyDate.minusDays(1);
        LocalDate betweenBuyAndSell = buyDate.plusDays(1);
        PortfolioValueSeries result = analyticsService.valueAtDates(
                userId, List.of(beforeAnyTrade, buyDate, betweenBuyAndSell, sellDate));

        assertThat(result.historyPending()).isFalse();
        assertThat(valueAt(result, beforeAnyTrade)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(valueAt(result, buyDate)).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(valueAt(result, betweenBuyAndSell)).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(valueAt(result, sellDate)).isEqualByComparingTo(new BigDecimal("700.00"));
    }

    @Test
    @DisplayName("valueAtDates — сделка в 23:59:59 дня d учитывается в d, но не в d-1 (граница конца дня)")
    void valueAtDates_endOfDayBoundary() {
        Security sber = buildSecurity("SBER", HistoryStatus.READY);
        LocalDate tradeDate = LocalDate.of(2024, 3, 10);
        Transaction buy = buildTransaction(sber, TransactionType.BUY, "4", instantAt(tradeDate, 23, 59, 59));
        when(transactionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(buy));
        when(priceHistoryRepository.findByTickerInAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any()))
                .thenReturn(List.of(buildPriceHistory("SBER", tradeDate, "50.00")));

        LocalDate dayBefore = tradeDate.minusDays(1);
        PortfolioValueSeries result = analyticsService.valueAtDates(userId, List.of(dayBefore, tradeDate));

        assertThat(valueAt(result, dayBefore)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(valueAt(result, tradeDate)).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    @Test
    @DisplayName("valueAtDates — цена берётся как последняя известная цена закрытия ≤ даты (forward fill)")
    void valueAtDates_priceIsLastKnownCloseAtOrBeforeDate() {
        Security sber = buildSecurity("SBER", HistoryStatus.READY);
        LocalDate buyDate = LocalDate.of(2024, 4, 1);
        Transaction buy = buildTransaction(sber, TransactionType.BUY, "2", instantAt(buyDate, 9, 0, 0));
        when(transactionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(buy));
        // Only one price point, before the requested date — must be forward-filled.
        when(priceHistoryRepository.findByTickerInAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any()))
                .thenReturn(List.of(buildPriceHistory("SBER", buyDate, "300.00")));

        LocalDate requestedDate = buyDate.plusDays(3);
        PortfolioValueSeries result = analyticsService.valueAtDates(userId, List.of(requestedDate));

        assertThat(valueAt(result, requestedDate)).isEqualByComparingTo(new BigDecimal("600.00"));
    }

    @Test
    @DisplayName("valueAtDates — бумага с незагруженной историей цен исключена из расчёта, historyPending = true, дозагрузка запущена")
    void valueAtDates_pendingHistorySecurity_excludedAndHistoryPendingTrue() {
        Security pending = buildSecurity("LKOH", HistoryStatus.PENDING);
        LocalDate tradeDate = LocalDate.of(2024, 2, 1);
        Transaction buy = buildTransaction(pending, TransactionType.BUY, "5", instantAt(tradeDate, 10, 0, 0));
        when(transactionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(buy));

        PortfolioValueSeries result = analyticsService.valueAtDates(userId, List.of(tradeDate));

        assertThat(result.historyPending()).isTrue();
        assertThat(valueAt(result, tradeDate)).isEqualByComparingTo(BigDecimal.ZERO);
        verify(marketDataService).triggerHistoryAsync("LKOH");
    }

    private BigDecimal valueAt(PortfolioValueSeries series, LocalDate date) {
        return series.points().stream()
                .filter(p -> p.date().equals(date))
                .map(PortfolioValueAt::value)
                .findFirst()
                .orElseThrow();
    }

    private Security buildSecurity(String ticker, HistoryStatus historyStatus) {
        return Security.builder()
                .ticker(ticker)
                .name(ticker)
                .type(SecurityType.STOCK)
                .historyStatus(historyStatus)
                .build();
    }

    private Transaction buildTransaction(Security security, TransactionType type, String quantity, Instant executedAt) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .security(security)
                .quantity(new BigDecimal(quantity))
                .price(new BigDecimal("1.00"))
                .type(type)
                .executedAt(executedAt)
                .build();
    }

    private Instant instantAt(LocalDate date, int hour, int minute, int second) {
        return date.atTime(hour, minute, second).atZone(ZoneId.systemDefault()).toInstant();
    }

    private Position buildPosition(String ticker, String quantity) {
        Security sec = Security.builder()
                .ticker(ticker)
                .name(ticker.equals("SBER") ? "Сбербанк" : ticker)
                .type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.READY)
                .build();
        return Position.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .security(sec)
                .quantity(new BigDecimal(quantity))
                .averagePrice(new BigDecimal("250.00"))
                .totalCost(new BigDecimal(quantity).multiply(new BigDecimal("250.00")))
                .build();
    }

    private List<PriceHistory> buildHistoryDays(String ticker, LocalDate startDate, int days, String close) {
        return java.util.stream.IntStream.range(0, days)
                .mapToObj(i -> buildPriceHistory(ticker, startDate.plusDays(i), close))
                .toList();
    }

    private PriceHistory buildPriceHistory(String ticker, LocalDate date, String close) {
        return PriceHistory.builder()
                .ticker(ticker)
                .tradeDate(date)
                .open(new BigDecimal("270.00"))
                .close(new BigDecimal(close))
                .high(new BigDecimal("272.00"))
                .low(new BigDecimal("268.00"))
                .volume(11000L)
                .build();
    }
}
