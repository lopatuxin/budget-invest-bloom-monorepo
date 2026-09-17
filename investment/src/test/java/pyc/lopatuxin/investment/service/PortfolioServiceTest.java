package pyc.lopatuxin.investment.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import pyc.lopatuxin.investment.config.DividendTaxProperties;
import pyc.lopatuxin.investment.dto.request.PortfolioSort;
import pyc.lopatuxin.investment.dto.response.PortfolioAllocationDto;
import pyc.lopatuxin.investment.dto.response.PortfolioGroupingResult;
import pyc.lopatuxin.investment.dto.response.PortfolioPageResponseDto;
import pyc.lopatuxin.investment.dto.response.PositionResponseDto;
import pyc.lopatuxin.investment.dto.response.SnapshotResult;
import pyc.lopatuxin.investment.dto.response.TransactionResponseDto;
import pyc.lopatuxin.investment.dto.response.UpcomingDividendDto;
import pyc.lopatuxin.investment.entity.Dividend;
import pyc.lopatuxin.investment.entity.Position;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.Transaction;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.entity.enums.TransactionType;
import pyc.lopatuxin.investment.mapper.PositionMapper;
import pyc.lopatuxin.investment.mapper.TransactionMapper;
import pyc.lopatuxin.investment.repository.DividendRepository;
import pyc.lopatuxin.investment.repository.PositionRepository;
import pyc.lopatuxin.investment.repository.TransactionRepository;
import pyc.lopatuxin.investment.service.market.DividendSyncService;
import pyc.lopatuxin.investment.service.market.MarketDataService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// HoldingsOnDateService and DividendTaxCalculator are used as real instances (not mocked): both
// are tiny, already covered by their own unit tests (HoldingsOnDateServiceTest,
// DividendTaxCalculatorTest), and using the real 13% arithmetic here makes every dividend
// assertion below a genuine end-to-end number instead of a per-test mock stub keyed by date.
@ExtendWith(MockitoExtension.class)
@DisplayName("PortfolioServiceTest")
class PortfolioServiceTest {

    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.13");
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private PositionMapper positionMapper;

    @Mock
    private MarketDataService marketDataService;

    @Mock
    private DividendRepository dividendRepository;

    @Mock
    private DividendSyncService dividendSyncService;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionMapper transactionMapper;

    @Mock
    private PortfolioGroupingService portfolioGroupingService;

    private PortfolioService portfolioService;

    private final UUID userId = UUID.randomUUID();

    private TimeZone originalDefaultTimeZone;

