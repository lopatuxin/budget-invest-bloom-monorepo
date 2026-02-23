package pyc.lopatuxin.budget.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import pyc.lopatuxin.budget.dto.response.BudgetSummaryResponseDto;
import pyc.lopatuxin.budget.dto.response.CategorySummaryDto;
import pyc.lopatuxin.budget.entity.CapitalRecord;
import pyc.lopatuxin.budget.entity.Category;
import pyc.lopatuxin.budget.repository.CapitalRecordRepository;
import pyc.lopatuxin.budget.repository.CategoryRepository;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BudgetSummaryServiceUnitTest")
class BudgetSummaryServiceUnitTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private IncomeRepository incomeRepository;

    @Mock
    private CapitalRecordRepository capitalRecordRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private BudgetSummaryService budgetSummaryService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
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

        CapitalRecord capitalRecord = CapitalRecord.builder()
                .userId(userId)
                .amount(new BigDecimal("1200000.00"))
                .month(month)
                .year(year)
                .build();

        when(incomeRepository.sumAmountByUserIdAndDateBetween(userId, start, end))
                .thenReturn(Optional.of(new BigDecimal("150000.00")));
        when(expenseRepository.sumAmountByUserIdAndDateBetween(userId, start, end))
                .thenReturn(Optional.of(new BigDecimal("89500.00")));
        when(capitalRecordRepository.findByUserIdAndMonthAndYear(userId, month, year))
                .thenReturn(Optional.of(capitalRecord));
        when(expenseRepository.sumAmountByUserIdAndYear(userId, year))
                .thenReturn(Optional.of(new BigDecimal("268500.00")));
        when(expenseRepository.sumAmountByUserIdAndYear(userId, year - 1))
                .thenReturn(Optional.of(new BigDecimal("960000.00")));
        when(categoryRepository.findByUserId(userId)).thenReturn(List.of(category));
        when(expenseRepository.sumAmountByCategoryForUserAndDateBetween(userId, start, end))
                .thenReturn(List.<Object[]>of(new Object[]{categoryId, new BigDecimal("25000.00")}));
        stubEmptyPreviousPeriod(2, 2024);

        BudgetSummaryResponseDto result = budgetSummaryService.getSummary(userId, month, year);

        assertThat(result).isNotNull();
        assertThat(result.getIncome()).isEqualByComparingTo(new BigDecimal("150000.00"));
        assertThat(result.getExpenses()).isEqualByComparingTo(new BigDecimal("89500.00"));
        assertThat(result.getCapital()).isEqualByComparingTo(new BigDecimal("1200000.00"));
        assertThat(result.getCategories()).hasSize(1);
        assertThat(result.getCategories().getFirst().getPercentUsed()).isEqualByComparingTo(new BigDecimal("83.33"));

        verify(incomeRepository).sumAmountByUserIdAndDateBetween(userId, start, end);
        verify(expenseRepository).sumAmountByUserIdAndDateBetween(userId, start, end);
        verify(capitalRecordRepository).findByUserIdAndMonthAndYear(userId, month, year);
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

        when(incomeRepository.sumAmountByUserIdAndDateBetween(userId, start, end))
                .thenReturn(Optional.empty());
        when(expenseRepository.sumAmountByUserIdAndDateBetween(userId, start, end))
                .thenReturn(Optional.empty());
        when(capitalRecordRepository.findByUserIdAndMonthAndYear(userId, month, year))
                .thenReturn(Optional.empty());
        when(capitalRecordRepository.findLatestByUserId(eq(userId), any(PageRequest.class)))
                .thenReturn(Collections.emptyList());
        when(expenseRepository.sumAmountByUserIdAndYear(userId, year))
                .thenReturn(Optional.empty());
        when(expenseRepository.sumAmountByUserIdAndYear(userId, year - 1))
                .thenReturn(Optional.empty());
        when(categoryRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(expenseRepository.sumAmountByCategoryForUserAndDateBetween(userId, start, end))
                .thenReturn(Collections.emptyList());
        stubEmptyPreviousPeriod(4, 2024);

        BudgetSummaryResponseDto result = budgetSummaryService.getSummary(userId, month, year);

        assertThat(result.getIncome()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getExpenses()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getCapital()).isEqualByComparingTo(BigDecimal.ZERO);
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

        when(incomeRepository.sumAmountByUserIdAndDateBetween(userId, start, end))
                .thenReturn(Optional.of(new BigDecimal("150000")));
        when(expenseRepository.sumAmountByUserIdAndDateBetween(userId, start, end))
                .thenReturn(Optional.of(new BigDecimal("89500")));
        when(capitalRecordRepository.findByUserIdAndMonthAndYear(userId, month, year))
                .thenReturn(Optional.empty());
        when(capitalRecordRepository.findLatestByUserId(eq(userId), any(PageRequest.class)))
                .thenReturn(Collections.emptyList());
        when(expenseRepository.sumAmountByUserIdAndYear(userId, year))
                .thenReturn(Optional.empty());
        when(expenseRepository.sumAmountByUserIdAndYear(userId, year - 1))
                .thenReturn(Optional.empty());
        when(categoryRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(expenseRepository.sumAmountByCategoryForUserAndDateBetween(userId, start, end))
                .thenReturn(Collections.emptyList());
        stubEmptyPreviousPeriod(5, 2024);

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

        when(incomeRepository.sumAmountByUserIdAndDateBetween(userId, start, end))
                .thenReturn(Optional.empty());
        when(expenseRepository.sumAmountByUserIdAndDateBetween(userId, start, end))
                .thenReturn(Optional.empty());
        when(capitalRecordRepository.findByUserIdAndMonthAndYear(userId, month, year))
                .thenReturn(Optional.empty());
        when(capitalRecordRepository.findLatestByUserId(eq(userId), any(PageRequest.class)))
                .thenReturn(Collections.emptyList());
        when(expenseRepository.sumAmountByUserIdAndYear(userId, year))
                .thenReturn(Optional.of(new BigDecimal("99000")));
        when(expenseRepository.sumAmountByUserIdAndYear(userId, year - 1))
                .thenReturn(Optional.of(new BigDecimal("360000")));
        when(categoryRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(expenseRepository.sumAmountByCategoryForUserAndDateBetween(userId, start, end))
                .thenReturn(Collections.emptyList());
        stubEmptyPreviousPeriod(2, 2024);

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

        LocalDate prevStart = LocalDate.of(year, 3, 1);
        LocalDate prevEnd = prevStart.withDayOfMonth(prevStart.lengthOfMonth());

        when(incomeRepository.sumAmountByUserIdAndDateBetween(userId, start, end))
                .thenReturn(Optional.of(new BigDecimal("108200")));
        when(expenseRepository.sumAmountByUserIdAndDateBetween(userId, start, end))
                .thenReturn(Optional.empty());
        when(capitalRecordRepository.findByUserIdAndMonthAndYear(userId, month, year))
                .thenReturn(Optional.empty());
        when(capitalRecordRepository.findLatestByUserId(eq(userId), any(PageRequest.class)))
                .thenReturn(Collections.emptyList());
        when(expenseRepository.sumAmountByUserIdAndYear(userId, year))
                .thenReturn(Optional.empty());
        when(categoryRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(expenseRepository.sumAmountByCategoryForUserAndDateBetween(userId, start, end))
                .thenReturn(Collections.emptyList());
        when(incomeRepository.sumAmountByUserIdAndDateBetween(userId, prevStart, prevEnd))
                .thenReturn(Optional.of(new BigDecimal("100000")));
        when(expenseRepository.sumAmountByUserIdAndDateBetween(userId, prevStart, prevEnd))
                .thenReturn(Optional.empty());
        when(capitalRecordRepository.findByUserIdAndMonthAndYear(userId, 3, 2024))
                .thenReturn(Optional.empty());
        when(expenseRepository.sumAmountByUserIdAndYear(userId, 2023))
                .thenReturn(Optional.empty());

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

        LocalDate prevStart = LocalDate.of(year, 3, 1);
        LocalDate prevEnd = prevStart.withDayOfMonth(prevStart.lengthOfMonth());

        when(incomeRepository.sumAmountByUserIdAndDateBetween(userId, start, end))
                .thenReturn(Optional.empty());
        when(expenseRepository.sumAmountByUserIdAndDateBetween(userId, start, end))
                .thenReturn(Optional.of(new BigDecimal("87210")));
        when(capitalRecordRepository.findByUserIdAndMonthAndYear(userId, month, year))
                .thenReturn(Optional.empty());
        when(capitalRecordRepository.findLatestByUserId(eq(userId), any(PageRequest.class)))
                .thenReturn(Collections.emptyList());
        when(expenseRepository.sumAmountByUserIdAndYear(userId, year))
                .thenReturn(Optional.empty());
        when(categoryRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(expenseRepository.sumAmountByCategoryForUserAndDateBetween(userId, start, end))
                .thenReturn(Collections.emptyList());
        when(incomeRepository.sumAmountByUserIdAndDateBetween(userId, prevStart, prevEnd))
                .thenReturn(Optional.empty());
        when(expenseRepository.sumAmountByUserIdAndDateBetween(userId, prevStart, prevEnd))
                .thenReturn(Optional.of(new BigDecimal("90000")));
        when(capitalRecordRepository.findByUserIdAndMonthAndYear(userId, 3, 2024))
                .thenReturn(Optional.empty());
        when(expenseRepository.sumAmountByUserIdAndYear(userId, 2023))
                .thenReturn(Optional.empty());

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

        when(incomeRepository.sumAmountByUserIdAndDateBetween(userId, start, end))
                .thenReturn(Optional.of(new BigDecimal("50000")));
        when(expenseRepository.sumAmountByUserIdAndDateBetween(userId, start, end))
                .thenReturn(Optional.of(new BigDecimal("30000")));
        when(capitalRecordRepository.findByUserIdAndMonthAndYear(userId, month, year))
                .thenReturn(Optional.empty());
        when(capitalRecordRepository.findLatestByUserId(eq(userId), any(PageRequest.class)))
                .thenReturn(Collections.emptyList());
        when(expenseRepository.sumAmountByUserIdAndYear(userId, year))
                .thenReturn(Optional.empty());
        when(expenseRepository.sumAmountByUserIdAndYear(userId, year - 1))
                .thenReturn(Optional.empty());
        when(categoryRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(expenseRepository.sumAmountByCategoryForUserAndDateBetween(userId, start, end))
                .thenReturn(Collections.emptyList());
        stubEmptyPreviousPeriod(6, 2024);

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

        when(incomeRepository.sumAmountByUserIdAndDateBetween(any(), any(), any())).thenReturn(Optional.empty());
        when(expenseRepository.sumAmountByUserIdAndDateBetween(userId, start, end))
                .thenReturn(Optional.empty());
        when(capitalRecordRepository.findByUserIdAndMonthAndYear(userId, month, year))
                .thenReturn(Optional.empty());
        when(capitalRecordRepository.findLatestByUserId(eq(userId), any(PageRequest.class)))
                .thenReturn(Collections.emptyList());
        when(expenseRepository.sumAmountByUserIdAndYear(any(), anyInt())).thenReturn(Optional.empty());
        when(categoryRepository.findByUserId(userId)).thenReturn(List.of(category));
        when(expenseRepository.sumAmountByCategoryForUserAndDateBetween(userId, start, end))
                .thenReturn(List.<Object[]>of(new Object[]{categoryId, new BigDecimal("25000")}));
        stubEmptyPreviousPeriod(2, 2024);

        BudgetSummaryResponseDto result = budgetSummaryService.getSummary(userId, month, year);

        CategorySummaryDto catDto = result.getCategories().getFirst();
        assertThat(catDto.getPercentUsed()).isEqualByComparingTo(new BigDecimal("83.33"));
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

        when(incomeRepository.sumAmountByUserIdAndDateBetween(any(), any(), any())).thenReturn(Optional.empty());
        when(expenseRepository.sumAmountByUserIdAndDateBetween(userId, start, end))
                .thenReturn(Optional.empty());
        when(capitalRecordRepository.findByUserIdAndMonthAndYear(userId, month, year))
                .thenReturn(Optional.empty());
        when(capitalRecordRepository.findLatestByUserId(eq(userId), any(PageRequest.class)))
                .thenReturn(Collections.emptyList());
        when(expenseRepository.sumAmountByUserIdAndYear(any(), anyInt())).thenReturn(Optional.empty());
        when(categoryRepository.findByUserId(userId)).thenReturn(List.of(category));
        when(expenseRepository.sumAmountByCategoryForUserAndDateBetween(userId, start, end))
                .thenReturn(List.<Object[]>of(new Object[]{categoryId, new BigDecimal("15000")}));
        stubEmptyPreviousPeriod(2, 2024);

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

        when(incomeRepository.sumAmountByUserIdAndDateBetween(any(), any(), any())).thenReturn(Optional.empty());
        when(expenseRepository.sumAmountByUserIdAndDateBetween(userId, start, end))
                .thenReturn(Optional.empty());
        when(capitalRecordRepository.findByUserIdAndMonthAndYear(userId, month, year))
                .thenReturn(Optional.empty());
        when(capitalRecordRepository.findLatestByUserId(eq(userId), any(PageRequest.class)))
                .thenReturn(Collections.emptyList());
        when(expenseRepository.sumAmountByUserIdAndYear(any(), anyInt())).thenReturn(Optional.empty());
        when(categoryRepository.findByUserId(userId)).thenReturn(List.of(category));
        when(expenseRepository.sumAmountByCategoryForUserAndDateBetween(userId, start, end))
                .thenReturn(List.<Object[]>of(new Object[]{categoryId, new BigDecimal("5000")}));
        stubEmptyPreviousPeriod(2, 2024);

        BudgetSummaryResponseDto result = budgetSummaryService.getSummary(userId, month, year);

        assertThat(result.getCategories().getFirst().getPercentUsed())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Должен корректно рассчитать тренд при переходе через год (январь → декабрь прошлого года)")
    void shouldCalculateTrendCorrectlyWhenCrossingYearBoundary() {
        // Запрос за январь 2024 → предыдущий период декабрь 2023
        // trend income = (100000 - 80000) / 80000 * 100 = +25.0%
        int month = 1;
        int year = 2024;
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        LocalDate prevStart = LocalDate.of(2023, 12, 1);
        LocalDate prevEnd = prevStart.withDayOfMonth(prevStart.lengthOfMonth());

        when(incomeRepository.sumAmountByUserIdAndDateBetween(userId, start, end))
                .thenReturn(Optional.of(new BigDecimal("100000")));
        when(expenseRepository.sumAmountByUserIdAndDateBetween(userId, start, end))
                .thenReturn(Optional.empty());
        when(capitalRecordRepository.findByUserIdAndMonthAndYear(userId, 1, 2024))
                .thenReturn(Optional.empty());
        when(capitalRecordRepository.findLatestByUserId(eq(userId), any(PageRequest.class)))
                .thenReturn(Collections.emptyList());
        when(expenseRepository.sumAmountByUserIdAndYear(userId, 2024))
                .thenReturn(Optional.empty());
        when(expenseRepository.sumAmountByUserIdAndYear(userId, 2023))
                .thenReturn(Optional.empty());
        when(categoryRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(expenseRepository.sumAmountByCategoryForUserAndDateBetween(userId, start, end))
                .thenReturn(Collections.emptyList());
        when(incomeRepository.sumAmountByUserIdAndDateBetween(userId, prevStart, prevEnd))
                .thenReturn(Optional.of(new BigDecimal("80000")));
        when(expenseRepository.sumAmountByUserIdAndDateBetween(userId, prevStart, prevEnd))
                .thenReturn(Optional.empty());
        when(capitalRecordRepository.findByUserIdAndMonthAndYear(userId, 12, 2023))
                .thenReturn(Optional.empty());
        when(expenseRepository.sumAmountByUserIdAndYear(userId, 2022))
                .thenReturn(Optional.empty());

        BudgetSummaryResponseDto result = budgetSummaryService.getSummary(userId, month, year);

        assertThat(result.getTrends().getIncome()).isEqualTo("+25.0%");
    }

    @Test
    @DisplayName("Должен использовать последнюю запись капитала, если за текущий месяц запись отсутствует")
    void shouldUseLatestCapitalRecordWhenCurrentMonthRecordIsAbsent() {
        int month = 3;
        int year = 2024;
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        CapitalRecord latestRecord = CapitalRecord.builder()
                .userId(userId)
                .amount(new BigDecimal("500000.00"))
                .month(2)
                .year(2024)
                .build();

        when(incomeRepository.sumAmountByUserIdAndDateBetween(any(), any(), any())).thenReturn(Optional.empty());
        when(expenseRepository.sumAmountByUserIdAndDateBetween(userId, start, end))
                .thenReturn(Optional.empty());
        when(capitalRecordRepository.findByUserIdAndMonthAndYear(userId, month, year))
                .thenReturn(Optional.empty());
        when(capitalRecordRepository.findLatestByUserId(eq(userId), any(PageRequest.class)))
                .thenReturn(List.of(latestRecord));
        when(expenseRepository.sumAmountByUserIdAndYear(any(), anyInt())).thenReturn(Optional.empty());
        when(categoryRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(expenseRepository.sumAmountByCategoryForUserAndDateBetween(userId, start, end))
                .thenReturn(Collections.emptyList());
        stubEmptyPreviousPeriod(2, 2024);

        BudgetSummaryResponseDto result = budgetSummaryService.getSummary(userId, month, year);

        assertThat(result.getCapital()).isEqualByComparingTo(new BigDecimal("500000.00"));
        verify(capitalRecordRepository).findLatestByUserId(eq(userId), any(PageRequest.class));
    }

    private void stubEmptyPreviousPeriod(int prevMonth, int prevYear) {
        LocalDate prevStart = LocalDate.of(prevYear, prevMonth, 1);
        LocalDate prevEnd = prevStart.withDayOfMonth(prevStart.lengthOfMonth());
        when(incomeRepository.sumAmountByUserIdAndDateBetween(userId, prevStart, prevEnd))
                .thenReturn(Optional.empty());
        when(expenseRepository.sumAmountByUserIdAndDateBetween(userId, prevStart, prevEnd))
                .thenReturn(Optional.empty());
        when(capitalRecordRepository.findByUserIdAndMonthAndYear(userId, prevMonth, prevYear))
                .thenReturn(Optional.empty());
    }
}