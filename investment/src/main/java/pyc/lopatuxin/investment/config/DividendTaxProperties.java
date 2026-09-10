package pyc.lopatuxin.investment.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Getter
@Setter
@Validated
@ConfigurationProperties("dividend-tax")
public class DividendTaxProperties {

    // Flat personal-income-tax rate withheld from RUB dividends (see DividendTaxCalculator).
    // No progressive brackets, deductions or IIS accounting — out of scope by design.
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private BigDecimal rate = new BigDecimal("0.13");
}