    // HoldingsOnDateService.quantityAt derives its cutoff from ZoneId.systemDefault() (App.main
    // sets it to Europe/Moscow in production). Pinning it here makes journal fixtures built with
    // gaps of a day or two behave the same regardless of the timezone of the machine running the
    // build — same pattern as HoldingsOnDateServiceTest.
    @BeforeEach
    void pinDefaultTimeZoneToMoscow() {
        originalDefaultTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(MOSCOW));
    }

    @AfterEach
    void restoreDefaultTimeZone() {
        TimeZone.setDefault(originalDefaultTimeZone);
    }

    @BeforeEach
    void setUp() {
        DividendTaxProperties taxProperties = new DividendTaxProperties();
        taxProperties.setRate(DEFAULT_TAX_RATE);
        portfolioService = new PortfolioService(positionRepository, positionMapper, marketDataService,
                dividendRepository, dividendSyncService, transactionRepository, transactionMapper,
                portfolioGroupingService, new HoldingsOnDateService(), new DividendTaxCalculator(taxProperties));
    }

    @Test
    @DisplayName("getPortfolioPage — нет позиций и пустой журнал сделок → пустые группы и распределение, MOEX и группировка не вызываются")
    void getPortfolioPage_noPositionsAndNoJournal_returnsEmptyGroupsAndAllocation() {
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of());
        stubEmptyTransactions();

        PortfolioPageResponseDto page = portfolioService.getPortfolioPage(userId, PortfolioSort.WEIGHT);

        assertThat(page.getGroups()).isEmpty();
        assertThat(page.getAllocation().getByType()).isEmpty();
        assertThat(page.getAllocation().getBySector()).isEmpty();
        assertThat(page.getPositions()).isEmpty();
        assertThat(page.getUpcomingDividends()).isEmpty();
        assertThat(page.getRecentDividends()).isEmpty();
        assertThat(page.getOverview().getTotalValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(page.getOverview().getDividendTaxRatePercent()).isEqualByComparingTo("13.0");
        verifyNoInteractions(marketDataService, portfolioGroupingService, dividendRepository);
    }

    @Test
    @DisplayName("getPortfolioPage — pricesAsOf берётся из самого свежего снимка, pricesStale = true при устаревшем снимке")
    void getPortfolioPage_pricesAsOfAndStale_derivedFromSnapshots() {
        stubOnePosition();
        Instant fetchedAt = Instant.parse("2026-09-06T15:45:01Z");
        when(marketDataService.getSnapshots(Set.of("SBER")))
                .thenReturn(Map.of("SBER", new SnapshotResult(new BigDecimal("310.50"), new BigDecimal("308.00"), fetchedAt, true)));
        when(portfolioGroupingService.group(anyList(), any(), eq(PortfolioSort.WEIGHT)))
                .thenReturn(groupingResult());
        stubEmptyDividendsAndTransactions();

        PortfolioPageResponseDto page = portfolioService.getPortfolioPage(userId, PortfolioSort.WEIGHT);

        assertThat(page.getOverview().getPricesAsOf()).isEqualTo(fetchedAt);
        assertThat(page.getOverview().isPricesStale()).isTrue();
    }

    @Test
    @DisplayName("getPortfolioPage — recentTransactions — пять последних сделок пользователя")
    void getPortfolioPage_recentTransactions_fetchesTopFive() {
        stubOnePosition();
        when(marketDataService.getSnapshots(Set.of("SBER"))).thenReturn(Map.of());
        when(portfolioGroupingService.group(anyList(), any(), eq(PortfolioSort.WEIGHT)))
                .thenReturn(groupingResult());
        when(dividendRepository.findByTickerInAndReceivedDateBetweenWithSecurity(any(), any(), any())).thenReturn(List.of());
        when(dividendRepository.findUpcomingByTickersWithSecurity(any(), any())).thenReturn(List.of());

        Transaction tx = Transaction.builder().id(UUID.randomUUID()).userId(userId)
                .security(sberSecurity()).type(TransactionType.BUY)
                .quantity(new BigDecimal("20")).price(new BigDecimal("305.00"))
                .executedAt(Instant.parse("2026-09-04T10:30:00Z")).build();
        TransactionResponseDto txDto = TransactionResponseDto.builder()
                .id(tx.getId()).ticker("SBER").securityName("Сбербанк").type(TransactionType.BUY)
                .quantity(tx.getQuantity()).price(tx.getPrice()).amount(new BigDecimal("6100.00"))
                .executedAt(tx.getExecutedAt()).build();
        when(transactionRepository.findRecentByUserIdWithSecurity(userId, PageRequest.of(0, 5)))
                .thenReturn(List.of(tx));
        when(transactionMapper.toDtoList(List.of(tx))).thenReturn(List.of(txDto));
        when(transactionRepository.countByUserId(userId)).thenReturn(21L);

        PortfolioPageResponseDto page = portfolioService.getPortfolioPage(userId, PortfolioSort.WEIGHT);

        assertThat(page.getRecentTransactions()).containsExactly(txDto);
        assertThat(page.getTransactionsTotal()).isEqualTo(21L);
    }

    @Test
    @DisplayName("getPortfolioPage — recentDividends за 12 месяцев считаются по количеству на отсечку и после налога")
    void getPortfolioPage_recentDividends_within12Months() {
        stubOnePosition();
        when(marketDataService.getSnapshots(Set.of("SBER"))).thenReturn(Map.of());
        PortfolioGroupingResult grouping = groupingResultWithTotalCost(new BigDecimal("6100.00"));
        when(portfolioGroupingService.group(anyList(), any(), eq(PortfolioSort.WEIGHT))).thenReturn(grouping);
        stubEmptyTransactions();
        when(dividendRepository.findUpcomingByTickersWithSecurity(any(), any())).thenReturn(List.of());

        Dividend dividend = Dividend.builder().security(sberSecurity())
                .recordDate(LocalDate.now().minusMonths(2))
                .amountPerShare(new BigDecimal("34.84"))
                .currency("RUB").build();
        when(dividendRepository.findByTickerInAndReceivedDateBetweenWithSecurity(
                Set.of("SBER"), LocalDate.now().minusYears(1), LocalDate.now()))
                .thenReturn(List.of(dividend));
        // 34.84 * 200 = 6968.00 declared; 13% withheld floors to 905, so 6063.00 is credited.
        when(portfolioGroupingService.percentOf(new BigDecimal("6063.00"), new BigDecimal("6100.00")))
                .thenReturn(new BigDecimal("99.4"));

        PortfolioPageResponseDto page = portfolioService.getPortfolioPage(userId, PortfolioSort.WEIGHT);

        assertThat(page.getRecentDividends()).hasSize(1);
        assertThat(page.getRecentDividends().get(0).getTicker()).isEqualTo("SBER");
        assertThat(page.getRecentDividends().get(0).getAmountPerShare()).isEqualByComparingTo("34.84"); // declared per share, untaxed
        assertThat(page.getRecentDividends().get(0).getTotalAmount()).isEqualByComparingTo("6063.00");
        assertThat(page.getOverview().getDividends12m()).isEqualByComparingTo("6063.00");
        assertThat(page.getOverview().getDividendYieldPercent()).isEqualByComparingTo("99.4");
    }

    @Test
    @DisplayName("getReceivedPayouts — когда все выплаты пользователя внутри последних 12 месяцев, сумма совпадает с dividends12m портфельной страницы")
    void getReceivedPayouts_allWithinTwelveMonths_matchesPortfolioPageDividends12m() {
        stubJournal(buy(sberSecurity(), "200", LocalDate.now().minusYears(2)));
        Dividend dividend = Dividend.builder().security(sberSecurity())
                .recordDate(LocalDate.now().minusMonths(2))
                .amountPerShare(new BigDecimal("34.84"))
                .currency("RUB").build();
        when(dividendRepository.findByTickerInAndReceivedDateBeforeWithSecurity(Set.of("SBER"), LocalDate.now()))
                .thenReturn(List.of(dividend));

        List<UpcomingDividendDto> payouts = portfolioService.getReceivedPayouts(userId);

        BigDecimal payoutsTotal = payouts.stream().map(UpcomingDividendDto::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        // Same dividend, same net-of-tax rule as getPortfolioPage_recentDividends_within12Months above: 6063.00.
        assertThat(payoutsTotal).isEqualByComparingTo("6063.00");
    }

    @Test
    @DisplayName("getPortfolioPage — эталон живых данных: выплата ВТБ 320.43 при 33 акциях на отсечку → налог 41 → к получению 279.43")
    void getPortfolioPage_vtbReferencePayout_matchesLiveData() {
        Position position = Position.builder().id(UUID.randomUUID()).userId(userId)
                .security(security("VTBR")).quantity(new BigDecimal("33"))
                .averagePrice(BigDecimal.ONE).totalCost(new BigDecimal("33.00")).build();
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));
        when(positionMapper.toDto(position)).thenReturn(PositionResponseDto.builder()
                .ticker("VTBR").securityName("ВТБ").securityType(SecurityType.STOCK).sector("Финансы")
                .quantity(position.getQuantity()).averagePrice(position.getAveragePrice()).totalCost(position.getTotalCost()).build());
        stubJournal(buy(security("VTBR"), "33", LocalDate.now().minusYears(1)));
        when(marketDataService.getSnapshots(Set.of("VTBR"))).thenReturn(Map.of());
        when(portfolioGroupingService.group(anyList(), any(), eq(PortfolioSort.WEIGHT))).thenReturn(groupingResult());
        stubEmptyTransactions();
        when(dividendRepository.findUpcomingByTickersWithSecurity(any(), any())).thenReturn(List.of());

        Dividend dividend = Dividend.builder().security(security("VTBR"))
                .recordDate(LocalDate.now().minusMonths(3))
                .amountPerShare(new BigDecimal("9.71"))
                .currency("RUB").build();
        when(dividendRepository.findByTickerInAndReceivedDateBetweenWithSecurity(
                Set.of("VTBR"), LocalDate.now().minusYears(1), LocalDate.now()))
                .thenReturn(List.of(dividend));

        PortfolioPageResponseDto page = portfolioService.getPortfolioPage(userId, PortfolioSort.WEIGHT);

        assertThat(page.getRecentDividends()).hasSize(1);
        assertThat(page.getRecentDividends().get(0).getQuantity()).isEqualByComparingTo("33");
        assertThat(page.getRecentDividends().get(0).getTotalAmount()).isEqualByComparingTo("279.43");
    }

    @Test
    @DisplayName("getPortfolioPage — 12 месяцев считаются по дате выплаты, если она есть, иначе по дате отсечки")
    void getPortfolioPage_receivedWindow_usesPaymentDateWithRecordDateFallback() {
        stubOnePosition();
        when(marketDataService.getSnapshots(Set.of("SBER"))).thenReturn(Map.of());
        when(portfolioGroupingService.group(anyList(), any(), eq(PortfolioSort.WEIGHT))).thenReturn(groupingResult());
        stubEmptyTransactions();
        when(dividendRepository.findUpcomingByTickersWithSecurity(any(), any())).thenReturn(List.of());

        // recordDate is 13 months ago (outside the window on its own) but paymentDate is 1 month
        // ago (inside it) — the repository query itself applies the COALESCE rule, this test
        // only has to confirm PortfolioService passes the row through into the response as is.
        Dividend dividend = Dividend.builder().security(sberSecurity())
                .recordDate(LocalDate.now().minusMonths(13))
                .paymentDate(LocalDate.now().minusMonths(1))
                .amountPerShare(new BigDecimal("10.00"))
                .currency("RUB").build();
        when(dividendRepository.findByTickerInAndReceivedDateBetweenWithSecurity(
                Set.of("SBER"), LocalDate.now().minusMonths(12), LocalDate.now()))
                .thenReturn(List.of(dividend));

        PortfolioPageResponseDto page = portfolioService.getPortfolioPage(userId, PortfolioSort.WEIGHT);

        assertThat(page.getRecentDividends()).hasSize(1);
        // 10.00 * 200 = 2000.00 declared, 13% withheld = 260.00 -> 1740.00 credited.
        assertThat(page.getOverview().getDividends12m()).isEqualByComparingTo("1740.00");
    }

    @Test
    @DisplayName("getPortfolioPage — иностранная валюта в recentDividends: показана в списке без вычета налога, не входит в сумму за 12 месяцев")
    void getPortfolioPage_foreignCurrencyDividend_excludedFromSum_butListed() {
        stubOnePosition();
        when(marketDataService.getSnapshots(Set.of("SBER"))).thenReturn(Map.of());
        when(portfolioGroupingService.group(anyList(), any(), eq(PortfolioSort.WEIGHT))).thenReturn(groupingResult());
        stubEmptyTransactions();
        when(dividendRepository.findUpcomingByTickersWithSecurity(any(), any())).thenReturn(List.of());

        Dividend rub = Dividend.builder().security(sberSecurity())
                .recordDate(LocalDate.now().minusMonths(1)).amountPerShare(new BigDecimal("10.00")).currency("RUB").build();
        Dividend usd = Dividend.builder().security(sberSecurity())
                .recordDate(LocalDate.now().minusMonths(1)).amountPerShare(new BigDecimal("5.00")).currency("USD").build();
        when(dividendRepository.findByTickerInAndReceivedDateBetweenWithSecurity(
                Set.of("SBER"), LocalDate.now().minusMonths(12), LocalDate.now()))
                .thenReturn(List.of(rub, usd));

        PortfolioPageResponseDto page = portfolioService.getPortfolioPage(userId, PortfolioSort.WEIGHT);

        assertThat(page.getRecentDividends()).hasSize(2);
        assertThat(page.getRecentDividends()).extracting(UpcomingDividendDto::getCurrency)
                .containsExactlyInAnyOrder("RUB", "USD");
        UpcomingDividendDto usdRow = page.getRecentDividends().stream()
                .filter(d -> "USD".equals(d.getCurrency())).findFirst().orElseThrow();
        assertThat(usdRow.getTotalAmount()).isEqualByComparingTo("1000.00"); // 5.00 * 200, untaxed
        // Only the RUB row counts toward the sum, after tax: 10.00 * 200 = 2000.00 -> 1740.00.
        assertThat(page.getOverview().getDividends12m()).isEqualByComparingTo("1740.00");
    }

    @Test
    @DisplayName("getPortfolioPage — предстоящие: запись с прошедшей отсечкой, но будущей выплатой, идёт раньше записи с более далёкой будущей отсечкой")
    void getPortfolioPage_upcomingDividends_sortedByEffectiveDate() {
        stubOnePosition();
        when(marketDataService.getSnapshots(Set.of("SBER"))).thenReturn(Map.of());
        when(portfolioGroupingService.group(anyList(), any(), eq(PortfolioSort.WEIGHT))).thenReturn(groupingResult());
        stubEmptyTransactions();
        when(dividendRepository.findByTickerInAndReceivedDateBetweenWithSecurity(any(), any(), any())).thenReturn(List.of());

        // Past record date, payment still ahead in 3 days — sorts by paymentDate (pt. 21).
        Dividend soonByPayment = Dividend.builder().security(sberSecurity())
                .recordDate(LocalDate.now().minusDays(2)).paymentDate(LocalDate.now().plusDays(3))
                .amountPerShare(new BigDecimal("10.00")).currency("RUB").build();
        // Record date itself is 10 days out — sorts by recordDate.
        Dividend laterByRecordDate = Dividend.builder().security(sberSecurity())
                .recordDate(LocalDate.now().plusDays(10))
                .amountPerShare(new BigDecimal("20.00")).currency("RUB").build();
        when(dividendRepository.findUpcomingByTickersWithSecurity(Set.of("SBER"), LocalDate.now()))
                .thenReturn(List.of(laterByRecordDate, soonByPayment));

        PortfolioPageResponseDto page = portfolioService.getPortfolioPage(userId, PortfolioSort.WEIGHT);

        assertThat(page.getUpcomingDividends()).hasSize(2);
        assertThat(page.getUpcomingDividends().get(0).getAmountPerShare()).isEqualByComparingTo("10.00");
        assertThat(page.getUpcomingDividends().get(1).getAmountPerShare()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("getPortfolioPage — предстоящий дивиденд: будущая отсечка использует текущее количество, прошедшая — количество на дату отсечки")
    void getPortfolioPage_upcomingDividend_quantityRuleDependsOnRecordDate() {
        Position position = Position.builder().id(UUID.randomUUID()).userId(userId)
                .security(sberSecurity()).quantity(new BigDecimal("300"))
                .averagePrice(new BigDecimal("280.00")).totalCost(new BigDecimal("84000.00")).build();
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));
        when(positionMapper.toDto(position)).thenReturn(PositionResponseDto.builder()
                .ticker("SBER").securityName("Сбербанк").securityType(SecurityType.STOCK).sector("Финансы")
                .quantity(position.getQuantity()).averagePrice(position.getAveragePrice()).totalCost(position.getTotalCost()).build());
        // Bought 200 long ago, then topped up with another 100 two days ago.
        stubJournal(
                buy(sberSecurity(), "200", LocalDate.now().minusYears(2)),
                buy(sberSecurity(), "100", LocalDate.now().minusDays(2))
        );
        when(marketDataService.getSnapshots(Set.of("SBER"))).thenReturn(Map.of());
        when(portfolioGroupingService.group(anyList(), any(), eq(PortfolioSort.WEIGHT))).thenReturn(groupingResult());
        stubEmptyTransactions();
        when(dividendRepository.findByTickerInAndReceivedDateBetweenWithSecurity(any(), any(), any())).thenReturn(List.of());

        Dividend pastRecordDate = Dividend.builder().security(sberSecurity())
                .recordDate(LocalDate.now().minusDays(5)).paymentDate(LocalDate.now().plusDays(2))
                .amountPerShare(new BigDecimal("10.00")).currency("RUB").build();
        Dividend futureRecordDate = Dividend.builder().security(sberSecurity())
                .recordDate(LocalDate.now().plusDays(5))
                .amountPerShare(new BigDecimal("20.00")).currency("RUB").build();
        when(dividendRepository.findUpcomingByTickersWithSecurity(Set.of("SBER"), LocalDate.now()))
                .thenReturn(List.of(pastRecordDate, futureRecordDate));

        PortfolioPageResponseDto page = portfolioService.getPortfolioPage(userId, PortfolioSort.WEIGHT);

        assertThat(page.getUpcomingDividends()).hasSize(2);
        UpcomingDividendDto past = dividendWithAmountPerShare(page, "10.00");
        UpcomingDividendDto future = dividendWithAmountPerShare(page, "20.00");
        // The top-up (2 days ago) came after the past record date (5 days ago) — not counted there.
        assertThat(past.getQuantity()).isEqualByComparingTo("200");
        // Record date has not happened yet — nobody knows the future holding, so today's full 300 is used.
        assertThat(future.getQuantity()).isEqualByComparingTo("300");
    }

    @Test
    @DisplayName("getPortfolioPage — бумага продана целиком (нет активной позиции), но прошлая выплата с отсечкой во время владения входит в сумму за 12 месяцев")
    void getPortfolioPage_fullySoldSecurity_pastDividendStillCounted() {
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of());
        Security lkoh = security("LKOH");
        stubJournal(
                buy(lkoh, "2", LocalDate.now().minusMonths(6)),
                sell(lkoh, "2", LocalDate.now().minusMonths(1))
        );
        when(portfolioGroupingService.group(List.of(), Map.of(), PortfolioSort.WEIGHT)).thenReturn(groupingResult());
        stubEmptyTransactions();
        when(dividendRepository.findUpcomingByTickersWithSecurity(any(), any())).thenReturn(List.of());

        // Record date falls between the buy and the sell — the security was fully held then.
        Dividend dividend = Dividend.builder().security(lkoh)
                .recordDate(LocalDate.now().minusMonths(3))
                .amountPerShare(new BigDecimal("397.00"))
                .currency("RUB").build();
        when(dividendRepository.findByTickerInAndReceivedDateBetweenWithSecurity(
                Set.of("LKOH"), LocalDate.now().minusMonths(12), LocalDate.now()))
                .thenReturn(List.of(dividend));

        PortfolioPageResponseDto page = portfolioService.getPortfolioPage(userId, PortfolioSort.WEIGHT);

        assertThat(page.getPositions()).isEmpty();
        assertThat(page.getRecentDividends()).hasSize(1);
        assertThat(page.getRecentDividends().get(0).getQuantity()).isEqualByComparingTo("2");
        // 397.00 * 2 = 794.00 declared, 13% withheld floors to 103, so 691.00 is credited.
        assertThat(page.getRecentDividends().get(0).getTotalAmount()).isEqualByComparingTo("691.00");
        assertThat(page.getOverview().getDividends12m()).isEqualByComparingTo("691.00");
        verifyNoInteractions(marketDataService);
    }

    @Test
    @DisplayName("getPortfolioPage — dividendsSourceConfigured пробрасывается из DividendSyncService")
    void getPortfolioPage_dividendsSourceConfigured_reflectsSyncServiceFlag() {
        stubOnePosition();
        when(marketDataService.getSnapshots(Set.of("SBER"))).thenReturn(Map.of());
        when(portfolioGroupingService.group(anyList(), any(), eq(PortfolioSort.WEIGHT))).thenReturn(groupingResult());
        stubEmptyDividendsAndTransactions();
        when(dividendSyncService.isSourceConfigured()).thenReturn(true);

        PortfolioPageResponseDto page = portfolioService.getPortfolioPage(userId, PortfolioSort.WEIGHT);

        assertThat(page.getOverview().isDividendsSourceConfigured()).isTrue();
    }

    @Test
    @DisplayName("getPortfolioPage — dividendsSourceConfigured = false по умолчанию (токен не задан или ни разу не синхронизировано)")
    void getPortfolioPage_dividendsSourceConfigured_falseByDefault() {
        stubOnePosition();
        when(marketDataService.getSnapshots(Set.of("SBER"))).thenReturn(Map.of());
        when(portfolioGroupingService.group(anyList(), any(), eq(PortfolioSort.WEIGHT))).thenReturn(groupingResult());
        stubEmptyDividendsAndTransactions();

        PortfolioPageResponseDto page = portfolioService.getPortfolioPage(userId, PortfolioSort.WEIGHT);

        assertThat(page.getOverview().isDividendsSourceConfigured()).isFalse();
    }

    @Test
    @DisplayName("getPortfolioPage — dividendTaxRatePercent в overview равен настроенной ставке (13%)")
    void getPortfolioPage_dividendTaxRatePercent_reflectsConfiguredRate() {
        stubOnePosition();
        when(marketDataService.getSnapshots(Set.of("SBER"))).thenReturn(Map.of());
        when(portfolioGroupingService.group(anyList(), any(), eq(PortfolioSort.WEIGHT))).thenReturn(groupingResult());
        stubEmptyDividendsAndTransactions();

        PortfolioPageResponseDto page = portfolioService.getPortfolioPage(userId, PortfolioSort.WEIGHT);

        assertThat(page.getOverview().getDividendTaxRatePercent()).isEqualByComparingTo("13.0");
    }

    @Test
    @DisplayName("getPortfolioPage — две позиции на один тикер (нарушение уникальности) → не падает, количества суммируются")
    void getPortfolioPage_duplicatePositionsForSameTicker_doesNotThrow_quantitiesSummed() {
        Position first = Position.builder().id(UUID.randomUUID()).userId(userId)
                .security(sberSecurity()).quantity(new BigDecimal("100"))
                .averagePrice(new BigDecimal("280.00")).totalCost(new BigDecimal("28000.00")).build();
        Position second = Position.builder().id(UUID.randomUUID()).userId(userId)
                .security(sberSecurity()).quantity(new BigDecimal("100"))
                .averagePrice(new BigDecimal("280.00")).totalCost(new BigDecimal("28000.00")).build();
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(first, second));
        when(positionMapper.toDto(first)).thenReturn(PositionResponseDto.builder()
                .ticker("SBER").securityName("Сбербанк").securityType(SecurityType.STOCK).sector("Финансы")
                .quantity(first.getQuantity()).averagePrice(first.getAveragePrice()).totalCost(first.getTotalCost()).build());
        when(positionMapper.toDto(second)).thenReturn(PositionResponseDto.builder()
                .ticker("SBER").securityName("Сбербанк").securityType(SecurityType.STOCK).sector("Финансы")
                .quantity(second.getQuantity()).averagePrice(second.getAveragePrice()).totalCost(second.getTotalCost()).build());
        stubJournal(buy(sberSecurity(), "200", LocalDate.now().minusYears(2)));
        when(marketDataService.getSnapshots(Set.of("SBER"))).thenReturn(Map.of());
        when(portfolioGroupingService.group(anyList(), any(), eq(PortfolioSort.WEIGHT))).thenReturn(groupingResult());
        stubEmptyTransactions();

        Dividend dividend = Dividend.builder().security(sberSecurity())
                .recordDate(LocalDate.now().plusDays(5))
                .amountPerShare(new BigDecimal("10.00"))
                .currency("RUB").build();
        when(dividendRepository.findByTickerInAndReceivedDateBetweenWithSecurity(any(), any(), any())).thenReturn(List.of());
        when(dividendRepository.findUpcomingByTickersWithSecurity(Set.of("SBER"), LocalDate.now())).thenReturn(List.of(dividend));

        PortfolioPageResponseDto page = portfolioService.getPortfolioPage(userId, PortfolioSort.WEIGHT);

        assertThat(page.getUpcomingDividends()).hasSize(1);
        assertThat(page.getUpcomingDividends().get(0).getQuantity()).isEqualByComparingTo("200");
        // 10.00 * 200 = 2000.00 declared, 13% withheld = 260.00 -> 1740.00 credited.
        assertThat(page.getUpcomingDividends().get(0).getTotalAmount()).isEqualByComparingTo("1740.00");
    }

    // ─── getReceivedPayouts (all-time received payouts, for the "capital" page) ───────────────

    @Test
    @DisplayName("getReceivedPayouts — не ограничен 12 месяцами и не обращается к бирже: только БД (журнал сделок + дивиденды)")
    void getReceivedPayouts_noTwelveMonthFloor_touchesOnlyDatabase() {
        stubJournal(buy(sberSecurity(), "200", LocalDate.now().minusYears(3)));

        Dividend old = Dividend.builder().security(sberSecurity())
                .recordDate(LocalDate.now().minusYears(2))
                .amountPerShare(new BigDecimal("10.00"))
                .currency("RUB").build();
        when(dividendRepository.findByTickerInAndReceivedDateBeforeWithSecurity(Set.of("SBER"), LocalDate.now()))
                .thenReturn(List.of(old));

        List<UpcomingDividendDto> payouts = portfolioService.getReceivedPayouts(userId);

        assertThat(payouts).hasSize(1);
        // 10.00 * 200 = 2000.00 declared, 13% withheld = 260.00 -> 1740.00 credited.
        assertThat(payouts.get(0).getTotalAmount()).isEqualByComparingTo("1740.00");
        verifyNoInteractions(marketDataService, positionRepository, positionMapper);
    }

    @Test
    @DisplayName("getReceivedPayouts — иностранная валюта исключена из результата (учитывается только RUB)")
    void getReceivedPayouts_foreignCurrency_excluded() {
        stubJournal(buy(sberSecurity(), "200", LocalDate.now().minusYears(1)));

        Dividend rub = Dividend.builder().security(sberSecurity())
                .recordDate(LocalDate.now().minusMonths(1)).amountPerShare(new BigDecimal("10.00")).currency("RUB").build();
        Dividend usd = Dividend.builder().security(sberSecurity())
                .recordDate(LocalDate.now().minusMonths(1)).amountPerShare(new BigDecimal("5.00")).currency("USD").build();
        when(dividendRepository.findByTickerInAndReceivedDateBeforeWithSecurity(Set.of("SBER"), LocalDate.now()))
                .thenReturn(List.of(rub, usd));

        List<UpcomingDividendDto> payouts = portfolioService.getReceivedPayouts(userId);

        assertThat(payouts).hasSize(1);
        assertThat(payouts.get(0).getCurrency()).isEqualTo("RUB");
    }

    @Test
    @DisplayName("getReceivedPayouts — бумага продана целиком, но прошлая выплата с отсечкой во время владения всё равно учитывается")
    void getReceivedPayouts_fullySoldSecurity_stillCounted() {
        Security lkoh = security("LKOH");
        stubJournal(
                buy(lkoh, "2", LocalDate.now().minusMonths(6)),
                sell(lkoh, "2", LocalDate.now().minusMonths(1))
        );
        Dividend dividend = Dividend.builder().security(lkoh)
                .recordDate(LocalDate.now().minusMonths(3))
                .amountPerShare(new BigDecimal("397.00"))
                .currency("RUB").build();
        when(dividendRepository.findByTickerInAndReceivedDateBeforeWithSecurity(Set.of("LKOH"), LocalDate.now()))
                .thenReturn(List.of(dividend));

        List<UpcomingDividendDto> payouts = portfolioService.getReceivedPayouts(userId);

        assertThat(payouts).hasSize(1);
        assertThat(payouts.get(0).getQuantity()).isEqualByComparingTo("2");
        // 397.00 * 2 = 794.00 declared, 13% withheld floors to 103, so 691.00 is credited.
        assertThat(payouts.get(0).getTotalAmount()).isEqualByComparingTo("691.00");
    }

    @Test
    @DisplayName("getReceivedPayouts — нулевой остаток на дату отсечки исключает выплату из результата")
    void getReceivedPayouts_zeroHoldingAtRecordDate_excluded() {
        Security lkoh = security("LKOH");
        // Bought only after the record date — held nothing when the dividend was declared.
        stubJournal(buy(lkoh, "2", LocalDate.now().minusDays(1)));
        Dividend dividend = Dividend.builder().security(lkoh)
                .recordDate(LocalDate.now().minusMonths(1))
                .amountPerShare(new BigDecimal("397.00"))
                .currency("RUB").build();
        when(dividendRepository.findByTickerInAndReceivedDateBeforeWithSecurity(Set.of("LKOH"), LocalDate.now()))
                .thenReturn(List.of(dividend));

        List<UpcomingDividendDto> payouts = portfolioService.getReceivedPayouts(userId);

        assertThat(payouts).isEmpty();
    }

    @Test
    @DisplayName("getReceivedPayouts — пустой журнал сделок → пустой результат, дивиденды не запрашиваются")
    void getReceivedPayouts_emptyJournal_returnsEmpty_noDividendQuery() {
        stubJournal();

        List<UpcomingDividendDto> payouts = portfolioService.getReceivedPayouts(userId);

        assertThat(payouts).isEmpty();
        verifyNoInteractions(dividendRepository, marketDataService, positionRepository);
    }

    private UpcomingDividendDto dividendWithAmountPerShare(PortfolioPageResponseDto page, String amountPerShare) {
        return page.getUpcomingDividends().stream()
                .filter(d -> d.getAmountPerShare().compareTo(new BigDecimal(amountPerShare)) == 0)
                .findFirst().orElseThrow();
    }

    private void stubOnePosition() {
        Position position = Position.builder().id(UUID.randomUUID()).userId(userId)
                .security(sberSecurity()).quantity(new BigDecimal("200"))
                .averagePrice(new BigDecimal("280.00")).totalCost(new BigDecimal("56000.00")).build();
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));
        when(positionMapper.toDto(position)).thenReturn(PositionResponseDto.builder()
                .ticker("SBER").securityName("Сбербанк").securityType(SecurityType.STOCK).sector("Финансы")
                .quantity(position.getQuantity()).averagePrice(position.getAveragePrice())
                .totalCost(position.getTotalCost()).build());
        stubJournal(buy(sberSecurity(), "200", LocalDate.now().minusYears(2)));
    }

    private void stubEmptyDividendsAndTransactions() {
        when(dividendRepository.findByTickerInAndReceivedDateBetweenWithSecurity(any(), any(), any())).thenReturn(List.of());
        when(dividendRepository.findUpcomingByTickersWithSecurity(any(), any())).thenReturn(List.of());
        stubEmptyTransactions();
    }

    private void stubEmptyTransactions() {
        when(transactionRepository.findRecentByUserIdWithSecurity(any(), any())).thenReturn(List.of());
        when(transactionMapper.toDtoList(anyList())).thenReturn(List.of());
        when(transactionRepository.countByUserId(userId)).thenReturn(0L);
    }

    private void stubJournal(Transaction... transactions) {
        when(transactionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(transactions));
    }

    private Transaction buy(Security security, String quantity, LocalDate executedAtDate) {
        return transaction(security, quantity, executedAtDate, TransactionType.BUY);
    }

    private Transaction sell(Security security, String quantity, LocalDate executedAtDate) {
        return transaction(security, quantity, executedAtDate, TransactionType.SELL);
    }

    private Transaction transaction(Security security, String quantity, LocalDate executedAtDate, TransactionType type) {
        return Transaction.builder()
                .security(security)
                .type(type)
                .quantity(new BigDecimal(quantity))
                .price(BigDecimal.TEN)
                .executedAt(executedAtDate.atStartOfDay(MOSCOW).toInstant())
                .build();
    }

    private Security sberSecurity() {
        return security("SBER");
    }

    private Security security(String ticker) {
        return Security.builder().ticker(ticker).name(ticker).type(SecurityType.STOCK).sector("Финансы").build();
    }

    private PortfolioGroupingResult groupingResult() {
        return groupingResultWithTotalCost(BigDecimal.ZERO);
    }

    private PortfolioGroupingResult groupingResultWithTotalCost(BigDecimal totalCost) {
        return new PortfolioGroupingResult(
                List.of(),
                PortfolioAllocationDto.builder().byType(List.of()).bySector(List.of()).build(),
                List.of(),
                BigDecimal.ZERO, totalCost, BigDecimal.ZERO, null,
                BigDecimal.ZERO, null,
                1, 0, 0);
    }
}
