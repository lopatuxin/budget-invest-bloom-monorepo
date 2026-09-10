package pyc.lopatuxin.investment.client.tinvest;

import java.time.Instant;

public record TinvestDividendsRequest(String instrumentId, Instant from, Instant to) {
}
