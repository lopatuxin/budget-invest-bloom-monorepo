package pyc.lopatuxin.budget.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.budget.dto.response.BudgetSummaryResponseDto;
import pyc.lopatuxin.budget.dto.response.CategorySummaryDto;
import pyc.lopatuxin.budget.entity.Category;
import pyc.lopatuxin.budget.repository.CategoryRepository;
import pyc.lopatuxin.budget.repository.ExpenseRepository;
import pyc.lopatuxin.budget.service.PeriodAggregateService.PeriodAggregates;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BudgetSummaryServiceUnitTest")
class BudgetSummaryServiceUnitTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private PeriodAggregateService periodAggregateService;

    @Mock
    private CategorySummaryBuilder categorySummaryBuilder;

    @InjectMocks
    private BudgetSummaryService budgetSummaryService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        // Default lenient stubs for inflation calculation — return empty lists so inflation = 0
        lenient().when(expenseRepository.findMonthlyExpenseByUserIdAndYear(eq(userId), anyInt()))
                .thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("Должен вернуть корректную сводку за месяц с данными")
    void shouldReturnCorrectSummaryWhenDataExists() {
        int month = 3;
        int year = 2024;
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        UUID categoryId = UUID.randomUUID();
        Category category = Category.builder()
                .id(categoryId)
                .userId(userId)
                .name("Продукты")
                .emoji("🛒")
                .budget(new BigDecimal("30000.00"))
                .build();

        PeriodAggregates current = new PeriodAggregates(start, end,
                new BigDecimal("150000.00"), new BigDecimal("89500.00"), new BigDecimal("60500.00"));
        PeriodAggregates prev = new PeriodAggregates(
                LocalDate.of(year, 2, 1), LocalDate.of(year, 2, 29),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        CategorySummaryDto catDto = CategorySummaryDto.builder()
                .id(categoryId).name("Продукты").emoji("🛒")
                .amount(new BigDecimal("25000.00"))
                .budget(new BigDecimal("30000.00"))
                .percentUsed(new BigDecimal("83.33"))
                .build();

        when(periodAggregateService.buildPeriodAggregates(userId, month, year)).thenReturn(current);
        when(periodAggregateService.buildPeriodAggregates(userId, 2, year)).thenReturn(prev);
        when(categoryRepository.findByUserId(userId)).thenReturn(List.of(category));
        when(expenseRepository.sumAmountByCategoryForUserAndDateBetween(userId, start, end))
                .thenReturn(List.<Object[]>of(new Object[]{categoryId, new BigDecimal("25000.00")}));
        when(categorySummaryBuilder.buildCategorySummary(eq(category), any())).thenReturn(catDto);

        BudgetSummaryResponseDto result = budgetSummaryService.getSummary(userId, month, year);

        assertThat(result).isNotNull();
        assertThat(result.getIncome()).isEqualByComparingTo(new BigDecimal("150000.00"));
        assertThat(result.getExpenses()).isEqualByComparingTo(new BigDecimal("89500.00"));
        assertThat(result.getCategories()).hasSize(1);
        assertThat(result.getCategories().getFirst().getPercentUsed()).isEqualByComparingTo(new BigDecimal("83.33"));

        verify(periodAggregateService).buildPeriodAggregates(userId, month, year);
        verify(categoryRepository).findByUserId(userId);
        verify(expenseRepository).sumAmountByCategoryForUserAndDateBetween(userId, start, end);
    }

    @Test
    @DisplayName("Должен вернуть нулевые показатели при отсутствии данных")
    void shouldReturnZeroValuesWhenNoDataExists() {
        int month = 5;
        int year = 2024;
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        PeriodAggregates current = new PeriodAggregates(start, end,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        PeriodAggregates prev = new PeriodAggregates(
                LocalDate.of(year, 4, 1), LocalDate.of(year, 4, 30),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        when(periodAggregateService.buildPeriodAggregates(userId, month, year)).thenReturn(current);
        when(periodAggregateService.buildPeriodAggregates(userId, 4, year)).thenReturn(prev);
        when(categoryRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(expenseRepository.sumAmountByCategoryForUserAndDateBetween(userId, start, end))
                .thenReturn(Collections.emptyList());

        BudgetSummaryResponseDto result = budgetSummaryService.getSummary(userId, month, year);

        assertThat(result.getIncome()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getExpenses()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getPersonalInflation()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTrends().getIncome()).isEqualTo("+0.0%");
        assertThat(result.getTrends().getExpenses()).isEqualTo("+0.0%");
        assertThat(result.getCategories()).isEmpty();
    }

    @Test
    @DisplayName("Должен корректно рассчитать баланс как разность доходов и расходов")
    void shouldCalculateBalanceAsIncomeMinusExpenses() {
        int month = 6;
        int year = 2024;
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        PeriodAggregates current = new PeriodAggregates(start, end,
                new BigDecimal("150000"), new BigDecimal("89500"), new BigDecimal("60500"));
        PeriodAggregates prev = new PeriodAggregates(
                LocalDate.of(year, 5, 1), LocalDate.of(year, 5, 31),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        when(periodAggregateService.buildPeriodAggregates(userId, month, year)).thenReturn(current);
        when(periodAggregateService.buildPeriodAggregates(userId, 5, year)).thenReturn(prev);
        when(categoryRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(expenseRepository.sumAmountByCategoryForUserAndDateBetween(userId, start, end))
                .thenReturn(Collections.emptyList());

        BudgetSummaryResponseDto result = budgetSummaryService.getSummary(userId, month, year);

        assertThat(result.getBalance()).isEqualByComparingTo(new BigDecimal("60500"));
    }

    @Test
    @DisplayName("Должен корректно рассчитать personalInflation на основе средних месячных расходов")
    void shouldCalculatePersonalInflationCorrectly() {
        // currentYearTotal = 99000 (3 месяца) → avg = 33000
        // previousYearTotal = 360000 (12 месяцев) → avg = 30000
        // inflation = (33000 - 30000) / 30000 * 100 = 10.0%
        int month = 3;
        int year = 2024;
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        PeriodAggregates current = new PeriodAggregates(start, end,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        PeriodAggregates prev = new PeriodAggregates(
                LocalDate.of(year, 2, 1), LocalDate.of(year, 2, 29),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        when(periodAggregateService.buildPeriodAggregates(userId, month, year)).thenReturn(current);
        when(periodAggregateService.buildPeriodAggregates(userId, 2, year)).thenReturn(prev);
        // Override default lenient stub for the specific years needed
        when(expenseRepository.findMonthlyExpenseByUserIdAndYear(userId, year))
                .thenReturn(List.of(
                        new Object[]{1, new BigDecimal("33000")},
                        new Object[]{2, new BigDecimal("33000")},
                        new Object[]{3, new BigDecimal("33000")}
                ));
        when(expenseRepository.findMonthlyExpenseByUserIdAndYear(userId, year - 1))
                .thenReturn(List.of(
                        new Object[]{1, new BigDecimal("30000")},
                        new Object[]{2, new BigDecimal("30000")},
                        new Object[]{3, new BigDecimal("30000")},
                        new Object[]{4, new BigDecimal("30000")},
                        new Object[]{5, new BigDecimal("30000")},
                        new Object[]{6, new BigDecimal("30000")},
                        new Object[]{7, new BigDecimal("30000")},
                        new Object[]{8, new BigDecimal("30000")},
                        new Object[]{9, new BigDecimal("30000")},
                        new Object[]{10, new BigDecimal("30000")},
                        new Object[]{11, new BigDecimal("30000")},
                        new Object[]{12, new BigDecimal("30000")}
                ));
        when(categoryRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(expenseRepository.sumAmountByCategoryForUserAndDateBetween(userId, start, end))
                .thenReturn(Collections.emptyList());

        BudgetSummaryResponseDto result = budgetSummaryService.getSummary(userId, month, year);

        assertThat(result.getPersonalInflation()).isEqualByComparingTo(new BigDecimal("10.0"));
    }

    @Test
    @DisplayName("Должен корректно форматировать положительный тренд доходов (+8.2%)")
    void shouldFormatPositiveIncomeTrendCorrectly() {
        // currentIncome=108200, prevIncome=100000 → (108200-100000)/100000*100 = +8.2%
        int month = 4;
        int year = 2024;
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        PeriodAggregates current = new PeriodAggregates(start, end,
                new BigDecimal("108200"), BigDecimal.ZERO, new BigDecimal("108200"));
        PeriodAggregates prev = new PeriodAggregates(
                LocalDate.of(year, 3, 1), LocalDate.of(year, 3, 31),
                new BigDecimal("100000"), BigDecimal.ZERO, new BigDecimal("100000"));

        when(periodAggregateService.buildPeriodAggregates(userId, month, year)).thenReturn(current);
        when(periodAggregateService.buildPeriodAggregates(userId, 3, year)).thenReturn(prev);
        when(categoryRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(expenseRepository.sumAmountByCategoryForUserAndDateBetween(userId, start, end))
                .thenReturn(Collections.emptyList());

        BudgetSummaryResponseDto result = budgetSummaryService.getSummary(userId, month, year);

        assertThat(result.getTrends().getIncome()).isEqualTo("+8.2%");
    }

    @Test
    @DisplayName("Должен корректно форматировать отрицательный тренд расходов (-3.1%)")
    void shouldFormatNegativeExpensesTrendCorrectly() {
        // currentExpenses=87210, prevExpenses=90000 → (87210-90000)/90000*100 = -3.1%
        int month = 4;
        int year = 2024;
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        PeriodAggregates current = new PeriodAggregates(start, end,
                BigDecimal.ZERO, new BigDecimal("87210"), new BigDecimal("-87210"));
        PeriodAggregates prev = new PeriodAggregates(
                LocalDate.of(year, 3, 1), LocalDate.of(year, 3, 31),
                BigDecimal.ZERO, new BigDecimal("90000"), new BigDecimal("-90000"));

        when(periodAggregateService.buildPeriodAggregates(userId, month, year)).thenReturn(current);
        when(periodAggregateService.buildPeriodAggregates(userId, 3, year)).thenReturn(prev);
        when(categoryRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(expenseRepository.sumAmountByCategoryForUserAndDateBetween(userId, start, end))
                .thenReturn(Collections.emptyList());

        BudgetSummaryResponseDto result = budgetSummaryService.getSummary(userId, month, year);

        assertThat(result.getTrends().getExpenses()).isEqualTo("-3.1%");
    }

    @Test
    @DisplayName("Должен вернуть +0.0% для тренда при нулевом предыдущем значении без ArithmeticException")
    void shouldReturnZeroTrendWhenPreviousValueIsZero() {
        int month = 7;
        int year = 2024;
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        PeriodAggregates current = new PeriodAggregates(start, end,
                new BigDecimal("50000"), new BigDecimal("30000"), new BigDecimal("20000"));
        PeriodAggregates prev = new PeriodAggregates(
                LocalDate.of(year, 6, 1), LocalDate.of(year, 6, 30),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        when(periodAggregateService.buildPeriodAggregates(userId, month, year)).thenReturn(current);
        when(periodAggregateService.buildPeriodAggregates(userId, 6, year)).thenReturn(prev);
        when(categoryRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(expenseRepository.sumAmountByCategoryForUserAndDateBetween(userId, start, end))
                .thenReturn(Collections.emptyList());

        BudgetSummaryResponseDto result = budgetSummaryService.getSummary(userId, month, year);

        assertThat(result.getTrends().getIncome()).isEqualTo("+0.0%");
        assertThat(result.getTrends().getExpenses()).isEqualTo("+0.0%");
    }

    @Test
    @DisplayName("Должен корректно рассчитать percentUsed категории (budget=30000, amount=25000 → 83.33%)")
    void shouldCalculatePercentUsedCorrectly() {
        int month = 3;
        int year = 2024;
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        UUID categoryId = UUID.randomUUID();

        Category category = Category.builder()
                .id(categoryId)
                .userId(userId)
                .name("Продукты")
                .budget(new BigDecimal("30000"))
                .build();

        CategorySummaryDto catDto = CategorySummaryDto.builder()
                .id(categoryId).name("Продукты")
                .amount(new BigDecimal("25000"))
                .budget(new BigDecimal("30000"))
                .percentUsed(new BigDecimal("83.33"))
                .build();

        PeriodAggregates current = new PeriodAggregates(start, end,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        PeriodAggregates prev = new PeriodAggregates(
                LocalDate.of(year, 2, 1), LocalDate.of(year, 2, 29),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        when(periodAggregateService.buildPeriodAggregates(userId, month, year)).thenReturn(current);
        when(periodAggregateService.buildPeriodAggregates(userId, 2, year)).thenReturn(prev);
        when(categoryRepository.findByUserId(userId)).thenReturn(List.of(category));
        when(expenseRepository.sumAmountByCategoryForUserAndDateBetween(userId, start, end))
                .thenReturn(List.<Object[]>of(new Object[]{categoryId, new BigDecimal("25000")}));
        when(categorySummaryBuilder.buildCategorySummary(eq(category), any())).thenReturn(catDto);

        BudgetSummaryResponseDto result = budgetSummaryService.getSummary(userId, month, year);

        CategorySummaryDto resultCat = result.getCategories().getFirst();
        assertThat(resultCat.getPercentUsed()).isEqualByComparingTo(new BigDecimal("83.33"));
    }

    @Test
    @DisplayName("Должен ограничить percentUsed значением 100% при перерасходе (budget=10000, amount=15000)")
    void shouldCapPercentUsedAt100WhenOverspent() {
        int month = 3;
        int year = 2024;
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        UUID categoryId = UUID.randomUUID();

        Category category = Category.builder()
                .id(categoryId)
                .userId(userId)
                .name("Развлечения")
                .budget(new BigDecimal("10000"))
                .build();

        CategorySummaryDto catDto = CategorySummaryDto.builder()
                .id(categoryId).name("Развлечения")
                .amount(new BigDecimal("15000"))
                .budget(new BigDecimal("10000"))
                .percentUsed(new BigDecimal("100.00"))
                .build();

        PeriodAggregates current = new PeriodAggregates(start, end,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        PeriodAggregates prev = new PeriodAggregates(
                LocalDate.of(year, 2, 1), LocalDate.of(year, 2, 29),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        when(periodAggregateService.buildPeriodAggregates(userId, month, year)).thenReturn(current);
        when(periodAggregateService.buildPeriodAggregates(userId, 2, year)).thenReturn(prev);
        when(categoryRepository.findByUserId(userId)).thenReturn(List.of(category));
        when(expenseRepository.sumAmountByCategoryForUserAndDateBetween(userId, start, end))
                .thenReturn(List.<Object[]>of(new Object[]{categoryId, new BigDecimal("15000")}));
        when(categorySummaryBuilder.buildCategorySummary(eq(category), any())).thenReturn(catDto);

        BudgetSummaryResponseDto result = budgetSummaryService.getSummary(userId, month, year);

        assertThat(result.getCategories().getFirst().getPercentUsed())
                .isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Должен вернуть percentUsed = 0 при нулевом бюджете категории")
    void shouldReturnZeroPercentUsedWhenBudgetIsZero() {
        int month = 3;
        int year = 2024;
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        UUID categoryId = UUID.randomUUID();

        Category category = Category.builder()
                .id(categoryId)
                .userId(userId)
                .name("Прочее")
                .budget(BigDecimal.ZERO)
                .build();

        CategorySummaryDto catDto = CategorySummaryDto.builder()
                .id(categoryId).name("Прочее")
                .amount(new BigDecimal("5000"))
                .budget(BigDecimal.ZERO)
                .percentUsed(BigDecimal.ZERO)
                .build();

        PeriodAggregates current = new PeriodAggregates(start, end,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        PeriodAggregates prev = new PeriodAggregates(
                LocalDate.of(year, 2, 1), LocalDate.of(year, 2, 29),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        when(periodAggregateService.buildPeriodAggregates(userId, month, year)).thenReturn(current);
        when(periodAggregateService.buildPeriodAggregates(userId, 2, year)).thenReturn(prev);
        when(categoryRepository.findByUserId(userId)).thenReturn(List.of(category));
        when(expenseRepository.sumAmountByCategoryForUserAndDateBetween(userId, start, end))
                .thenReturn(List.<Object[]>of(new Object[]{categoryId, new BigDecimal("5000")}));
        when(categorySummaryBuilder.buildCategorySummary(eq(category), any())).thenReturn(catDto);

        BudgetSummaryResponseDto result = budgetSummaryService.getSummary(userId, month, year);

        assertThat(result.getCategories().getFirst().getPercentUsed())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Должен корректно рассчитать тренд при переходе через год (январь → декабрь прошлого года)")
    void shouldCalculateTrendCorrectlyWhenCrossingYearBoundary() {
        // trend income = (100000 - 80000) / 80000 * 100 = +25.0%
        int month = 1;
        int year = 2024;
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        PeriodAggregates current = new PeriodAggregates(start, end,
                new BigDecimal("100000"), BigDecimal.ZERO, new BigDecimal("100000"));
        PeriodAggregates prev = new PeriodAggregates(
                LocalDate.of(2023, 12, 1), LocalDate.of(2023, 12, 31),
                new BigDecimal("80000"), BigDecimal.ZERO, new BigDecimal("80000"));

        when(periodAggregateService.buildPeriodAggregates(userId, 1, 2024)).thenReturn(current);
        when(periodAggregateService.buildPeriodAggregates(userId, 12, 2023)).thenReturn(prev);
        when(categoryRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(expenseRepository.sumAmountByCategoryForUserAndDateBetween(userId, start, end))
                .thenReturn(Collections.emptyList());

        BudgetSummaryResponseDto result = budgetSummaryService.getSummary(userId, month, year);

        assertThat(result.getTrends().getIncome()).isEqualTo("+25.0%");
    }

    @Test
    @DisplayName("Должен вернуть все категории без ограничения по количеству")
    void shouldReturnAllCategoriesWithoutLimit() {
        int month = 3;
        int year = 2024;
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        UUID id4 = UUID.randomUUID();
        UUID id5 = UUID.randomUUID();
        UUID id6 = UUID.randomUUID();

        List<Category> categories = List.of(
                Category.builder().id(id1).userId(userId).name("Продукты").budget(BigDecimal.ZERO).build(),
                Category.builder().id(id2).userId(userId).name("Транспорт").budget(BigDecimal.ZERO).build(),
                Category.builder().id(id3).userId(userId).name("Кафе").budget(BigDecimal.ZERO).build(),
                Category.builder().id(id4).userId(userId).name("Здоровье").budget(BigDecimal.ZERO).build(),
                Category.builder().id(id5).userId(userId).name("Одежда").budget(BigDecimal.ZERO).build(),
                Category.builder().id(id6).userId(userId).name("Развлечения").budget(BigDecimal.ZERO).build()
        );

        PeriodAggregates current = new PeriodAggregates(start, end,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        PeriodAggregates prev = new PeriodAggregates(
                LocalDate.of(year, 2, 1), LocalDate.of(year, 2, 29),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        when(periodAggregateService.buildPeriodAggregates(userId, month, year)).thenReturn(current);
        when(periodAggregateService.buildPeriodAggregates(userId, 2, year)).thenReturn(prev);
        when(categoryRepository.findByUserId(userId)).thenReturn(categories);
        when(expenseRepository.sumAmountByCategoryForUserAndDateBetween(userId, start, end))
                .thenReturn(List.<Object[]>of(
                        new Object[]{id1, new BigDecimal("1000")},
                        new Object[]{id2, new BigDecimal("2000")},
                        new Object[]{id3, new BigDecimal("3000")},
                        new Object[]{id4, new BigDecimal("4000")},
                        new Object[]{id5, new BigDecimal("5000")},
                        new Object[]{id6, new BigDecimal("6000")}
                ));
        categories.forEach(cat ->
                when(categorySummaryBuilder.buildCategorySummary(eq(cat), any()))
                        .thenReturn(CategorySummaryDto.builder().id(cat.getId()).name(cat.getName())
                                .amount(BigDecimal.ZERO).budget(BigDecimal.ZERO).percentUsed(BigDecimal.ZERO).build()));

        BudgetSummaryResponseDto result = budgetSummaryService.getSummary(userId, month, year);

        assertThat(result.getCategories()).hasSize(6);
    }

    @Test
    @DisplayName("Должен вернуть категории отсортированные по убыванию суммы расходов")
    void shouldReturnCategoriesSortedByAmountDescending() {
        int month = 3;
        int year = 2024;
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        List<Category> categories = List.of(
                Category.builder().id(id1).userId(userId).name("Малые расходы").budget(BigDecimal.ZERO).build(),
                Category.builder().id(id2).userId(userId).name("Средние расходы").budget(BigDecimal.ZERO).build(),
                Category.builder().id(id3).userId(userId).name("Большие расходы").budget(BigDecimal.ZERO).build()
        );

        PeriodAggregates current = new PeriodAggregates(start, end,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        PeriodAggregates prev = new PeriodAggregates(
                LocalDate.of(year, 2, 1), LocalDate.of(year, 2, 29),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        when(periodAggregateService.buildPeriodAggregates(userId, month, year)).thenReturn(current);
        when(periodAggregateService.buildPeriodAggregates(userId, 2, year)).thenReturn(prev);
        when(categoryRepository.findByUserId(userId)).thenReturn(categories);
        when(expenseRepository.sumAmountByCategoryForUserAndDateBetween(userId, start, end))
                .thenReturn(List.<Object[]>of(
                        new Object[]{id1, new BigDecimal("500")},
                        new Object[]{id2, new BigDecimal("3000")},
                        new Object[]{id3, new BigDecimal("12000")}
                ));
        when(categorySummaryBuilder.buildCategorySummary(eq(categories.get(0)), any()))
                .thenReturn(CategorySummaryDto.builder().id(id1).name("Малые расходы")
                        .amount(new BigDecimal("500")).budget(BigDecimal.ZERO).percentUsed(BigDecimal.ZERO).build());
        when(categorySummaryBuilder.buildCategorySummary(eq(categories.get(1)), any()))
                .thenReturn(CategorySummaryDto.builder().id(id2).name("Средние расходы")
                        .amount(new BigDecimal("3000")).budget(BigDecimal.ZERO).percentUsed(BigDecimal.ZERO).build());
        when(categorySummaryBuilder.buildCategorySummary(eq(categories.get(2)), any()))
                .thenReturn(CategorySummaryDto.builder().id(id3).name("Большие расходы")
                        .amount(new BigDecimal("12000")).budget(BigDecimal.ZERO).percentUsed(BigDecimal.ZERO).build());

        BudgetSummaryResponseDto result = budgetSummaryService.getSummary(userId, month, year);

        List<CategorySummaryDto> resultCategories = result.getCategories();
        assertThat(resultCategories).hasSize(3);
        assertThat(resultCategories.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("12000"));
        assertThat(resultCategories.get(1).getAmount()).isEqualByComparingTo(new BigDecimal("3000"));
        assertThat(resultCategories.get(2).getAmount()).isEqualByComparingTo(new BigDecimal("500"));
    }

    @Test
    @DisplayName("Должен вернуть все категории, если их 4 или меньше")
    void shouldReturnAllCategoriesWhenFourOrFewer() {
        int month = 3;
        int year = 2024;
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        UUID id4 = UUID.randomUUID();

        List<Category> categories = List.of(
                Category.builder().id(id1).userId(userId).name("Продукты").budget(BigDecimal.ZERO).build(),
                Category.builder().id(id2).userId(userId).name("Транспорт").budget(BigDecimal.ZERO).build(),
                Category.builder().id(id3).userId(userId).name("Кафе").budget(BigDecimal.ZERO).build(),
                Category.builder().id(id4).userId(userId).name("Здоровье").budget(BigDecimal.ZERO).build()
        );

        PeriodAggregates current = new PeriodAggregates(start, end,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        PeriodAggregates prev = new PeriodAggregates(
                LocalDate.of(year, 2, 1), LocalDate.of(year, 2, 29),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        when(periodAggregateService.buildPeriodAggregates(userId, month, year)).thenReturn(current);
        when(periodAggregateService.buildPeriodAggregates(userId, 2, year)).thenReturn(prev);
        when(categoryRepository.findByUserId(userId)).thenReturn(categories);
        when(expenseRepository.sumAmountByCategoryForUserAndDateBetween(userId, start, end))
                .thenReturn(List.<Object[]>of(
                        new Object[]{id1, new BigDecimal("8000")},
                        new Object[]{id2, new BigDecimal("2000")},
                        new Object[]{id3, new BigDecimal("5000")},
                        new Object[]{id4, new BigDecimal("1000")}
                ));
        categories.forEach(cat ->
                when(categorySummaryBuilder.buildCategorySummary(eq(cat), any()))
                        .thenReturn(CategorySummaryDto.builder().id(cat.getId()).name(cat.getName())
                                .amount(BigDecimal.ZERO).budget(BigDecimal.ZERO).percentUsed(BigDecimal.ZERO).build()));

        BudgetSummaryResponseDto result = budgetSummaryService.getSummary(userId, month, year);

        assertThat(result.getCategories()).hasSize(4);
    }
}
