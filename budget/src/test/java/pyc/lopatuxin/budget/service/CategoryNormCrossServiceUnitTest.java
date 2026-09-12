package pyc.lopatuxin.budget.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.budget.dto.common.NormComparisonDto;
import pyc.lopatuxin.budget.dto.request.CategoryPageRequestDto;
import pyc.lopatuxin.budget.dto.response.BudgetSummaryResponseDto;
import pyc.lopatuxin.budget.dto.response.CategoryPageResponseDto;
import pyc.lopatuxin.budget.dto.response.CategorySummaryDto;
import pyc.lopatuxin.budget.dto.response.OperationDto;
import pyc.lopatuxin.budget.entity.Category;
import pyc.lopatuxin.budget.entity.enums.NormStatus;
import pyc.lopatuxin.budget.mapper.ExpenseMapper;
import pyc.lopatuxin.budget.repository.CategoryRepository;
import pyc.lopatuxin.budget.repository.ExpenseRepository;
import pyc.lopatuxin.budget.repository.IncomeRepository;
import pyc.lopatuxin.budget.service.PeriodAggregateService.PeriodAggregates;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Проверяет, что карточка категории на странице бюджета ({@link BudgetSummaryService}) и страница
 * категории ({@link CategoryPageService}) считают одну и ту же личную норму по одной и той же
 * категории на одних и тех же данных репозиториев. Оба сервиса используют настоящие
 * {@link NormWindowLoader} и {@link NormCalculationService} — замоканы только репозитории, чтобы
 * поймать регресс, если расчёт окна норм или самой нормы разъедется между двумя экранами.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryNormCrossServiceUnitTest — норма категории совпадает на странице бюджета и странице категории")
class CategoryNormCrossServiceUnitTest {

    // Запрошенный месяц — заведомо прошлый относительно FIXED_TODAY, так что BudgetSummaryService и
    // CategoryPageService детерминированно попадают в одну и ту же ветку resolveDayOfMonth —
    // "прошлый месяц" — и получают dayOfMonth = 31 независимо от календаря, на котором фактически
    // запускается тест.
    private static final int REQUEST_MONTH = 3;
    private static final int REQUEST_YEAR = 2024;
    private static final LocalDate FIXED_TODAY = LocalDate.of(2026, 9, 11);
    private static final int EXPECTED_DAY_OF_MONTH = 31;

    // Запрошенный месяц совпадает с месяцем FIXED_TODAY — ветка "текущий месяц" резолвится в
    // dayOfMonth = today.getDayOfMonth(), а не в длину месяца, так что число месяца реально влияет
    // на расчёт usualByDay.
    private static final int CURRENT_MONTH = FIXED_TODAY.getMonthValue();
    private static final int CURRENT_YEAR = FIXED_TODAY.getYear();
    private static final int CURRENT_EXPECTED_DAY_OF_MONTH = FIXED_TODAY.getDayOfMonth();

    // Запрошенный месяц строго в будущем относительно FIXED_TODAY — ветка resolveDayOfMonth
    // возвращает 0, и обе реализации должны сойтись на NO_HISTORY независимо от данных окна.
    private static final int FUTURE_MONTH = 12;
    private static final int FUTURE_YEAR = 2026;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private IncomeRepository incomeRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private PeriodAggregateService periodAggregateService;

    @Mock
    private PersonalInflationCalculator personalInflationCalculator;

    @Mock
    private ExpenseMapper expenseMapper;

    private NormWindowLoader normWindowLoader;
    private NormCalculationService normCalculationService;
    private BudgetSummaryService budgetSummaryService;
    private CategoryPageService categoryPageService;

    private UUID userId;
    private UUID categoryId;
    private Category category;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        category = Category.builder()
                .id(categoryId).userId(userId).name("Продукты").emoji("🛒").budget(BigDecimal.ZERO).build();

        normWindowLoader = new NormWindowLoader(expenseRepository, incomeRepository);
        normCalculationService = new NormCalculationService();
        budgetSummaryService = new BudgetSummaryService(periodAggregateService, new CategorySummaryBuilder(),
                normCalculationService, personalInflationCalculator, expenseRepository, categoryRepository, normWindowLoader);
        categoryPageService = new CategoryPageService(categoryRepository, expenseRepository, normWindowLoader,
                normCalculationService, expenseMapper);

