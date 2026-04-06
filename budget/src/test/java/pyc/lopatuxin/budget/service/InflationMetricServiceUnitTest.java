package pyc.lopatuxin.budget.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.budget.dto.response.MetricResponseDto;
import pyc.lopatuxin.budget.dto.response.MonthlyMetricDto;
import pyc.lopatuxin.budget.repository.ExpenseRepository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("InflationMetricService")
class InflationMetricServiceUnitTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @InjectMocks
    private InflationMetricService inflationMetricService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Должен корректно рассчитать инфляцию при наличии расходов за текущий и предыдущий год")
    void shouldCalculateInflationWhenBothYearsHaveData() {
        int year = 2026;
        // Предыдущий год: 3 месяца с данными, среднее = (100000 + 80000 + 120000) / 3 = 100000
        List<Object[]> prevYearData = List.of(
                new Object[]{1, new BigDecimal("100000.00")},
                new Object[]{5, new BigDecimal("80000.00")},
                new Object[]{9, new BigDecimal("120000.00")}
        );
        doReturn(prevYearData).when(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year - 1);

        // Текущий год: Январь=110000, Март=130000
        List<Object[]> currentYearData = List.<Object[]>of(
                new Object[]{1, new BigDecimal("110000.00")},
                new Object[]{3, new BigDecimal("130000.00")}
        );
        doReturn(currentYearData).when(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year);

        MetricResponseDto result = inflationMetricService.getInflationMetric(userId, year);

        assertThat(result).isNotNull();
        assertThat(result.getYear()).isEqualTo(year);
        assertThat(result.getMonthlyData()).hasSize(12);

        // Январь: cumulativeSum=110000, monthsWithData=1, avg=110000
        // инфляция = (110000 - 100000) / 100000 * 100 = 10.0%
        assertThat(result.getMonthlyData().get(0).getAmount()).isEqualByComparingTo(new BigDecimal("10.0"));

        // Февраль: cumulativeSum=110000, но monthsWithData по-прежнему 1 (февраль=0, не > 0)
        // avg = 110000/1 = 110000, инфляция = 10.0%
        assertThat(result.getMonthlyData().get(1).getAmount()).isEqualByComparingTo(new BigDecimal("10.0"));

        // Март: cumulativeSum=240000, monthsWithData=2, avg=120000
        // инфляция = (120000 - 100000) / 100000 * 100 = 20.0%
        assertThat(result.getMonthlyData().get(2).getAmount()).isEqualByComparingTo(new BigDecimal("20.0"));

        verify(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year - 1);
        verify(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year);
    }

    @Test
    @DisplayName("Должен вернуть нулевые показатели при отсутствии расходов за предыдущий год")
    void shouldReturnZeroValuesWhenNoPreviousYearExpenses() {
        int year = 2026;
        doReturn(Collections.emptyList())
                .when(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year - 1);

        MetricResponseDto result = inflationMetricService.getInflationMetric(userId, year);

        assertThat(result.getYear()).isEqualTo(year);
        assertThat(result.getMonthlyData()).hasSize(12);
        assertThat(result.getCurrentValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getPreviousValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getYearlyAverage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getYearlyMax()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getChangePercent()).isEqualTo("+0.0%");

        for (MonthlyMetricDto monthly : result.getMonthlyData()) {
            assertThat(monthly.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Test
    @DisplayName("Должен вернуть нулевые показатели при отсутствии расходов за текущий год")
    void shouldReturnZeroValuesWhenNoCurrentYearExpenses() {
        int year = 2026;
        List<Object[]> prevYearData = List.<Object[]>of(
                new Object[]{1, new BigDecimal("50000.00")}
        );
        doReturn(prevYearData).when(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year - 1);
        doReturn(Collections.emptyList())
                .when(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year);

        MetricResponseDto result = inflationMetricService.getInflationMetric(userId, year);

        assertThat(result.getYear()).isEqualTo(year);
        assertThat(result.getCurrentValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getYearlyAverage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getYearlyMax()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getChangePercent()).isEqualTo("+0.0%");

        for (MonthlyMetricDto monthly : result.getMonthlyData()) {
            assertThat(monthly.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Test
    @DisplayName("Должен корректно рассчитать отрицательную инфляцию (дефляцию)")
    void shouldCalculateDeflationWhenCurrentYearExpensesAreLower() {
        int year = 2026;
        // Предыдущий год: один месяц 100000, среднее = 100000
        List<Object[]> prevYearData = List.<Object[]>of(
                new Object[]{1, new BigDecimal("100000.00")}
        );
        doReturn(prevYearData).when(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year - 1);

        // Текущий год: Январь=80000 (ниже среднего предыдущего года)
        List<Object[]> currentYearData = List.<Object[]>of(
                new Object[]{1, new BigDecimal("80000.00")}
        );
        doReturn(currentYearData).when(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year);

        MetricResponseDto result = inflationMetricService.getInflationMetric(userId, year);

        // Январь: avg = 80000/1 = 80000
        // инфляция = (80000 - 100000) / 100000 * 100 = -20.0%
        assertThat(result.getMonthlyData().get(0).getAmount()).isEqualByComparingTo(new BigDecimal("-20.0"));
    }

    @Test
    @DisplayName("Должен проверить кумулятивность: инфляция учитывает только месяцы с данными для avg")
    void shouldCalculateCumulativeInflation() {
        int year = 2026;
        // Предыдущий год: среднее = (60000 + 40000) / 2 = 50000
        List<Object[]> prevYearData = List.<Object[]>of(
                new Object[]{1, new BigDecimal("60000.00")},
                new Object[]{2, new BigDecimal("40000.00")}
        );
        doReturn(prevYearData).when(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year - 1);

        // Текущий год: Январь=60000, Март=40000
        List<Object[]> currentYearData = List.<Object[]>of(
                new Object[]{1, new BigDecimal("60000.00")},
                new Object[]{3, new BigDecimal("40000.00")}
        );
        doReturn(currentYearData).when(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year);

        MetricResponseDto result = inflationMetricService.getInflationMetric(userId, year);

        // Январь: cumSum=60000, monthsWithData=1, avg=60000
        // инфляция = (60000 - 50000) / 50000 * 100 = 20.0%
        assertThat(result.getMonthlyData().get(0).getAmount()).isEqualByComparingTo(new BigDecimal("20.0"));

        // Февраль: cumSum=60000, monthsWithData=1 (февраль=0 => без изменений), avg=60000
        // инфляция = (60000 - 50000) / 50000 * 100 = 20.0%
        assertThat(result.getMonthlyData().get(1).getAmount()).isEqualByComparingTo(new BigDecimal("20.0"));

        // Март: cumSum=100000, monthsWithData=2, avg=50000
        // инфляция = (50000 - 50000) / 50000 * 100 = 0.0%
        assertThat(result.getMonthlyData().get(2).getAmount()).isEqualByComparingTo(new BigDecimal("0.0"));
    }

    @Test
    @DisplayName("Должен пропускать месяцы с нулевой кумулятивной суммой")
    void shouldSkipMonthsWithZeroCumulativeSum() {
        int year = 2026;
        // Предыдущий год: среднее = 10000
        List<Object[]> prevYearData = List.<Object[]>of(
                new Object[]{6, new BigDecimal("10000.00")}
        );
        doReturn(prevYearData).when(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year - 1);

        // Текущий год: только Март=15000
        List<Object[]> currentYearData = List.<Object[]>of(
                new Object[]{3, new BigDecimal("15000.00")}
        );
        doReturn(currentYearData).when(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year);

        MetricResponseDto result = inflationMetricService.getInflationMetric(userId, year);

        // Январь и Февраль: кумулятивная = 0, пропускаются
        assertThat(result.getMonthlyData().get(0).getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getMonthlyData().get(1).getAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        // Март: cumSum=15000, monthsWithData=1, avg=15000
        // инфляция = (15000 - 10000) / 10000 * 100 = 50.0%
        assertThat(result.getMonthlyData().get(2).getAmount()).isEqualByComparingTo(new BigDecimal("50.0"));
    }

    @Test
    @DisplayName("extractNonZeroAmounts должен оставлять отрицательные значения (дефляция)")
    void shouldKeepNegativeValuesInExtractNonZeroAmounts() {
        int year = 2026;
        // Предыдущий год: среднее = 100000
        List<Object[]> prevYearData = List.<Object[]>of(
                new Object[]{1, new BigDecimal("100000.00")}
        );
        doReturn(prevYearData).when(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year - 1);

        // Текущий год: Январь=50000 (дефляция)
        List<Object[]> currentYearData = List.<Object[]>of(
                new Object[]{1, new BigDecimal("50000.00")}
        );
        doReturn(currentYearData).when(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year);

        MetricResponseDto result = inflationMetricService.getInflationMetric(userId, year);

        // Январь: (50000-100000)/100000*100 = -50.0%, значение отрицательное но ненулевое
        assertThat(result.getMonthlyData().get(0).getAmount()).isEqualByComparingTo(new BigDecimal("-50.0"));
        // currentValue должен быть заполнен (не ноль), даже если отрицательный
        assertThat(result.getCurrentValue()).isNotEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Должен корректно вызывать findMonthlyExpenseByUserIdAndYear для обоих годов")
    void shouldCallRepositoryWithCorrectParams() {
        int year = 2025;
        doReturn(Collections.emptyList())
                .when(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year - 1);

        inflationMetricService.getInflationMetric(userId, year);

        verify(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year - 1);
    }

    @Test
    @DisplayName("getMetricName должен возвращать 'инфляции'")
    void shouldReturnCorrectMetricName() {
        assertThat(inflationMetricService.getMetricName()).isEqualTo("инфляции");
    }

    @Test
    @DisplayName("Должен заполнить месяцы после первого расхода при кумулятивной сумме > 0")
    void shouldPopulateMonthsAfterFirstExpense() {
        int year = 2026;
        // Предыдущий год: среднее = 20000
        List<Object[]> prevYearData = List.<Object[]>of(
                new Object[]{1, new BigDecimal("20000.00")}
        );
        doReturn(prevYearData).when(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year - 1);

        // Текущий год: Январь=30000
        List<Object[]> currentYearData = List.<Object[]>of(
                new Object[]{1, new BigDecimal("30000.00")}
        );
        doReturn(currentYearData).when(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year);

        MetricResponseDto result = inflationMetricService.getInflationMetric(userId, year);

        // Январь: avg=30000/1=30000, инфляция = 50.0%
        assertThat(result.getMonthlyData().get(0).getAmount()).isEqualByComparingTo(new BigDecimal("50.0"));

        // Февраль: cumSum=30000, monthsWithData=1 (февраль без расходов), avg=30000/1=30000
        // инфляция = 50.0%
        assertThat(result.getMonthlyData().get(1).getAmount()).isEqualByComparingTo(new BigDecimal("50.0"));

        // Все месяцы с 1 по 12 ненулевые (кумулятивная сумма > 0)
        for (int i = 0; i < 12; i++) {
            assertThat(result.getMonthlyData().get(i).getAmount())
                    .as("Месяц %d должен быть ненулевым", i + 1)
                    .isNotEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Test
    @DisplayName("Должен корректно рассчитать среднее предыдущего года из нескольких месяцев")
    void shouldCalculatePreviousYearAverageFromMultipleMonths() {
        int year = 2026;
        // Предыдущий год: 4 месяца, среднее = (10000+20000+30000+40000)/4 = 25000
        List<Object[]> prevYearData = List.of(
                new Object[]{2, new BigDecimal("10000.00")},
                new Object[]{4, new BigDecimal("20000.00")},
                new Object[]{8, new BigDecimal("30000.00")},
                new Object[]{11, new BigDecimal("40000.00")}
        );
        doReturn(prevYearData).when(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year - 1);

        // Текущий год: Январь=25000 (точно равно среднему)
        List<Object[]> currentYearData = List.<Object[]>of(
                new Object[]{1, new BigDecimal("25000.00")}
        );
        doReturn(currentYearData).when(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year);

        MetricResponseDto result = inflationMetricService.getInflationMetric(userId, year);

        // Январь: avg=25000, previousAvg=25000, инфляция = 0.0%
        assertThat(result.getMonthlyData().get(0).getAmount()).isEqualByComparingTo(new BigDecimal("0.0"));
    }
}
