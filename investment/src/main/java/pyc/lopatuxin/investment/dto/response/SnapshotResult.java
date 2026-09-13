package pyc.lopatuxin.investment.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record SnapshotResult(
        BigDecimal lastPrice,
        BigDecimal previousClose,
        Instant fetchedAt,
        boolean stale,
        // Accrued coupon interest (NKD), rubles — bonds/OFZ only, see PriceSnapshot.accruedInterest.
        BigDecimal accruedInterest
) {
    public SnapshotResult(BigDecimal lastPrice, BigDecimal previousClose, Instant fetchedAt, boolean stale) {
        this(lastPrice, previousClose, fetchedAt, stale, null);
    }
}
