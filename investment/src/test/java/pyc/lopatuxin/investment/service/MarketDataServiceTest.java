package pyc.lopatuxin.investment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import pyc.lopatuxin.investment.client.moex.MoexIssClient;
import pyc.lopatuxin.investment.client.moex.MoexUnavailableException;
import pyc.lopatuxin.investment.dto.response.MoexCandleDto;
import pyc.lopatuxin.investment.dto.response.MoexSecurityDto;
import pyc.lopatuxin.investment.dto.response.MoexSnapshotDto;
import pyc.lopatuxin.investment.config.MoexProperties;
import pyc.lopatuxin.investment.dto.request.SearchCategory;
import pyc.lopatuxin.investment.entity.PriceHistory;
import pyc.lopatuxin.investment.entity.PriceSnapshot;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.repository.PriceHistoryRepository;
import pyc.lopatuxin.investment.repository.PriceSnapshotRepository;
import pyc.lopatuxin.investment.repository.SecurityRepository;
import pyc.lopatuxin.investment.service.market.HistoryLoaderService;
import pyc.lopatuxin.investment.service.market.MarketDataService;
import pyc.lopatuxin.investment.dto.response.SnapshotResult;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarketDataServiceTest")
class MarketDataServiceTest {

    @Mock
    private MoexIssClient moexIssClient;

    @Mock
    private SecurityRepository securityRepository;

    @Mock
    private PriceSnapshotRepository priceSnapshotRepository;

    @Mock
    private PriceHistoryRepository priceHistoryRepository;

    @Mock
    private MoexProperties moexProperties;

    @Mock
    private HistoryLoaderService historyLoaderService;

    private MarketDataService marketDataService;

    @BeforeEach
    void setUp() throws Exception {
        // MarketDataService has a @Lazy self-reference for @Transactional/@Cacheable proxy calls.
        // @InjectMocks cannot wire it; construct manually and inject self via reflection.
        marketDataService = spy(new MarketDataService(
                moexIssClient,
                securityRepository,
                priceSnapshotRepository,
                priceHistoryRepository,
                moexProperties,
                historyLoaderService,
                null   // self — set below
        ));
        Field selfField = MarketDataService.class.getDeclaredField("self");
        selfField.setAccessible(true);
        selfField.set(marketDataService, marketDataService);
    }

    @Test
    @DisplayName("ensureSecurity — нет в БД, MOEX возвращает данные → Security сохранена с READY")
    void ensureSecurity_notInDb_moexReturnsData_savedAsReady() {
        when(securityRepository.findById("SBER")).thenReturn(Optional.empty());
        MoexSecurityDto moexDto = new MoexSecurityDto("SBER", "TQBR", "Сбербанк", SecurityType.STOCK, null, "RUB");
        when(moexIssClient.fetchSecurity("SBER")).thenReturn(Optional.of(moexDto));
        Security saved = Security.builder().ticker("SBER").name("Сбербанк").type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.READY).build();
        when(securityRepository.save(any(Security.class))).thenReturn(saved);

        Security result = marketDataService.ensureSecurity("SBER", SecurityType.STOCK);

