package pyc.lopatuxin.investment.service.market;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.investment.client.tinvest.TinvestApi;
import pyc.lopatuxin.investment.client.tinvest.TinvestFindInstrumentRequest;
import pyc.lopatuxin.investment.client.tinvest.TinvestFindInstrumentResponse;
import pyc.lopatuxin.investment.client.tinvest.TinvestInstrumentShort;
import pyc.lopatuxin.investment.client.tinvest.TinvestResilience;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.repository.SecurityRepository;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TinvestInstrumentResolverTest")
class TinvestInstrumentResolverTest {

    @Mock
    private TinvestApi tinvestApi;

    @Mock
    private TinvestResilience tinvestResilience;

    @Mock
    private SecurityRepository securityRepository;

    private TinvestInstrumentResolver resolver;

    // TinvestInstrumentResolver has a @Lazy self-reference for the @Transactional proxy call
    // from within resolve() (same pattern as DividendSyncService, see DividendSyncServiceTest):
    // @InjectMocks cannot wire it, so it is set via reflection to the resolver itself.
    @BeforeEach
    void setUp() throws Exception {
        resolver = new TinvestInstrumentResolver(tinvestApi, tinvestResilience, securityRepository, null);
        Field selfField = TinvestInstrumentResolver.class.getDeclaredField("self");
        selfField.setAccessible(true);
        selfField.set(resolver, resolver);
    }

    @SuppressWarnings("unchecked")
    private void stubResilienceExecutesSupplier() {
        when(tinvestResilience.execute(anyString(), any())).thenAnswer(invocation -> {
            Supplier<TinvestFindInstrumentResponse> supplier = invocation.getArgument(1);
            return supplier.get();
        });
    }

    private Security security(SecurityType type) {
        return Security.builder().ticker("SBER").name("Сбербанк").type(type)
                .historyStatus(HistoryStatus.READY).build();
    }

    @Test
    @DisplayName("resolve — уже есть tinvestUid → сеть не вызывается")
    void resolve_alreadyHasUid_doesNotCallNetwork() {
        Security sber = security(SecurityType.STOCK);
        sber.setTinvestUid("existing-uid");

        boolean result = resolver.resolve(sber);

        assertThat(result).isTrue();
        verify(tinvestResilience, never()).execute(anyString(), any());
    }

    @Test
    @DisplayName("resolve — акция, несколько площадок → выбирается TQBR")
    void resolve_stock_choosesTqbrAmongSeveralBoards() {
        stubResilienceExecutesSupplier();
        Security sber = security(SecurityType.STOCK);
        // saveResolution re-reads the Security by ticker inside its own write transaction instead
        // of merging the (possibly stale) instance resolve() was called with — see saveResolution's
        // own javadoc-style comment. Stubbed to return the same instance here since this test only
        // cares that the resolved uid/classCode end up persisted, not about the lost-update fix
        // itself (covered separately for DividendSyncService/mergeAndPersist).
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(sber));
        when(tinvestApi.findInstrument(any())).thenReturn(new TinvestFindInstrumentResponse(List.of(
                new TinvestInstrumentShort("SBER", "SPEQ", "uid-speq"),
                new TinvestInstrumentShort("SBER", "TQBR", "uid-tqbr"),
                new TinvestInstrumentShort("SBER", "BEB", "uid-beb")
        )));

        boolean result = resolver.resolve(sber);

