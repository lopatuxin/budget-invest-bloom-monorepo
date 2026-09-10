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
import static org.mockito.ArgumentMatchers.anyString;
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
    @DisplayName("refreshActiveSnapshots — тикеры есть → refreshSnapshots вызван с этим списком (принудительно, минуя TTL)")
    void refreshActiveSnapshots_callsRefreshSnapshots_whenTickersPresent() {
        when(positionRepository.findActiveTickers()).thenReturn(List.of("SBER"));

        scheduler.refreshActiveSnapshots();

        verify(marketDataService).refreshSnapshots(List.of("SBER"));
    }

    @Test
    @DisplayName("refreshActiveSnapshots — пустой список тикеров → refreshSnapshots не вызывается")
    void refreshActiveSnapshots_doesNothing_whenNoTickers() {
        when(positionRepository.findActiveTickers()).thenReturn(List.of());

        scheduler.refreshActiveSnapshots();

        verify(marketDataService, never()).refreshSnapshots(anyList());
    }

    @Test
    @DisplayName("refreshActiveSnapshots — refreshSnapshots бросает MoexUnavailableException → метод не падает")
    void refreshActiveSnapshots_doesNotThrow_whenMoexUnavailable() {
        when(positionRepository.findActiveTickers()).thenReturn(List.of("SBER"));
        when(marketDataService.refreshSnapshots(anyList()))
                .thenThrow(new MoexUnavailableException("MOEX unavailable"));

        assertThatCode(() -> scheduler.refreshActiveSnapshots())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("refreshHistoryAndDividends — тикер GAZP → triggerHistoryAsync и syncNightly вызваны")
    void refreshHistoryAndDividends_callsBothServices() {
        when(positionRepository.findActiveTickers()).thenReturn(List.of("GAZP"));

        scheduler.refreshHistoryAndDividends();

        verify(marketDataService).triggerHistoryAsync("GAZP");
        verify(dividendSyncService).syncNightly();
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
        verify(dividendSyncService).syncNightly();
    }

    @Test
    @DisplayName("refreshHistoryAndDividends — самолечение PENDING-бумаг вызывается в начале")
    void refreshHistoryAndDividends_healsPendingSecuritiesFirst() {
        when(positionRepository.findActiveTickers()).thenReturn(List.of("GAZP"));

        scheduler.refreshHistoryAndDividends();

        verify(marketDataService).healPendingSecurities();
    }

    @Test
    @DisplayName("refreshHistoryAndDividends — healPendingSecurities падает → syncNightly и markPastRecordDatesAsPaid всё равно вызываются")
    void refreshHistoryAndDividends_healPendingSecuritiesThrows_restOfJobStillRuns() {
        when(positionRepository.findActiveTickers()).thenReturn(List.of("GAZP"));
        doThrow(new RuntimeException("db unavailable")).when(marketDataService).healPendingSecurities();

        assertThatCode(() -> scheduler.refreshHistoryAndDividends())
                .doesNotThrowAnyException();

        verify(marketDataService).triggerHistoryAsync("GAZP");
        verify(dividendSyncService).syncNightly();
        verify(dividendSyncService).markPastRecordDatesAsPaid();
    }

    @Test
    @DisplayName("refreshHistoryAndDividends — перевод дивидендов в статус «выплачено» вызывается даже без активных тикеров")
    void refreshHistoryAndDividends_marksPastRecordDatesAsPaid_evenWhenNoTickers() {
        when(positionRepository.findActiveTickers()).thenReturn(List.of());

        scheduler.refreshHistoryAndDividends();

        verify(dividendSyncService).markPastRecordDatesAsPaid();
        verify(marketDataService, never()).triggerHistoryAsync(anyString());
    }

    @Test
    @DisplayName("refreshHistoryAndDividends — тикеры есть → markPastRecordDatesAsPaid вызывается в конце")
    void refreshHistoryAndDividends_marksPastRecordDatesAsPaid_whenTickersPresent() {
        when(positionRepository.findActiveTickers()).thenReturn(List.of("GAZP"));

        scheduler.refreshHistoryAndDividends();

        verify(dividendSyncService).markPastRecordDatesAsPaid();
    }

    @Test
    @DisplayName("refreshHistoryAndDividends — syncNightly падает → markPastRecordDatesAsPaid всё равно вызывается")
    void refreshHistoryAndDividends_syncNightlyThrows_markPastRecordDatesAsPaidStillRuns() {
        when(positionRepository.findActiveTickers()).thenReturn(List.of("GAZP"));
        doThrow(new RuntimeException("T-Invest unavailable")).when(dividendSyncService).syncNightly();

        assertThatCode(() -> scheduler.refreshHistoryAndDividends())
                .doesNotThrowAnyException();

        verify(dividendSyncService).markPastRecordDatesAsPaid();
    }

    @Test
    @DisplayName("refreshHistoryAndDividends — markPastRecordDatesAsPaid падает → метод не падает (симметрично самолечению PENDING)")
    void refreshHistoryAndDividends_markPastRecordDatesAsPaidThrows_doesNotFailJob() {
        when(positionRepository.findActiveTickers()).thenReturn(List.of("GAZP"));
        doThrow(new RuntimeException("db unavailable")).when(dividendSyncService).markPastRecordDatesAsPaid();

        assertThatCode(() -> scheduler.refreshHistoryAndDividends())
                .doesNotThrowAnyException();

        verify(marketDataService).triggerHistoryAsync("GAZP");
        verify(dividendSyncService).syncNightly();
    }
}
