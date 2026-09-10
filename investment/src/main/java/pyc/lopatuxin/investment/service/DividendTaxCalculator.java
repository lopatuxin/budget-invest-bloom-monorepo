package pyc.lopatuxin.investment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pyc.lopatuxin.investment.config.DividendTaxProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The flat-rate personal-income-tax rule applied to a dividend line's already-multiplied total
 * (per-share amount times quantity on the record date) — never per share. One place for the
 * formula so every caller (recent and upcoming dividends, the "capital" page's next-dividend
 * port) shows the same number.
 */
@Component
@RequiredArgsConstructor
public class DividendTaxCalculator {

    private static final String TAXABLE_CURRENCY = "RUB";
    private static final int PERCENT_SCALE = 1;

    private final DividendTaxProperties dividendTaxProperties;

    // A foreign-currency dividend follows a different tax regime (out of scope, plan point 12)
    // and is returned as declared, without any withholding.
    public BigDecimal netAmount(BigDecimal grossAmount, String currency) {
        if (!TAXABLE_CURRENCY.equals(currency)) {
            return grossAmount;
        }
        return grossAmount.subtract(taxAmount(grossAmount));
    }

    // Rounded down to whole rubles, not kopecks — brokers withhold whole-currency-unit tax.
    // Verified against a live VTB payout: 320.43 * 0.13 = 41.6559 -> withheld 41 (plan point 10).
    private BigDecimal taxAmount(BigDecimal grossAmount) {
        return grossAmount.multiply(dividendTaxProperties.getRate())
                .setScale(0, RoundingMode.DOWN);
    }

    // e.g. rate 0.13 -> 13.0, for PortfolioOverviewDto.dividendTaxRatePercent (plan point 18).
    public BigDecimal taxRatePercent() {
        return dividendTaxProperties.getRate()
                .multiply(BigDecimal.valueOf(100))
                .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }
}
