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
import pyc.lopatuxin.investment.entity.PriceHistory;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.Transaction;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.entity.enums.TransactionType;
import pyc.lopatuxin.investment.repository.PositionRepository;
import pyc.lopatuxin.investment.repository.PriceHistoryRepository;
import pyc.lopatuxin.investment.repository.TransactionRepository;
import pyc.lopatuxin.investment.service.market.MarketDataService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsServiceTest")
class AnalyticsServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PriceHistoryRepository priceHistoryRepository;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private MarketDataService marketDataService;

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
                .historyStatus(HistoryStatus.PENDING)
                .build();
        doNothing().when(marketDataService).ensureHistory(anyString());
    }

    @Test
    @DisplayName("portfolioValueHistory — одна BUY транзакция, 5 дней истории: value = qty × close каждый день")
    void portfolioValueHistory_singleBuy_correctValue() {
        Transaction buy = buildTransaction(TransactionType.BUY, "10", "250.00", Instant.ofEpochSecond(1000));
        when(transactionRepository.findByUserId(userId)).thenReturn(List.of(buy));

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
    @DisplayName("portfolioValueHistory — BUY 10 затем SELL 5: после продажи qty = 5")
    void portfolioValueHistory_buyThenSell_quantityDecreases() {
        Instant buyTime = Instant.ofEpochSecond(1000);
        Instant sellTime = Instant.ofEpochSecond(1_000_000);
        Transaction buy = buildTransaction(TransactionType.BUY, "10", "250.00", buyTime);
        Transaction sell = buildTransaction(TransactionType.SELL, "5", "300.00", sellTime);
        when(transactionRepository.findByUserId(userId)).thenReturn(List.of(buy, sell));

        LocalDate buyDay = LocalDate.of(2024, 1, 1);
        LocalDate sellDay = LocalDate.ofEpochDay(sellTime.getEpochSecond() / 86400);
        LocalDate dayAfterSell = sellDay.plusDays(1);

        List<PriceHistory> history = List.of(
                buildPriceHistory("SBER", buyDay, "270.00"),
                buildPriceHistory("SBER", dayAfterSell, "280.00")
        );
        when(priceHistoryRepository.findByTickerInAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any()))
                .thenReturn(history);

        SeriesResponseDto<PortfolioValuePointDto> result = analyticsService.portfolioValueHistory(
                userId, buyDay, dayAfterSell);

        assertThat(result.getSeries()).hasSize(2);
        // Before sell: 10 × 270 = 2700
        assertThat(result.getSeries().get(0).getValue()).isEqualByComparingTo(new BigDecimal("2700.00"));
        // After sell: 5 × 280 = 1400
        assertThat(result.getSeries().get(1).getValue()).isEqualByComparingTo(new BigDecimal("1400.00"));
    }

    @Test
    @DisplayName("portfolioValueHistory — история пустая, возвращает пустой список")
    void portfolioValueHistory_emptyHistory_returnsEmpty() {
        Transaction buy = buildTransaction(TransactionType.BUY, "10", "250.00", Instant.ofEpochSecond(1000));
        when(transactionRepository.findByUserId(userId)).thenReturn(List.of(buy));
        when(priceHistoryRepository.findByTickerInAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any()))
                .thenReturn(List.of());

        SeriesResponseDto<PortfolioValuePointDto> result = analyticsService.portfolioValueHistory(
                userId, LocalDate.now().minusYears(1), LocalDate.now());

        assertThat(result.getSeries()).isEmpty();
    }

    @Test
    @DisplayName("portfolioValueHistory — forward fill: тикер без цены на день использует последнюю известную")
    void portfolioValueHistory_forwardFill() {
        Transaction buy = buildTransaction(TransactionType.BUY, "10", "250.00", Instant.ofEpochSecond(1000));
        when(transactionRepository.findByUserId(userId)).thenReturn(List.of(buy));

        LocalDate day1 = LocalDate.of(2024, 1, 15);
        LocalDate day2 = LocalDate.of(2024, 1, 16);
        // day2 has no price — forward fill should use day1 close
        List<PriceHistory> history = List.of(buildPriceHistory("SBER", day1, "270.00"));
        when(priceHistoryRepository.findByTickerInAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any()))
                .thenReturn(history);

        SeriesResponseDto<PortfolioValuePointDto> result = analyticsService.portfolioValueHistory(userId, day1, day2);

        // Only day1 has data, so only 1 point
        assertThat(result.getSeries()).hasSize(1);
        assertThat(result.getSeries().get(0).getValue()).isEqualByComparingTo(new BigDecimal("2700.00"));
    }

    @Test
    @DisplayName("securityPriceHistory — 3 PriceHistory маппируются в 3 PricePointDto с корректными полями")
    void securityPriceHistory_mapsToPricePointDto() {
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

    private Transaction buildTransaction(TransactionType type, String quantity, String price, Instant executedAt) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .security(security)
                .type(type)
                .quantity(new BigDecimal(quantity))
                .price(new BigDecimal(price))
                .executedAt(executedAt)
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
