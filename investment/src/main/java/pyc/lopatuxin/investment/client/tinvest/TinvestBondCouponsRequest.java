package pyc.lopatuxin.investment.client.tinvest;

import java.time.Instant;

public record TinvestBondCouponsRequest(String instrumentId, Instant from, Instant to) {
}