        // Тренды и инфляция бюджетной сводки не участвуют в проверке нормы — гасим их нейтральными
        // значениями, чтобы getSummary не упал на NPE в TrendFormatter. computeBothNorms
        // переопределяет этот стаб конкретными датами запрошенного месяца.
        lenient().when(periodAggregateService.buildPeriodAggregates(eq(userId), anyInt(), anyInt()))
                .thenReturn(new PeriodAggregates(LocalDate.of(REQUEST_YEAR, REQUEST_MONTH, 1),
                        YearMonth.of(REQUEST_YEAR, REQUEST_MONTH).atEndOfMonth(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        lenient().when(personalInflationCalculator.calculate(eq(userId), anyInt(), anyInt(), any()))
                .thenReturn(BigDecimal.ZERO);

        lenient().when(categoryRepository.findUserCategoriesByUserId(userId)).thenReturn(List.of(category));
        lenient().when(categoryRepository.findByNameAndUserId(category.getName(), userId)).thenReturn(Optional.of(category));

        // CategoryPageService-специфичные запросы, не влияющие на норму: пусть возвращают пустоту.
        lenient().when(expenseRepository.findMonthlyNonTransferExpenseByUserIdAndDateBetween(any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(expenseRepository.findByUserIdAndCategoryIdAndDateBetweenOrderByDateDesc(any(), any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(expenseMapper.toOperationDto(any())).thenReturn(OperationDto.builder().build());

        lenient().when(incomeRepository.findWindowedNonTransferIncomeStats(any(), any(), any(), anyInt()))
                .thenReturn(List.of());
    }

    /**
     * Прогоняет обе реализации на одних данных репозиториев для {@code month}/{@code year} и
     * {@code today}, и возвращает норму категории, посчитанную каждой из них. Дополнительно
     * проверяет, что обе реализации запрашивают у {@link ExpenseRepository} потраченную сумму этой
     * категории за один и тот же период — а не только то, что при подставленных тестом одинаковых
     * числах получаются одинаковые нормы.
     */
    private NormComparisonDto[] computeBothNorms(int month, int year, LocalDate today, int expectedDayOfMonth,
                                                  List<Object[]> overallExpenseWindowRows,
                                                  List<Object[]> categoryExpenseWindowRows,
                                                  BigDecimal actualAmountThisMonth) {
        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = YearMonth.of(year, month).atEndOfMonth();

        when(periodAggregateService.buildPeriodAggregates(userId, month, year))
                .thenReturn(new PeriodAggregates(monthStart, monthEnd, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        when(expenseRepository.findWindowedNonTransferExpenseStats(
                eq(userId), eq(monthStart.minusMonths(12)), eq(monthStart.minusDays(1)), eq(expectedDayOfMonth)))
                .thenReturn(overallExpenseWindowRows);
        when(expenseRepository.findWindowedNonTransferExpenseStatsByCategory(
                eq(userId), eq(monthStart.minusMonths(12)), eq(monthStart.minusDays(1)), eq(expectedDayOfMonth)))
                .thenReturn(categoryExpenseWindowRows);

        // Даты этого запроса намеренно не фиксированы matcher'ами eq(...) — совпадение периода с
        // запросом CategoryPageService проверяется ниже через ArgumentCaptor, а не предположением.
        when(expenseRepository.sumNonTransferAmountByCategoryForUserAndDateBetween(eq(userId), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{categoryId, actualAmountThisMonth}));

        // Несколько строк за разные месяцы окна, а не одна — чтобы toMonthlyAmountMap реально
        // выбирал строку запрошенного года/месяца среди других, а не просто пробрасывал
        // единственно возможное значение. distractorSameMonthPriorYear отличается от искомой строки
        // только годом (тот же номер месяца) — ключевание по одному лишь номеру месяца без года
        // выбрало бы её, а не строку запрошенного года.
        YearMonth requestedYearMonth = YearMonth.of(year, month);
        YearMonth distractorMonth1 = requestedYearMonth.minusMonths(1);
        YearMonth distractorMonth2 = requestedYearMonth.minusMonths(2);
        YearMonth distractorSameMonthPriorYear = requestedYearMonth.minusYears(1);
        when(expenseRepository.findMonthlyNonTransferExpenseByCategoryAndDateBetween(
                eq(userId), eq(categoryId), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{distractorMonth1.getYear(), distractorMonth1.getMonthValue(), new BigDecimal("77777.77")},
                        new Object[]{year, month, actualAmountThisMonth},
                        new Object[]{distractorMonth2.getYear(), distractorMonth2.getMonthValue(), new BigDecimal("55555.55")},
                        new Object[]{distractorSameMonthPriorYear.getYear(), distractorSameMonthPriorYear.getMonthValue(),
                                new BigDecimal("99999.99")}));

        BudgetSummaryResponseDto summary = budgetSummaryService.getSummary(userId, month, year, today);
        assertThat(summary.getDayOfMonth()).isEqualTo(expectedDayOfMonth);
        CategorySummaryDto categorySummary = summary.getCategories().stream()
                .filter(dto -> dto.getId().equals(categoryId)).findFirst().orElseThrow();

        CategoryPageResponseDto page = categoryPageService.getPage(userId,
                CategoryPageRequestDto.builder().categoryName(category.getName())
                        .month(month).year(year).build(),
                today);
        assertThat(page.getDayOfMonth()).isEqualTo(expectedDayOfMonth);
        assertThat(page.getSpent())
                .as("CategoryPageService должен выбрать из нескольких помесячных строк именно строку "
                        + "запрошенного года/месяца, а не строку месяца-соседа")
                .isEqualByComparingTo(actualAmountThisMonth);

        ArgumentCaptor<LocalDate> summaryStartCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> summaryEndCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(expenseRepository).sumNonTransferAmountByCategoryForUserAndDateBetween(
                eq(userId), summaryStartCaptor.capture(), summaryEndCaptor.capture());

        ArgumentCaptor<LocalDate> pageEndCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(expenseRepository).findMonthlyNonTransferExpenseByCategoryAndDateBetween(
                eq(userId), eq(categoryId), any(), pageEndCaptor.capture());

        assertThat(summaryStartCaptor.getValue())
                .as("BudgetSummaryService должен запрашивать сумму траты с начала запрошенного месяца")
                .isEqualTo(monthStart);
        assertThat(summaryEndCaptor.getValue())
                .as("BudgetSummaryService должен запрашивать сумму траты по конец запрошенного месяца")
                .isEqualTo(monthEnd);
        assertThat(pageEndCaptor.getValue())
                .as("CategoryPageService должен запрашивать историю траты по ту же дату, что и BudgetSummaryService, "
                        + "иначе deviationPercent разъедется между двумя экранами при расхождении диапазонов дат")
                .isEqualTo(summaryEndCaptor.getValue());

        return new NormComparisonDto[]{categorySummary.getNorm(), page.getNorm()};
    }

    private static void assertNormsMatch(NormComparisonDto budgetNorm, NormComparisonDto pageNorm,
                                          NormStatus expectedStatus, String expectedUsualByDay,
                                          String expectedAverageMonthly, String expectedDeviationPercent) {
        assertThat(budgetNorm.getStatus()).isEqualTo(expectedStatus);
        assertThat(pageNorm.getStatus()).isEqualTo(expectedStatus);
        assertThat(budgetNorm.getUsualByDay()).isEqualByComparingTo(expectedUsualByDay);
        assertThat(pageNorm.getUsualByDay()).isEqualByComparingTo(expectedUsualByDay);
        assertThat(budgetNorm.getAverageMonthly()).isEqualByComparingTo(expectedAverageMonthly);
        assertThat(pageNorm.getAverageMonthly()).isEqualByComparingTo(expectedAverageMonthly);
        assertThat(budgetNorm.getDeviationPercent()).isEqualByComparingTo(expectedDeviationPercent);
        assertThat(pageNorm.getDeviationPercent()).isEqualByComparingTo(expectedDeviationPercent);
    }

    @Test
    @DisplayName("Норма совпадает при обычной истории с переходом от помесячного учёта к подневному")
    void normMatchesForRegularHistoryWithGranularityTransition() {
        // Март 2023 — единственная запись за месяц (помесячный учёт, ещё не подневный).
        // Август 2023 и январь 2024 — подневный учёт. usualByDay считается только с августа,
        // averageMonthly — по всем трём месяцам.
        List<Object[]> overallRows = List.<Object[]>of(
                new Object[]{2023, 4, new BigDecimal("6000.00"), new BigDecimal("6000.00"), 1L},
                new Object[]{2023, 8, new BigDecimal("900.00"), new BigDecimal("900.00"), 3L},
                new Object[]{2024, 1, new BigDecimal("1100.00"), new BigDecimal("1100.00"), 2L}
        );
        List<Object[]> categoryRows = List.<Object[]>of(
                new Object[]{2023, 4, categoryId, new BigDecimal("6000.00"), new BigDecimal("6000.00")},
                new Object[]{2023, 8, categoryId, new BigDecimal("900.00"), new BigDecimal("900.00")},
                new Object[]{2024, 1, categoryId, new BigDecimal("1100.00"), new BigDecimal("1100.00")}
        );

        NormComparisonDto[] norms = computeBothNorms(REQUEST_MONTH, REQUEST_YEAR, FIXED_TODAY, EXPECTED_DAY_OF_MONTH,
                overallRows, categoryRows, new BigDecimal("1300.00"));

        // usualByDay = (900+1100)/2 = 1000.00; averageMonthly = (6000+900+1100)/3 = 2666.67
        // deviationPercent = (1300-1000)/1000*100 = 30.0% -> ABOVE
        assertNormsMatch(norms[0], norms[1], NormStatus.ABOVE, "1000.00", "2666.67", "30.0");
    }

    @Test
    @DisplayName("Норма совпадает, когда категория тратилась не во все месяцы окна (дозаполнение нулями)")
    void normMatchesWhenCategoryIsMissingInSomeWindowMonths() {
        // В окне 4 месяца с общими расходами, но у этой категории есть записи только в двух —
        // NormWindowLoader должен дозаполнить остальные два нулями с признаком подневности месяца.
        List<Object[]> overallRows = List.<Object[]>of(
                new Object[]{2023, 4, new BigDecimal("500.00"), new BigDecimal("500.00"), 2L},
                new Object[]{2023, 7, new BigDecimal("700.00"), new BigDecimal("700.00"), 2L},
                new Object[]{2023, 10, new BigDecimal("1500.00"), new BigDecimal("1500.00"), 2L},
                new Object[]{2024, 1, new BigDecimal("400.00"), new BigDecimal("400.00"), 2L}
        );
        List<Object[]> categoryRows = List.<Object[]>of(
                new Object[]{2023, 4, categoryId, new BigDecimal("1000.00"), new BigDecimal("1000.00")},
                new Object[]{2023, 10, categoryId, new BigDecimal("3000.00"), new BigDecimal("3000.00")}
        );

        NormComparisonDto[] norms = computeBothNorms(REQUEST_MONTH, REQUEST_YEAR, FIXED_TODAY, EXPECTED_DAY_OF_MONTH,
                overallRows, categoryRows, new BigDecimal("1200.00"));

        // usualByDay = averageMonthly = (1000+0+3000+0)/4 = 1000.00
        // deviationPercent = (1200-1000)/1000*100 = 20.0% -> ABOVE
        assertNormsMatch(norms[0], norms[1], NormStatus.ABOVE, "1000.00", "1000.00", "20.0");
    }

    @Test
    @DisplayName("Норма совпадает и равна NO_HISTORY, когда в окне нет вообще никаких расходов")
    void normMatchesAsNoHistoryWhenWindowHasNoExpensesAtAll() {
        NormComparisonDto[] norms = computeBothNorms(REQUEST_MONTH, REQUEST_YEAR, FIXED_TODAY, EXPECTED_DAY_OF_MONTH,
                List.of(), List.of(), new BigDecimal("500.00"));

        assertThat(norms[0].getStatus()).isEqualTo(NormStatus.NO_HISTORY);
        assertThat(norms[1].getStatus()).isEqualTo(NormStatus.NO_HISTORY);
        assertThat(norms[0].getUsualByDay()).isNull();
        assertThat(norms[1].getUsualByDay()).isNull();
    }

    @Test
    @DisplayName("Норма совпадает и равна NO_HISTORY, когда усреднённый usualByDay округляется до нуля")
    void normMatchesAsNoHistoryWhenUsualByDayRoundsToZero() {
        // Единственный подневный месяц с исчезающе малой суммой к дню отсечения (дозаполнения нет —
        // окно состоит из одного месяца, и он у категории есть) — usualByDay после округления до
        // копеек равен 0.00, что calculateNorm трактует как отсутствие истории.
        List<Object[]> overallRows = List.<Object[]>of(
                new Object[]{2023, 9, new BigDecimal("0.001"), new BigDecimal("0.001"), 2L}
        );
        List<Object[]> categoryRows = List.<Object[]>of(
                new Object[]{2023, 9, categoryId, new BigDecimal("0.001"), new BigDecimal("0.001")}
        );

        NormComparisonDto[] norms = computeBothNorms(REQUEST_MONTH, REQUEST_YEAR, FIXED_TODAY, EXPECTED_DAY_OF_MONTH,
                overallRows, categoryRows, new BigDecimal("500.00"));

        assertThat(norms[0].getStatus()).isEqualTo(NormStatus.NO_HISTORY);
        assertThat(norms[1].getStatus()).isEqualTo(NormStatus.NO_HISTORY);
    }

    @Test
    @DisplayName("Норма совпадает для текущего месяца, где день отсечения — число сегодняшнего дня, а не длина месяца")
    void normMatchesForCurrentMonthUsingTodaysDayOfMonthAsCutoff() {
        // Оба месяца окна — подневные, с суммой к 11-му числу заметно меньше суммы за весь месяц,
        // так что тест реально проверяет ветку "текущий месяц" (cutoffAmount != fullAmount), а не
        // просто совпадает с веткой "прошлый месяц" по случайности.
        List<Object[]> overallRows = List.<Object[]>of(
                new Object[]{2025, 10, new BigDecimal("400.00"), new BigDecimal("1000.00"), 5L},
                new Object[]{2026, 1, new BigDecimal("200.00"), new BigDecimal("800.00"), 4L}
        );
        List<Object[]> categoryRows = List.<Object[]>of(
                new Object[]{2025, 10, categoryId, new BigDecimal("400.00"), new BigDecimal("1000.00")},
                new Object[]{2026, 1, categoryId, new BigDecimal("200.00"), new BigDecimal("800.00")}
        );

        NormComparisonDto[] norms = computeBothNorms(CURRENT_MONTH, CURRENT_YEAR, FIXED_TODAY, CURRENT_EXPECTED_DAY_OF_MONTH,
                overallRows, categoryRows, new BigDecimal("360.00"));

        // usualByDay = (400+200)/2 = 300.00; averageMonthly = (1000+800)/2 = 900.00
        // deviationPercent = (360-300)/300*100 = 20.0% -> ABOVE
        assertNormsMatch(norms[0], norms[1], NormStatus.ABOVE, "300.00", "900.00", "20.0");
    }

    @Test
    @DisplayName("Норма совпадает и равна NO_HISTORY для будущего месяца независимо от данных окна")
    void normMatchesAsNoHistoryForFutureMonthRegardlessOfWindowData() {
        // Месяц строго в будущем относительно FIXED_TODAY -> resolveDayOfMonth возвращает 0, и
        // calculateNorm обязан вернуть NO_HISTORY в обеих реализациях, даже если в окне есть данные.
        List<Object[]> overallRows = List.<Object[]>of(
                new Object[]{2026, 3, new BigDecimal("500.00"), new BigDecimal("500.00"), 3L}
        );
        List<Object[]> categoryRows = List.<Object[]>of(
                new Object[]{2026, 3, categoryId, new BigDecimal("500.00"), new BigDecimal("500.00")}
        );

        NormComparisonDto[] norms = computeBothNorms(FUTURE_MONTH, FUTURE_YEAR, FIXED_TODAY, 0,
                overallRows, categoryRows, new BigDecimal("700.00"));

        assertThat(norms[0].getStatus()).isEqualTo(NormStatus.NO_HISTORY);
        assertThat(norms[1].getStatus()).isEqualTo(NormStatus.NO_HISTORY);
    }
}