        ArgumentCaptor<Security> captor = ArgumentCaptor.forClass(Security.class);
        verify(securityRepository).save(captor.capture());
        assertThat(captor.getValue().getHistoryStatus()).isEqualTo(HistoryStatus.READY);
        assertThat(captor.getValue().getName()).isEqualTo("Сбербанк");
        assertThat(result).isEqualTo(saved);
    }

    @Test
    @DisplayName("ensureSecurity — нет в БД, MOEX недоступен → Security сохранена с PENDING")
    void ensureSecurity_notInDb_moexUnavailable_savedAsPending() {
        when(securityRepository.findById("SBER")).thenReturn(Optional.empty());
        when(moexIssClient.fetchSecurity("SBER")).thenThrow(new MoexUnavailableException("MOEX down"));
        Security saved = Security.builder().ticker("SBER").name("SBER").type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.PENDING).build();
        when(securityRepository.save(any(Security.class))).thenReturn(saved);

        Security result = marketDataService.ensureSecurity("SBER", SecurityType.STOCK);

        ArgumentCaptor<Security> captor = ArgumentCaptor.forClass(Security.class);
        verify(securityRepository).save(captor.capture());
        assertThat(captor.getValue().getHistoryStatus()).isEqualTo(HistoryStatus.PENDING);
        assertThat(result).isEqualTo(saved);
    }

    @Test
    @DisplayName("getSnapshot — свежий снимок в БД → не вызывает MOEX")
    void getSnapshot_freshInDb_doesNotCallMoex() {
        when(moexProperties.getSnapshotTtlMinutes()).thenReturn(5);
        PriceSnapshot snapshot = PriceSnapshot.builder()
                .ticker("SBER")
                .lastPrice(new BigDecimal("310.50"))
                .previousClose(new BigDecimal("308.00"))
                .fetchedAt(Instant.now())
                .build();
        when(priceSnapshotRepository.findById("SBER")).thenReturn(Optional.of(snapshot));

        SnapshotResult result = marketDataService.getSnapshot("SBER");

        verify(moexIssClient, never()).fetchSnapshots(any());
        assertThat(result.lastPrice()).isEqualByComparingTo(new BigDecimal("310.50"));
        assertThat(result.stale()).isFalse();
    }

    @Test
    @DisplayName("getSnapshot — снимок устарел → вызывает MOEX и обновляет")
    void getSnapshot_staleInDb_callsMoexAndUpdates() {
        when(moexProperties.getSnapshotTtlMinutes()).thenReturn(5);
        PriceSnapshot staleSnapshot = PriceSnapshot.builder()
                .ticker("SBER")
                .lastPrice(new BigDecimal("300.00"))
                .previousClose(new BigDecimal("298.00"))
                .fetchedAt(Instant.now().minusSeconds(600))
                .build();
        when(priceSnapshotRepository.findById("SBER")).thenReturn(Optional.of(staleSnapshot));
        when(securityRepository.existsById("SBER")).thenReturn(true);

        MoexSnapshotDto moexDto = new MoexSnapshotDto("SBER", new BigDecimal("315.00"), new BigDecimal("310.00"));
        when(moexIssClient.fetchSnapshots(List.of("SBER")))
                .thenReturn(Map.of("SBER", moexDto));

        PriceSnapshot updated = PriceSnapshot.builder()
                .ticker("SBER")
                .lastPrice(new BigDecimal("315.00"))
                .previousClose(new BigDecimal("310.00"))
                .fetchedAt(Instant.now())
                .build();
        when(priceSnapshotRepository.saveAndFlush(any(PriceSnapshot.class))).thenReturn(updated);

        SnapshotResult result = marketDataService.getSnapshot("SBER");

        verify(moexIssClient).fetchSnapshots(List.of("SBER"));
        assertThat(result.lastPrice()).isEqualByComparingTo(new BigDecimal("315.00"));
        assertThat(result.stale()).isFalse();
    }

    @Test
    @DisplayName("getSnapshot — MOEX недоступен → stale=true")
    void getSnapshot_moexUnavailable_returnsStale() {
        when(moexProperties.getSnapshotTtlMinutes()).thenReturn(5);
        PriceSnapshot staleSnapshot = PriceSnapshot.builder()
                .ticker("SBER")
                .lastPrice(new BigDecimal("300.00"))
                .previousClose(new BigDecimal("298.00"))
                .fetchedAt(Instant.now().minusSeconds(600))
                .build();
        when(priceSnapshotRepository.findById("SBER")).thenReturn(Optional.of(staleSnapshot));
        when(moexIssClient.fetchSnapshots(any()))
                .thenThrow(new MoexUnavailableException("MOEX down"));

        SnapshotResult result = marketDataService.getSnapshot("SBER");

        assertThat(result.stale()).isTrue();
        assertThat(result.lastPrice()).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    @DisplayName("getSnapshot — нет в БД и MOEX недоступен → stale=true, nulls")
    void getSnapshot_notInDbAndMoexUnavailable_returnsNullStale() {
        when(priceSnapshotRepository.findById(anyString())).thenReturn(Optional.empty());
        when(moexIssClient.fetchSnapshots(any()))
                .thenThrow(new MoexUnavailableException("MOEX down"));

        SnapshotResult result = marketDataService.getSnapshot("SBER");

        assertThat(result.stale()).isTrue();
        assertThat(result.lastPrice()).isNull();
    }

    @Test
    @DisplayName("search — category=null → возвращает все типы")
    void search_categoryNull_returnsAll() {
        when(moexIssClient.searchSecurities("test")).thenReturn(searchFixture());

        List<MoexSecurityDto> result = marketDataService.search("test", null);

        assertThat(result).hasSize(4);
    }

    @Test
    @DisplayName("search — category=STOCKS → только STOCK и ETF")
    void search_categoryStocks_returnsStocksAndEtf() {
        when(moexIssClient.searchSecurities("test")).thenReturn(searchFixture());

        List<MoexSecurityDto> result = marketDataService.search("test", SearchCategory.STOCKS);

        assertThat(result)
                .extracting(MoexSecurityDto::securityType)
                .containsExactlyInAnyOrder(SecurityType.STOCK, SecurityType.ETF);
    }

    @Test
    @DisplayName("search — category=BONDS → только BOND и OFZ")
    void search_categoryBonds_returnsBondsAndOfz() {
        when(moexIssClient.searchSecurities("test")).thenReturn(searchFixture());

        List<MoexSecurityDto> result = marketDataService.search("test", SearchCategory.BONDS);

        assertThat(result)
                .extracting(MoexSecurityDto::securityType)
                .containsExactlyInAnyOrder(SecurityType.BOND, SecurityType.OFZ);
    }

    @Test
    @DisplayName("ensureHistory — READY с историей до вчера → докачивает только со следующего дня по сегодня")
    void ensureHistory_readyWithOlderHistory_loadsOnlyMissingTail() {
        LocalDate lastSaved = LocalDate.now().minusDays(3);
        Security security = Security.builder().ticker("SBER").type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.READY).build();
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(security));
        when(priceHistoryRepository.findFirstByTickerOrderByTradeDateDesc("SBER"))
                .thenReturn(Optional.of(PriceHistory.builder().ticker("SBER").tradeDate(lastSaved).build()));
        when(priceHistoryRepository.findFirstByTickerOrderByTradeDateAsc("SBER"))
                .thenReturn(Optional.of(PriceHistory.builder().ticker("SBER").tradeDate(LocalDate.now().minusYears(3)).build()));
        MoexCandleDto candle = new MoexCandleDto("SBER", LocalDate.now(), null, null, null, null, null);
        when(moexIssClient.fetchHistory("SBER", lastSaved.plusDays(1), LocalDate.now()))
                .thenReturn(List.of(candle));

        marketDataService.ensureHistory("SBER");

        verify(moexIssClient).fetchHistory("SBER", lastSaved.plusDays(1), LocalDate.now());
        ArgumentCaptor<List<PriceHistory>> captor = ArgumentCaptor.forClass(List.class);
        verify(priceHistoryRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getTicker()).isEqualTo("SBER");
        assertThat(captor.getValue().get(0).getTradeDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("ensureHistory — READY с историей уже за сегодня → MOEX не вызывается")
    void ensureHistory_readyWithTodaysHistory_skipsMoexCall() {
        Security security = Security.builder().ticker("SBER").type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.READY).build();
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(security));
        when(priceHistoryRepository.findFirstByTickerOrderByTradeDateDesc("SBER"))
                .thenReturn(Optional.of(PriceHistory.builder().ticker("SBER").tradeDate(LocalDate.now()).build()));
        when(priceHistoryRepository.findFirstByTickerOrderByTradeDateAsc("SBER"))
                .thenReturn(Optional.of(PriceHistory.builder().ticker("SBER").tradeDate(LocalDate.now().minusYears(3)).build()));

        marketDataService.ensureHistory("SBER");

        verify(moexIssClient, never()).fetchHistory(anyString(), any(), any());
    }

    @Test
    @DisplayName("ensureHistory — READY, но самая ранняя запись не доходит до начала трёхлетнего окна → докачивает полный диапазон")
    void ensureHistory_readyWithFrontGap_reloadsFullThreeYearRange() {
        Security security = Security.builder().ticker("SBER").type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.READY).build();
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(security));
        when(priceHistoryRepository.findFirstByTickerOrderByTradeDateDesc("SBER"))
                .thenReturn(Optional.of(PriceHistory.builder().ticker("SBER").tradeDate(LocalDate.now().minusDays(1)).build()));
        // Earliest saved row is only 1 year back, far short of the 3-year window an earlier
        // incomplete backfill should have covered — this is the front gap.
        when(priceHistoryRepository.findFirstByTickerOrderByTradeDateAsc("SBER"))
                .thenReturn(Optional.of(PriceHistory.builder().ticker("SBER").tradeDate(LocalDate.now().minusYears(1)).build()));
        MoexCandleDto candle = new MoexCandleDto("SBER", LocalDate.now(), null, null, null, null, null);
        when(moexIssClient.fetchHistory(eq("SBER"), eq(LocalDate.now().minusYears(3)), eq(LocalDate.now())))
                .thenReturn(List.of(candle));

        marketDataService.ensureHistory("SBER");

        verify(moexIssClient).fetchHistory("SBER", LocalDate.now().minusYears(3), LocalDate.now());
    }

    @Test
    @DisplayName("ensureHistory — новая бумага без истории → полная загрузка за три года")
    void ensureHistory_newSecurityWithoutHistory_loadsThreeYears() {
        Security security = Security.builder().ticker("SBER").type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.PENDING).build();
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(security));
        MoexCandleDto candle = new MoexCandleDto("SBER", LocalDate.now(), null, null, null, null, null);
        when(moexIssClient.fetchHistory(eq("SBER"), eq(LocalDate.now().minusYears(3)), eq(LocalDate.now())))
                .thenReturn(List.of(candle));

        marketDataService.ensureHistory("SBER");

        verify(moexIssClient).fetchHistory("SBER", LocalDate.now().minusYears(3), LocalDate.now());
        verify(priceHistoryRepository).saveAll(any());
    }

    @Test
    @DisplayName("getSnapshots — все тикеры свежие в БД → MOEX не вызывается")
    void getSnapshots_allFreshInDb_doesNotCallMoex() {
        when(moexProperties.getSnapshotTtlMinutes()).thenReturn(5);
        PriceSnapshot sber = PriceSnapshot.builder().ticker("SBER")
                .lastPrice(new BigDecimal("310.50")).previousClose(new BigDecimal("308.00"))
                .fetchedAt(Instant.now()).build();
        PriceSnapshot lkoh = PriceSnapshot.builder().ticker("LKOH")
                .lastPrice(new BigDecimal("7180.00")).previousClose(new BigDecimal("7081.00"))
                .fetchedAt(Instant.now()).build();
        when(priceSnapshotRepository.findAllById(List.of("SBER", "LKOH"))).thenReturn(List.of(sber, lkoh));

        Map<String, SnapshotResult> result = marketDataService.getSnapshots(List.of("SBER", "LKOH"));

        verify(moexIssClient, never()).fetchSnapshots(any());
        assertThat(result.get("SBER").lastPrice()).isEqualByComparingTo("310.50");
        assertThat(result.get("LKOH").lastPrice()).isEqualByComparingTo("7180.00");
        assertThat(result.values()).allSatisfy(r -> assertThat(r.stale()).isFalse());
    }

    @Test
    @DisplayName("getSnapshots — часть тикеров устарела → на биржу уходят только устаревшие")
    void getSnapshots_someStale_fetchesOnlyStaleFromMoex() {
        when(moexProperties.getSnapshotTtlMinutes()).thenReturn(5);
        PriceSnapshot freshSber = PriceSnapshot.builder().ticker("SBER")
                .lastPrice(new BigDecimal("310.50")).previousClose(new BigDecimal("308.00"))
                .fetchedAt(Instant.now()).build();
        PriceSnapshot staleLkoh = PriceSnapshot.builder().ticker("LKOH")
                .lastPrice(new BigDecimal("7000.00")).previousClose(new BigDecimal("6950.00"))
                .fetchedAt(Instant.now().minusSeconds(600)).build();
        when(priceSnapshotRepository.findAllById(List.of("SBER", "LKOH")))
                .thenReturn(List.of(freshSber, staleLkoh));
        when(securityRepository.existsById("LKOH")).thenReturn(true);
        MoexSnapshotDto lkohDto = new MoexSnapshotDto("LKOH", new BigDecimal("7180.00"), new BigDecimal("7081.00"));
        when(moexIssClient.fetchSnapshots(List.of("LKOH"))).thenReturn(Map.of("LKOH", lkohDto));
        PriceSnapshot updatedLkoh = PriceSnapshot.builder().ticker("LKOH")
                .lastPrice(new BigDecimal("7180.00")).previousClose(new BigDecimal("7081.00"))
                .fetchedAt(Instant.now()).build();
        when(priceSnapshotRepository.saveAndFlush(any(PriceSnapshot.class))).thenReturn(updatedLkoh);

        Map<String, SnapshotResult> result = marketDataService.getSnapshots(List.of("SBER", "LKOH"));

        verify(moexIssClient).fetchSnapshots(List.of("LKOH"));
        assertThat(result.get("SBER").lastPrice()).isEqualByComparingTo("310.50");
        assertThat(result.get("SBER").stale()).isFalse();
        assertThat(result.get("LKOH").lastPrice()).isEqualByComparingTo("7180.00");
        assertThat(result.get("LKOH").stale()).isFalse();
    }

    @Test
    @DisplayName("getSnapshots — гонка по первичному ключу при апсерте нового тикера → повторная попытка вместо падения")
    void getSnapshots_primaryKeyRaceOnUpsert_retriesAndSucceeds() {
        when(securityRepository.existsById("SBER")).thenReturn(true);
        when(priceSnapshotRepository.findAllById(List.of("SBER"))).thenReturn(List.of());
        MoexSnapshotDto dto = new MoexSnapshotDto("SBER", new BigDecimal("310.50"), new BigDecimal("308.00"));
        when(moexIssClient.fetchSnapshots(List.of("SBER"))).thenReturn(Map.of("SBER", dto));
        PriceSnapshot saved = PriceSnapshot.builder().ticker("SBER")
                .lastPrice(new BigDecimal("310.50")).previousClose(new BigDecimal("308.00"))
                .fetchedAt(Instant.now()).build();
        // First attempt loses the primary-key race to a concurrent insert for the same
        // brand-new ticker; the second (fresh REQUIRES_NEW) attempt succeeds. SQLState 23505
        // is Postgres' unique_violation — what Hibernate's exception translation actually
        // wraps a duplicate-key failure in.
        when(priceSnapshotRepository.saveAndFlush(any(PriceSnapshot.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint",
                        new SQLException("duplicate key value violates unique constraint \"price_snapshots_pkey\"", "23505")))
                .thenReturn(saved);

        Map<String, SnapshotResult> result = marketDataService.getSnapshots(List.of("SBER"));

        verify(priceSnapshotRepository, times(2)).saveAndFlush(any(PriceSnapshot.class));
        assertThat(result.get("SBER").lastPrice()).isEqualByComparingTo("310.50");
        assertThat(result.get("SBER").stale()).isFalse();
    }

    @Test
    @DisplayName("getSnapshots — нарушение NOT NULL при апсерте (не гонка по ключу) → повтор не выполняется, исключение пробрасывается")
    void getSnapshots_notNullConstraintViolation_notRetried_exceptionPropagates() {
        when(securityRepository.existsById("SBER")).thenReturn(true);
        when(priceSnapshotRepository.findAllById(List.of("SBER"))).thenReturn(List.of());
        MoexSnapshotDto dto = new MoexSnapshotDto("SBER", new BigDecimal("310.50"), new BigDecimal("308.00"));
        when(moexIssClient.fetchSnapshots(List.of("SBER"))).thenReturn(Map.of("SBER", dto));
        // Not a duplicate-key race: SQLState 23502 is Postgres' not_null_violation, which a
        // retry with the same data cannot fix.
        when(priceSnapshotRepository.saveAndFlush(any(PriceSnapshot.class)))
                .thenThrow(new DataIntegrityViolationException("null value in column \"last_price\" violates not-null constraint",
                        new SQLException("null value in column \"last_price\" violates not-null constraint", "23502")));

        assertThatThrownBy(() -> marketDataService.getSnapshots(List.of("SBER")))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(priceSnapshotRepository, times(1)).saveAndFlush(any(PriceSnapshot.class));
    }

    @Test
    @DisplayName("upsertSnapshot — LAST пуст, снимок уже есть, PREVPRICE пришёл: цена закрытия и время обновляются, текущая цена не затирается")
    void upsertSnapshot_lastPriceNull_existingSnapshotPresent_updatesPreviousCloseWithoutWipingLastPrice() {
        when(securityRepository.existsById("SBER")).thenReturn(true);
        PriceSnapshot existing = PriceSnapshot.builder()
                .ticker("SBER")
                .lastPrice(new BigDecimal("300.00"))
                .previousClose(new BigDecimal("298.00"))
                .fetchedAt(Instant.now().minusSeconds(600))
                .build();
        when(priceSnapshotRepository.findById("SBER")).thenReturn(Optional.of(existing));
        MoexSnapshotDto dto = new MoexSnapshotDto("SBER", null, new BigDecimal("308.00"));
        when(priceSnapshotRepository.saveAndFlush(any(PriceSnapshot.class))).thenAnswer(inv -> inv.getArgument(0));

        PriceSnapshot result = marketDataService.upsertSnapshot("SBER", dto);

        // The ticker stopped trading (LAST empty), but MOEX still answered with a fresh
        // previousClose — that must not leave the row looking stale forever just because it
        // already had a snapshot: previousClose and fetchedAt are refreshed, while the last
        // known real trade price is preserved untouched.
        assertThat(result).isNotNull();
        assertThat(result.getLastPrice()).isEqualByComparingTo("300.00");
        assertThat(result.getPreviousClose()).isEqualByComparingTo("308.00");
        assertThat(result.getFetchedAt()).isAfter(Instant.now().minusSeconds(5));
        verify(priceSnapshotRepository).saveAndFlush(existing);
    }

    @Test
    @DisplayName("upsertSnapshot — LAST пуст, снимка нет, PREVPRICE тоже пуст: не пишется")
    void upsertSnapshot_lastPriceNull_noExistingSnapshot_previousCloseAlsoNull_doesNotWriteSnapshot() {
        when(securityRepository.existsById("SBER")).thenReturn(true);
        when(priceSnapshotRepository.findById("SBER")).thenReturn(Optional.empty());
        MoexSnapshotDto dto = new MoexSnapshotDto("SBER", null, null);

        PriceSnapshot result = marketDataService.upsertSnapshot("SBER", dto);

        assertThat(result).isNull();
        verify(priceSnapshotRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("upsertSnapshot — LAST пуст, снимка нет, но PREVPRICE пришёл (бумага добавлена вечером/в выходной): снимок сохраняется только с ценой закрытия")
    void upsertSnapshot_lastPriceNull_noExistingSnapshot_previousClosePresent_savesSnapshotWithPreviousCloseOnly() {
        when(securityRepository.existsById("SBER")).thenReturn(true);
        when(priceSnapshotRepository.findById("SBER")).thenReturn(Optional.empty());
        MoexSnapshotDto dto = new MoexSnapshotDto("SBER", null, new BigDecimal("308.00"));
        PriceSnapshot saved = PriceSnapshot.builder()
                .ticker("SBER")
                .previousClose(new BigDecimal("308.00"))
                .fetchedAt(Instant.now())
                .build();
        when(priceSnapshotRepository.saveAndFlush(any(PriceSnapshot.class))).thenReturn(saved);

        PriceSnapshot result = marketDataService.upsertSnapshot("SBER", dto);

        ArgumentCaptor<PriceSnapshot> captor = ArgumentCaptor.forClass(PriceSnapshot.class);
        verify(priceSnapshotRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getLastPrice()).isNull();
        assertThat(captor.getValue().getPreviousClose()).isEqualByComparingTo("308.00");
        assertThat(result).isEqualTo(saved);
    }

    @Test
    @DisplayName("getSnapshots — бумага без снимка в БД, у MOEX LAST пуст, но PREVPRICE есть → снимок сохраняется с одной ценой закрытия, сборка не падает")
    void getSnapshots_moexReturnsNullLastPriceWithPreviousClose_noExistingSnapshot_savesPreviousCloseOnly() {
        when(priceSnapshotRepository.findAllById(List.of("SBER"))).thenReturn(List.of());
        when(securityRepository.existsById("SBER")).thenReturn(true);
        MoexSnapshotDto dto = new MoexSnapshotDto("SBER", null, new BigDecimal("308.00"));
        when(moexIssClient.fetchSnapshots(List.of("SBER"))).thenReturn(Map.of("SBER", dto));
        when(priceSnapshotRepository.findById("SBER")).thenReturn(Optional.empty());
        PriceSnapshot saved = PriceSnapshot.builder()
                .ticker("SBER")
                .previousClose(new BigDecimal("308.00"))
                .fetchedAt(Instant.now())
                .build();
        when(priceSnapshotRepository.saveAndFlush(any(PriceSnapshot.class))).thenReturn(saved);

        Map<String, SnapshotResult> result = marketDataService.getSnapshots(List.of("SBER"));

        verify(priceSnapshotRepository).saveAndFlush(any());
        assertThat(result.get("SBER").lastPrice()).isNull();
        assertThat(result.get("SBER").previousClose()).isEqualByComparingTo("308.00");
        assertThat(result.get("SBER").stale()).isFalse();
    }

    @Test
    @DisplayName("getSnapshots — снимок в БД устарел, у MOEX LAST пуст, но PREVPRICE пришёл (бумага долго не торгуется) → цена закрытия и время обновляются, снимок больше не устаревший")
    void getSnapshots_moexReturnsNullLastPriceWithFreshPreviousClose_existingSnapshotRefreshed_noLongerStale() {
        PriceSnapshot staleSnapshot = PriceSnapshot.builder()
                .ticker("SBER")
                .lastPrice(new BigDecimal("300.00"))
                .previousClose(new BigDecimal("298.00"))
                .fetchedAt(Instant.now().minusSeconds(600))
                .build();
        when(priceSnapshotRepository.findAllById(List.of("SBER"))).thenReturn(List.of(staleSnapshot));
        when(moexProperties.getSnapshotTtlMinutes()).thenReturn(5);
        when(securityRepository.existsById("SBER")).thenReturn(true);
        MoexSnapshotDto dto = new MoexSnapshotDto("SBER", null, new BigDecimal("308.00"));
        when(moexIssClient.fetchSnapshots(List.of("SBER"))).thenReturn(Map.of("SBER", dto));
        when(priceSnapshotRepository.findById("SBER")).thenReturn(Optional.of(staleSnapshot));
        when(priceSnapshotRepository.saveAndFlush(any(PriceSnapshot.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, SnapshotResult> result = marketDataService.getSnapshots(List.of("SBER"));

        // MOEX did answer (just with no new trade) — the row must not sit stale forever just
        // because LAST stayed empty: previousClose/fetchedAt are refreshed, lastPrice preserved.
        verify(priceSnapshotRepository).saveAndFlush(any());
        assertThat(result.get("SBER").lastPrice()).isEqualByComparingTo("300.00");
        assertThat(result.get("SBER").previousClose()).isEqualByComparingTo("308.00");
        assertThat(result.get("SBER").stale()).isFalse();
    }

    @Test
    @DisplayName("refreshSnapshots — принудительно вызывает MOEX даже для свежего снимка в БД")
    void refreshSnapshots_callsMoexEvenWhenDbSnapshotFresh() {
        when(securityRepository.existsById("SBER")).thenReturn(true);
        MoexSnapshotDto dto = new MoexSnapshotDto("SBER", new BigDecimal("315.00"), new BigDecimal("310.00"));
        when(moexIssClient.fetchSnapshots(List.of("SBER"))).thenReturn(Map.of("SBER", dto));
        PriceSnapshot updated = PriceSnapshot.builder().ticker("SBER")
                .lastPrice(new BigDecimal("315.00")).previousClose(new BigDecimal("310.00"))
                .fetchedAt(Instant.now()).build();
        when(priceSnapshotRepository.findById("SBER")).thenReturn(Optional.empty());
        when(priceSnapshotRepository.saveAndFlush(any(PriceSnapshot.class))).thenReturn(updated);

        Map<String, SnapshotResult> result = marketDataService.refreshSnapshots(List.of("SBER"));

        // No isStale/DB-freshness check consulted at all — refreshSnapshots always calls MOEX.
        verify(priceSnapshotRepository, never()).findAllById(any());
        verify(moexIssClient).fetchSnapshots(List.of("SBER"));
        assertThat(result.get("SBER").lastPrice()).isEqualByComparingTo("315.00");
    }

    @Test
    @DisplayName("saveHistoryAndUpdateStatus — пустой список свечей и истории ещё нет → статус READY не проставляется")
    void saveHistoryAndUpdateStatus_emptyRecordsAndNoExistingHistory_notMarkedReady() {
        when(priceHistoryRepository.existsByTicker("SBER")).thenReturn(false);

        marketDataService.saveHistoryAndUpdateStatus("SBER", List.of());

        verify(securityRepository, never()).findById(anyString());
        verify(securityRepository, never()).save(any());
    }

    @Test
    @DisplayName("saveHistoryAndUpdateStatus — пустой список свечей, но история уже сохранена ранее → статус остаётся READY")
    void saveHistoryAndUpdateStatus_emptyRecordsButHistoryAlreadyExists_stillMarkedReady() {
        when(priceHistoryRepository.existsByTicker("SBER")).thenReturn(true);
        Security security = Security.builder().ticker("SBER").historyStatus(HistoryStatus.READY).build();
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(security));

        marketDataService.saveHistoryAndUpdateStatus("SBER", List.of());

        verify(securityRepository).save(security);
        assertThat(security.getHistoryStatus()).isEqualTo(HistoryStatus.READY);
    }

    private List<MoexSecurityDto> searchFixture() {
        return List.of(
                new MoexSecurityDto("SBER", "TQBR", "Сбербанк", SecurityType.STOCK, null, "RUB"),
                new MoexSecurityDto("FXRL", "TQTF", "FinEx ETF", SecurityType.ETF, null, "RUB"),
                new MoexSecurityDto("RU000A0JX0J2", "TQCB", "Корп. облигация", SecurityType.BOND, null, "RUB"),
                new MoexSecurityDto("SU26238RMFS4", "TQOB", "ОФЗ 26238", SecurityType.OFZ, null, "RUB")
        );
    }
}
