package pyc.lopatuxin.budget.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pyc.lopatuxin.budget.dto.common.NormComparisonDto;
import pyc.lopatuxin.budget.entity.enums.NormStatus;
import pyc.lopatuxin.budget.service.NormCalculationService.MonthlyAggregateRow;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NormCalculationServiceUnitTest")
class NormCalculationServiceUnitTest {

    private final NormCalculationService service = new NormCalculationService();

    @Test
    @DisplayName("Должен усреднить cutoff- и full-суммы только по переданным месяцам с данными")
    void shouldAverageOnlyOverDataMonths() {
        List<MonthlyAggregateRow> dataMonths = List.of(
                new MonthlyAggregateRow(2025, 1, new BigDecimal("60000.00"), new BigDecimal("90000.00"), true),
                new MonthlyAggregateRow(2025, 2, new BigDecimal("80000.00"), new BigDecimal("110000.00"), true),
                new MonthlyAggregateRow(2025, 3, new BigDecimal("70000.00"), new BigDecimal("100000.00"), true)
        );

        NormComparisonDto result = service.calculateNorm(dataMonths, new BigDecimal("72300.00"), 18);

        assertThat(result.getUsualByDay()).isEqualByComparingTo(new BigDecimal("70000.00"));
        assertThat(result.getAverageMonthly()).isEqualByComparingTo(new BigDecimal("100000.00"));
    }

