package pyc.lopatuxin.investment.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.investment.client.moex.MoexUnavailableException;
import pyc.lopatuxin.investment.repository.PositionRepository;
import pyc.lopatuxin.investment.service.market.DividendSyncService;
import pyc.lopatuxin.investment.service.market.MarketDataService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarketDataRefreshSchedulerTest")
class MarketDataRefreshSchedulerTest {

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private MarketDataService marketDataService;

    @Mock
    private DividendSyncService dividendSyncService;

    @InjectMocks
    private MarketDataRefreshScheduler scheduler;

    @Test
    @DisplayName("refreshActiveSnapshots — тикеры есть → getSnapshots вызван с этим списком")
    void refreshActiveSnapshots_callsGetSnapshots_whenTickersPresent() {
        when(positionRepository.findActiveTickers()).thenReturn(List.of("SBER"));

        scheduler.refreshActiveSnapshots();

        verify(marketDataService).getSnapshots(List.of("SBER"));
    }

    @Test
    @DisplayName("refreshActiveSnapshots — пустой список тикеров → getSnapshots не вызывается")
    void refreshActiveSnapshots_doesNothing_whenNoTickers() {
        when(positionRepository.findActiveTickers()).thenReturn(List.of());

        scheduler.refreshActiveSnapshots();

        verify(marketDataService, never()).getSnapshots(anyList());
    }

    @Test
    @DisplayName("refreshActiveSnapshots — getSnapshots бросает MoexUnavailableException → метод не падает")
    void refreshActiveSnapshots_doesNotThrow_whenMoexUnavailable() {
        when(positionRepository.findActiveTickers()).thenReturn(List.of("SBER"));
        when(marketDataService.getSnapshots(anyList()))
                .thenThrow(new MoexUnavailableException("MOEX unavailable"));

        assertThatCode(() -> scheduler.refreshActiveSnapshots())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("refreshHistoryAndDividends — тикер GAZP → triggerHistoryAsync и syncDividends вызваны")
    void refreshHistoryAndDividends_callsBothServices() {
        when(positionRepository.findActiveTickers()).thenReturn(List.of("GAZP"));

        scheduler.refreshHistoryAndDividends();

        verify(marketDataService).triggerHistoryAsync("GAZP");
        verify(dividendSyncService).syncDividends("GAZP");
    }

    @Test
    @DisplayName("refreshHistoryAndDividends — первый тикер бросает исключение → второй тикер обрабатывается")
    void refreshHistoryAndDividends_continuesOnError_forOneTicker() {
        when(positionRepository.findActiveTickers()).thenReturn(List.of("SBER", "GAZP"));
        // triggerHistoryAsync is void; use doThrow for the first ticker, then proceed normally
        doThrow(new RuntimeException("history unavailable"))
                .when(marketDataService).triggerHistoryAsync("SBER");

        scheduler.refreshHistoryAndDividends();

        // second ticker must still be processed
        verify(marketDataService).triggerHistoryAsync("GAZP");
        verify(dividendSyncService).syncDividends("GAZP");
    }
}
