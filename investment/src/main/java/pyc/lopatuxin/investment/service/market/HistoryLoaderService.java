package pyc.lopatuxin.investment.service.market;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HistoryLoaderService {

    private final MarketDataService marketDataService;

    public HistoryLoaderService(@Lazy MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @Async("historyLoaderExecutor")
    public void loadAsync(String ticker) {
        try {
            marketDataService.ensureHistory(ticker);
        } catch (Exception e) {
            log.warn("Async history load failed for {}: {}", ticker, e.getMessage());
        }
    }
}