        assertThat(result).isTrue();
        assertThat(sber.getTinvestUid()).isEqualTo("uid-tqbr");
        assertThat(sber.getTinvestClassCode()).isEqualTo("TQBR");
        verify(securityRepository).save(sber);
    }

    @Test
    @DisplayName("resolve — Security изменился в БД, пока шёл сетевой поиск инструмента → saveResolution перечитывает свежую сущность, не затирая её чужие поля устаревшим снимком")
    void resolve_securityChangedDuringNetworkCall_saveResolutionUsesFreshEntity() {
        stubResilienceExecutesSupplier();
        Security staleSecurity = security(SecurityType.STOCK);
        Security freshSecurity = Security.builder()
                .ticker("SBER").name("Сбербанк").type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.PENDING).build();
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(freshSecurity));
        when(tinvestApi.findInstrument(any())).thenReturn(new TinvestFindInstrumentResponse(List.of(
                new TinvestInstrumentShort("SBER", "TQBR", "uid-tqbr"))));

        boolean result = resolver.resolve(staleSecurity);

        assertThat(result).isTrue();
        verify(securityRepository).save(freshSecurity);
        verify(securityRepository, never()).save(staleSecurity);
        assertThat(freshSecurity.getTinvestUid()).isEqualTo("uid-tqbr");
        assertThat(freshSecurity.getHistoryStatus()).isEqualTo(HistoryStatus.PENDING);
    }

    @Test
    @DisplayName("resolve — ОФЗ → выбирается TQOB, если нет — TQCB")
    void resolve_ofz_choosesTqobThenTqcb() {
        stubResilienceExecutesSupplier();
        Security ofz = Security.builder().ticker("SU26219").name("ОФЗ 26219").type(SecurityType.OFZ)
                .historyStatus(HistoryStatus.READY).build();
        when(tinvestApi.findInstrument(any())).thenReturn(new TinvestFindInstrumentResponse(List.of(
                new TinvestInstrumentShort("SU26219", "TQCB", "uid-tqcb"),
                new TinvestInstrumentShort("SU26219", "TQOB", "uid-tqob")
        )));

        resolver.resolve(ofz);

        assertThat(ofz.getTinvestUid()).isEqualTo("uid-tqob");
    }

    @Test
    @DisplayName("resolve — нет предпочтительного classCode → берётся первый с совпавшим тикером")
    void resolve_noPreferredClassCode_choosesFirstMatch() {
        stubResilienceExecutesSupplier();
        Security sber = security(SecurityType.STOCK);
        when(tinvestApi.findInstrument(any())).thenReturn(new TinvestFindInstrumentResponse(List.of(
                new TinvestInstrumentShort("SBER", "SPEQ", "uid-speq"),
                new TinvestInstrumentShort("SBER", "BEB", "uid-beb")
        )));

        resolver.resolve(sber);

        assertThat(sber.getTinvestUid()).isEqualTo("uid-speq");
    }

    @Test
    @DisplayName("resolve — совпадений по тикеру нет → false, warn, uid не сохраняется")
    void resolve_noMatches_returnsFalse() {
        stubResilienceExecutesSupplier();
        Security sber = security(SecurityType.STOCK);
        when(tinvestApi.findInstrument(any())).thenReturn(new TinvestFindInstrumentResponse(List.of(
                new TinvestInstrumentShort("GAZP", "TQBR", "uid-gazp")
        )));

        boolean result = resolver.resolve(sber);

        assertThat(result).isFalse();
        assertThat(sber.getTinvestUid()).isNull();
        verify(securityRepository, never()).save(any());
    }

    @Test
    @DisplayName("resolve — тикер сравнивается без учёта регистра")
    void resolve_matchesTickerCaseInsensitively() {
        stubResilienceExecutesSupplier();
        Security sber = security(SecurityType.STOCK);
        when(tinvestApi.findInstrument(any())).thenReturn(new TinvestFindInstrumentResponse(List.of(
                new TinvestInstrumentShort("sber", "TQBR", "uid-tqbr")
        )));

        boolean result = resolver.resolve(sber);

        assertThat(result).isTrue();
        assertThat(sber.getTinvestUid()).isEqualTo("uid-tqbr");
    }

    @Test
    @DisplayName("resolve — запрос содержит тикер и корректный instrumentKind по типу бумаги")
    void resolve_sendsCorrectRequestForType() {
        stubResilienceExecutesSupplier();
        Security etf = Security.builder().ticker("TMOS").name("Тинькофф Мосбиржа").type(SecurityType.ETF)
                .historyStatus(HistoryStatus.READY).build();
        when(tinvestApi.findInstrument(any())).thenReturn(new TinvestFindInstrumentResponse(List.of(
                new TinvestInstrumentShort("TMOS", "TQTF", "uid-tmos")
        )));

        resolver.resolve(etf);

        org.mockito.ArgumentCaptor<TinvestFindInstrumentRequest> captor =
                org.mockito.ArgumentCaptor.forClass(TinvestFindInstrumentRequest.class);
        verify(tinvestApi).findInstrument(captor.capture());
        assertThat(captor.getValue().query()).isEqualTo("TMOS");
        assertThat(captor.getValue().instrumentKind()).isEqualTo("INSTRUMENT_TYPE_ETF");
    }
}
