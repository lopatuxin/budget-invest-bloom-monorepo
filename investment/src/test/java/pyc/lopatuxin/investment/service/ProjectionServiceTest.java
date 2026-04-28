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
import pyc.lopatuxin.investment.entity.PriceHistory;
import pyc.lopatuxin.investment.entity.Position;
import pyc.lopatuxin.investment.entity.Security;
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
        when(positionRepository.findByUserId(userId)).thenReturn(List.of());

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(12);

        ProjectionResultDto result = projectionService.project(userId, req);

        assertThat(result.getStartValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getSeries()).isEmpty();
    }

    @Test
    @DisplayName("project — фиксированные позиции и история (2 точки), horizonMonths=3 → series содержит 3 точки, value[0] > startValue")
    void project_computesCorrectSeries_withFixedInputs() {
        Position position = Position.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .security(sber)
                .quantity(new BigDecimal("10"))
                .averagePrice(new BigDecimal("250.00"))
                .totalCost(new BigDecimal("2500.00"))
                .build();

        when(positionRepository.findByUserId(userId)).thenReturn(List.of(position));

        SnapshotResult snap = new SnapshotResult(new BigDecimal("300.00"), new BigDecimal("295.00"), Instant.now(), false);
        when(marketDataService.getSnapshots(List.of("SBER"))).thenReturn(Map.of("SBER", snap));

        LocalDate now = LocalDate.now();
        PriceHistory h1 = PriceHistory.builder()
                .ticker("SBER")
                .tradeDate(now.minusDays(400))
                .open(new BigDecimal("200.00"))
                .close(new BigDecimal("200.00"))
                .high(new BigDecimal("205.00"))
                .low(new BigDecimal("195.00"))
                .volume(10000L)
                .build();
        PriceHistory h2 = PriceHistory.builder()
                .ticker("SBER")
                .tradeDate(now.minusDays(10))
                .open(new BigDecimal("290.00"))
                .close(new BigDecimal("295.00"))
                .high(new BigDecimal("300.00"))
                .low(new BigDecimal("285.00"))
                .volume(12000L)
                .build();

        when(priceHistoryRepository.findByTickerAndTradeDateBetweenOrderByTradeDateAsc(
                eq("SBER"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(h1, h2));

        when(dividendRepository.findBySecurity_TickerAndPaymentDateAfter(eq("SBER"), any(LocalDate.class)))
                .thenReturn(List.of());

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(3);
        req.setMonthlyDeposit(BigDecimal.ZERO);
        req.setWithdrawalRatePerYear(BigDecimal.ZERO);

        ProjectionResultDto result = projectionService.project(userId, req);

        assertThat(result.getSeries()).hasSize(3);
        // first point value must exceed startValue since CAGR is positive
        assertThat(result.getSeries().get(0).getValue())
                .isGreaterThan(result.getStartValue());
    }

    @Test
    @DisplayName("project — история пуста → тикер попадает в pendingHistoryTickers")
    void project_addsTickerToPending_whenHistoryEmpty() {
        Position position = Position.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .security(sber)
                .quantity(new BigDecimal("5"))
                .averagePrice(new BigDecimal("250.00"))
                .totalCost(new BigDecimal("1250.00"))
                .build();

        when(positionRepository.findByUserId(userId)).thenReturn(List.of(position));
        when(marketDataService.getSnapshots(List.of("SBER")))
                .thenReturn(Map.of("SBER", new SnapshotResult(new BigDecimal("250.00"), null, Instant.now(), false)));

        when(priceHistoryRepository.findByTickerAndTradeDateBetweenOrderByTradeDateAsc(
                eq("SBER"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        when(dividendRepository.findBySecurity_TickerAndPaymentDateAfter(eq("SBER"), any(LocalDate.class)))
                .thenReturn(List.of());

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(3);

        ProjectionResultDto result = projectionService.project(userId, req);

        assertThat(result.getPendingHistoryTickers()).contains("SBER");
    }

    @Test
    @DisplayName("project — override для тикера → priceHistoryRepository не вызывается для этого тикера")
    void project_usesOverride_whenProvided() {
        Position position = Position.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .security(sber)
                .quantity(new BigDecimal("10"))
                .averagePrice(new BigDecimal("300.00"))
                .totalCost(new BigDecimal("3000.00"))
                .build();

        when(positionRepository.findByUserId(userId)).thenReturn(List.of(position));
        when(marketDataService.getSnapshots(List.of("SBER")))
                .thenReturn(Map.of("SBER", new SnapshotResult(new BigDecimal("300.00"), null, Instant.now(), false)));

        when(dividendRepository.findBySecurity_TickerAndPaymentDateAfter(eq("SBER"), any(LocalDate.class)))
                .thenReturn(List.of());

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(3);
        req.setOverrides(Map.of("SBER", new BigDecimal("0.15")));

        projectionService.project(userId, req);

        verify(priceHistoryRepository, never())
                .findByTickerAndTradeDateBetweenOrderByTradeDateAsc(eq("SBER"), any(), any());
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

        when(positionRepository.findByUserId(userId)).thenReturn(List.of(position));
        when(marketDataService.getSnapshots(List.of("SBER")))
                .thenReturn(Map.of("SBER", new SnapshotResult(new BigDecimal("300.00"), null, Instant.now(), false)));

        when(dividendRepository.findBySecurity_TickerAndPaymentDateAfter(eq("SBER"), any(LocalDate.class)))
                .thenReturn(List.of());

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
}
