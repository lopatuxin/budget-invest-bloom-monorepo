package pyc.lopatuxin.investment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.investment.config.DividendTaxProperties;
import pyc.lopatuxin.investment.dto.request.ProjectionRequestDto;
import pyc.lopatuxin.investment.dto.response.ProjectionBreakdownItemDto;
import pyc.lopatuxin.investment.dto.response.ProjectionPayoutKind;
import pyc.lopatuxin.investment.dto.response.ProjectionPointDto;
import pyc.lopatuxin.investment.dto.response.ProjectionResultDto;
import pyc.lopatuxin.investment.entity.Dividend;
import pyc.lopatuxin.investment.entity.PriceHistory;
import pyc.lopatuxin.investment.entity.Position;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.DividendSource;
import pyc.lopatuxin.investment.entity.enums.DividendStatus;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.PayoutKind;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.repository.DividendRepository;
import pyc.lopatuxin.investment.repository.PositionRepository;
import pyc.lopatuxin.investment.repository.PriceHistoryRepository;
import pyc.lopatuxin.investment.service.market.MarketDataService;
import pyc.lopatuxin.investment.dto.response.SnapshotResult;

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

    private ProjectionService projectionService;

    private UUID userId;
    private Security sber;
    private Security ofz;
    private int currentYear;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        currentYear = LocalDate.now().getYear();
        sber = Security.builder()
                .ticker("SBER")
                .name("Сбербанк")
                .type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.READY)
                .build();
        ofz = Security.builder()
                .ticker("SU26219RMFS4")
                .name("ОФЗ 26219")
                .type(SecurityType.OFZ)
                .nominal(new BigDecimal("1000.00"))
                .historyStatus(HistoryStatus.READY)
                // Coupon sync already ran and found the coupon below — a bond with rows in
                // `dividends` but a never-set dividendsSyncedAt is not a state the sync leaves
                // behind (plan point 18 only fires historyPending for a bond that genuinely has
                // no coupon rows or has never been synced).
                .dividendsSyncedAt(Instant.now())
                .build();
        // BondPricing and DividendTaxProperties are real, tiny instances (both already covered by
        // their own tests) — same choice SecurityPageServiceTest makes for HoldingsOnDateService/
        // DividendTaxCalculator, so every assertion here is a genuine end-to-end number.
        DividendTaxProperties taxProperties = new DividendTaxProperties();
        projectionService = new ProjectionService(positionRepository, priceHistoryRepository, dividendRepository,
                marketDataService, new BondPricing(), taxProperties);
    }

    @Test
    @DisplayName("project — пустой список позиций → startValue=0, series пустой, разбивка пустая")
    void project_returnsEmptyResult_whenNoPositions() {
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of());

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(12);

        ProjectionResultDto result = projectionService.project(userId, req);

        assertThat(result.getStartValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getSeries()).isEmpty();
        assertThat(result.getBreakdown()).isEmpty();
        assertThat(result.getPriceGrowthPercent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getPayoutYieldPercent()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("project — рублёвая выплата облагается налогом по ставке DividendTaxProperties")
    void project_paysAfterTax_forRubPayouts() {
        Position position = buildPosition(sber, "10", "250.00");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));
        when(marketDataService.getSnapshots(List.of("SBER")))
                .thenReturn(Map.of("SBER", new SnapshotResult(new BigDecimal("300.00"), null, Instant.now(), false)));

        // No price history at all → no full calendar year is covered, so the payout falls back to
        // the trailing twelve months (plan point 6); a fixed date in "last calendar year" is not
        // reliably inside that rolling window, so it is anchored to "now" instead.
        LocalDate paymentDate = LocalDate.now().minusMonths(3);
        Dividend div = buildPaidDividend(sber, paymentDate, new BigDecimal("13.00"));
        when(dividendRepository.findBySecurity_Ticker("SBER")).thenReturn(List.of(div));
        when(priceHistoryRepository.findFirstByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc(
                eq("SBER"), eq(paymentDate)))
                .thenReturn(Optional.of(buildPriceHistory("SBER", paymentDate, new BigDecimal("100.00"))));

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(1);

        ProjectionResultDto result = projectionService.project(userId, req);

        // No full year covered → trailing-twelve-months fallback, divisor 1 (plan point 6): raw
        // yield 13/100 = 0.13, after 13% tax = 0.1131 → 11.3%, undiluted by a 5-year divisor.
        ProjectionBreakdownItemDto item = result.getBreakdown().get(0);
        assertThat(item.getPayoutYieldPercent()).isEqualByComparingTo("11.3");
        assertThat(item.getYearsCounted()).isEqualTo(1);
    }

    @Test
    @DisplayName("project — окно пяти лет: выплата только в одном году из пяти → доходность делится на 5, не на число лет с выплатой (пример Газпрома)")
    void project_fiveYearWindowWithOnlyOneYearOfPayouts_dividesByFiveNotByYearsWithData() {
        Position position = buildPosition(sber, "10", "250.00");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));
        when(marketDataService.getSnapshots(List.of("SBER")))
                .thenReturn(Map.of("SBER", new SnapshotResult(new BigDecimal("300.00"), null, Instant.now(), false)));

        // Five full years of price history so yearsCounted is capped at the window size (5), not
        // shortened by a thin price history — isolates the "one year of five paid" behaviour.
        List<PriceHistory> fullHistory = List.of(
                buildPriceHistory("SBER", LocalDate.now().minusYears(6), new BigDecimal("100.00")));
        when(priceHistoryRepository.findByTickerOrderByTradeDateAsc("SBER")).thenReturn(fullHistory);

        // Only one payout, four calendar years ago (inside the 5-year window, 1 of 5 years paid).
        LocalDate paymentDate = LocalDate.of(currentYear - 4, 7, 1);
        Dividend div = buildPaidDividend(sber, paymentDate, new BigDecimal("11.90"));
        when(dividendRepository.findBySecurity_Ticker("SBER")).thenReturn(List.of(div));
        when(priceHistoryRepository.findFirstByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc(
                eq("SBER"), eq(paymentDate)))
                .thenReturn(Optional.of(buildPriceHistory("SBER", paymentDate, new BigDecimal("100.00"))));

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(1);

        ProjectionResultDto result = projectionService.project(userId, req);

        ProjectionBreakdownItemDto item = result.getBreakdown().get(0);
        // raw yield 11.90/100 = 0.119, after 13% tax = 0.10353; divided by 5 (not 1) = 0.020706 → 2.1%
        assertThat(item.getPayoutYieldPercent()).isEqualByComparingTo("2.1");
        assertThat(item.getYearsCounted()).isEqualTo(5);
        assertThat(item.getLastPayoutYear()).isEqualTo(currentYear - 4);
    }

    @Test
    @DisplayName("project — выплата за пределами пятилетнего окна (шесть лет назад) не учитывается вовсе")
    void project_payoutOutsideFiveYearWindow_excludedFromYield() {
        Position position = buildPosition(sber, "10", "250.00");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));
        when(marketDataService.getSnapshots(List.of("SBER")))
                .thenReturn(Map.of("SBER", new SnapshotResult(new BigDecimal("300.00"), null, Instant.now(), false)));

        LocalDate oldPaymentDate = LocalDate.of(currentYear - 6, 7, 1);
        Dividend div = buildPaidDividend(sber, oldPaymentDate, new BigDecimal("20.00"));
        when(dividendRepository.findBySecurity_Ticker("SBER")).thenReturn(List.of(div));

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(1);

        ProjectionResultDto result = projectionService.project(userId, req);

        ProjectionBreakdownItemDto item = result.getBreakdown().get(0);
        assertThat(item.getPayoutYieldPercent()).isEqualByComparingTo(BigDecimal.ZERO);
        // Still reported, since the last payout ever is what "нет с {год}" needs on the page.
        assertThat(item.getLastPayoutYear()).isEqualTo(currentYear - 6);
        verify(priceHistoryRepository, never())
                .findFirstByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc(eq("SBER"), any());
    }

    @Test
    @DisplayName("project — история цен короче пяти лет и начинается не с начала года → делитель считает только полные покрытые годы (пример: с октября прошлого-прошлого года — 1 год)")
    void project_priceHistoryShorterThanFiveYears_dividesByFullyCoveredYearsOnly() {
        Position position = buildPosition(sber, "10", "250.00");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));
        when(marketDataService.getSnapshots(List.of("SBER")))
                .thenReturn(Map.of("SBER", new SnapshotResult(new BigDecimal("300.00"), null, Instant.now(), false)));

        // History starts in October of two years ago (plan point 6's own example): that year is
        // not fully covered (October is past its first trading week), so only last year counts.
        LocalDate historyStart = LocalDate.of(currentYear - 2, 10, 1);
        List<PriceHistory> fullHistory = List.of(
                buildPriceHistory("SBER", historyStart, new BigDecimal("100.00")));
        when(priceHistoryRepository.findByTickerOrderByTradeDateAsc("SBER")).thenReturn(fullHistory);

        LocalDate paymentDate = LocalDate.of(currentYear - 1, 7, 1);
        Dividend div = buildPaidDividend(sber, paymentDate, new BigDecimal("20.00"));
        when(dividendRepository.findBySecurity_Ticker("SBER")).thenReturn(List.of(div));
        when(priceHistoryRepository.findFirstByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc(
                eq("SBER"), eq(paymentDate)))
                .thenReturn(Optional.of(buildPriceHistory("SBER", paymentDate, new BigDecimal("200.00"))));

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(1);

        ProjectionResultDto result = projectionService.project(userId, req);

        assertThat(result.getBreakdown().get(0).getYearsCounted()).isEqualTo(1);
    }

    @Test
    @DisplayName("project — рост цены не старше 10 лет: точка старше окна не участвует в CAGR")
    void project_priceGrowthCappedAtTenYears() {
        Position position = buildPosition(sber, "10", "250.00");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));
        when(marketDataService.getSnapshots(List.of("SBER")))
                .thenReturn(Map.of("SBER", new SnapshotResult(new BigDecimal("300.00"), null, Instant.now(), false)));
        when(dividendRepository.findBySecurity_Ticker("SBER")).thenReturn(List.of());

        LocalDate now = LocalDate.now();
        // A point 11 years back at a wildly different price must be excluded by the 10-year cap —
        // if it were included, the CAGR would be a large negative/positive figure instead of ~0.
        List<PriceHistory> fullHistory = List.of(
                buildPriceHistory("SBER", now.minusYears(11), new BigDecimal("10.00")),
                buildPriceHistory("SBER", now.minusYears(9), new BigDecimal("100.00")),
                buildPriceHistory("SBER", now, new BigDecimal("100.00")));
        when(priceHistoryRepository.findByTickerOrderByTradeDateAsc("SBER")).thenReturn(fullHistory);

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(1);

        ProjectionResultDto result = projectionService.project(userId, req);

        // Flat 100 → 100 over the counted window: ~0% growth.
        assertThat(result.getBreakdown().get(0).getPriceGrowthPercent()).isEqualByComparingTo("0.0");
    }

    @Test
    @DisplayName("project — скачок цены более чем в три раза (дробление) сдвигает окно роста на день после скачка")
    void project_splitLikeJump_shiftsGrowthWindowAfterJump() {
        Position position = buildPosition(sber, "10", "250.00");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));
        when(marketDataService.getSnapshots(List.of("SBER")))
                .thenReturn(Map.of("SBER", new SnapshotResult(new BigDecimal("300.00"), null, Instant.now(), false)));
        when(dividendRepository.findBySecurity_Ticker("SBER")).thenReturn(List.of());

        LocalDate now = LocalDate.now();
        // Reverse split: price jumps from 10 to 100 (×10) partway through the window — without
        // the guard this would read as ~900% annual growth; the window must restart right after it.
        List<PriceHistory> fullHistory = List.of(
                buildPriceHistory("SBER", now.minusYears(5), new BigDecimal("10.00")),
                buildPriceHistory("SBER", now.minusYears(4), new BigDecimal("10.00")),
                buildPriceHistory("SBER", now.minusYears(4).plusDays(1), new BigDecimal("100.00")),
                buildPriceHistory("SBER", now, new BigDecimal("100.00")));
        when(priceHistoryRepository.findByTickerOrderByTradeDateAsc("SBER")).thenReturn(fullHistory);

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(1);

        ProjectionResultDto result = projectionService.project(userId, req);

        assertThat(result.getBreakdown().get(0).getPriceGrowthPercent()).isEqualByComparingTo("0.0");
    }

    @Test
    @DisplayName("project — выплата с record_date до скачка цены ×3 не учитывается в доходности выплат (реальный случай ВТБ на обратном дроблении)")
    void project_payoutBeforeSplitJump_excludedFromPayoutYield() {
        Position position = buildPosition(sber, "1000", "0.05");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));
        when(marketDataService.getSnapshots(List.of("SBER")))
                .thenReturn(Map.of("SBER", new SnapshotResult(new BigDecimal("5.00"), null, Instant.now(), false)));

        LocalDate now = LocalDate.now();
        LocalDate splitDate = now.minusYears(4).plusDays(1);
        // Reverse split: price jumps ×100 from 0.05 to 5.00 — the same jump detector that shifts
        // the price-growth window (plan point 12) now also gates which payouts are trustworthy
        // (plan point 1): a payout priced against the unadjusted 0.05 would read as an absurd
        // yield (VTBR 2021: a 7₽ coupon recalculated onto the post-split share count divided by a
        // pre-split price of ~0.05 produced a 2441% "yield").
        List<PriceHistory> fullHistory = List.of(
                buildPriceHistory("SBER", now.minusYears(5), new BigDecimal("0.05")),
                buildPriceHistory("SBER", splitDate, new BigDecimal("5.00")),
                buildPriceHistory("SBER", now, new BigDecimal("5.00")));
        when(priceHistoryRepository.findByTickerOrderByTradeDateAsc("SBER")).thenReturn(fullHistory);

        LocalDate prePlitPaymentDate = now.minusYears(4).minusDays(10);
        Dividend prePlitPayout = buildPaidDividend(sber, prePlitPaymentDate, new BigDecimal("7.00"));
        LocalDate postSplitPaymentDate = now.minusYears(1);
        Dividend postSplitPayout = buildPaidDividend(sber, postSplitPaymentDate, new BigDecimal("0.50"));
        when(dividendRepository.findBySecurity_Ticker("SBER"))
                .thenReturn(List.of(prePlitPayout, postSplitPayout));
        when(priceHistoryRepository.findFirstByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc(
                eq("SBER"), eq(postSplitPaymentDate)))
                .thenReturn(Optional.of(buildPriceHistory("SBER", postSplitPaymentDate, new BigDecimal("5.00"))));

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(1);

        ProjectionResultDto result = projectionService.project(userId, req);

        ProjectionBreakdownItemDto item = result.getBreakdown().get(0);
        // Only the post-split payout counts — a sane single-digit percentage, nowhere near the
        // thousands-of-percent the pre-split payout would have produced.
        assertThat(item.getPayoutYieldPercent()).isGreaterThan(BigDecimal.ZERO);
        assertThat(item.getPayoutYieldPercent()).isLessThan(new BigDecimal("100"));
        verify(priceHistoryRepository, never())
                .findFirstByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc(eq("SBER"), eq(prePlitPaymentDate));
    }

    @Test
    @DisplayName("project — строка MOEX в пределах 20 дней от строки TINVEST — двойник той же выплаты, считается один раз")
    void project_duplicatePayoutsWithin20Days_countedOnceByHigherPrioritySource() {
        Position position = buildPosition(sber, "10", "250.00");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));
        when(marketDataService.getSnapshots(List.of("SBER")))
                .thenReturn(Map.of("SBER", new SnapshotResult(new BigDecimal("300.00"), null, Instant.now(), false)));

        // Same real payout as two DB rows (unique per ticker+record_date, plan point 2): MOEX's
        // ex-dividend date carries no amount yet (a common MOEX quirk), Т-Инвестиции's fixation
        // date 5 days later carries the real one. Counting both would double the yield.
        LocalDate moexRecordDate = LocalDate.now().minusMonths(6);
        LocalDate tinvestRecordDate = moexRecordDate.plusDays(5);
        Dividend moexDiv = Dividend.builder()
                .id(UUID.randomUUID()).security(sber)
                .recordDate(moexRecordDate).paymentDate(moexRecordDate.plusDays(10))
                .amountPerShare(BigDecimal.ZERO).currency("RUB")
                .status(DividendStatus.PAID).source(DividendSource.MOEX).kind(PayoutKind.DIVIDEND)
                .build();
        Dividend tinvestDiv = Dividend.builder()
                .id(UUID.randomUUID()).security(sber)
                .recordDate(tinvestRecordDate).paymentDate(tinvestRecordDate.plusDays(10))
                .amountPerShare(new BigDecimal("27.71")).currency("RUB")
                .status(DividendStatus.PAID).source(DividendSource.TINVEST).kind(PayoutKind.DIVIDEND)
                .build();
        when(dividendRepository.findBySecurity_Ticker("SBER")).thenReturn(List.of(moexDiv, tinvestDiv));
        when(priceHistoryRepository.findFirstByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc(
                eq("SBER"), eq(tinvestDiv.getPaymentDate())))
                .thenReturn(Optional.of(buildPriceHistory("SBER", tinvestDiv.getPaymentDate(), new BigDecimal("100.00"))));

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(1);

        ProjectionResultDto result = projectionService.project(userId, req);

        ProjectionBreakdownItemDto item = result.getBreakdown().get(0);
        // Deduped to the TINVEST row alone: 27.71/100 = 0.2771, after 13% tax = 0.241077 → 24.1%.
        // Summing both (or picking MOEX's zero) would give a different figure.
        assertThat(item.getPayoutYieldPercent()).isEqualByComparingTo("24.1");
        verify(priceHistoryRepository, never())
                .findFirstByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc(eq("SBER"), eq(moexDiv.getPaymentDate()));
    }

    @Test
    @DisplayName("project — две разные выплаты одного источника в пределах 20 дней считаются обе")
    void project_sameSourcePayoutsWithin20Days_bothCounted() {
        Position position = buildPosition(sber, "10", "250.00");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));
        when(marketDataService.getSnapshots(List.of("SBER")))
                .thenReturn(Map.of("SBER", new SnapshotResult(new BigDecimal("300.00"), null, Instant.now(), false)));

        LocalDate firstRecordDate = LocalDate.now().minusMonths(6);
        LocalDate secondRecordDate = firstRecordDate.plusDays(9);
        Dividend first = Dividend.builder()
                .id(UUID.randomUUID()).security(sber)
                .recordDate(firstRecordDate).paymentDate(firstRecordDate.plusDays(10))
                .amountPerShare(new BigDecimal("10.00")).currency("RUB")
                .status(DividendStatus.PAID).source(DividendSource.MOEX).kind(PayoutKind.DIVIDEND)
                .build();
        Dividend second = Dividend.builder()
                .id(UUID.randomUUID()).security(sber)
                .recordDate(secondRecordDate).paymentDate(secondRecordDate.plusDays(10))
                .amountPerShare(new BigDecimal("20.00")).currency("RUB")
                .status(DividendStatus.PAID).source(DividendSource.MOEX).kind(PayoutKind.DIVIDEND)
                .build();
        when(dividendRepository.findBySecurity_Ticker("SBER")).thenReturn(List.of(first, second));
        when(priceHistoryRepository.findFirstByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc(eq("SBER"), any()))
                .thenAnswer(inv -> Optional.of(buildPriceHistory("SBER", inv.getArgument(1), new BigDecimal("100.00"))));

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(1);

        ProjectionResultDto result = projectionService.project(userId, req);

        ProjectionBreakdownItemDto item = result.getBreakdown().get(0);
        // Both count: (10 + 20)/100 = 0.30, after 13% tax = 0.261 → 26.1%.
        assertThat(item.getPayoutYieldPercent()).isEqualByComparingTo("26.1");
    }

    @Test
    @DisplayName("project — купоны облигации входят в выплаты наравне с дивидендами, payoutKind = COUPON")
    void project_bondCoupons_countAsPayoutYieldWithCouponKind() {
        Position position = buildPosition(ofz, "71", "981.60");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));
        // Quoted 99.95% of a 1000₽ nominal → 999.50₽/bond.
        when(marketDataService.getSnapshots(List.of("SU26219RMFS4")))
                .thenReturn(Map.of("SU26219RMFS4", new SnapshotResult(new BigDecimal("99.95"), null, Instant.now(), false)));

        // No price history stubbed → no full calendar year covered → trailing-twelve-months
        // fallback (plan point 6), so the payment date is anchored to "now" rather than a fixed
        // month in "last calendar year".
        LocalDate paymentDate = LocalDate.now().minusMonths(4);
        Dividend coupon = Dividend.builder()
                .id(UUID.randomUUID())
                .security(ofz)
                .recordDate(paymentDate.minusDays(1))
                .paymentDate(paymentDate)
                .amountPerShare(new BigDecimal("38.64"))
                .currency("RUB")
                .status(DividendStatus.PAID)
                .kind(PayoutKind.COUPON)
                .build();
        when(dividendRepository.findBySecurity_Ticker("SU26219RMFS4")).thenReturn(List.of(coupon));
        // Quoted 99.50 on the payment date → 995.00₽ ruble price for the yield ratio.
        when(priceHistoryRepository.findFirstByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc(
                eq("SU26219RMFS4"), eq(paymentDate)))
                .thenReturn(Optional.of(buildPriceHistory("SU26219RMFS4", paymentDate, new BigDecimal("99.50"))));

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(1);

        ProjectionResultDto result = projectionService.project(userId, req);

        ProjectionBreakdownItemDto item = result.getBreakdown().get(0);
        assertThat(item.getPayoutKind()).isEqualTo(ProjectionPayoutKind.COUPON);
        assertThat(item.getPayoutYieldPercent()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("project — облигация без загруженных купонов (синхронизация не выполнялась) → historyPending, payoutKind = NONE (плюс с ценовой историей)")
    void project_bondWithoutCoupons_isHistoryPendingWithNoneKind() {
        Position position = buildPosition(ofz, "71", "981.60");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));
        when(marketDataService.getSnapshots(List.of("SU26219RMFS4")))
                .thenReturn(Map.of("SU26219RMFS4", new SnapshotResult(new BigDecimal("99.95"), null, Instant.now(), false)));

        // Price history is complete — only the coupon side is missing (plan point 18: this must
        // still read as "pending", not "genuinely pays nothing", even though the price data alone
        // would otherwise make historyPending false).
        List<PriceHistory> fullHistory = List.of(
                buildPriceHistory("SU26219RMFS4", LocalDate.now().minusYears(6), new BigDecimal("100.00")),
                buildPriceHistory("SU26219RMFS4", LocalDate.now(), new BigDecimal("99.95")));
        when(priceHistoryRepository.findByTickerOrderByTradeDateAsc("SU26219RMFS4")).thenReturn(fullHistory);
        when(dividendRepository.findBySecurity_Ticker("SU26219RMFS4")).thenReturn(List.of());

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(1);

        ProjectionResultDto result = projectionService.project(userId, req);

        ProjectionBreakdownItemDto item = result.getBreakdown().get(0);
        assertThat(item.isHistoryPending()).isTrue();
        assertThat(item.getPayoutKind()).isEqualTo(ProjectionPayoutKind.NONE);
        assertThat(result.getPendingHistoryTickers()).contains("SU26219RMFS4");
    }

    @Test
    @DisplayName("project — бумага без выплат вовсе → payoutKind = NONE")
    void project_noPayoutsAtAll_payoutKindIsNone() {
        Position position = buildPosition(sber, "10", "250.00");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));
        when(marketDataService.getSnapshots(List.of("SBER")))
                .thenReturn(Map.of("SBER", new SnapshotResult(new BigDecimal("300.00"), null, Instant.now(), false)));
        when(dividendRepository.findBySecurity_Ticker("SBER")).thenReturn(List.of());

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(1);

        ProjectionResultDto result = projectionService.project(userId, req);

        assertThat(result.getBreakdown().get(0).getPayoutKind()).isEqualTo(ProjectionPayoutKind.NONE);
        assertThat(result.getBreakdown().get(0).getLastPayoutYear()).isNull();
    }

    @Test
    @DisplayName("project — разбивка сходится с итоговой ставкой: priceGrowthPercent + payoutYieldPercent = portfolioWeightedAnnualReturn × 100")
    void project_breakdownPercentagesSumToPortfolioRate() {
        Position sberPosition = buildPosition(sber, "10", "250.00");
        Position ofzPosition = buildPosition(ofz, "71", "981.60");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(sberPosition, ofzPosition));
        when(marketDataService.getSnapshots(List.of("SBER", "SU26219RMFS4"))).thenReturn(Map.of(
                "SBER", new SnapshotResult(new BigDecimal("300.00"), null, Instant.now(), false),
                "SU26219RMFS4", new SnapshotResult(new BigDecimal("99.95"), null, Instant.now(), false)));

        // Neither security has price history stubbed → trailing-twelve-months fallback (plan
        // point 6) for both, so payment dates are anchored to "now".
        LocalDate sberPaymentDate = LocalDate.now().minusMonths(3);
        Dividend sberDiv = buildPaidDividend(sber, sberPaymentDate, new BigDecimal("13.00"));
        when(dividendRepository.findBySecurity_Ticker("SBER")).thenReturn(List.of(sberDiv));
        when(priceHistoryRepository.findFirstByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc(
                eq("SBER"), eq(sberPaymentDate)))
                .thenReturn(Optional.of(buildPriceHistory("SBER", sberPaymentDate, new BigDecimal("260.00"))));

        LocalDate ofzPaymentDate = LocalDate.now().minusMonths(4);
        Dividend coupon = Dividend.builder()
                .id(UUID.randomUUID()).security(ofz).recordDate(ofzPaymentDate.minusDays(1))
                .paymentDate(ofzPaymentDate).amountPerShare(new BigDecimal("38.64")).currency("RUB")
                .status(DividendStatus.PAID).kind(PayoutKind.COUPON).build();
        when(dividendRepository.findBySecurity_Ticker("SU26219RMFS4")).thenReturn(List.of(coupon));
        when(priceHistoryRepository.findFirstByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc(
                eq("SU26219RMFS4"), eq(ofzPaymentDate)))
                .thenReturn(Optional.of(buildPriceHistory("SU26219RMFS4", ofzPaymentDate, new BigDecimal("99.50"))));

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(1);

        ProjectionResultDto result = projectionService.project(userId, req);

        BigDecimal sumOfParts = result.getPriceGrowthPercent().add(result.getPayoutYieldPercent());
        BigDecimal expectedTotal = result.getPortfolioWeightedAnnualReturn()
                .multiply(BigDecimal.valueOf(100)).setScale(1, java.math.RoundingMode.HALF_UP);
        assertThat(sumOfParts).isEqualByComparingTo(expectedTotal);
    }

    @Test
    @DisplayName("project — нет оплаченных дивидендов → тикер попадает в pendingHistoryTickers")
    void project_addsTickerToPending_whenNoPaidDividends() {
        Position position = buildPosition(sber, "5", "250.00");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));
        when(marketDataService.getSnapshots(List.of("SBER")))
                .thenReturn(Map.of("SBER", new SnapshotResult(new BigDecimal("250.00"), null, Instant.now(), false)));

        when(dividendRepository.findBySecurity_Ticker("SBER")).thenReturn(List.of());

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(3);

        ProjectionResultDto result = projectionService.project(userId, req);

        assertThat(result.getPendingHistoryTickers()).contains("SBER");
        assertThat(result.getBreakdown().get(0).isHistoryPending()).isTrue();
    }

    @Test
    @DisplayName("project — все дивиденды без цены в истории → тикер попадает в pendingHistoryTickers")
    void project_addsTickerToPending_whenNoPriceFoundForAnyDividend() {
        Position position = buildPosition(sber, "5", "250.00");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));
        when(marketDataService.getSnapshots(List.of("SBER")))
                .thenReturn(Map.of("SBER", new SnapshotResult(new BigDecimal("250.00"), null, Instant.now(), false)));

        // No price history stubbed → trailing-twelve-months fallback (plan point 6): the payment
        // date must fall inside that rolling window for the price lookup below to even run.
        LocalDate paymentDate = LocalDate.now().minusMonths(3);
        Dividend div = buildPaidDividend(sber, paymentDate, new BigDecimal("20.00"));
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
        Position position = buildPosition(sber, "10", "300.00");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));
        when(marketDataService.getSnapshots(List.of("SBER")))
                .thenReturn(Map.of("SBER", new SnapshotResult(new BigDecimal("300.00"), null, Instant.now(), false)));

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(3);
        req.setOverrides(Map.of("SBER", new BigDecimal("0.15")));

        ProjectionResultDto result = projectionService.project(userId, req);

        verify(dividendRepository, never()).findBySecurity_Ticker(eq("SBER"));
        verify(priceHistoryRepository, never())
                .findFirstByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc(eq("SBER"), any());
        verify(priceHistoryRepository, never()).findByTickerOrderByTradeDateAsc(eq("SBER"));
        assertThat(result.getBreakdown().get(0).getPayoutKind()).isEqualTo(ProjectionPayoutKind.NONE);
    }

    @Test
    @DisplayName("project — monthlyDeposit=10000 → каждый ProjectionPoint имеет deposit=10000")
    void project_addsMonthlyDeposit() {
        Position position = buildPosition(sber, "10", "300.00");
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

    @Test
    @DisplayName("project — startValue=0, monthlyDeposit=10000, без изъятий → contributed растёт на пополнение каждый месяц")
    void project_contributed_growsByMonthlyDeposit_whenNoWithdrawal() {
        Position position = buildPosition(sber, "10", "300.00");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));
        when(marketDataService.getSnapshots(List.of("SBER")))
                .thenReturn(Map.of("SBER", new SnapshotResult(new BigDecimal("300.00"), null, Instant.now(), false)));

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(3);
        req.setMonthlyDeposit(new BigDecimal("10000"));
        req.setWithdrawalRatePerYear(BigDecimal.ZERO);
        req.setOverrides(Map.of("SBER", BigDecimal.ZERO));

        ProjectionResultDto result = projectionService.project(userId, req);

        // startValue = 10 * 300.00 = 3000.00; contributed = startValue + deposit * month.
        assertThat(result.getSeries().get(0).getContributed()).isEqualByComparingTo("13000.00");
        assertThat(result.getSeries().get(1).getContributed()).isEqualByComparingTo("23000.00");
        assertThat(result.getSeries().get(2).getContributed()).isEqualByComparingTo("33000.00");
    }

    @Test
    @DisplayName("project — изъятие уменьшает contributed на ту же сумму, что вычитается из value")
    void project_contributed_shrinksByWithdrawal() {
        Position position = buildPosition(sber, "10", "300.00");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));
        when(marketDataService.getSnapshots(List.of("SBER")))
                .thenReturn(Map.of("SBER", new SnapshotResult(new BigDecimal("300.00"), null, Instant.now(), false)));

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(2);
        req.setMonthlyDeposit(BigDecimal.ZERO);
        req.setWithdrawalRatePerYear(new BigDecimal("0.12")); // 1% a month, flat
        req.setOverrides(Map.of("SBER", BigDecimal.ZERO));

        ProjectionResultDto result = projectionService.project(userId, req);

        ProjectionPointDto firstPoint = result.getSeries().get(0);
        // No return, no deposit: contributed drops by exactly the withdrawal taken that month,
        // same as value — the two stay in lockstep whenever nothing is actually earned.
        assertThat(firstPoint.getContributed())
                .isEqualByComparingTo(new BigDecimal("3000.00").subtract(firstPoint.getWithdrawal()));
    }

    @Test
    @DisplayName("project — contributedTotal равен contributed последней точки, earned = значение последней точки минус contributedTotal")
    void project_contributedTotalAndEarned_matchLastPoint() {
        Position position = buildPosition(sber, "10", "300.00");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));
        when(marketDataService.getSnapshots(List.of("SBER")))
                .thenReturn(Map.of("SBER", new SnapshotResult(new BigDecimal("300.00"), null, Instant.now(), false)));

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(6);
        req.setMonthlyDeposit(new BigDecimal("5000"));
        req.setWithdrawalRatePerYear(BigDecimal.ZERO);
        req.setOverrides(Map.of("SBER", new BigDecimal("0.10")));

        ProjectionResultDto result = projectionService.project(userId, req);

        ProjectionPointDto lastPoint = result.getSeries().get(result.getSeries().size() - 1);
        assertThat(result.getContributedTotal()).isEqualByComparingTo(lastPoint.getContributed());
        assertThat(result.getEarned()).isEqualByComparingTo(lastPoint.getValue().subtract(lastPoint.getContributed()));
    }

    @Test
    @DisplayName("project — пустой портфель → contributedTotal и earned равны нулю")
    void project_emptyPortfolio_contributedTotalAndEarnedAreZero() {
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of());

        ProjectionRequestDto req = new ProjectionRequestDto();
        req.setHorizonMonths(12);

        ProjectionResultDto result = projectionService.project(userId, req);

        assertThat(result.getContributedTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getEarned()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private Position buildPosition(Security security, String quantity, String averagePrice) {
        return Position.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .security(security)
                .quantity(new BigDecimal(quantity))
                .averagePrice(new BigDecimal(averagePrice))
                .totalCost(new BigDecimal(quantity).multiply(new BigDecimal(averagePrice)))
                .build();
    }

    private Dividend buildPaidDividend(Security security, LocalDate paymentDate, BigDecimal amountPerShare) {
        return Dividend.builder()
                .id(UUID.randomUUID())
                .security(security)
                .recordDate(paymentDate.minusDays(14))
                .paymentDate(paymentDate)
                .amountPerShare(amountPerShare)
                .currency("RUB")
                .status(DividendStatus.PAID)
                .kind(PayoutKind.DIVIDEND)
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
