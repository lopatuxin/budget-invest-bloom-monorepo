package pyc.lopatuxin.investment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.investment.dto.request.ProjectionRequestDto;
import pyc.lopatuxin.investment.dto.response.ProjectionResultDto;
import pyc.lopatuxin.investment.entity.Dividend;
import pyc.lopatuxin.investment.entity.PriceHistory;
import pyc.lopatuxin.investment.entity.Position;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.DividendStatus;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.repository.DividendRepository;
import pyc.lopatuxin.investment.repository.PositionRepository;
import pyc.lopatuxin.investment.repository.PriceHistoryRepository;
import pyc.lopatuxin.investment.service.market.MarketDataService;
import pyc.lopatuxin.investment.service.market.dto.SnapshotResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectionServiceTest")
class ProjectionServiceTest {

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private PriceHistoryRepository priceHistoryRepository;

    @Mock
    private DividendRepository dividendRepository;

    @Mock
    private MarketDataService marketDataService;

    @InjectMocks
    private ProjectionService projectionService;

    private UUID userId;
    private Security sber;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sber = Security.builder()
                .ticker("SBER")
                .name("Сбербанк")
                .type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.READY)
                .build();
    }

    @Test
    @DisplayName("project — пустой список позиций → startValue=0, series пустой")
    void project_returnsEmptyResult_whenNoPositions() {
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of());

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(12);

        ProjectionResultDto result = projectionService.project(userId, req);

        assertThat(result.getStartValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getSeries()).isEmpty();
    }

    @Test
    @DisplayName("project — дивиденды за 2 года → аннуальная доходность = среднее по годам, series содержит 3 точки")
    void project_computesAverageAnnualReturn_fromMultipleYearsDividends() {
        Position position = Position.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .security(sber)
                .quantity(new BigDecimal("10"))
                .averagePrice(new BigDecimal("250.00"))
                .totalCost(new BigDecimal("2500.00"))
                .build();

        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));

        SnapshotResult snap = new SnapshotResult(new BigDecimal("300.00"), new BigDecimal("295.00"), Instant.now(), false);
        when(marketDataService.getSnapshots(List.of("SBER"))).thenReturn(Map.of("SBER", snap));

        // year 2022: amountPerShare=20, close=200 → yield=0.10
        Dividend div2022 = buildPaidDividend("SBER", LocalDate.of(2022, 7, 1), new BigDecimal("20.00"));
        // year 2023: amountPerShare=30, close=300 → yield=0.10
        Dividend div2023 = buildPaidDividend("SBER", LocalDate.of(2023, 7, 1), new BigDecimal("30.00"));

        when(dividendRepository.findBySecurity_Ticker("SBER"))
                .thenReturn(List.of(div2022, div2023));

        PriceHistory ph2022 = buildPriceHistory("SBER", LocalDate.of(2022, 6, 30), new BigDecimal("200.00"));
        PriceHistory ph2023 = buildPriceHistory("SBER", LocalDate.of(2023, 6, 30), new BigDecimal("300.00"));

        when(priceHistoryRepository.findFirstByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc(
                eq("SBER"), eq(LocalDate.of(2022, 7, 1))))
                .thenReturn(Optional.of(ph2022));
        when(priceHistoryRepository.findFirstByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc(
                eq("SBER"), eq(LocalDate.of(2023, 7, 1))))
                .thenReturn(Optional.of(ph2023));

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(3);
        req.setMonthlyDeposit(BigDecimal.ZERO);
        req.setWithdrawalRatePerYear(BigDecimal.ZERO);

        ProjectionResultDto result = projectionService.project(userId, req);

        assertThat(result.getSeries()).hasSize(3);
        // yield per year = 0.10 both years → average = 0.10 → positive return → value grows
        assertThat(result.getSeries().get(0).getValue())
                .isGreaterThan(result.getStartValue());
        // pendingTickers must be empty — both years have valid data
        assertThat(result.getPendingHistoryTickers()).doesNotContain("SBER");
    }

    @Test
    @DisplayName("project — нет оплаченных дивидендов → тикер попадает в pendingHistoryTickers")
    void project_addsTickerToPending_whenNoPaidDividends() {
        Position position = Position.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .security(sber)
                .quantity(new BigDecimal("5"))
                .averagePrice(new BigDecimal("250.00"))
                .totalCost(new BigDecimal("1250.00"))
                .build();

        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));
        when(marketDataService.getSnapshots(List.of("SBER")))
                .thenReturn(Map.of("SBER", new SnapshotResult(new BigDecimal("250.00"), null, Instant.now(), false)));

        when(dividendRepository.findBySecurity_Ticker("SBER")).thenReturn(List.of());

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(3);

        ProjectionResultDto result = projectionService.project(userId, req);

        assertThat(result.getPendingHistoryTickers()).contains("SBER");
    }

    @Test
    @DisplayName("project — все дивиденды без цены в истории → тикер попадает в pendingHistoryTickers")
    void project_addsTickerToPending_whenNoPriceFoundForAnyDividend() {
        Position position = Position.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .security(sber)
                .quantity(new BigDecimal("5"))
                .averagePrice(new BigDecimal("250.00"))
                .totalCost(new BigDecimal("1250.00"))
                .build();

        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));
        when(marketDataService.getSnapshots(List.of("SBER")))
                .thenReturn(Map.of("SBER", new SnapshotResult(new BigDecimal("250.00"), null, Instant.now(), false)));

        Dividend div = buildPaidDividend("SBER", LocalDate.of(2023, 7, 1), new BigDecimal("20.00"));
        when(dividendRepository.findBySecurity_Ticker("SBER")).thenReturn(List.of(div));

        when(priceHistoryRepository.findFirstByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc(
                eq("SBER"), any(LocalDate.class)))
                .thenReturn(Optional.empty());

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(3);

        ProjectionResultDto result = projectionService.project(userId, req);

        assertThat(result.getPendingHistoryTickers()).contains("SBER");
    }

    @Test
    @DisplayName("project — override для тикера → dividendRepository и priceHistoryRepository не вызываются для этого тикера")
    void project_usesOverride_whenProvided() {
        Position position = Position.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .security(sber)
                .quantity(new BigDecimal("10"))
                .averagePrice(new BigDecimal("300.00"))
                .totalCost(new BigDecimal("3000.00"))
                .build();

        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));
        when(marketDataService.getSnapshots(List.of("SBER")))
                .thenReturn(Map.of("SBER", new SnapshotResult(new BigDecimal("300.00"), null, Instant.now(), false)));

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(3);
        req.setOverrides(Map.of("SBER", new BigDecimal("0.15")));

        projectionService.project(userId, req);

        verify(dividendRepository, never()).findBySecurity_Ticker(eq("SBER"));
        verify(priceHistoryRepository, never())
                .findFirstByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc(eq("SBER"), any());
    }

    @Test
    @DisplayName("project — monthlyDeposit=10000 → каждый ProjectionPoint имеет deposit=10000")
    void project_addsMonthlyDeposit() {
        Position position = Position.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .security(sber)
                .quantity(new BigDecimal("10"))
                .averagePrice(new BigDecimal("300.00"))
                .totalCost(new BigDecimal("3000.00"))
                .build();

        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));
        when(marketDataService.getSnapshots(List.of("SBER")))
                .thenReturn(Map.of("SBER", new SnapshotResult(new BigDecimal("300.00"), null, Instant.now(), false)));

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(4);
        req.setMonthlyDeposit(new BigDecimal("10000"));
        req.setWithdrawalRatePerYear(BigDecimal.ZERO);
        req.setOverrides(Map.of("SBER", new BigDecimal("0.10")));

        ProjectionResultDto result = projectionService.project(userId, req);

        assertThat(result.getSeries()).hasSize(4);
        result.getSeries().forEach(point ->
                assertThat(point.getDeposit()).isEqualByComparingTo(new BigDecimal("10000.00")));
    }

    private Dividend buildPaidDividend(String ticker, LocalDate paymentDate, BigDecimal amountPerShare) {
        Security sec = Security.builder()
                .ticker(ticker)
                .name(ticker)
                .type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.READY)
                .build();
        return Dividend.builder()
                .id(UUID.randomUUID())
                .security(sec)
                .recordDate(paymentDate.minusDays(14))
                .paymentDate(paymentDate)
                .amountPerShare(amountPerShare)
                .currency("RUB")
                .status(DividendStatus.PAID)
                .build();
    }

    private PriceHistory buildPriceHistory(String ticker, LocalDate tradeDate, BigDecimal close) {
        return PriceHistory.builder()
                .ticker(ticker)
                .tradeDate(tradeDate)
                .open(close)
                .close(close)
                .high(close)
                .low(close)
                .volume(10000L)
                .build();
    }
}
