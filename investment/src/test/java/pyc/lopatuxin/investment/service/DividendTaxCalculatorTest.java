package pyc.lopatuxin.investment.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pyc.lopatuxin.investment.config.DividendTaxProperties;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DividendTaxCalculatorTest — правило НДФЛ с дивидендов")
class DividendTaxCalculatorTest {

    @Test
    @DisplayName("netAmount — эталон ВТБ: 320.43 при ставке 13% → налог 41, к получению 279.43")
    void netAmount_vtbReferencePayout_matchesLiveData() {
        DividendTaxCalculator calculator = calculatorWithRate("0.13");

        BigDecimal net = calculator.netAmount(new BigDecimal("320.43"), "RUB");

        assertThat(net).isEqualByComparingTo("279.43");
    }

    @Test
    @DisplayName("netAmount — эталон привилегированного Сбера: 639.88 при ставке 13% → 556.88")
    void netAmount_sberpReferencePayout_matchesLiveData() {
        DividendTaxCalculator calculator = calculatorWithRate("0.13");

        BigDecimal net = calculator.netAmount(new BigDecimal("639.88"), "RUB");

        assertThat(net).isEqualByComparingTo("556.88");
    }

    @Test
    @DisplayName("netAmount — сумма меньше рубля: налог округляется вниз до нуля, к получению вся сумма")
    void netAmount_amountUnderOneRuble_taxRoundsDownToZero() {
        DividendTaxCalculator calculator = calculatorWithRate("0.13");

        BigDecimal net = calculator.netAmount(new BigDecimal("0.50"), "RUB");

        assertThat(net).isEqualByComparingTo("0.50");
    }

    @Test
    @DisplayName("netAmount — ставка 0: сумма не меняется")
    void netAmount_zeroRate_amountUnchanged() {
        DividendTaxCalculator calculator = calculatorWithRate("0");

        BigDecimal net = calculator.netAmount(new BigDecimal("320.43"), "RUB");

        assertThat(net).isEqualByComparingTo("320.43");
    }

    @Test
    @DisplayName("netAmount — иностранная валюта: без вычета налога")
    void netAmount_foreignCurrency_noDeduction() {
        DividendTaxCalculator calculator = calculatorWithRate("0.13");

        BigDecimal net = calculator.netAmount(new BigDecimal("20.00"), "USD");

        assertThat(net).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("taxRatePercent — ставка 0.13 отображается как 13.0")
    void taxRatePercent_convertsFractionToPercent() {
        DividendTaxCalculator calculator = calculatorWithRate("0.13");

        assertThat(calculator.taxRatePercent()).isEqualByComparingTo("13.0");
    }

    @Test
    @DisplayName("taxRatePercent — ставка 0 отображается как 0.0")
    void taxRatePercent_zeroRate_isZeroPercent() {
        DividendTaxCalculator calculator = calculatorWithRate("0");

        assertThat(calculator.taxRatePercent()).isEqualByComparingTo("0.0");
    }

    private DividendTaxCalculator calculatorWithRate(String rate) {
        DividendTaxProperties properties = new DividendTaxProperties();
        properties.setRate(new BigDecimal(rate));
        return new DividendTaxCalculator(properties);
    }
}
