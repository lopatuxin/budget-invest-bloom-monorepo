package pyc.lopatuxin.investment.client.tinvest;

import java.time.Instant;

/**
 * One row of {@code GetBondCoupons}' {@code events[]} (T-Invest API contract, InstrumentsService).
 * Only the fields the app actually uses: {@code fixDate} is the record-date analogue,
 * {@code couponDate} the payment date, {@code payOneBond} the amount for a single bond.
 */
public record TinvestCoupon(
        Instant couponDate,
        Instant fixDate,
        TinvestMoneyValue payOneBond,
        Long couponNumber,
        String couponType
) {
}
