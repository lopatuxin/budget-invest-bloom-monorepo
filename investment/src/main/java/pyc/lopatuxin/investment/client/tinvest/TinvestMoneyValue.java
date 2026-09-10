package pyc.lopatuxin.investment.client.tinvest;

import java.math.BigDecimal;

/**
 * protobuf JSON mapping of MoneyValue: int64 {@code units} arrives as a JSON string, {@code nano}
 * as a regular number; both parts share the same sign (see the T-Invest API contract, MoneyValue).
 */
public record TinvestMoneyValue(String currency, String units, int nano) {

    public BigDecimal toAmount() {
        if (units == null) {
            return null;
        }
        return new BigDecimal(units).add(BigDecimal.valueOf(nano, 9));
    }
}
