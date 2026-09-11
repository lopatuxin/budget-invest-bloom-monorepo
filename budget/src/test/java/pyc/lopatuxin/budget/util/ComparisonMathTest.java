package pyc.lopatuxin.budget.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pyc.lopatuxin.budget.dto.common.ChangeDto;
import pyc.lopatuxin.budget.entity.enums.NormStatus;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ComparisonMathTest")
class ComparisonMathTest {

    // ─── changeFrom ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Должен рассчитать процент и статус изменения при положительной базе")
    void shouldCalculatePercentAndStatusForPositiveBase() {
        ChangeDto result = ComparisonMath.changeFrom(new BigDecimal("10000"), new BigDecimal("100000"));

        assertThat(result.getPercent()).isEqualByComparingTo("10.0");
        assertThat(result.getStatus()).isEqualTo(NormStatus.NORMAL);
    }

    @ParameterizedTest(name = "delta={0}, base={1} → {2}")
    @DisplayName("Должен вернуть NO_HISTORY, если база равна нулю или отрицательна")
    @CsvSource({
            "5000, 0, NO_HISTORY",
            "5000, -1000, NO_HISTORY"
    })
    void shouldReturnNoHistoryWhenBaseIsZeroOrNegative(BigDecimal delta, BigDecimal base, NormStatus expected) {
        ChangeDto result = ComparisonMath.changeFrom(delta, base);

        assertThat(result.getStatus()).isEqualTo(expected);
        assertThat(result.getPercent()).isNull();
    }

    @Test
    @DisplayName("Должен вернуть NO_HISTORY, если база равна null")
    void shouldReturnNoHistoryWhenBaseIsNull() {
        ChangeDto result = ComparisonMath.changeFrom(new BigDecimal("5000"), null);

        assertThat(result.getStatus()).isEqualTo(NormStatus.NO_HISTORY);
        assertThat(result.getPercent()).isNull();
    }

    // ─── savingsRate ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Должен рассчитать норму сбережений и округлить по HALF_UP")
    void shouldCalculateSavingsRate() {
        Integer result = ComparisonMath.savingsRate(new BigDecimal("150000.00"), new BigDecimal("100500.00"));

        // (150000-100500)/150000*100 = 33.0%
        assertThat(result).isEqualTo(33);
    }

    @Test
    @DisplayName("Должен зажать норму сбережений сверху при доходах без расходов")
    void shouldClampSavingsRateAtNinetyNine() {
        Integer result = ComparisonMath.savingsRate(new BigDecimal("100000.00"), BigDecimal.ZERO);

        assertThat(result).isEqualTo(99);
    }

    @Test
    @DisplayName("Должен зажать норму сбережений снизу при расходах, многократно превышающих доход")
    void shouldClampSavingsRateAtMinusNinetyNine() {
        Integer result = ComparisonMath.savingsRate(new BigDecimal("10000.00"), new BigDecimal("1000000.00"));

        assertThat(result).isEqualTo(-99);
    }

    @Test
    @DisplayName("Должен вернуть null при нулевом доходе")
    void shouldReturnNullSavingsRateWhenIncomeIsZero() {
        assertThat(ComparisonMath.savingsRate(BigDecimal.ZERO, new BigDecimal("500.00"))).isNull();
    }

    @Test
    @DisplayName("Должен вернуть null при отрицательном доходе")
    void shouldReturnNullSavingsRateWhenIncomeIsNegative() {
        assertThat(ComparisonMath.savingsRate(new BigDecimal("-100.00"), new BigDecimal("500.00"))).isNull();
    }

    @Test
    @DisplayName("Должен вернуть null при null доходе")
    void shouldReturnNullSavingsRateWhenIncomeIsNull() {
        assertThat(ComparisonMath.savingsRate(null, new BigDecimal("500.00"))).isNull();
    }

    // ─── money ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Должен округлить сумму до 2 знаков по HALF_UP")
    void shouldRoundMoneyToTwoDecimals() {
        assertThat(ComparisonMath.money(new BigDecimal("123.455"))).isEqualByComparingTo("123.46");
    }

    @Test
    @DisplayName("Должен вернуть null для null суммы")
    void shouldReturnNullMoneyForNullValue() {
        assertThat(ComparisonMath.money(null)).isNull();
    }
}
