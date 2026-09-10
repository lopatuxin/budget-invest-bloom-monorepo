package pyc.lopatuxin.investment.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the @DecimalMin/@DecimalMax constraints on
 * DividendTaxProperties.rate: a rate outside [0, 1] must fail bean validation with a
 * readable message so the app refuses to start with a bad DIVIDEND_TAX_RATE (see plan's
 * dividends-net-amount.md), instead of silently applying an out-of-range tax. If the annotations
 * or the validation starter ever disappear, this test fails loudly instead of the app just
 * starting up wrong.
 */
@DisplayName("DividendTaxPropertiesTest — валидация ставки налога на дивиденды")
class DividendTaxPropertiesTest {

    private ValidatorFactory validatorFactory;
    private Validator validator;

    @BeforeEach
    void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterEach
    void tearDown() {
        validatorFactory.close();
    }

    @ParameterizedTest(name = "rate={0} -> нарушение ограничения")
    @CsvSource({"-0.01", "1.01", "-1", "2"})
    @DisplayName("ставка вне диапазона 0..1 -> constraint violation с понятным сообщением")
    void rateOutsideZeroToOne_producesConstraintViolation(String rate) {
        DividendTaxProperties properties = new DividendTaxProperties();
        properties.setRate(new BigDecimal(rate));

        Set<ConstraintViolation<DividendTaxProperties>> violations = validator.validate(properties);

        assertThat(violations).isNotEmpty();
        assertThat(violations).allSatisfy(v -> assertThat(v.getMessage()).isNotBlank());
    }

    @ParameterizedTest(name = "rate={0} -> без нарушений")
    @CsvSource({"0", "0.13", "1"})
    @DisplayName("ставка внутри диапазона 0..1 (границы включительно) -> без нарушений")
    void rateWithinZeroToOne_noViolations(String rate) {
        DividendTaxProperties properties = new DividendTaxProperties();
        properties.setRate(new BigDecimal(rate));

        Set<ConstraintViolation<DividendTaxProperties>> violations = validator.validate(properties);

        assertThat(violations).isEmpty();
    }
}
