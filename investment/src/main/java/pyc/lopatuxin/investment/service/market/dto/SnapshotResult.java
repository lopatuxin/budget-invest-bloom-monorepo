package pyc.lopatuxin.investment.service.market.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record SnapshotResult(
        BigDecimal lastPrice,
        BigDecimal previousClose,
        Instant fetchedAt,
        boolean stale
) {
}
