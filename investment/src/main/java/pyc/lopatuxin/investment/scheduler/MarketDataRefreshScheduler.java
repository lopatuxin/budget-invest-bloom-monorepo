package pyc.lopatuxin.investment.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pyc.lopatuxin.investment.client.moex.MoexUnavailableException;
import pyc.lopatuxin.investment.repository.PositionRepository;
import pyc.lopatuxin.investment.service.market.DividendSyncService;
import pyc.lopatuxin.investment.service.market.MarketDataService;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataRefreshScheduler {

    private final PositionRepository positionRepository;
    private final MarketDataService marketDataService;
    private final DividendSyncService dividendSyncService;

    // every 5 min during MSK trading hours Mon-Fri
    @Scheduled(cron = "0 */5 10-18 * * MON-FRI", zone = "Europe/Moscow")
    public void refreshActiveSnapshots() {
        List<String> tickers = positionRepository.findActiveTickers();
        if (tickers.isEmpty()) return;
        log.debug("Refreshing snapshots for {} tickers", tickers.size());
        try {
            marketDataService.getSnapshots(tickers);
        } catch (MoexUnavailableException e) {
            log.warn("MOEX unavailable during snapshot refresh: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Snapshot refresh failed", e);
        }
    }

    // daily at 12:00 MSK
    @Scheduled(cron = "0 0 12 * * *", zone = "Europe/Moscow")
    public void refreshHistoryAndDividends() {
        List<String> tickers = positionRepository.findActiveTickers();
        if (tickers.isEmpty()) return;
        log.info("Nightly refresh for {} tickers", tickers.size());
        for (String ticker : tickers) {
            try {
                marketDataService.triggerHistoryAsync(ticker);
                dividendSyncService.syncDividends(ticker);
            } catch (Exception e) {
                log.warn("Nightly refresh failed for {}: {}", ticker, e.getMessage());
            }
        }
    }
}
