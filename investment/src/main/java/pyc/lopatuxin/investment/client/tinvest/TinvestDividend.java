package pyc.lopatuxin.investment.client.tinvest;

import java.time.Instant;

public record TinvestDividend(
        TinvestMoneyValue dividendNet,
        Instant recordDate,
        Instant paymentDate
) {
}
