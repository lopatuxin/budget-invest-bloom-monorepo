package pyc.lopatuxin.budget.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.budget.dto.response.CategoryInflationDto;
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
        int year = 2025;
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
        int year = 2025;
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
        int year = 2025;
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
        int year = 2025;
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
        int year = 2025;
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
        int year = 2025;
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
        int year = 2025;
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

    // -----------------------------------------------------------------------
    // buildCategoryBreakdown — тесты через getInflationMetric
    // -----------------------------------------------------------------------

    /**
     * Вспомогательный метод: строит строку Object[] в формате findCategoryStatsByUserIdAndYear.
     * Поля: [UUID categoryId, String name, String emoji, Long monthCount, BigDecimal totalAmount]
     */
    private Object[] categoryRow(UUID categoryId, String name, String emoji,
                                 long monthCount, BigDecimal totalAmount) {
        return new Object[]{categoryId, name, emoji, monthCount, totalAmount};
    }

    @Test
    @DisplayName("categoryBreakdown пустой когда нет данных прошлого года")
    void categoryBreakdown_shouldBeEmptyWhenNoPreviousYearData() {
        int year = 2025;
        doReturn(Collections.emptyList())
                .when(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year - 1);
        doReturn(Collections.emptyList())
                .when(expenseRepository).findCategoryStatsByUserIdAndYear(userId, year);
        doReturn(Collections.emptyList())
                .when(expenseRepository).findCategoryStatsByUserIdAndYear(userId, year - 1);

        MetricResponseDto result = inflationMetricService.getInflationMetric(userId, year);

        assertThat(result.getCategoryBreakdown()).isEmpty();
    }

    @Test
    @DisplayName("categoryBreakdown с одной категорией: корректный changePercent, weightPercent=100%, contribution=changePercent")
    void categoryBreakdown_shouldCalculateCorrectlyForSingleCategory() {
        int year = 2025;
        UUID catId = UUID.randomUUID();

        // Monthly data stubs (used by base getMetric)
        doReturn(List.<Object[]>of(new Object[]{1, new BigDecimal("12000.00")}))
                .when(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year - 1);
        doReturn(List.<Object[]>of(new Object[]{1, new BigDecimal("13200.00")}))
                .when(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year);

        // Category stats: current year — 1 month, 13200 total; previous — 1 month, 12000 total
        doReturn(List.<Object[]>of(categoryRow(catId, "Продукты", "🛒", 1L, new BigDecimal("13200.00"))))
                .when(expenseRepository).findCategoryStatsByUserIdAndYear(userId, year);
        doReturn(List.<Object[]>of(categoryRow(catId, "Продукты", "🛒", 1L, new BigDecimal("12000.00"))))
                .when(expenseRepository).findCategoryStatsByUserIdAndYear(userId, year - 1);

        MetricResponseDto result = inflationMetricService.getInflationMetric(userId, year);

        assertThat(result.getCategoryBreakdown()).hasSize(1);
        CategoryInflationDto dto = result.getCategoryBreakdown().get(0);

        // avgCurrent = 13200 / 1 = 13200, avgPrevious = 12000 / 1 = 12000
        // changePercent = (13200 - 12000) / 12000 * 100 = 10.0%
        assertThat(dto.getChangePercent()).isEqualByComparingTo(new BigDecimal("10.0"));

        // Единственная категория → weightPercent = 100%
        assertThat(dto.getWeightPercent()).isEqualByComparingTo(new BigDecimal("100.0"));

        // contribution = weightPercent/100 * changePercent = 1.0 * 10.0 = 10.0
        assertThat(dto.getContribution()).isEqualByComparingTo(new BigDecimal("10.0"));

        assertThat(dto.getCategoryId()).isEqualTo(catId);
        assertThat(dto.getCategoryName()).isEqualTo("Продукты");
    }

    @Test
    @DisplayName("categoryBreakdown с двумя категориями: сортировка по |contribution| убыванию")
    void categoryBreakdown_shouldBeSortedByAbsoluteContributionDescending() {
        int year = 2025;
        UUID catGrow = UUID.randomUUID();  // рост расходов
        UUID catDrop = UUID.randomUUID();  // снижение расходов

        // Monthly stubs
        doReturn(List.<Object[]>of(new Object[]{1, new BigDecimal("20000.00")}))
                .when(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year - 1);
        doReturn(List.<Object[]>of(new Object[]{1, new BigDecimal("24000.00")}))
                .when(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year);

        // current: catGrow=18000 (1 мес), catDrop=6000 (1 мес); total=24000
        doReturn(List.of(
                categoryRow(catGrow, "Кафе", "☕", 1L, new BigDecimal("18000.00")),
                categoryRow(catDrop, "Транспорт", "🚌", 1L, new BigDecimal("6000.00"))
        )).when(expenseRepository).findCategoryStatsByUserIdAndYear(userId, year);

        // previous: catGrow=10000 (1 мес), catDrop=10000 (1 мес)
        doReturn(List.of(
                categoryRow(catGrow, "Кафе", "☕", 1L, new BigDecimal("10000.00")),
                categoryRow(catDrop, "Транспорт", "🚌", 1L, new BigDecimal("10000.00"))
        )).when(expenseRepository).findCategoryStatsByUserIdAndYear(userId, year - 1);

        MetricResponseDto result = inflationMetricService.getInflationMetric(userId, year);

        assertThat(result.getCategoryBreakdown()).hasSize(2);

        // catGrow: avgCurrent=18000, avgPrevious=10000, changePercent=+80%
        // weight = 18000/24000 = 75%, contribution = 0.75 * 80 = +60.0
        //
        // catDrop: avgCurrent=6000, avgPrevious=10000, changePercent=-40%
        // weight = 6000/24000 = 25%, contribution = 0.25 * (-40) = -10.0
        //
        // |60.0| > |-10.0| => catGrow первый

        CategoryInflationDto first = result.getCategoryBreakdown().get(0);
        CategoryInflationDto second = result.getCategoryBreakdown().get(1);

        assertThat(first.getCategoryId()).isEqualTo(catGrow);
        assertThat(first.getContribution()).isEqualByComparingTo(new BigDecimal("60.0"));

        assertThat(second.getCategoryId()).isEqualTo(catDrop);
        assertThat(second.getContribution()).isEqualByComparingTo(new BigDecimal("-10.0"));

        // Проверяем что |first.contribution| >= |second.contribution|
        assertThat(first.getContribution().abs())
                .isGreaterThanOrEqualTo(second.getContribution().abs());
    }

    @Test
    @DisplayName("categoryBreakdown: категория только в текущем году исключается из breakdown")
    void categoryBreakdown_shouldExcludeCategoryPresentOnlyInCurrentYear() {
        int year = 2025;
        UUID catExisting = UUID.randomUUID();  // есть в обоих годах
        UUID catNew = UUID.randomUUID();       // только в текущем году

        // Monthly stubs
        doReturn(List.<Object[]>of(new Object[]{1, new BigDecimal("10000.00")}))
                .when(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year - 1);
        doReturn(List.<Object[]>of(new Object[]{1, new BigDecimal("15000.00")}))
                .when(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year);

        // current: обе категории присутствуют
        doReturn(List.of(
                categoryRow(catExisting, "Продукты", "🛒", 1L, new BigDecimal("10000.00")),
                categoryRow(catNew, "Новая категория", "🆕", 1L, new BigDecimal("5000.00"))
        )).when(expenseRepository).findCategoryStatsByUserIdAndYear(userId, year);

        // previous: только catExisting
        doReturn(List.<Object[]>of(
                categoryRow(catExisting, "Продукты", "🛒", 1L, new BigDecimal("10000.00"))
        )).when(expenseRepository).findCategoryStatsByUserIdAndYear(userId, year - 1);

        MetricResponseDto result = inflationMetricService.getInflationMetric(userId, year);

        // catNew не должна попасть в breakdown
        assertThat(result.getCategoryBreakdown()).hasSize(1);
        assertThat(result.getCategoryBreakdown().get(0).getCategoryId()).isEqualTo(catExisting);
    }

    @Test
    @DisplayName("Должен заполнить месяцы после первого расхода при кумулятивной сумме > 0")
    void shouldPopulateMonthsAfterFirstExpense() {
        int year = 2025;
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
        int year = 2025;
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
