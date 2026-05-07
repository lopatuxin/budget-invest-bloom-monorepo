package pyc.lopatuxin.investment.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record SnapshotResult(
        BigDecimal lastPrice,
        BigDecimal previousClose,
        Instant fetchedAt,
        boolean stale
) {
}
