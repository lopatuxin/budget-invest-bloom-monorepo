package pyc.lopatuxin.investment.service.market;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DividendLoaderService {

    private final DividendSyncService dividendSyncService;

    @Async("historyLoaderExecutor")
    public void loadAsync(String ticker) {
        try {
            dividendSyncService.syncDividends(ticker);
        } catch (Exception e) {
            log.warn("Async dividend load failed for {}: {}", ticker, e.getMessage());
        }
    }
}
