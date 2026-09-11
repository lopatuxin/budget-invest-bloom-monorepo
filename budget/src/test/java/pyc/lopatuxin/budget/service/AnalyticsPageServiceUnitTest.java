package pyc.lopatuxin.budget.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.budget.dto.response.AnalyticsCategoryDto;
import pyc.lopatuxin.budget.dto.response.AnalyticsPageResponseDto;
import pyc.lopatuxin.budget.dto.response.AnalyticsSectionDto;
import pyc.lopatuxin.budget.entity.enums.NormStatus;
import pyc.lopatuxin.budget.repository.ExpenseRepository;
import pyc.lopatuxin.budget.repository.IncomeRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsPageServiceUnitTest")
class AnalyticsPageServiceUnitTest {

    @Mock
    private IncomeRepository incomeRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @InjectMocks
    private AnalyticsPageService analyticsPageService;

    private final UUID userId = UUID.randomUUID();
    // 11 сентября 2026 — не последний день месяца, сентябрь не завершён.
    private final LocalDate today = LocalDate.of(2026, 9, 11);
    private static final int YEAR = 2026;
    private static final int PREVIOUS_YEAR = 2025;

    @BeforeEach
    void setUp() {
        lenient().when(incomeRepository.findMonthlyNonTransferIncomeByUserIdAndDateBetween(eq(userId), any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(expenseRepository.findMonthlyNonTransferExpenseByUserIdAndDateBetween(eq(userId), any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(expenseRepository.findNonTransferCategoryTotalsByYearForUserAndDateBetween(eq(userId), any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(expenseRepository.findMinNonTransferDateByUserId(userId)).thenReturn(Optional.empty());
        lenient().when(incomeRepository.findMinNonTransferDateByUserId(userId)).thenReturn(Optional.empty());
    }

    private void stubMonthly(List<Object[]> incomeRows, List<Object[]> expenseRows) {
        when(incomeRepository.findMonthlyNonTransferIncomeByUserIdAndDateBetween(eq(userId), any(), any()))
                .thenReturn(incomeRows);
        when(expenseRepository.findMonthlyNonTransferExpenseByUserIdAndDateBetween(eq(userId), any(), any()))
                .thenReturn(expenseRows);
    }

    private Object[] row(int year, int month, String amount) {
        return new Object[]{year, month, new BigDecimal(amount)};
    }

    private AnalyticsPageResponseDto build() {
        return analyticsPageService.getAnalytics(userId, YEAR, today);
    }

    // ─── Incomplete month rule ──────────────────────────────────────────────────

    @Test
    @DisplayName("Незавершённый текущий месяц должен войти в total и months, но не в среднее и не в максимум")
    void shouldExcludeIncompleteMonthFromAverageButKeepItInTotalAndMonths() {
        stubMonthly(Collections.emptyList(), List.<Object[]>of(
                row(YEAR, 1, "50000.00"),
                row(YEAR, 9, "999999.00")));

        AnalyticsPageResponseDto result = build();
        AnalyticsSectionDto expenses = result.getExpenses();

        assertThat(expenses.getTotal()).isEqualByComparingTo("1049999.00");
        assertThat(expenses.getMonthsCounted()).isEqualTo(1);
        assertThat(expenses.getAverage()).isEqualByComparingTo("50000.00");
        assertThat(expenses.getMaxMonth().getMonth()).isEqualTo(1);

        var septemberPoint = expenses.getMonths().stream().filter(m -> m.getMonth() == 9).findFirst().orElseThrow();
        assertThat(septemberPoint.getCurrent()).isEqualByComparingTo("999999.00");
        assertThat(septemberPoint.getPartial()).isTrue();
        assertThat(expenses.getMonths()).hasSize(12);
    }

    @Test
    @DisplayName("Текущий месяц должен быть учтён в среднем, если сегодня — его последний день")
    void shouldCountCurrentMonthOnItsLastDay() {
        LocalDate lastDayOfSeptember = LocalDate.of(2026, 9, 30);
        stubMonthly(Collections.emptyList(), List.<Object[]>of(
                row(YEAR, 9, "30000.00")));

        AnalyticsPageResponseDto result = analyticsPageService.getAnalytics(userId, YEAR, lastDayOfSeptember);

        assertThat(result.getExpenses().getMonthsCounted()).isEqualTo(1);
        assertThat(result.getExpenses().getAverage()).isEqualByComparingTo("30000.00");
    }

    @Test
    @DisplayName("current должен быть null для месяцев после текущего и для всех месяцев будущего года")
    void shouldNullCurrentForFutureMonths() {
        AnalyticsPageResponseDto result = build();

        List<Boolean> nullFlags = result.getExpenses().getMonths().stream()
                .map(m -> m.getCurrent() == null)
                .toList();
        // Октябрь (10), ноябрь (11), декабрь (12) — после текущего месяца (сентябрь) → null.
        assertThat(nullFlags.subList(9, 12)).containsExactly(true, true, true);
        // Январь-сентябрь — не null (сентябрь тоже: partial, но не null).
        assertThat(nullFlags.subList(0, 9)).containsOnly(false);
    }

    @Test
    @DisplayName("Все месяцы будущего запрошенного года должны быть null")
    void shouldNullAllMonthsOfFutureRequestedYear() {
        AnalyticsPageResponseDto result = analyticsPageService.getAnalytics(userId, 2027, today);

        assertThat(result.getExpenses().getMonths()).allSatisfy(m -> assertThat(m.getCurrent()).isNull());
        assertThat(result.getExpenses().getMonthsCounted()).isZero();
        assertThat(result.getCategories()).isEmpty();
    }

    // ─── Averages per section ───────────────────────────────────────────────────

    @Test
    @DisplayName("Средний месяц раздела сбережений должен учитывать месяц, если есть доход ИЛИ расход")
    void shouldCountSavingsMonthWhenEitherIncomeOrExpensePresent() {
        stubMonthly(
                List.<Object[]>of(row(YEAR, 1, "100000.00")),
                List.<Object[]>of(row(YEAR, 2, "40000.00")));

        AnalyticsPageResponseDto result = build();
        AnalyticsSectionDto savings = result.getSavings();

        // Январь: saved=100000 (income>0). Февраль: saved=-40000 (expense>0). Оба учтены.
        assertThat(savings.getMonthsCounted()).isEqualTo(2);
        assertThat(savings.getAverage()).isEqualByComparingTo("30000.00");
    }

    // ─── maxMonth / minMonth ────────────────────────────────────────────────────

    @Test
    @DisplayName("При одном учтённом месяце maxMonth и minMonth должны указывать на него же")
    void shouldPointMaxAndMinToSameMonthWhenOnlyOneCounted() {
        stubMonthly(Collections.emptyList(), List.<Object[]>of(row(YEAR, 3, "15000.00")));

        AnalyticsPageResponseDto result = build();
        AnalyticsSectionDto expenses = result.getExpenses();

        assertThat(expenses.getMaxMonth().getMonth()).isEqualTo(3);
        assertThat(expenses.getMinMonth().getMonth()).isEqualTo(3);
        assertThat(expenses.getMaxMonth().getAmount()).isEqualByComparingTo("15000.00");
    }

    @Test
    @DisplayName("Без учтённых месяцев maxMonth и minMonth должны быть null")
    void shouldReturnNullExtremesWhenNoCountedMonths() {
        AnalyticsPageResponseDto result = build();

        assertThat(result.getExpenses().getMaxMonth()).isNull();
        assertThat(result.getExpenses().getMinMonth()).isNull();
        assertThat(result.getExpenses().getAverage()).isNull();
    }

    @Test
    @DisplayName("Должен выбрать месяцы с наибольшей и наименьшей суммой среди учтённых")
    void shouldPickHighestAndLowestAmongCountedMonths() {
        stubMonthly(Collections.emptyList(), List.<Object[]>of(
                row(YEAR, 1, "50000.00"),
                row(YEAR, 5, "112300.00"),
                row(YEAR, 8, "80100.00")));

        AnalyticsPageResponseDto result = build();
        AnalyticsSectionDto expenses = result.getExpenses();

        assertThat(expenses.getMaxMonth().getMonth()).isEqualTo(5);
        assertThat(expenses.getMinMonth().getMonth()).isEqualTo(1);
    }

    // ─── Change thresholds ──────────────────────────────────────────────────────

    @ParameterizedTest(name = "average={0}, previous={1} → {2}")
    @DisplayName("Изменение среднего месяца должно определяться личными порогами")
    @CsvSource({
            "110000.00, 100000.00, NORMAL",
            "110100.00, 100000.00, ABOVE",
            "150100.00, 100000.00, ABOVE_MUCH",
            "89900.00,  100000.00, BELOW"
    })
    void shouldResolveChangeStatusByThresholds(BigDecimal average, BigDecimal previous, NormStatus expected) {
        stubMonthly(Collections.emptyList(), List.<Object[]>of(
                row(YEAR, 1, average.toPlainString()),
                row(PREVIOUS_YEAR, 1, previous.toPlainString())));

        AnalyticsPageResponseDto result = build();

        assertThat(result.getExpenses().getChange().getStatus()).isEqualTo(expected);
    }

    @Test
    @DisplayName("Должен вернуть NO_HISTORY, если за прошлый год нет данных")
    void shouldReturnNoHistoryWhenNoPreviousYearData() {
        stubMonthly(Collections.emptyList(), List.<Object[]>of(row(YEAR, 1, "50000.00")));

        AnalyticsPageResponseDto result = build();

        assertThat(result.getExpenses().getChange().getStatus()).isEqualTo(NormStatus.NO_HISTORY);
        assertThat(result.getExpenses().getChange().getPercent()).isNull();
    }

    @Test
    @DisplayName("Должен вернуть NO_HISTORY для сбережений при отрицательном среднем прошлого года")
    void shouldReturnNoHistoryForSavingsWhenPreviousAverageIsNegative() {
        stubMonthly(
                List.<Object[]>of(row(PREVIOUS_YEAR, 1, "50000.00")),
                List.<Object[]>of(row(PREVIOUS_YEAR, 1, "80000.00"), row(YEAR, 1, "10000.00")));

        AnalyticsPageResponseDto result = build();

        // P: saved = 50000-80000 = -30000 (<=0) → NO_HISTORY.
        assertThat(result.getSavings().getChange().getStatus()).isEqualTo(NormStatus.NO_HISTORY);
        assertThat(result.getSavings().getPreviousAverage()).isEqualByComparingTo("-30000.00");
    }

    // ─── Savings rate ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Норма сбережений года должна округляться и зажиматься в [-99, 99]")
    void shouldRoundAndClampSavingsRatePercent() {
        stubMonthly(
                List.<Object[]>of(row(YEAR, 1, "10000.00")),
                List.<Object[]>of(row(YEAR, 1, "500.00")));

        AnalyticsPageResponseDto result = build();

        assertThat(result.getSavingsRatePercent()).isEqualTo(95);
    }

    @Test
    @DisplayName("Норма сбережений должна быть null при нулевых доходах за учтённые месяцы")
    void shouldReturnNullSavingsRateWhenNoIncome() {
        stubMonthly(Collections.emptyList(), List.<Object[]>of(row(YEAR, 1, "5000.00")));

        AnalyticsPageResponseDto result = build();

        assertThat(result.getSavingsRatePercent()).isNull();
    }

    // ─── earliestYear / previousYearHasData ─────────────────────────────────────

    @Test
    @DisplayName("earliestYear должен быть минимумом из дат обоих репозиториев")
    void shouldResolveEarliestYearAsMinimumAcrossBothRepositories() {
        when(expenseRepository.findMinNonTransferDateByUserId(userId))
                .thenReturn(Optional.of(LocalDate.of(2022, 6, 1)));
        when(incomeRepository.findMinNonTransferDateByUserId(userId))
                .thenReturn(Optional.of(LocalDate.of(2021, 3, 1)));

        AnalyticsPageResponseDto result = build();

        assertThat(result.getEarliestYear()).isEqualTo(2021);
    }

    @Test
    @DisplayName("earliestYear должен быть null у пользователя без записей")
    void shouldReturnNullEarliestYearWhenNoRecords() {
        AnalyticsPageResponseDto result = build();

        assertThat(result.getEarliestYear()).isNull();
    }

    @Test
    @DisplayName("previousYearHasData должен быть true при наличии хотя бы одного учтённого месяца P")
    void shouldReturnTruePreviousYearHasDataWhenPreviousYearHasAnyCountedMonth() {
        stubMonthly(Collections.emptyList(), List.<Object[]>of(row(PREVIOUS_YEAR, 6, "1000.00")));

        AnalyticsPageResponseDto result = build();

        assertThat(result.getPreviousYearHasData()).isTrue();
    }

    @Test
    @DisplayName("previousYearHasData должен быть false без записей за P")
    void shouldReturnFalsePreviousYearHasDataWhenEmpty() {
        AnalyticsPageResponseDto result = build();

        assertThat(result.getPreviousYearHasData()).isFalse();
    }

    // ─── personalInflationPercent ───────────────────────────────────────────────

    @Test
    @DisplayName("personalInflationPercent должен совпадать с expenses.change.percent")
    void shouldEqualExpensesChangePercent() {
        stubMonthly(Collections.emptyList(), List.<Object[]>of(
                row(YEAR, 1, "110000.00"),
                row(PREVIOUS_YEAR, 1, "100000.00")));

        AnalyticsPageResponseDto result = build();

        assertThat(result.getPersonalInflationPercent())
                .isEqualByComparingTo(result.getExpenses().getChange().getPercent());
    }

    // ─── Categories ─────────────────────────────────────────────────────────────

    private final UUID housingId = UUID.randomUUID();
    private final UUID newCategoryId = UUID.randomUUID();
    private final UUID vanishedCategoryId = UUID.randomUUID();

    @Test
    @DisplayName("categories должен быть пуст, если в году нет ни одного учтённого месяца расходов")
    void shouldReturnEmptyCategoriesWhenNoCountedExpenseMonth() {
        AnalyticsPageResponseDto result = build();

        assertThat(result.getCategories()).isEmpty();
    }

    @Test
    @DisplayName("Новая категория (которой не было в P) должна получить NO_HISTORY и положительный вклад")
    void shouldMarkNewCategoryAsNoHistoryWithPositiveContribution() {
        stubMonthly(Collections.emptyList(), List.<Object[]>of(row(YEAR, 1, "10000.00"), row(PREVIOUS_YEAR, 1, "8000.00")));
        when(expenseRepository.findNonTransferCategoryTotalsByYearForUserAndDateBetween(eq(userId), any(), any()))
                .thenReturn(List.of(
                        new Object[]{YEAR, housingId, "Жильё", "🏠", new BigDecimal("6000.00")},
                        new Object[]{YEAR, newCategoryId, "Хобби", "🎨", new BigDecimal("4000.00")},
                        new Object[]{PREVIOUS_YEAR, housingId, "Жильё", "🏠", new BigDecimal("8000.00")}));

        AnalyticsPageResponseDto result = build();

        AnalyticsCategoryDto hobby = findCategory(result, newCategoryId);
        assertThat(hobby.getAveragePrevious()).isEqualByComparingTo("0.00");
        assertThat(hobby.getChange().getStatus()).isEqualTo(NormStatus.NO_HISTORY);
        assertThat(hobby.getContributionPoints()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Исчезнувшая категория (только в P) должна получить averageCurrent=0, -100% и отрицательный вклад")
    void shouldMarkVanishedCategoryWithZeroCurrentAndNegativeContribution() {
        stubMonthly(Collections.emptyList(), List.<Object[]>of(row(YEAR, 1, "10000.00"), row(PREVIOUS_YEAR, 1, "13000.00")));
        when(expenseRepository.findNonTransferCategoryTotalsByYearForUserAndDateBetween(eq(userId), any(), any()))
                .thenReturn(List.of(
                        new Object[]{YEAR, housingId, "Жильё", "🏠", new BigDecimal("10000.00")},
                        new Object[]{PREVIOUS_YEAR, housingId, "Жильё", "🏠", new BigDecimal("10000.00")},
                        new Object[]{PREVIOUS_YEAR, vanishedCategoryId, "Такси", "🚕", new BigDecimal("3000.00")}));

        AnalyticsPageResponseDto result = build();

        AnalyticsCategoryDto vanished = findCategory(result, vanishedCategoryId);
        assertThat(vanished.getAverageCurrent()).isEqualByComparingTo("0.00");
        assertThat(vanished.getChange().getPercent()).isEqualByComparingTo("-100.0");
        assertThat(vanished.getChange().getStatus()).isEqualTo(NormStatus.BELOW);
        assertThat(vanished.getContributionPoints()).isLessThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Сумма вкладов категорий должна сходиться с личной инфляцией с точностью 0.1 п.п.")
    void shouldReconcileContributionPointsSumWithPersonalInflation() {
        stubMonthly(Collections.emptyList(), List.<Object[]>of(
                row(YEAR, 1, "24500.00"), row(YEAR, 2, "10000.00"),
                row(PREVIOUS_YEAR, 1, "22000.00"), row(PREVIOUS_YEAR, 2, "15000.00")));
        when(expenseRepository.findNonTransferCategoryTotalsByYearForUserAndDateBetween(eq(userId), any(), any()))
                .thenReturn(List.of(
                        new Object[]{YEAR, housingId, "Жильё", "🏠", new BigDecimal("24500.00")},
                        new Object[]{YEAR, newCategoryId, "Хобби", "🎨", new BigDecimal("10000.00")},
                        new Object[]{PREVIOUS_YEAR, housingId, "Жильё", "🏠", new BigDecimal("22000.00")},
                        new Object[]{PREVIOUS_YEAR, vanishedCategoryId, "Такси", "🚕", new BigDecimal("15000.00")}));

        AnalyticsPageResponseDto result = build();

        BigDecimal contributionSum = result.getCategories().stream()
                .map(AnalyticsCategoryDto::getContributionPoints)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal shareSum = result.getCategories().stream()
                .map(AnalyticsCategoryDto::getSharePercent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(contributionSum).isCloseTo(result.getPersonalInflationPercent(),
                org.assertj.core.data.Offset.offset(new BigDecimal("0.2")));
        assertThat(shareSum).isCloseTo(new BigDecimal("100"), org.assertj.core.data.Offset.offset(new BigDecimal("0.2")));
    }

    @Test
    @DisplayName("Порядок категорий должен быть по убыванию |вклада|, при равенстве — по убыванию averageCurrent")
    void shouldSortCategoriesByAbsoluteContributionDescending() {
        stubMonthly(Collections.emptyList(), List.<Object[]>of(
                row(YEAR, 1, "30000.00"), row(PREVIOUS_YEAR, 1, "20000.00")));
        UUID bigChangeId = UUID.randomUUID();
        UUID smallChangeId = UUID.randomUUID();
        when(expenseRepository.findNonTransferCategoryTotalsByYearForUserAndDateBetween(eq(userId), any(), any()))
                .thenReturn(List.of(
                        new Object[]{YEAR, smallChangeId, "Мелочи", "🧦", new BigDecimal("10000.00")},
                        new Object[]{YEAR, bigChangeId, "Крупное", "💥", new BigDecimal("20000.00")},
                        new Object[]{PREVIOUS_YEAR, smallChangeId, "Мелочи", "🧦", new BigDecimal("9500.00")},
                        new Object[]{PREVIOUS_YEAR, bigChangeId, "Крупное", "💥", new BigDecimal("10500.00")}));

        AnalyticsPageResponseDto result = build();

        assertThat(result.getCategories()).extracting(AnalyticsCategoryDto::getCategoryId)
                .containsExactly(bigChangeId, smallChangeId);
    }

    @Test
    @DisplayName("Без данных за P категории должны сортироваться по убыванию averageCurrent, а вклад — быть null")
    void shouldSortByAverageCurrentAndNullContributionsWhenNoPreviousYearData() {
        stubMonthly(Collections.emptyList(), List.<Object[]>of(row(YEAR, 1, "30000.00")));
        UUID smallId = UUID.randomUUID();
        UUID bigId = UUID.randomUUID();
        when(expenseRepository.findNonTransferCategoryTotalsByYearForUserAndDateBetween(eq(userId), any(), any()))
                .thenReturn(List.of(
                        new Object[]{YEAR, smallId, "Мелочи", "🧦", new BigDecimal("5000.00")},
                        new Object[]{YEAR, bigId, "Крупное", "💥", new BigDecimal("25000.00")}));

        AnalyticsPageResponseDto result = build();

        assertThat(result.getCategories()).allSatisfy(c -> assertThat(c.getContributionPoints()).isNull());
        assertThat(result.getCategories()).extracting(AnalyticsCategoryDto::getCategoryId)
                .containsExactly(bigId, smallId);
    }

    private AnalyticsCategoryDto findCategory(AnalyticsPageResponseDto result, UUID categoryId) {
        return result.getCategories().stream()
                .filter(c -> c.getCategoryId().equals(categoryId))
                .findFirst()
                .orElseThrow();
    }
}
