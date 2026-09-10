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
            // Forces the MOEX round-trip regardless of the DB snapshot's TTL: this job's cron
            // period equals that TTL (see MarketDataService.refreshSnapshots), so getSnapshots
            // here would silently skip a snapshot upserted moments into the previous run.
            marketDataService.refreshSnapshots(tickers);
        } catch (MoexUnavailableException e) {
            log.warn("MOEX unavailable during snapshot refresh: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Snapshot refresh failed", e);
        }
    }

    // daily at 20:30 MSK
    @Scheduled(cron = "0 30 20 * * *", zone = "Europe/Moscow")
    public void refreshHistoryAndDividends() {
        try {
            marketDataService.healPendingSecurities();
        } catch (Exception e) {
            log.warn("Самолечение PENDING в ночном задании не выполнено: {}", e.getMessage());
        }
        List<String> tickers = positionRepository.findActiveTickers();
        if (!tickers.isEmpty()) {
            log.info("Nightly refresh for {} tickers", tickers.size());
            for (String ticker : tickers) {
                try {
                    marketDataService.triggerHistoryAsync(ticker);
                } catch (Exception e) {
                    log.warn("Nightly refresh failed for {}: {}", ticker, e.getMessage());
                }
            }
        }
        try {
            dividendSyncService.syncNightly();
        } catch (Exception e) {
            log.warn("Ночная синхронизация дивидендов T-Invest не выполнена: {}", e.getMessage());
        }
        try {
            dividendSyncService.markPastRecordDatesAsPaid();
        } catch (Exception e) {
            log.warn("Перевод дивидендов из ANNOUNCED в PAID в ночном задании не выполнен: {}", e.getMessage());
        }
    }
}
