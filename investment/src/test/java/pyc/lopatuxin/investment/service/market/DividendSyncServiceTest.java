package pyc.lopatuxin.investment.service.market;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.investment.client.tinvest.TinvestApi;
import pyc.lopatuxin.investment.client.tinvest.TinvestDividend;
import pyc.lopatuxin.investment.client.tinvest.TinvestDividendsResponse;
import pyc.lopatuxin.investment.client.tinvest.TinvestMoneyValue;
import pyc.lopatuxin.investment.client.tinvest.TinvestResilience;
import pyc.lopatuxin.investment.client.tinvest.TinvestUnauthorizedException;
import pyc.lopatuxin.investment.config.TinvestProperties;
import pyc.lopatuxin.investment.entity.Dividend;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.DividendSource;
import pyc.lopatuxin.investment.entity.enums.DividendStatus;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.repository.DividendRepository;
import pyc.lopatuxin.investment.repository.PositionRepository;
import pyc.lopatuxin.investment.repository.SecurityRepository;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DividendSyncServiceTest")
class DividendSyncServiceTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    @Mock
    private TinvestApi tinvestApi;

    @Mock
    private TinvestResilience tinvestResilience;

    @Mock
    private TinvestInstrumentResolver instrumentResolver;

    @Mock
    private DividendRepository dividendRepository;

    @Mock
    private SecurityRepository securityRepository;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private DividendLoaderService dividendLoaderService;

    private TinvestProperties tinvestProperties;
    private DividendSyncService dividendSyncService;

    private Security sber;

    private TimeZone originalDefaultTimeZone;

    // toApplicationDate() derives the record/payment date from ZoneId.systemDefault() (App.main
    // sets it to Europe/Moscow in production). Pinning it here makes the tests assert real
    // Moscow calendar days regardless of the timezone of the machine running the build — same
    // pattern as HoldingsOnDateServiceTest.
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
    void setUp() throws Exception {
        tinvestProperties = new TinvestProperties();
        tinvestProperties.setToken("test-token");
        tinvestProperties.setBaseUrl("https://invest-public-api.tbank.ru/rest");

        // DividendSyncService has a @Lazy self-reference for @Transactional proxy calls from
        // within syncNightly/syncOnStartup — same pattern as MarketDataService (see
        // MarketDataServiceTest): @InjectMocks cannot wire it, so it is set via reflection.
        dividendSyncService = spy(new DividendSyncService(
                tinvestApi, tinvestResilience, tinvestProperties, instrumentResolver,
                dividendRepository, securityRepository, positionRepository, dividendLoaderService,
                null));
        Field selfField = DividendSyncService.class.getDeclaredField("self");
        selfField.setAccessible(true);
        selfField.set(dividendSyncService, dividendSyncService);

        sber = Security.builder()
                .ticker("SBER")
                .name("Сбербанк")
                .type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.READY)
                .tinvestUid("uid-sber")
                .build();
    }

    @SuppressWarnings("unchecked")
    private void stubResilienceExecutesSupplier() {
        when(tinvestResilience.execute(anyString(), any())).thenAnswer(invocation -> {
            Supplier<Object> supplier = invocation.getArgument(1);
            return supplier.get();
        });
    }

    private TinvestDividend dividend(String currency, String units, int nano, Instant recordDate, Instant paymentDate) {
        return new TinvestDividend(new TinvestMoneyValue(currency, units, nano), recordDate, paymentDate);
    }

    @Test
    @DisplayName("syncDividends — новый дивиденд → вставляется с source=TINVEST и статусом по дате отсечки")
    void syncDividends_insertsNewDividend() {
        stubResilienceExecutesSupplier();
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(sber));
        when(dividendRepository.existsBySecurity_Ticker("SBER")).thenReturn(false);
        Instant recordDate = LocalDate.now().plusDays(10).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        when(tinvestApi.getDividends(any())).thenReturn(new TinvestDividendsResponse(List.of(
                dividend("rub", "34", 840000000, recordDate, null))));
        when(dividendRepository.findBySecurity_TickerAndRecordDate(eq("SBER"), any())).thenReturn(Optional.empty());

        DividendSyncService.DividendSyncResult result = dividendSyncService.syncDividends("SBER");

        assertThat(result.added()).isEqualTo(1);
        assertThat(result.updated()).isZero();
        ArgumentCaptor<Dividend> captor = ArgumentCaptor.forClass(Dividend.class);
        verify(dividendRepository).save(captor.capture());
        Dividend saved = captor.getValue();
        assertThat(saved.getAmountPerShare()).isEqualByComparingTo("34.8400");
        assertThat(saved.getCurrency()).isEqualTo("RUB");
        assertThat(saved.getSource()).isEqualTo(DividendSource.TINVEST);
        assertThat(saved.getStatus()).isEqualTo(DividendStatus.ANNOUNCED);
        verify(securityRepository).save(sber);
        assertThat(sber.getDividendsSyncedAt()).isNotNull();
    }

    @Test
    @DisplayName("syncDividends — Security изменился в БД, пока шёл сетевой вызов → mergeAndPersist перечитывает свежую сущность и не затирает её чужие поля устаревшим снимком")
    void syncDividends_securityChangedDuringNetworkCall_mergeAndPersistUsesFreshEntity() {
        stubResilienceExecutesSupplier();
        // loadSecurity's own transaction returns the entity as it was before the (possibly
        // minute-long) network round-trip; by the time mergeAndPersist's write transaction runs,
        // MarketDataService has concurrently updated historyStatus/lastPriceUpdatedAt on the same
        // row — represented here by a second, different Security instance findById returns on its
        // second call.
        Security staleSecurity = Security.builder()
                .ticker("SBER").name("Сбербанк").type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.READY).tinvestUid("uid-sber")
                .lastPriceUpdatedAt(Instant.parse("2026-01-01T00:00:00Z")).build();
        Security freshSecurity = Security.builder()
                .ticker("SBER").name("Сбербанк").type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.PENDING).tinvestUid("uid-sber")
                .lastPriceUpdatedAt(Instant.parse("2026-01-01T00:05:00Z")).build();
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(staleSecurity), Optional.of(freshSecurity));
        when(dividendRepository.existsBySecurity_Ticker("SBER")).thenReturn(false);
        when(tinvestApi.getDividends(any())).thenReturn(new TinvestDividendsResponse(List.of()));

        dividendSyncService.syncDividends("SBER");

        verify(securityRepository).save(freshSecurity);
        verify(securityRepository, never()).save(staleSecurity);
        assertThat(freshSecurity.getDividendsSyncedAt()).isNotNull();
        assertThat(freshSecurity.getHistoryStatus()).isEqualTo(HistoryStatus.PENDING);
        assertThat(freshSecurity.getLastPriceUpdatedAt()).isEqualTo(Instant.parse("2026-01-01T00:05:00Z"));
        assertThat(staleSecurity.getDividendsSyncedAt()).isNull();
    }

    @Test
    @DisplayName("syncDividends — recordDate в прошлом → статус PAID")
    void syncDividends_pastRecordDate_statusPaid() {
        stubResilienceExecutesSupplier();
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(sber));
        when(dividendRepository.existsBySecurity_Ticker("SBER")).thenReturn(false);
        Instant recordDate = LocalDate.now().minusDays(5).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        when(tinvestApi.getDividends(any())).thenReturn(new TinvestDividendsResponse(List.of(
                dividend("rub", "18", 700000000, recordDate, null))));
        when(dividendRepository.findBySecurity_TickerAndRecordDate(eq("SBER"), any())).thenReturn(Optional.empty());

        dividendSyncService.syncDividends("SBER");

        ArgumentCaptor<Dividend> captor = ArgumentCaptor.forClass(Dividend.class);
        verify(dividendRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(DividendStatus.PAID);
    }

    @Test
    @DisplayName("syncDividends — существующий MOEX/TINVEST дивиденд с изменившейся суммой → обновляется, источник становится TINVEST")
    void syncDividends_updatesExistingMoexOrTinvestDividend() {
        stubResilienceExecutesSupplier();
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(sber));
        when(dividendRepository.existsBySecurity_Ticker("SBER")).thenReturn(true);
        LocalDate recordLocalDate = LocalDate.now().plusDays(10);
        Instant recordDate = recordLocalDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        when(tinvestApi.getDividends(any())).thenReturn(new TinvestDividendsResponse(List.of(
                dividend("rub", "34", 840000000, recordDate, null))));

        Dividend existing = Dividend.builder()
                .security(sber).recordDate(recordLocalDate)
                .amountPerShare(new BigDecimal("30.0000")).currency("RUB")
                .status(DividendStatus.ANNOUNCED).source(DividendSource.MOEX).build();
        when(dividendRepository.findBySecurity_TickerAndRecordDate("SBER", recordLocalDate))
                .thenReturn(Optional.of(existing));

        DividendSyncService.DividendSyncResult result = dividendSyncService.syncDividends("SBER");

        assertThat(result.added()).isZero();
        assertThat(result.updated()).isEqualTo(1);
        verify(dividendRepository).save(existing);
        assertThat(existing.getAmountPerShare()).isEqualByComparingTo("34.8400");
        assertThat(existing.getSource()).isEqualTo(DividendSource.TINVEST);
        // status is left untouched by the merge — only markPastRecordDatesAsPaid moves it forward
        assertThat(existing.getStatus()).isEqualTo(DividendStatus.ANNOUNCED);
    }

    @Test
    @DisplayName("syncDividends — существующий MANUAL дивиденд → не трогается")
    void syncDividends_doesNotTouchManualDividend() {
        stubResilienceExecutesSupplier();
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(sber));
        when(dividendRepository.existsBySecurity_Ticker("SBER")).thenReturn(true);
        LocalDate recordLocalDate = LocalDate.now().plusDays(10);
        Instant recordDate = recordLocalDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        when(tinvestApi.getDividends(any())).thenReturn(new TinvestDividendsResponse(List.of(
                dividend("rub", "999", 0, recordDate, null))));

        Dividend manual = Dividend.builder()
                .security(sber).recordDate(recordLocalDate)
                .amountPerShare(new BigDecimal("30.0000")).currency("RUB")
                .status(DividendStatus.ANNOUNCED).source(DividendSource.MANUAL).build();
        when(dividendRepository.findBySecurity_TickerAndRecordDate("SBER", recordLocalDate))
                .thenReturn(Optional.of(manual));

        DividendSyncService.DividendSyncResult result = dividendSyncService.syncDividends("SBER");

        assertThat(result.added()).isZero();
        assertThat(result.updated()).isZero();
        verify(dividendRepository, never()).save(manual);
        assertThat(manual.getAmountPerShare()).isEqualByComparingTo("30.0000");
    }

    @Test
    @DisplayName("syncDividends — повторный запуск с теми же данными → save не вызывается (обновлений ноль)")
    void syncDividends_sameDataAgain_noSaveCalled() {
        stubResilienceExecutesSupplier();
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(sber));
        when(dividendRepository.existsBySecurity_Ticker("SBER")).thenReturn(true);
        LocalDate recordLocalDate = LocalDate.now().plusDays(10);
        Instant recordDate = recordLocalDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        when(tinvestApi.getDividends(any())).thenReturn(new TinvestDividendsResponse(List.of(
                dividend("rub", "34", 840000000, recordDate, null))));

        Dividend existing = Dividend.builder()
                .security(sber).recordDate(recordLocalDate)
                .amountPerShare(new BigDecimal("34.8400")).currency("RUB")
                .status(DividendStatus.ANNOUNCED).source(DividendSource.TINVEST).build();
        when(dividendRepository.findBySecurity_TickerAndRecordDate("SBER", recordLocalDate))
                .thenReturn(Optional.of(existing));

        DividendSyncService.DividendSyncResult result = dividendSyncService.syncDividends("SBER");

        assertThat(result.added()).isZero();
        assertThat(result.updated()).isZero();
        verify(dividendRepository, never()).save(any());
    }

    @Test
    @DisplayName("syncDividends — отсечка в payload не в полночь → дата приводится к зоне приложения (Europe/Moscow), не UTC")
    void syncDividends_recordDateNotMidnight_usesApplicationZoneNotUtc() {
        stubResilienceExecutesSupplier();
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(sber));
        when(dividendRepository.existsBySecurity_Ticker("SBER")).thenReturn(false);
        // 22:00 UTC on 2025-10-05 is 01:00 Europe/Moscow on 2025-10-06 — converting via UTC
        // instead of the app's own zone would report the record date as 2025-10-05.
        Instant recordDate = LocalDate.of(2025, 10, 5).atTime(22, 0)
                .atZone(java.time.ZoneOffset.UTC).toInstant();
        when(tinvestApi.getDividends(any())).thenReturn(new TinvestDividendsResponse(List.of(
                dividend("rub", "34", 840000000, recordDate, null))));
        when(dividendRepository.findBySecurity_TickerAndRecordDate(eq("SBER"), any())).thenReturn(Optional.empty());

        dividendSyncService.syncDividends("SBER");

        ArgumentCaptor<Dividend> captor = ArgumentCaptor.forClass(Dividend.class);
        verify(dividendRepository).save(captor.capture());
        assertThat(captor.getValue().getRecordDate()).isEqualTo(LocalDate.of(2025, 10, 6));
    }

    @Test
    @DisplayName("syncDividends — запись без recordDate или без суммы → пропускается")
    void syncDividends_skipsRecordsWithoutRecordDateOrAmount() {
        stubResilienceExecutesSupplier();
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(sber));
        when(dividendRepository.existsBySecurity_Ticker("SBER")).thenReturn(false);
        when(tinvestApi.getDividends(any())).thenReturn(new TinvestDividendsResponse(List.of(
                new TinvestDividend(new TinvestMoneyValue("RUB", "10", 0), null, null),
                new TinvestDividend(null, Instant.now(), null))));

        DividendSyncService.DividendSyncResult result = dividendSyncService.syncDividends("SBER");

        assertThat(result.added()).isZero();
        assertThat(result.updated()).isZero();
        verify(dividendRepository, never()).save(any());
    }

    @Test
    @DisplayName("syncDividends — пустой токен → пропуск без вызовов сети")
    void syncDividends_blankToken_skipsWithoutNetworkCalls() {
        tinvestProperties.setToken("");

        DividendSyncService.DividendSyncResult result = dividendSyncService.syncDividends("SBER");

        assertThat(result.added()).isZero();
        assertThat(result.updated()).isZero();
        verify(instrumentResolver, never()).resolve(any());
        verify(tinvestResilience, never()).execute(anyString(), any());
    }

    @Test
    @DisplayName("syncDividends — бумага не найдена в T-Invest → пропуск без исключения")
    void syncDividends_instrumentNotResolved_skips() {
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(sber));
        sber.setTinvestUid(null);
        when(instrumentResolver.resolve(sber)).thenReturn(false);

        DividendSyncService.DividendSyncResult result = dividendSyncService.syncDividends("SBER");

        assertThat(result.added()).isZero();
        verify(tinvestApi, never()).getDividends(any());
    }

    @Test
    @DisplayName("syncDividends — облигация/ОФЗ → GetDividends не вызывается вовсе")
    void syncDividends_bondOrOfz_skipsGetDividendsCall() {
        Security bond = Security.builder()
                .ticker("SU26219RMFS4")
                .name("ОФЗ 26219")
                .type(SecurityType.OFZ)
                .historyStatus(HistoryStatus.READY)
                .tinvestUid("uid-ofz")
                .build();
        when(securityRepository.findById("SU26219RMFS4")).thenReturn(Optional.of(bond));

        DividendSyncService.DividendSyncResult result = dividendSyncService.syncDividends("SU26219RMFS4");

        assertThat(result.added()).isZero();
        assertThat(result.updated()).isZero();
        verify(tinvestApi, never()).getDividends(any());
        verify(tinvestResilience, never()).execute(eq("getDividends"), any());
    }

    @Test
    @DisplayName("syncDividends — 401 при поиске инструмента → пропуск без исключения наружу")
    void syncDividends_unauthorizedDuringResolve_doesNotThrow() {
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(sber));
        sber.setTinvestUid(null);
        when(instrumentResolver.resolve(sber)).thenThrow(new TinvestUnauthorizedException("отклонён"));

        assertThatCode(() -> dividendSyncService.syncDividends("SBER")).doesNotThrowAnyException();
        verify(tinvestApi, never()).getDividends(any());
    }

    @Test
    @DisplayName("syncNightly — обходит активные тикеры последовательно и логирует итог")
    void syncNightly_processesActiveTickers() {
        when(positionRepository.findActiveTickers()).thenReturn(List.of("SBER", "GAZP"));
        doReturnResult(new DividendSyncService.DividendSyncResult(1, 0, false), "SBER");
        doReturnResult(new DividendSyncService.DividendSyncResult(0, 1, false), "GAZP");

        dividendSyncService.syncNightly();

        verify(dividendSyncService).syncDividends("SBER");
        verify(dividendSyncService).syncDividends("GAZP");
    }

    @Test
    @DisplayName("syncNightly — пустой токен → тикеры не запрашиваются вовсе")
    void syncNightly_blankToken_doesNothing() {
        tinvestProperties.setToken(" ");

        dividendSyncService.syncNightly();

        verify(positionRepository, never()).findActiveTickers();
    }

    @Test
    @DisplayName("syncOnStartup — обрабатывает только бумаги без синхронизации или устаревшие (>20ч)")
    void syncOnStartup_onlyStaleOrNeverSynced() {
        when(positionRepository.findActiveTickers()).thenReturn(List.of("SBER", "GAZP", "LKOH"));
        Security neverSynced = Security.builder().ticker("SBER").dividendsSyncedAt(null).build();
        Security stale = Security.builder().ticker("GAZP").dividendsSyncedAt(Instant.now().minus(25, java.time.temporal.ChronoUnit.HOURS)).build();
        Security fresh = Security.builder().ticker("LKOH").dividendsSyncedAt(Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS)).build();
        when(securityRepository.findAllById(List.of("SBER", "GAZP", "LKOH")))
                .thenReturn(List.of(neverSynced, stale, fresh));
        doReturnResult(DividendSyncService.DividendSyncResult.EMPTY, "SBER");
        doReturnResult(DividendSyncService.DividendSyncResult.EMPTY, "GAZP");

        dividendSyncService.syncOnStartup();

        verify(dividendSyncService).syncDividends("SBER");
        verify(dividendSyncService).syncDividends("GAZP");
        verify(dividendSyncService, never()).syncDividends("LKOH");
    }

    @Test
    @DisplayName("markPastRecordDatesAsPaid — есть строки с прошедшей recordDate → одно bulk-обновление, возвращается число затронутых строк")
    void markPastRecordDatesAsPaid_rowsDue_returnsUpdatedCount() {
        when(dividendRepository.markPastRecordDatesAsPaid(LocalDate.now())).thenReturn(3);

        int updated = dividendSyncService.markPastRecordDatesAsPaid();

        assertThat(updated).isEqualTo(3);
    }

    @Test
    @DisplayName("markPastRecordDatesAsPaid — нет прошедших ANNOUNCED → возвращается 0")
    void markPastRecordDatesAsPaid_noneDue_returnsZero() {
        when(dividendRepository.markPastRecordDatesAsPaid(LocalDate.now())).thenReturn(0);

        int updated = dividendSyncService.markPastRecordDatesAsPaid();

        assertThat(updated).isZero();
    }

    @Test
    @DisplayName("isSourceConfigured — токен есть и хотя бы одна бумага синхронизирована → true")
    void isSourceConfigured_tokenAndSyncedSecurity_true() {
        when(securityRepository.existsByDividendsSyncedAtIsNotNull()).thenReturn(true);

        assertThat(dividendSyncService.isSourceConfigured()).isTrue();
    }

    @Test
    @DisplayName("isSourceConfigured — токен пуст → false без обращения к БД")
    void isSourceConfigured_blankToken_false() {
        tinvestProperties.setToken("");

        assertThat(dividendSyncService.isSourceConfigured()).isFalse();
        verify(securityRepository, never()).existsByDividendsSyncedAtIsNotNull();
    }

    @Test
    @DisplayName("isSourceConfigured — токен есть, но синхронизация ни разу не проходила → false")
    void isSourceConfigured_neverSynced_false() {
        when(securityRepository.existsByDividendsSyncedAtIsNotNull()).thenReturn(false);

        assertThat(dividendSyncService.isSourceConfigured()).isFalse();
    }

    private void doReturnResult(DividendSyncService.DividendSyncResult result, String ticker) {
        org.mockito.Mockito.doReturn(result).when(dividendSyncService).syncDividends(ticker);
    }
}