    @Test
    @DisplayName("Категория без записей в учтённом месяце (сумма 0) снижает среднее")
    void shouldTreatMonthWithoutCategoryRecordsAsZero() {
        List<MonthlyAggregateRow> dataMonths = List.of(
                new MonthlyAggregateRow(2025, 1, new BigDecimal("300.00"), new BigDecimal("300.00"), true),
                new MonthlyAggregateRow(2025, 2, new BigDecimal("300.00"), new BigDecimal("300.00"), true),
                new MonthlyAggregateRow(2025, 3, BigDecimal.ZERO, BigDecimal.ZERO, true)
        );

        NormComparisonDto result = service.calculateNorm(dataMonths, new BigDecimal("300.00"), 15);

        assertThat(result.getUsualByDay()).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    @Test
    @DisplayName("usualByDay должен совпасть с averageMonthly, если cutoff-сумма равна полной сумме месяца (прошлый месяц, D = длина месяца)")
    void shouldMatchUsualByDayAndAverageMonthlyWhenCutoffEqualsFullAmount() {
        List<MonthlyAggregateRow> dataMonths = List.of(
                new MonthlyAggregateRow(2025, 1, new BigDecimal("50000.00"), new BigDecimal("50000.00"), true),
                new MonthlyAggregateRow(2025, 2, new BigDecimal("70000.00"), new BigDecimal("70000.00"), true)
        );

        NormComparisonDto result = service.calculateNorm(dataMonths, new BigDecimal("60000.00"), 31);

        assertThat(result.getUsualByDay()).isEqualByComparingTo(result.getAverageMonthly());
    }

    @Test
    @DisplayName("Должен округлить процент отклонения до 1 знака по HALF_UP")
    void shouldRoundDeviationPercentToOneDecimalHalfUp() {
        List<MonthlyAggregateRow> dataMonths = List.of(
                new MonthlyAggregateRow(2025, 1, new BigDecimal("1000.00"), new BigDecimal("1000.00"), true)
        );

        // (1063 - 1000) / 1000 * 100 = 6.3%
        NormComparisonDto result = service.calculateNorm(dataMonths, new BigDecimal("1063.00"), 10);

        assertThat(result.getDeviationPercent()).isEqualByComparingTo(new BigDecimal("6.3"));
    }

    @ParameterizedTest(name = "deviation={0} → {1}")
    @DisplayName("Должен определить статус по границам отклонения")
    @CsvSource({
            "10.0, NORMAL",
            "10.1, ABOVE",
            "50.0, ABOVE",
            "50.1, ABOVE_MUCH",
            "-10.0, NORMAL",
            "-10.1, BELOW"
    })
    void shouldResolveStatusByDeviationBoundaries(String deviationPercent, NormStatus expectedStatus) {
        BigDecimal usual = new BigDecimal("1000.00");
        BigDecimal deviation = new BigDecimal(deviationPercent);
        // actual = usual + usual * deviation / 100
        BigDecimal actual = usual.add(usual.multiply(deviation).divide(new BigDecimal("100")));

        List<MonthlyAggregateRow> dataMonths = List.of(
                new MonthlyAggregateRow(2025, 1, usual, usual, true)
        );

        NormComparisonDto result = service.calculateNorm(dataMonths, actual, 15);

        assertThat(result.getStatus()).isEqualTo(expectedStatus);
    }

    @Test
    @DisplayName("Должен вернуть NO_HISTORY, если нет ни одного месяца с данными")
    void shouldReturnNoHistoryWhenNoDataMonths() {
        NormComparisonDto result = service.calculateNorm(Collections.emptyList(), new BigDecimal("500.00"), 15);

        assertThat(result.getStatus()).isEqualTo(NormStatus.NO_HISTORY);
        assertThat(result.getUsualByDay()).isNull();
        assertThat(result.getAverageMonthly()).isNull();
        assertThat(result.getDeviationPercent()).isNull();
    }

    @Test
    @DisplayName("Должен вернуть NO_HISTORY, если usualByDay равен нулю")
    void shouldReturnNoHistoryWhenUsualByDayIsZero() {
        List<MonthlyAggregateRow> dataMonths = List.of(
                new MonthlyAggregateRow(2025, 1, BigDecimal.ZERO, new BigDecimal("500.00"), true),
                new MonthlyAggregateRow(2025, 2, BigDecimal.ZERO, new BigDecimal("300.00"), true)
        );

        NormComparisonDto result = service.calculateNorm(dataMonths, new BigDecimal("100.00"), 3);

        assertThat(result.getStatus()).isEqualTo(NormStatus.NO_HISTORY);
        assertThat(result.getDeviationPercent()).isNull();
    }

    @Test
    @DisplayName("Должен вернуть NO_HISTORY для будущего месяца (день сравнения = 0), даже если история есть")
    void shouldReturnNoHistoryWhenDayOfMonthIsZero() {
        List<MonthlyAggregateRow> dataMonths = List.of(
                new MonthlyAggregateRow(2025, 1, new BigDecimal("1000.00"), new BigDecimal("2000.00"), true)
        );

        NormComparisonDto result = service.calculateNorm(dataMonths, BigDecimal.ZERO, 0);

        assertThat(result.getStatus()).isEqualTo(NormStatus.NO_HISTORY);
    }

    // --- граница начала подневного учёта ---

    @Test
    @DisplayName("usualByDay должен усредняться только с месяца, где учёт стал подневным, а не по всему окну")
    void shouldAverageUsualByDayOnlyFromDailyTrackingStart() {
        List<MonthlyAggregateRow> dataMonths = List.of(
                // помесячные итоги "одной строкой" (легаси): cutoff = вся сумма месяца
                new MonthlyAggregateRow(2025, 1, new BigDecimal("100000.00"), new BigDecimal("100000.00"), false),
                new MonthlyAggregateRow(2025, 2, new BigDecimal("120000.00"), new BigDecimal("120000.00"), false),
                // с марта — подневный учёт, к 18-му накоплено меньше полной суммы месяца
                new MonthlyAggregateRow(2025, 3, new BigDecimal("40000.00"), new BigDecimal("70000.00"), true),
                new MonthlyAggregateRow(2025, 4, new BigDecimal("50000.00"), new BigDecimal("80000.00"), true)
        );

        NormComparisonDto result = service.calculateNorm(dataMonths, new BigDecimal("45000.00"), 18);

        // usualByDay = (40000 + 50000) / 2 = 45000, легаси-месяцы января и февраля не участвуют
        assertThat(result.getUsualByDay()).isEqualByComparingTo(new BigDecimal("45000.00"));
    }

    @Test
    @DisplayName("averageMonthly считается по всем месяцам окна, включая те, что раньше границы подневного учёта")
    void shouldAverageMonthlyOverAllDataMonthsRegardlessOfDailyTrackingBoundary() {
        List<MonthlyAggregateRow> dataMonths = List.of(
                new MonthlyAggregateRow(2025, 1, new BigDecimal("100000.00"), new BigDecimal("100000.00"), false),
                new MonthlyAggregateRow(2025, 2, new BigDecimal("120000.00"), new BigDecimal("120000.00"), false),
                new MonthlyAggregateRow(2025, 3, new BigDecimal("40000.00"), new BigDecimal("70000.00"), true),
                new MonthlyAggregateRow(2025, 4, new BigDecimal("50000.00"), new BigDecimal("80000.00"), true)
        );

        NormComparisonDto result = service.calculateNorm(dataMonths, new BigDecimal("45000.00"), 18);

        // averageMonthly = (100000 + 120000 + 70000 + 80000) / 4 = 92500
        assertThat(result.getAverageMonthly()).isEqualByComparingTo(new BigDecimal("92500.00"));
    }

    @Test
    @DisplayName("Должен вернуть NO_HISTORY, если ни один месяц окна не является подневным")
    void shouldReturnNoHistoryWhenNoMonthHasDailyGranularity() {
        List<MonthlyAggregateRow> dataMonths = List.of(
                new MonthlyAggregateRow(2025, 1, new BigDecimal("100000.00"), new BigDecimal("100000.00"), false),
                new MonthlyAggregateRow(2025, 2, new BigDecimal("120000.00"), new BigDecimal("120000.00"), false)
        );

        NormComparisonDto result = service.calculateNorm(dataMonths, new BigDecimal("100000.00"), 18);

        assertThat(result.getStatus()).isEqualTo(NormStatus.NO_HISTORY);
        assertThat(result.getUsualByDay()).isNull();
        assertThat(result.getAverageMonthly()).isNull();
        assertThat(result.getDeviationPercent()).isNull();
    }

    @Test
    @DisplayName("Месяц с одним днём записей после границы подневного учёта не исключается из усреднения")
    void shouldIncludeSingleDayMonthAfterDailyTrackingStart() {
        List<MonthlyAggregateRow> dataMonths = List.of(
                // легаси-месяц до границы
                new MonthlyAggregateRow(2025, 1, new BigDecimal("100000.00"), new BigDecimal("100000.00"), false),
                // граница: первый подневный месяц
                new MonthlyAggregateRow(2025, 2, new BigDecimal("30000.00"), new BigDecimal("60000.00"), true),
                // после границы, но случайно все записи легли на один день — всё равно участвует
                new MonthlyAggregateRow(2025, 3, new BigDecimal("50000.00"), new BigDecimal("50000.00"), false)
        );

        NormComparisonDto result = service.calculateNorm(dataMonths, new BigDecimal("40000.00"), 18);

        // usualByDay = (30000 + 50000) / 2 = 40000 — месяц 3 участвует, несмотря на dailyGranularity=false
        assertThat(result.getUsualByDay()).isEqualByComparingTo(new BigDecimal("40000.00"));
    }
}
