package pyc.lopatuxin.budget.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TrendFormatter")
class TrendFormatterTest {

    @Test
    @DisplayName("Должен вернуть +0.0% когда предыдущее значение равно нулю")
    void shouldReturnZeroTrendWhenPreviousIsZero() {
        String result = TrendFormatter.formatTrend(new BigDecimal("50000"), BigDecimal.ZERO);

        assertThat(result).isEqualTo("+0.0%");
    }

    @Test
    @DisplayName("Должен вернуть +0.0% когда оба значения равны нулю")
    void shouldReturnZeroTrendWhenBothAreZero() {
        String result = TrendFormatter.formatTrend(BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(result).isEqualTo("+0.0%");
    }

    @Test
    @DisplayName("Должен корректно форматировать положительный тренд (+8.2%)")
    void shouldFormatPositiveTrend() {
        // (108200 - 100000) / 100000 * 100 = +8.2%
        String result = TrendFormatter.formatTrend(new BigDecimal("108200"), new BigDecimal("100000"));

        assertThat(result).isEqualTo("+8.2%");
    }

    @Test
    @DisplayName("Должен корректно форматировать отрицательный тренд (-3.1%)")
    void shouldFormatNegativeTrend() {
        // (87210 - 90000) / 90000 * 100 = -3.1%
        String result = TrendFormatter.formatTrend(new BigDecimal("87210"), new BigDecimal("90000"));

        assertThat(result).isEqualTo("-3.1%");
    }

    @Test
    @DisplayName("Должен вернуть +0.0% когда текущее и предыдущее значения равны")
    void shouldReturnZeroTrendWhenValuesAreEqual() {
        String result = TrendFormatter.formatTrend(new BigDecimal("50000"), new BigDecimal("50000"));

        assertThat(result).isEqualTo("+0.0%");
    }

    @Test
    @DisplayName("Должен корректно форматировать 100% рост (+100.0%)")
    void shouldFormatDoubledValue() {
        // (200000 - 100000) / 100000 * 100 = +100.0%
        String result = TrendFormatter.formatTrend(new BigDecimal("200000"), new BigDecimal("100000"));

        assertThat(result).isEqualTo("+100.0%");
    }

    @Test
    @DisplayName("Должен корректно форматировать полное снижение до нуля (-100.0%)")
    void shouldFormatCompleteDecrease() {
        // (0 - 100000) / 100000 * 100 = -100.0%
        String result = TrendFormatter.formatTrend(BigDecimal.ZERO, new BigDecimal("100000"));

        assertThat(result).isEqualTo("-100.0%");
    }

    @Test
    @DisplayName("Должен корректно округлять до одного знака после запятой")
    void shouldRoundToOneDecimalPlace() {
        // (10333 - 10000) / 10000 * 100 = 3.33 → 3.3%
        String result = TrendFormatter.formatTrend(new BigDecimal("10333"), new BigDecimal("10000"));

        assertThat(result).isEqualTo("+3.3%");
    }

    @Test
    @DisplayName("Должен корректно округлять вверх при пороговом значении (>=5)")
    void shouldRoundUpAtBoundary() {
        // (10555 - 10000) / 10000 * 100 = 5.55 → 5.6%
        String result = TrendFormatter.formatTrend(new BigDecimal("10555"), new BigDecimal("10000"));

        assertThat(result).isEqualTo("+5.6%");
    }

    @Test
    @DisplayName("Должен корректно работать с отрицательным предыдущим значением")
    void shouldHandleNegativePreviousValue() {
        // (50 - (-100)) / |-100| * 100 = 150 / 100 * 100 = +150.0%
        String result = TrendFormatter.formatTrend(new BigDecimal("50"), new BigDecimal("-100"));

        assertThat(result).isEqualTo("+150.0%");
    }

    @Test
    @DisplayName("Должен корректно работать с дробными значениями")
    void shouldHandleDecimalValues() {
        // (150.75 - 100.50) / 100.50 * 100 = 50.0%
        String result = TrendFormatter.formatTrend(new BigDecimal("150.75"), new BigDecimal("100.50"));

        assertThat(result).isEqualTo("+50.0%");
    }

    @Test
    @DisplayName("Должен корректно работать с очень маленьким изменением (+0.1%)")
    void shouldHandleSmallChange() {
        // (100100 - 100000) / 100000 * 100 = +0.1%
        String result = TrendFormatter.formatTrend(new BigDecimal("100100"), new BigDecimal("100000"));

        assertThat(result).isEqualTo("+0.1%");
    }
}
