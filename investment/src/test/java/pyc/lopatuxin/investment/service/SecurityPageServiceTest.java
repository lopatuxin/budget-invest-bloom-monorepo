package pyc.lopatuxin.investment.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.investment.config.DividendTaxProperties;
import pyc.lopatuxin.investment.dto.response.SecurityEventDto;
import pyc.lopatuxin.investment.dto.response.SecurityEventKind;
import pyc.lopatuxin.investment.dto.response.SecurityMarkerDto;
import pyc.lopatuxin.investment.dto.response.SecurityPageResponseDto;
import pyc.lopatuxin.investment.dto.response.SnapshotResult;
import pyc.lopatuxin.investment.entity.Dividend;
import pyc.lopatuxin.investment.entity.Position;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.Transaction;
import pyc.lopatuxin.investment.entity.enums.DividendSource;
import pyc.lopatuxin.investment.entity.enums.DividendStatus;
import pyc.lopatuxin.investment.entity.enums.PayoutKind;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.entity.enums.TransactionType;
import pyc.lopatuxin.investment.mapper.PositionMapper;
import pyc.lopatuxin.investment.mapper.PositionMapperImpl;
import pyc.lopatuxin.investment.repository.DividendRepository;
import pyc.lopatuxin.investment.repository.PositionRepository;
import pyc.lopatuxin.investment.repository.TransactionRepository;
import pyc.lopatuxin.investment.service.market.MarketDataService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// HoldingsOnDateService, DividendTaxCalculator and PortfolioGroupingService are real instances
// (not mocks), same choice as PortfolioServiceTest: all three are tiny, already covered by their
// own tests, and using the real arithmetic here makes every assertion a genuine end-to-end number.
// PositionMapper is the real generated MapStruct implementation, not a mock: it is pure field
// copying, and using it keeps positions built via the same route as production (Position entity in,
// PositionResponseDto out).
@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityPageServiceTest")
class SecurityPageServiceTest {

    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.13");
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final String TICKER = "LKOH";
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 12);

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private DividendRepository dividendRepository;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private MarketDataService marketDataService;

    private final PositionMapper positionMapper = new PositionMapperImpl();

    private SecurityPageService securityPageService;
    private final UUID userId = UUID.randomUUID();
    private TimeZone originalDefaultTimeZone;

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
        securityPageService = new SecurityPageService(transactionRepository, dividendRepository, positionRepository,
                positionMapper, marketDataService, new PortfolioGroupingService(new BondPricing()), new HoldingsOnDateService(),
                new DividendTaxCalculator(taxProperties), new BondPricing());
    }

    @Test
    @DisplayName("неизвестный тикер или тикер без сделок пользователя → 404")
    void getSecurityPage_noTransactions_throwsNotFound() {
        when(transactionRepository.findByUserIdAndTickerWithSecurity(userId, TICKER)).thenReturn(List.of());

        assertThatThrownBy(() -> securityPageService.getSecurityPage(userId, TICKER, TODAY))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("тикер в нижнем регистре приводится к верхнему")
    void getSecurityPage_lowercaseTicker_normalizedToUpperCase() {
        when(transactionRepository.findByUserIdAndTickerWithSecurity(userId, TICKER)).thenReturn(List.of());

        assertThatThrownBy(() -> securityPageService.getSecurityPage(userId, "lkoh", TODAY))
                .isInstanceOf(EntityNotFoundException.class);
        verify(transactionRepository).findByUserIdAndTickerWithSecurity(userId, TICKER);
    }

    @Test
    @DisplayName("проигрывание журнала: BUY-SELL-BUY → количество, вложено, средняя после каждого события; первая сделка помечена")
    void getSecurityPage_journalReplay_tracksQuantityInvestedAndAverage() {
        Transaction buy1 = tx(TransactionType.BUY, "10", "100.00", "2026-01-10T10:00:00Z", "2026-01-10T10:00:00Z");
        Transaction sell1 = tx(TransactionType.SELL, "4", "120.00", "2026-03-10T10:00:00Z", "2026-03-10T10:00:00Z");
        Transaction buy2 = tx(TransactionType.BUY, "5", "110.00", "2026-05-10T10:00:00Z", "2026-05-10T10:00:00Z");
        stubJournal(buy1, sell1, buy2);
        stubNoDividends();
        stubNoPositions();

        SecurityPageResponseDto page = securityPageService.getSecurityPage(userId, TICKER, TODAY);

        SecurityEventDto buyEvent1 = eventByTransactionId(page, buy1.getId());
        assertThat(buyEvent1.getFirst()).isTrue();
        assertThat(buyEvent1.getAmount()).isEqualByComparingTo("1000.00");
        assertThat(buyEvent1.getPositionAfter().getQuantity()).isEqualByComparingTo("10");
        assertThat(buyEvent1.getPositionAfter().getInvested()).isEqualByComparingTo("1000.00");
        assertThat(buyEvent1.getPositionAfter().getAveragePrice()).isEqualByComparingTo("100.00");

        SecurityEventDto sellEvent = eventByTransactionId(page, sell1.getId());
        assertThat(sellEvent.getFirst()).isNull();
        assertThat(sellEvent.getRealizedPnl()).isEqualByComparingTo("80.00"); // (120-100)*4
        assertThat(sellEvent.getPositionAfter().getQuantity()).isEqualByComparingTo("6");
        assertThat(sellEvent.getPositionAfter().getInvested()).isEqualByComparingTo("600.00");
        assertThat(sellEvent.getPositionAfter().getAveragePrice()).isEqualByComparingTo("100.00");

        SecurityEventDto buyEvent2 = eventByTransactionId(page, buy2.getId());
        assertThat(buyEvent2.getFirst()).isFalse();
        assertThat(buyEvent2.getPositionAfter().getQuantity()).isEqualByComparingTo("11");
        assertThat(buyEvent2.getPositionAfter().getInvested()).isEqualByComparingTo("1150.00");
        assertThat(buyEvent2.getPositionAfter().getAveragePrice()).isEqualByComparingTo("104.55"); // 1150/11

        assertThat(page.getTransactionsCount()).isEqualTo(3);
        assertThat(page.getBuysCount()).isEqualTo(2);
        assertThat(page.getSellsCount()).isEqualTo(1);
        assertThat(page.getResult().getRealizedPnl()).isEqualByComparingTo("80.00");
        assertThat(page.getResult().getInvestedAll()).isEqualByComparingTo("1550.00"); // 1000.00 + 550.00, sells never subtracted
    }

    @Test
    @DisplayName("markers — по одной отметке на сделку, с её датой, видом, количеством и ценой")
    void getSecurityPage_markers_onePerTransaction() {
        Transaction buy1 = tx(TransactionType.BUY, "3", "6540.00", "2026-01-14T10:00:00Z", "2026-01-14T10:00:00Z");
        Transaction sell1 = tx(TransactionType.SELL, "1", "7000.00", "2026-02-01T10:00:00Z", "2026-02-01T10:00:00Z");
        stubJournal(buy1, sell1);
        stubNoDividends();
        stubNoPositions();

        SecurityPageResponseDto page = securityPageService.getSecurityPage(userId, TICKER, TODAY);

        assertThat(page.getMarkers()).hasSize(2);
        SecurityMarkerDto marker1 = page.getMarkers().get(0);
        assertThat(marker1.getDate()).isEqualTo(LocalDate.of(2026, 1, 14));
        assertThat(marker1.getKind()).isEqualTo(TransactionType.BUY);
        assertThat(marker1.getQuantity()).isEqualByComparingTo("3");
        assertThat(marker1.getPrice()).isEqualByComparingTo("6540.00");
    }

    @Test
    @DisplayName("события — по убыванию даты, при равной дате по времени создания (позже созданное — выше)")
    void getSecurityPage_eventsOrder_descendingByDateThenCreatedAt() {
        Transaction earlier = tx(TransactionType.BUY, "1", "100.00", "2026-01-01T10:00:00Z", "2026-01-01T10:00:00Z");
        Transaction sameDayCreatedFirst = tx(TransactionType.BUY, "1", "100.00", "2026-06-01T09:00:00Z", "2026-06-01T09:00:00Z");
        Transaction sameDayCreatedSecond = tx(TransactionType.BUY, "1", "100.00", "2026-06-01T15:00:00Z", "2026-06-01T15:00:00Z");
        stubJournal(earlier, sameDayCreatedFirst, sameDayCreatedSecond);
        stubNoDividends();
        stubNoPositions();

        SecurityPageResponseDto page = securityPageService.getSecurityPage(userId, TICKER, TODAY);

        assertThat(page.getEvents()).extracting(SecurityEventDto::getTransactionId)
                .containsExactly(sameDayCreatedSecond.getId(), sameDayCreatedFirst.getId(), earlier.getId());
    }

    @Test
    @DisplayName("дивиденд полученный: количество на дату отсечки по журналу, сумма после НДФЛ")
    void getSecurityPage_paidDividend_quantityAtRecordDateAndTaxApplied() {
        Transaction buy = tx(TransactionType.BUY, "33", "1.00", "2025-01-01T10:00:00Z", "2025-01-01T10:00:00Z");
        stubJournal(buy);
        Dividend dividend = dividend(TODAY.minusMonths(3), null, "9.71", "RUB", DividendStatus.PAID, DividendSource.TINVEST);
        when(dividendRepository.findBySecurity_Ticker(TICKER)).thenReturn(List.of(dividend));
        stubNoPositions();

        SecurityPageResponseDto page = securityPageService.getSecurityPage(userId, TICKER, TODAY);

        SecurityEventDto paidEvent = onlyEventOfKind(page, SecurityEventKind.DIVIDEND_PAID);
        assertThat(paidEvent.getQuantity()).isEqualByComparingTo("33");
        assertThat(paidEvent.getNetAmount()).isEqualByComparingTo("279.43"); // 9.71*33=320.43, 13% -> 41 -> 279.43
        assertThat(paidEvent.getSource()).isEqualTo(DividendSource.TINVEST);
        assertThat(page.getDividends().getTotalAll()).isEqualByComparingTo("279.43");
    }

    @Test
    @DisplayName("сделка ровно в день отсечки не учитывается (Т+1) — дивиденд при нулевом количестве пропущен")
    void getSecurityPage_tradeOnRecordDate_notCounted_dividendSkipped() {
        LocalDate recordDate = TODAY.minusDays(5);
        Transaction buyOnRecordDate = tx(TransactionType.BUY, "10", "100.00",
                recordDate.atStartOfDay(MOSCOW).toInstant().toString(), recordDate.atStartOfDay(MOSCOW).toInstant().toString());
        stubJournal(buyOnRecordDate);
        Dividend dividend = dividend(recordDate, null, "5.00", "RUB", DividendStatus.PAID, DividendSource.MOEX);
        when(dividendRepository.findBySecurity_Ticker(TICKER)).thenReturn(List.of(dividend));
        stubNoPositions();

        SecurityPageResponseDto page = securityPageService.getSecurityPage(userId, TICKER, TODAY);

        assertThat(page.getEvents()).noneMatch(e -> e.getKind() == SecurityEventKind.DIVIDEND_PAID);
        assertThat(page.getDividends().getTotalAll()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("дивиденд в иностранной валюте: показан в ленте, но не входит в total12m/totalAll")
    void getSecurityPage_foreignCurrencyDividend_shownButExcludedFromSums() {
        Transaction buy = tx(TransactionType.BUY, "10", "100.00", "2025-01-01T10:00:00Z", "2025-01-01T10:00:00Z");
        stubJournal(buy);
        Dividend usdDividend = dividend(TODAY.minusMonths(1), null, "5.00", "USD", DividendStatus.PAID, DividendSource.MOEX);
        when(dividendRepository.findBySecurity_Ticker(TICKER)).thenReturn(List.of(usdDividend));
        stubNoPositions();

        SecurityPageResponseDto page = securityPageService.getSecurityPage(userId, TICKER, TODAY);

        SecurityEventDto event = onlyEventOfKind(page, SecurityEventKind.DIVIDEND_PAID);
        assertThat(event.getCurrency()).isEqualTo("USD");
        assertThat(event.getNetAmount()).isEqualByComparingTo("50.00"); // 5.00*10, untaxed, foreign currency
        assertThat(page.getDividends().getTotalAll()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(page.getDividends().getTotal12m()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("предстоящий дивиденд: отсечка впереди → текущее количество; отсечка прошла → количество на отсечку по журналу")
    void getSecurityPage_upcomingDividend_quantityRuleDependsOnRecordDate() {
        Transaction buy1 = tx(TransactionType.BUY, "200", "280.00", "2024-09-01T10:00:00Z", "2024-09-01T10:00:00Z");
        Transaction buy2 = tx(TransactionType.BUY, "100", "290.00", "2026-09-10T10:00:00Z", "2026-09-10T10:00:00Z");
        stubJournal(buy1, buy2);
        Dividend pastRecordDate = dividend(TODAY.minusDays(5), TODAY.plusDays(2), "10.00", "RUB", DividendStatus.ANNOUNCED, DividendSource.TINVEST);
        Dividend futureRecordDate = dividend(TODAY.plusDays(5), null, "20.00", "RUB", DividendStatus.ANNOUNCED, DividendSource.TINVEST);
        when(dividendRepository.findBySecurity_Ticker(TICKER)).thenReturn(List.of(pastRecordDate, futureRecordDate));
        stubNoPositions();

        SecurityPageResponseDto page = securityPageService.getSecurityPage(userId, TICKER, TODAY);

        SecurityEventDto pastEvent = page.getEvents().stream()
                .filter(e -> e.getKind() == SecurityEventKind.DIVIDEND_UPCOMING)
                .filter(e -> e.getAmountPerShare().compareTo(new BigDecimal("10.00")) == 0)
                .findFirst().orElseThrow();
        SecurityEventDto futureEvent = page.getEvents().stream()
                .filter(e -> e.getKind() == SecurityEventKind.DIVIDEND_UPCOMING)
                .filter(e -> e.getAmountPerShare().compareTo(new BigDecimal("20.00")) == 0)
                .findFirst().orElseThrow();
        // The top-up (2026-09-10) came after the past record date (today-5d) — not counted there.
        assertThat(pastEvent.getQuantity()).isEqualByComparingTo("200");
        // Record date has not happened yet — nobody knows the future holding, today's full 300 is used.
        assertThat(futureEvent.getQuantity()).isEqualByComparingTo("300");
    }

    @Test
    @DisplayName("dividends.next — ближайшее предстоящее по дате отсечки/выплаты, как на портфеле")
    void getSecurityPage_dividendsNext_earliestUpcomingByEffectiveDate() {
        Transaction buy = tx(TransactionType.BUY, "12", "6419.17", "2025-01-01T10:00:00Z", "2025-01-01T10:00:00Z");
        stubJournal(buy);
        // Past record date, payment still ahead in 3 days — sorts by paymentDate.
        Dividend soonByPayment = dividend(TODAY.minusDays(2), TODAY.plusDays(3), "10.00", "RUB", DividendStatus.ANNOUNCED, DividendSource.TINVEST);
        // Record date itself is 10 days out — sorts by recordDate.
        Dividend laterByRecordDate = dividend(TODAY.plusDays(10), null, "20.00", "RUB", DividendStatus.ANNOUNCED, DividendSource.TINVEST);
        when(dividendRepository.findBySecurity_Ticker(TICKER)).thenReturn(List.of(laterByRecordDate, soonByPayment));
        stubNoPositions();

        SecurityPageResponseDto page = securityPageService.getSecurityPage(userId, TICKER, TODAY);

        assertThat(page.getDividends().getNext().getAmountPerShare()).isEqualByComparingTo("10.00");
        assertThat(page.getDividends().getNext().getPaymentDate()).isEqualTo(TODAY.plusDays(3));
    }

    @Test
    @DisplayName("total12m — окно 12 месяцев по дате получения, totalAll — за всё время")
    void getSecurityPage_total12mWindow_and_totalAll() {
        Transaction buy = tx(TransactionType.BUY, "10", "100.00", "2023-01-01T10:00:00Z", "2023-01-01T10:00:00Z");
        stubJournal(buy);
        Dividend within12m = dividend(TODAY.minusMonths(2), null, "10.00", "RUB", DividendStatus.PAID, DividendSource.MOEX);
        Dividend outside12m = dividend(TODAY.minusMonths(13), null, "10.00", "RUB", DividendStatus.PAID, DividendSource.MOEX);
        when(dividendRepository.findBySecurity_Ticker(TICKER)).thenReturn(List.of(within12m, outside12m));
        stubNoPositions();

        SecurityPageResponseDto page = securityPageService.getSecurityPage(userId, TICKER, TODAY);

        // 10.00*10=100.00 declared, 13% withheld = 13, so 87.00 credited, per paid dividend.
        assertThat(page.getDividends().getTotal12m()).isEqualByComparingTo("87.00");
        assertThat(page.getDividends().getTotalAll()).isEqualByComparingTo("174.00");
    }

    @Test
    @DisplayName("закрытая позиция: position = null, pricePnl = 0, результат — по сделкам и дивидендам")
    void getSecurityPage_closedPosition_positionNullAndPricePnlZero() {
        Transaction buy = tx(TransactionType.BUY, "2", "100.00", "2026-01-01T10:00:00Z", "2026-01-01T10:00:00Z");
        Transaction sell = tx(TransactionType.SELL, "2", "150.00", "2026-06-01T10:00:00Z", "2026-06-01T10:00:00Z");
        stubJournal(buy, sell);
        stubNoDividends();
        stubNoPositions();

        SecurityPageResponseDto page = securityPageService.getSecurityPage(userId, TICKER, TODAY);

        assertThat(page.getPosition()).isNull();
        assertThat(page.getResult().getPricePnl()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(page.getResult().getRealizedPnl()).isEqualByComparingTo("100.00"); // (150-100)*2
    }

    @Test
    @DisplayName("нет снимка биржи → price = null, страница всё равно строится")
    void getSecurityPage_noSnapshot_priceNull() {
        Transaction buy = tx(TransactionType.BUY, "1", "100.00", "2026-01-01T10:00:00Z", "2026-01-01T10:00:00Z");
        stubJournal(buy);
        stubNoDividends();
        stubNoPositions();

        SecurityPageResponseDto page = securityPageService.getSecurityPage(userId, TICKER, TODAY);

        assertThat(page.getPrice()).isNull();
    }

    @Test
    @DisplayName("одна открытая позиция: цена и pnl берутся из единственного снимка biржи")
    void getSecurityPage_openPosition_priceAndPnlFromSnapshot() {
        Transaction buy = tx(TransactionType.BUY, "12", "6419.17", "2025-01-01T10:00:00Z", "2025-01-01T10:00:00Z");
        stubJournal(buy);
        stubNoDividends();
        Position position = position(TICKER, "12", "6419.17", "77030.04");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(position));
        when(marketDataService.getSnapshots(Set.of(TICKER)))
                .thenReturn(Map.of(TICKER, new SnapshotResult(new BigDecimal("7105.83"), new BigDecimal("7000.00"), Instant.now(), false)));

        SecurityPageResponseDto page = securityPageService.getSecurityPage(userId, TICKER, TODAY);

        assertThat(page.getPosition().getWeightPercent()).isEqualByComparingTo("100.0");
        assertThat(page.getResult().getPricePnl()).isEqualByComparingTo("8239.92"); // (7105.83-6419.17)*12
    }

    @Test
    @DisplayName("ОФЗ: цена в шапке — в рублях (не в процентах номинала), купон в ленте — с видом COUPON")
    void getSecurityPage_ofz_priceInRublesAndCouponEventHasCouponKind() {
        String ofzTicker = "SU26219RMFS4";
        Security ofz = Security.builder()
                .ticker(ofzTicker).name("ОФЗ 26219").type(SecurityType.OFZ)
                .nominal(new BigDecimal("1000.00")).build();
        Transaction buy = Transaction.builder()
                .id(UUID.randomUUID()).userId(userId).security(ofz).type(TransactionType.BUY)
                .quantity(new BigDecimal("71")).price(new BigDecimal("981.60"))
                .executedAt(Instant.parse("2026-01-10T10:00:00Z")).createdAt(Instant.parse("2026-01-10T10:00:00Z"))
                .build();
        when(transactionRepository.findByUserIdAndTickerWithSecurity(userId, ofzTicker)).thenReturn(List.of(buy));

        Dividend coupon = Dividend.builder()
                .id(UUID.randomUUID()).security(ofz)
                .recordDate(TODAY.minusMonths(1).minusDays(1)).paymentDate(TODAY.minusMonths(1))
                .amountPerShare(new BigDecimal("38.64")).currency("RUB")
                .status(DividendStatus.PAID).source(DividendSource.TINVEST).kind(PayoutKind.COUPON)
                .build();
        when(dividendRepository.findBySecurity_Ticker(ofzTicker)).thenReturn(List.of(coupon));

        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of());
        // Quoted 99.95% of a 1000₽ nominal → 999.50₽ a bond.
        when(marketDataService.getSnapshots(anySet()))
                .thenReturn(Map.of(ofzTicker, new SnapshotResult(new BigDecimal("99.95"), new BigDecimal("99.80"), Instant.now(), false)));

        SecurityPageResponseDto page = securityPageService.getSecurityPage(userId, ofzTicker, TODAY);

        assertThat(page.getPrice().getCurrent()).isEqualByComparingTo("999.50");
        assertThat(page.getPrice().getPreviousClose()).isEqualByComparingTo("998.00");
        SecurityEventDto couponEvent = onlyEventOfKind(page, SecurityEventKind.DIVIDEND_PAID);
        assertThat(couponEvent.getPayoutKind()).isEqualTo(PayoutKind.COUPON);
    }

    @Test
    @DisplayName("ОФЗ: НКД из снимка попадает в price.accruedInterest, номинал известен → nominalDefaulted = false")
    void getSecurityPage_ofz_priceCarriesAccruedInterestAndNominalDefaultedFalse() {
        String ofzTicker = "SU26219RMFS4";
        Security ofz = Security.builder()
                .ticker(ofzTicker).name("ОФЗ 26219").type(SecurityType.OFZ)
                .nominal(new BigDecimal("1000.00")).build();
        Transaction buy = Transaction.builder()
                .id(UUID.randomUUID()).userId(userId).security(ofz).type(TransactionType.BUY)
                .quantity(new BigDecimal("71")).price(new BigDecimal("981.60"))
                .executedAt(Instant.parse("2026-01-10T10:00:00Z")).createdAt(Instant.parse("2026-01-10T10:00:00Z"))
                .build();
        when(transactionRepository.findByUserIdAndTickerWithSecurity(userId, ofzTicker)).thenReturn(List.of(buy));
        when(dividendRepository.findBySecurity_Ticker(ofzTicker)).thenReturn(List.of());
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of());
        when(marketDataService.getSnapshots(anySet()))
                .thenReturn(Map.of(ofzTicker, new SnapshotResult(new BigDecimal("99.95"), new BigDecimal("99.80"),
                        Instant.now(), false, new BigDecimal("12.34"))));

        SecurityPageResponseDto page = securityPageService.getSecurityPage(userId, ofzTicker, TODAY);

        assertThat(page.getPrice().getAccruedInterest()).isEqualByComparingTo("12.34");
        assertThat(page.getPrice().isNominalDefaulted()).isFalse();
    }

    @Test
    @DisplayName("облигация без известного номинала (биржа не вернула FACEVALUE) → nominalDefaulted = true")
    void getSecurityPage_bondWithoutNominal_nominalDefaultedTrue() {
        String bondTicker = "RU000A10FAK6";
        Security bond = Security.builder()
                .ticker(bondTicker).name("Облигация").type(SecurityType.BOND).nominal(null).build();
        Transaction buy = Transaction.builder()
                .id(UUID.randomUUID()).userId(userId).security(bond).type(TransactionType.BUY)
                .quantity(new BigDecimal("10")).price(new BigDecimal("950.00"))
                .executedAt(Instant.parse("2026-01-10T10:00:00Z")).createdAt(Instant.parse("2026-01-10T10:00:00Z"))
                .build();
        when(transactionRepository.findByUserIdAndTickerWithSecurity(userId, bondTicker)).thenReturn(List.of(buy));
        when(dividendRepository.findBySecurity_Ticker(bondTicker)).thenReturn(List.of());
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of());
        when(marketDataService.getSnapshots(anySet()))
                .thenReturn(Map.of(bondTicker, new SnapshotResult(new BigDecimal("95.00"), null, Instant.now(), false)));

        SecurityPageResponseDto page = securityPageService.getSecurityPage(userId, bondTicker, TODAY);

        assertThat(page.getPrice().isNominalDefaulted()).isTrue();
    }

    @Test
    @DisplayName("ровно один вызов getSnapshots со всеми тикерами открытых позиций и тикером страницы; getSnapshot не вызывается")
    void getSecurityPage_pricing_singleBatchCallWithAllTickers_closedPageTickerIncluded() {
        Transaction buy = tx(TransactionType.BUY, "2", "100.00", "2026-01-01T10:00:00Z", "2026-01-01T10:00:00Z");
        Transaction sell = tx(TransactionType.SELL, "2", "150.00", "2026-06-01T10:00:00Z", "2026-06-01T10:00:00Z");
        stubJournal(buy, sell); // LKOH position is closed by the journal, but still open elsewhere in DB is not assumed
        stubNoDividends();
        Position otherOpenPosition = position("SBER", "5", "250.00", "1250.00");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(otherOpenPosition));
        when(marketDataService.getSnapshots(anySet())).thenReturn(Map.of(
                "SBER", new SnapshotResult(new BigDecimal("300.00"), new BigDecimal("290.00"), Instant.now(), false)
        ));

        securityPageService.getSecurityPage(userId, TICKER, TODAY);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> tickersCaptor = ArgumentCaptor.forClass(Set.class);
        verify(marketDataService).getSnapshots(tickersCaptor.capture());
        assertThat(tickersCaptor.getValue()).containsExactlyInAnyOrder("SBER", TICKER);
        verify(marketDataService, never()).getSnapshot(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("weightPercent позиции страницы — доля её стоимости в сумме стоимостей всех позиций по тем же снимкам")
    void getSecurityPage_weightPercent_shareOfTotalPositionsValue() {
        Transaction buy = tx(TransactionType.BUY, "10", "100.00", "2025-01-01T10:00:00Z", "2025-01-01T10:00:00Z");
        stubJournal(buy);
        stubNoDividends();
        Position pagePosition = position(TICKER, "10", "100.00", "1000.00");
        Position otherPosition = position("SBER", "20", "200.00", "4000.00");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(pagePosition, otherPosition));
        when(marketDataService.getSnapshots(anySet())).thenReturn(Map.of(
                TICKER, new SnapshotResult(new BigDecimal("150.00"), new BigDecimal("140.00"), Instant.now(), false),
                "SBER", new SnapshotResult(new BigDecimal("300.00"), new BigDecimal("290.00"), Instant.now(), false)
        ));
        // LKOH value = 150*10 = 1500.00, SBER value = 300*20 = 6000.00, total = 7500.00.

        SecurityPageResponseDto page = securityPageService.getSecurityPage(userId, TICKER, TODAY);

        assertThat(page.getPosition().getWeightPercent()).isEqualByComparingTo("20.0"); // 1500/7500*100
    }

    @Test
    @DisplayName("закрытая позиция при наличии снимка: position = null, price заполнен из снимка, pricePnl = 0")
    void getSecurityPage_closedPositionWithSnapshot_priceFilledPositionNull() {
        Transaction buy = tx(TransactionType.BUY, "2", "100.00", "2026-01-01T10:00:00Z", "2026-01-01T10:00:00Z");
        Transaction sell = tx(TransactionType.SELL, "2", "150.00", "2026-06-01T10:00:00Z", "2026-06-01T10:00:00Z");
        stubJournal(buy, sell);
        stubNoDividends();
        // No open LKOH position in the repository (it was fully sold), just some other holding.
        Position otherPosition = position("SBER", "1", "200.00", "200.00");
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of(otherPosition));
        Instant fetchedAt = Instant.parse("2026-09-12T10:00:00Z");
        when(marketDataService.getSnapshots(anySet())).thenReturn(Map.of(
                "SBER", new SnapshotResult(new BigDecimal("250.00"), new BigDecimal("240.00"), Instant.now(), false),
                TICKER, new SnapshotResult(new BigDecimal("155.00"), new BigDecimal("150.00"), fetchedAt, false)
        ));

        SecurityPageResponseDto page = securityPageService.getSecurityPage(userId, TICKER, TODAY);

        assertThat(page.getPosition()).isNull();
        assertThat(page.getPrice()).isNotNull();
        assertThat(page.getPrice().getCurrent()).isEqualByComparingTo("155.00");
        assertThat(page.getResult().getPricePnl()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("дрейф журнала: SELL раньше любой BUY не бросает исключение, средняя до продажи нулевая, остаток после — ноль")
    void getSecurityPage_journalDrift_sellBeforeAnyBuy_doesNotThrowAndZeroesAverage() {
        Transaction sellBeforeAnyBuy = tx(TransactionType.SELL, "5", "100.00", "2026-01-01T10:00:00Z", "2026-01-01T10:00:00Z");
        stubJournal(sellBeforeAnyBuy);
        stubNoDividends();
        stubNoPositions();

        SecurityPageResponseDto[] result = new SecurityPageResponseDto[1];
        assertThatCode(() -> result[0] = securityPageService.getSecurityPage(userId, TICKER, TODAY)).doesNotThrowAnyException();
        SecurityPageResponseDto page = result[0];

        SecurityEventDto sellEvent = eventByTransactionId(page, sellBeforeAnyBuy.getId());
        assertThat(sellEvent.getPositionAfter().getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        // averageBefore was 0 (nothing held), so realizedPnl is priced entirely off the sell price: (100-0)*5.
        assertThat(sellEvent.getRealizedPnl()).isEqualByComparingTo("500.00");
    }

    private SecurityEventDto eventByTransactionId(SecurityPageResponseDto page, UUID transactionId) {
        return page.getEvents().stream()
                .filter(e -> transactionId.equals(e.getTransactionId()))
                .findFirst().orElseThrow();
    }

    private SecurityEventDto onlyEventOfKind(SecurityPageResponseDto page, SecurityEventKind kind) {
        List<SecurityEventDto> matching = page.getEvents().stream().filter(e -> e.getKind() == kind).toList();
        assertThat(matching).hasSize(1);
        return matching.get(0);
    }

    private void stubJournal(Transaction... transactions) {
        when(transactionRepository.findByUserIdAndTickerWithSecurity(userId, TICKER)).thenReturn(List.of(transactions));
    }

    private void stubNoDividends() {
        when(dividendRepository.findBySecurity_Ticker(TICKER)).thenReturn(List.of());
    }

    private void stubNoPositions() {
        when(positionRepository.findByUserIdWithSecurity(userId)).thenReturn(List.of());
        when(marketDataService.getSnapshots(anySet())).thenReturn(Map.of());
    }

    private Transaction tx(TransactionType type, String quantity, String price, String executedAtIso, String createdAtIso) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .security(security(TICKER))
                .type(type)
                .quantity(new BigDecimal(quantity))
                .price(new BigDecimal(price))
                .executedAt(Instant.parse(executedAtIso))
                .createdAt(Instant.parse(createdAtIso))
                .build();
    }

    private Dividend dividend(LocalDate recordDate, LocalDate paymentDate, String amountPerShare, String currency,
                              DividendStatus status, DividendSource source) {
        return Dividend.builder()
                .id(UUID.randomUUID())
                .security(security(TICKER))
                .recordDate(recordDate)
                .paymentDate(paymentDate)
                .amountPerShare(new BigDecimal(amountPerShare))
                .currency(currency)
                .status(status)
                .source(source)
                .build();
    }

    private Position position(String ticker, String quantity, String averagePrice, String totalCost) {
        return Position.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .security(security(ticker))
                .quantity(new BigDecimal(quantity))
                .averagePrice(new BigDecimal(averagePrice))
                .totalCost(new BigDecimal(totalCost))
                .build();
    }

    private Security security(String ticker) {
        return Security.builder().ticker(ticker).name("ЛУКОЙЛ").type(SecurityType.STOCK).sector("Нефть и газ").build();
    }
}
