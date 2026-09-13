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
import pyc.lopatuxin.investment.dto.response.MoexSecurityDto;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.repository.PriceHistoryRepository;
import pyc.lopatuxin.investment.repository.PriceSnapshotRepository;
import pyc.lopatuxin.investment.repository.SecurityRepository;
import pyc.lopatuxin.investment.service.BondPricing;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarketDataServiceBackfillBoardsTest — донасыщение READY-бумаг без площадки")
class MarketDataServiceBackfillBoardsTest {

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
                new BondPricing(),
                null   // self — set below
        ));
        Field selfField = MarketDataService.class.getDeclaredField("self");
        selfField.setAccessible(true);
        selfField.set(marketDataService, marketDataService);
    }

    private Security readyWithoutBoard(String ticker) {
        return Security.builder().ticker(ticker).name(ticker).type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.READY).build();
    }

    @Test
    @DisplayName("backfillMissingBoards — MOEX вернула boardId → площадка заполнена")
    void backfillMissingBoards_moexReturnsBoardId_boardFilled() {
        Security sber = readyWithoutBoard("SBER");
        when(securityRepository.findByBoardIdIsNullAndHistoryStatus(HistoryStatus.READY)).thenReturn(List.of(sber));
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(sber));
        MoexSecurityDto moexDto = new MoexSecurityDto("SBER", "TQBR", "Сбербанк", SecurityType.STOCK, "Финансы", "RUB");
        when(moexIssClient.fetchSecurity("SBER")).thenReturn(Optional.of(moexDto));

        marketDataService.backfillMissingBoards();

        assertThat(sber.getBoardId()).isEqualTo("TQBR");
        verify(securityRepository).save(sber);
    }

    @Test
    @DisplayName("backfillMissingBoards — MOEX недоступна → обход прекращается, следующие тикеры не запрашиваются")
    void backfillMissingBoards_moexUnavailable_stopsWithoutQueryingRest() {
        Security sber = readyWithoutBoard("SBER");
        Security gazp = readyWithoutBoard("GAZP");
        when(securityRepository.findByBoardIdIsNullAndHistoryStatus(HistoryStatus.READY)).thenReturn(List.of(sber, gazp));
        when(moexIssClient.fetchSecurity("SBER")).thenThrow(new MoexUnavailableException("MOEX down"));

        assertThatCode(() -> marketDataService.backfillMissingBoards()).doesNotThrowAnyException();

        verify(moexIssClient, never()).fetchSecurity("GAZP");
        verify(securityRepository, never()).save(gazp);
    }

    @Test
    @DisplayName("backfillMissingBoards — неожиданное исключение на одном тикере не прерывает обход остальных")
    void backfillMissingBoards_unexpectedExceptionOnOneTicker_othersStillProcessed() {
        Security sber = readyWithoutBoard("SBER");
        Security gazp = readyWithoutBoard("GAZP");
        when(securityRepository.findByBoardIdIsNullAndHistoryStatus(HistoryStatus.READY)).thenReturn(List.of(sber, gazp));
        when(moexIssClient.fetchSecurity("SBER")).thenThrow(new RuntimeException("unexpected parsing failure"));
        when(securityRepository.findById("GAZP")).thenReturn(Optional.of(gazp));
        MoexSecurityDto moexDto = new MoexSecurityDto("GAZP", "TQBR", "Газпром", SecurityType.STOCK, "Энергетика", "RUB");
        when(moexIssClient.fetchSecurity("GAZP")).thenReturn(Optional.of(moexDto));

        assertThatCode(() -> marketDataService.backfillMissingBoards()).doesNotThrowAnyException();

        assertThat(gazp.getBoardId()).isEqualTo("TQBR");
        assertThat(sber.getBoardId()).isNull();
    }

    @Test
    @DisplayName("backfillMissingBoards — пустой ответ MOEX пропускается без обновления")
    void backfillMissingBoards_emptyMoexResponse_skipped() {
        Security sber = readyWithoutBoard("SBER");
        when(securityRepository.findByBoardIdIsNullAndHistoryStatus(HistoryStatus.READY)).thenReturn(List.of(sber));
        when(moexIssClient.fetchSecurity("SBER")).thenReturn(Optional.empty());

        marketDataService.backfillMissingBoards();

        verify(securityRepository, never()).save(sber);
    }

    @Test
    @DisplayName("backfillMissingBoards — boardId == null в ответе MOEX пропускается без обновления")
    void backfillMissingBoards_nullBoardIdInResponse_skipped() {
        Security sber = readyWithoutBoard("SBER");
        when(securityRepository.findByBoardIdIsNullAndHistoryStatus(HistoryStatus.READY)).thenReturn(List.of(sber));
        MoexSecurityDto moexDto = new MoexSecurityDto("SBER", null, "Сбербанк", SecurityType.STOCK, "Финансы", "RUB");
        when(moexIssClient.fetchSecurity("SBER")).thenReturn(Optional.of(moexDto));

        marketDataService.backfillMissingBoards();

        verify(securityRepository, never()).save(sber);
    }

    @Test
    @DisplayName("applyBoardId — площадка уже заполнена → не перезаписывает, возвращает false")
    void applyBoardId_boardAlreadyFilled_notOverwritten() {
        Security sber = Security.builder().ticker("SBER").boardId("TQBR").historyStatus(HistoryStatus.READY).build();
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(sber));

        boolean result = marketDataService.applyBoardId("SBER", "TQBR2");

        assertThat(result).isFalse();
        assertThat(sber.getBoardId()).isEqualTo("TQBR");
        verify(securityRepository, never()).save(sber);
    }

    @Test
    @DisplayName("applyBoardId — бумага не найдена → возвращает false")
    void applyBoardId_securityNotFound_returnsFalse() {
        when(securityRepository.findById("UNKN")).thenReturn(Optional.empty());

        boolean result = marketDataService.applyBoardId("UNKN", "TQBR");

        assertThat(result).isFalse();
        verify(securityRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
