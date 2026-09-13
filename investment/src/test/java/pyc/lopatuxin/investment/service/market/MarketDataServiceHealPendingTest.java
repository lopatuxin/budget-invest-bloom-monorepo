package pyc.lopatuxin.investment.service.market;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.investment.client.moex.MoexIssClient;
import pyc.lopatuxin.investment.client.moex.MoexUnavailableException;
import pyc.lopatuxin.investment.config.MoexProperties;
import pyc.lopatuxin.investment.dto.response.MoexCandleDto;
import pyc.lopatuxin.investment.dto.response.MoexSecurityDto;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.repository.PriceHistoryRepository;
import pyc.lopatuxin.investment.repository.PriceSnapshotRepository;
import pyc.lopatuxin.investment.repository.SecurityRepository;
import pyc.lopatuxin.investment.service.BondPricing;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarketDataServiceHealPendingTest — самолечение бумаг в статусе PENDING")
class MarketDataServiceHealPendingTest {

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
    private Security pending;

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
                new BondPricing(),
                null   // self — set below
        ));
        Field selfField = MarketDataService.class.getDeclaredField("self");
        selfField.setAccessible(true);
        selfField.set(marketDataService, marketDataService);

        pending = Security.builder().ticker("SBER").name("SBER").type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.PENDING).build();
    }

    @Test
    @DisplayName("healPendingSecurities — биржа доступна → PENDING становится READY с сектором из MOEX")
    void healPendingSecurities_moexAvailable_becomesReadyWithSector() {
        when(securityRepository.findAllByHistoryStatus(HistoryStatus.PENDING)).thenReturn(List.of(pending));
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(pending));
        MoexSecurityDto moexDto = new MoexSecurityDto("SBER", "TQBR", "Сбербанк", SecurityType.STOCK, "Финансы", "RUB");
        when(moexIssClient.fetchSecurity("SBER")).thenReturn(Optional.of(moexDto));
        MoexCandleDto candle = new MoexCandleDto("SBER", LocalDate.now(), null, null, null, null, null);
        when(moexIssClient.fetchHistory(anyString(), any(), any())).thenReturn(List.of(candle));

        marketDataService.healPendingSecurities();

        verify(securityRepository, atLeastOnce()).save(pending);
        assertThat(pending.getSector()).isEqualTo("Финансы");
        assertThat(pending.getName()).isEqualTo("Сбербанк");
        assertThat(pending.getHistoryStatus()).isEqualTo(HistoryStatus.READY);
        verify(priceHistoryRepository).saveAll(any());
    }

    @Test
    @DisplayName("healPendingSecurities — биржа недоступна → остаётся PENDING, исключение не выбрасывается")
    void healPendingSecurities_moexUnavailable_staysPendingWithoutException() {
        when(securityRepository.findAllByHistoryStatus(HistoryStatus.PENDING)).thenReturn(List.of(pending));
        when(moexIssClient.fetchSecurity("SBER")).thenThrow(new MoexUnavailableException("MOEX down"));

        assertThatCode(() -> marketDataService.healPendingSecurities()).doesNotThrowAnyException();

        assertThat(pending.getHistoryStatus()).isEqualTo(HistoryStatus.PENDING);
        verify(securityRepository, never()).save(any());
        verify(priceHistoryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("healPendingSecurities — справочник восстановлен, но история не загрузилась → бумага не считается вылеченной")
    void healPendingSecurities_dictionaryHealedButHistoryFails_notCountedAsHealed() {
        when(securityRepository.findAllByHistoryStatus(HistoryStatus.PENDING)).thenReturn(List.of(pending));
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(pending));
        MoexSecurityDto moexDto = new MoexSecurityDto("SBER", "TQBR", "Сбербанк", SecurityType.STOCK, "Финансы", "RUB");
        when(moexIssClient.fetchSecurity("SBER")).thenReturn(Optional.of(moexDto));
        when(moexIssClient.fetchHistory(anyString(), any(), any())).thenThrow(new MoexUnavailableException("MOEX down"));

        marketDataService.healPendingSecurities();

        // Dictionary fields (sector, name) were healed, but the security stays PENDING because
        // the history load failed — the "restored N of M" count must not include it.
        assertThat(pending.getSector()).isEqualTo("Финансы");
        assertThat(pending.getHistoryStatus()).isEqualTo(HistoryStatus.PENDING);
        verify(priceHistoryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("healPendingSecurities — одна бумага падает с неожиданным исключением, вторая всё равно лечится")
    void healPendingSecurities_oneTickerThrowsUnexpectedException_othersStillHealed() {
        Security other = Security.builder().ticker("SU26238RMFS4").name("SU26238RMFS4").type(SecurityType.OFZ)
                .historyStatus(HistoryStatus.PENDING).build();
        when(securityRepository.findAllByHistoryStatus(HistoryStatus.PENDING)).thenReturn(List.of(pending, other));
        when(moexIssClient.fetchSecurity("SBER")).thenThrow(new RuntimeException("unexpected parsing failure"));
        when(securityRepository.findById("SU26238RMFS4")).thenReturn(Optional.of(other));
        MoexSecurityDto moexDto = new MoexSecurityDto("SU26238RMFS4", "TQOB", "ОФЗ 26238", SecurityType.OFZ, "Государственные облигации", "RUB");
        when(moexIssClient.fetchSecurity("SU26238RMFS4")).thenReturn(Optional.of(moexDto));
        MoexCandleDto candle = new MoexCandleDto("SU26238RMFS4", LocalDate.now(), null, null, null, null, null);
        when(moexIssClient.fetchHistory(eq("SU26238RMFS4"), any(), any())).thenReturn(List.of(candle));

        assertThatCode(() -> marketDataService.healPendingSecurities()).doesNotThrowAnyException();

        assertThat(other.getHistoryStatus()).isEqualTo(HistoryStatus.READY);
        assertThat(pending.getHistoryStatus()).isEqualTo(HistoryStatus.PENDING);
    }
}
