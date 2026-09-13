package pyc.lopatuxin.investment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.SecurityType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The one place that turns an exchange quote into rubles (plan point 2 of the forecast-coupons
 * plan). MOEX quotes a BOND/OFZ as a percentage of its face value (e.g. 99.95 for a 1000₽ bond
 * means 999.50₽ a piece) — every other security type is already quoted in rubles and passes
 * through unchanged.
 */
@Slf4j
@Component
public class BondPricing {

    private static final BigDecimal DEFAULT_NOMINAL = BigDecimal.valueOf(1000);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int SCALE = 2;
    private static final Set<SecurityType> QUOTED_AS_PERCENT_OF_PAR = EnumSet.of(SecurityType.BOND, SecurityType.OFZ);

    // Tickers already warned about a missing nominal — a defaulted-to-1000 bond stays defaulted
    // on every later poll/graph point, so without this the warning would repeat on every single
    // one of them (thousands of lines when a chart is built from years of price history).
    private final Set<String> warnedMissingNominalTickers = ConcurrentHashMap.newKeySet();

    public boolean isQuotedAsPercentOfPar(SecurityType type) {
        return QUOTED_AS_PERCENT_OF_PAR.contains(type);
    }

    /**
     * @param quoted the exchange price: percent-of-par for a BOND/OFZ, rubles for anything else
     * @return the ruble price, or {@code null} when {@code quoted} is null
     */
    public BigDecimal quotedToRubles(SecurityType type, String ticker, BigDecimal nominal, BigDecimal quoted) {
        if (quoted == null) {
            return null;
        }
        if (!isQuotedAsPercentOfPar(type)) {
            return quoted;
        }
        BigDecimal effectiveNominal = nominal;
        if (effectiveNominal == null) {
            if (warnedMissingNominalTickers.add(ticker)) {
                log.warn("Номинал не известен для {}, принят {} ₽", ticker, DEFAULT_NOMINAL);
            }
            effectiveNominal = DEFAULT_NOMINAL;
        }
        return quoted.multiply(effectiveNominal).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal quotedToRubles(Security security, BigDecimal quoted) {
        return quotedToRubles(security.getType(), security.getTicker(), security.getNominal(), quoted);
    }

    /**
     * Position/portfolio valuation (plan point 3): quantity times the ruble price, plus accrued
     * coupon interest for a bond so the total matches what a broker shows. Price history and
     * price growth never call this — they stay clean quote-only series.
     */
    public BigDecimal rubleValue(Security security, BigDecimal quoted, BigDecimal accruedInterest, BigDecimal quantity) {
        BigDecimal rubPrice = quotedToRubles(security, quoted);
        if (rubPrice == null) {
            return null;
        }
        BigDecimal perUnit = accruedInterest != null ? rubPrice.add(accruedInterest) : rubPrice;
        return perUnit.multiply(quantity);
    }
}
