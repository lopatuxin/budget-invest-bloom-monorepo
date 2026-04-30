package pyc.lopatuxin.investment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.investment.client.moex.MoexIssClient;
import pyc.lopatuxin.investment.client.moex.MoexUnavailableException;
import pyc.lopatuxin.investment.client.moex.dto.MoexSecurityDto;
import pyc.lopatuxin.investment.client.moex.dto.MoexSnapshotDto;
import pyc.lopatuxin.investment.config.MoexProperties;
import pyc.lopatuxin.investment.dto.request.SearchCategory;
import pyc.lopatuxin.investment.entity.PriceSnapshot;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.repository.PriceHistoryRepository;
import pyc.lopatuxin.investment.repository.PriceSnapshotRepository;
import pyc.lopatuxin.investment.repository.SecurityRepository;
import pyc.lopatuxin.investment.service.market.MarketDataService;
import pyc.lopatuxin.investment.service.market.dto.SnapshotResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
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

    @InjectMocks
    private MarketDataService marketDataService;

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

        MoexSnapshotDto moexDto = new MoexSnapshotDto("SBER", new BigDecimal("315.00"), new BigDecimal("310.00"));
        when(moexIssClient.fetchSnapshots(List.of("SBER")))
                .thenReturn(Map.of("SBER", moexDto));

        PriceSnapshot updated = PriceSnapshot.builder()
                .ticker("SBER")
                .lastPrice(new BigDecimal("315.00"))
                .previousClose(new BigDecimal("310.00"))
                .fetchedAt(Instant.now())
                .build();
        when(priceSnapshotRepository.save(any(PriceSnapshot.class))).thenReturn(updated);

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

    private List<MoexSecurityDto> searchFixture() {
        return List.of(
                new MoexSecurityDto("SBER", "TQBR", "Сбербанк", SecurityType.STOCK, null, "RUB"),
                new MoexSecurityDto("FXRL", "TQTF", "FinEx ETF", SecurityType.ETF, null, "RUB"),
                new MoexSecurityDto("RU000A0JX0J2", "TQCB", "Корп. облигация", SecurityType.BOND, null, "RUB"),
                new MoexSecurityDto("SU26238RMFS4", "TQOB", "ОФЗ 26238", SecurityType.OFZ, null, "RUB")
        );
    }
}
