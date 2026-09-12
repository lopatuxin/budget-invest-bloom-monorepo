package pyc.lopatuxin.budget.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pyc.lopatuxin.budget.AbstractIntegrationTest;
import pyc.lopatuxin.budget.entity.Category;
import pyc.lopatuxin.budget.entity.Expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying that {@link ExpenseRepository#sumNonTransferAmountByCategoryForUserAndDateBetween}
 * (used by {@code BudgetSummaryService} for the budget page's category card) and
 * {@link ExpenseRepository#findMonthlyNonTransferExpenseByCategoryAndDateBetween} (used by
 * {@code CategoryPageService} for the category page's "spent" figure) agree on the amount spent
 * by a category over the same date range.
 *
 * <p>Both queries carry their own hand-written {@code isTransfer}/date-range predicate. A unit test
 * with a mocked {@link ExpenseRepository} cannot detect the two predicates drifting apart — the mock
 * simply returns whatever the test tells it to, regardless of what the real JPQL would select. This
 * test runs both queries against the same real rows so a divergence in either predicate — isTransfer
 * handling, boundary date comparison, category filtering — shows up as a real assertion failure.</p>
 */
@DisplayName("ExpenseRepository — сумма категории совпадает между запросом сводки и запросом страницы категории")
class ExpenseRepositoryCategorySpentAgreementTest extends AbstractIntegrationTest {

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID userId;
    private Category category;
    private Category otherCategory;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        category = categoryRepository.save(Category.builder()
                .userId(userId).name("Продукты").emoji("🛒").budget(BigDecimal.ZERO).build());
        otherCategory = categoryRepository.save(Category.builder()
                .userId(userId).name("Транспорт").emoji("🚗").budget(BigDecimal.ZERO).build());
    }

    @AfterEach
    void tearDown() {
        // Удаляем только строки, созданные этим тестом (по его собственным категориям — включая
        // расход другого пользователя, заведённый в этой же категории), а не всю таблицу — иначе
        // тест сносил бы данные, оставленные другими пользователями/тестами.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            expenseRepository.deleteAllByCategoryId(category.getId());
            expenseRepository.deleteAllByCategoryId(otherCategory.getId());
        });
        categoryRepository.deleteAll(List.of(category, otherCategory));
    }

    @Test
    @DisplayName("Обе стороны сходятся на одной сумме при смешанном наборе transfer/non-transfer записей и посторонних категорий")
    void bothQueriesAgreeOnMixedNonTransferAndTransferRecords() {
        LocalDate start = LocalDate.of(2025, 6, 1);
        LocalDate end = LocalDate.of(2025, 6, 30);

        saveExpense(category, new BigDecimal("1000.00"), LocalDate.of(2025, 6, 5), false);
        saveExpense(category, new BigDecimal("2000.00"), LocalDate.of(2025, 6, 20), false);
        saveExpense(category, new BigDecimal("9999.00"), LocalDate.of(2025, 6, 12), true); // transfer — исключается
        saveExpense(otherCategory, new BigDecimal("5000.00"), LocalDate.of(2025, 6, 10), false); // другая категория

        BigDecimal summarySum = sumFromSummaryQuery(start, end);
        BigDecimal pageSum = sumFromPageQuery(start, end);

        assertThat(summarySum).isEqualByComparingTo(new BigDecimal("3000.00"));
        assertThat(pageSum)
                .as("Сумма страницы категории должна совпадать с суммой сводки бюджета для тех же данных")
                .isEqualByComparingTo(summarySum);
    }

    @Test
    @DisplayName("Обе стороны одинаково трактуют границы диапазона дат — включают начало и конец, исключают соседние дни")
    void bothQueriesAgreeOnDateRangeBoundaries() {
        LocalDate start = LocalDate.of(2025, 7, 1);
        LocalDate end = LocalDate.of(2025, 7, 31);

        saveExpense(category, new BigDecimal("100.00"), start, false); // первый день — включается
        saveExpense(category, new BigDecimal("200.00"), end, false); // последний день — включается
        saveExpense(category, new BigDecimal("300.00"), start.minusDays(1), false); // день до диапазона — исключается
        saveExpense(category, new BigDecimal("400.00"), end.plusDays(1), false); // день после диапазона — исключается

        BigDecimal summarySum = sumFromSummaryQuery(start, end);
        BigDecimal pageSum = sumFromPageQuery(start, end);

        assertThat(summarySum).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(pageSum)
                .as("Сумма страницы категории должна совпадать с суммой сводки бюджета на границах диапазона дат")
                .isEqualByComparingTo(summarySum);
    }

    @Test
    @DisplayName("Обе стороны исключают расход другого пользователя в той же категории и том же диапазоне дат")
    void bothQueriesExcludeOtherUsersExpenseInSameCategoryAndDateRange() {
        LocalDate start = LocalDate.of(2025, 6, 1);
        LocalDate end = LocalDate.of(2025, 6, 30);
        UUID otherUserId = UUID.randomUUID();

        saveExpense(category, new BigDecimal("1000.00"), LocalDate.of(2025, 6, 5), false);
        saveExpenseForUser(otherUserId, category, new BigDecimal("777777.00"), LocalDate.of(2025, 6, 10), false);

        BigDecimal summarySum = sumFromSummaryQuery(start, end);
        BigDecimal pageSum = sumFromPageQuery(start, end);

        assertThat(summarySum)
                .as("Сумма сводки не должна включать расход другого пользователя в той же категории")
                .isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(pageSum)
                .as("Сумма страницы категории не должна включать расход другого пользователя в той же категории")
                .isEqualByComparingTo(summarySum);
    }

    @Test
    @DisplayName("Обе стороны сходятся на диапазоне, пересекающем границу года, где помесячная группировка даёт несколько вёдер")
    void bothQueriesAgreeOnDateRangeSpanningYearBoundary() {
        LocalDate start = LocalDate.of(2025, 12, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);

        saveExpense(category, new BigDecimal("100.00"), LocalDate.of(2025, 12, 10), false);
        saveExpense(category, new BigDecimal("250.00"), LocalDate.of(2025, 12, 28), false);
        saveExpense(category, new BigDecimal("400.00"), LocalDate.of(2026, 1, 5), false);
        saveExpense(category, new BigDecimal("50.00"), LocalDate.of(2026, 1, 20), false);

        List<Object[]> pageRows = expenseRepository.findMonthlyNonTransferExpenseByCategoryAndDateBetween(
                userId, category.getId(), start, end);
        BigDecimal summarySum = sumFromSummaryQuery(start, end);
        BigDecimal pageSum = pageRows.stream()
                .map(row -> (BigDecimal) row[2])
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(pageRows)
                .as("Диапазон декабрь-январь должен дать два отдельных помесячных ведра, а не одно")
                .hasSize(2);
        assertThat(summarySum).isEqualByComparingTo(new BigDecimal("800.00"));
        assertThat(pageSum)
                .as("Сумма по нескольким помесячным вёдрам должна совпадать с общей суммой сводки")
                .isEqualByComparingTo(summarySum);
    }

    private BigDecimal sumFromSummaryQuery(LocalDate start, LocalDate end) {
        List<Object[]> rows = expenseRepository.sumNonTransferAmountByCategoryForUserAndDateBetween(userId, start, end);
        return rows.stream()
                .filter(row -> row[0].equals(category.getId()))
                .map(row -> (BigDecimal) row[1])
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumFromPageQuery(LocalDate start, LocalDate end) {
        List<Object[]> rows = expenseRepository.findMonthlyNonTransferExpenseByCategoryAndDateBetween(
                userId, category.getId(), start, end);
        return rows.stream()
                .map(row -> (BigDecimal) row[2])
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void saveExpense(Category expenseCategory, BigDecimal amount, LocalDate date, boolean isTransfer) {
        saveExpenseForUser(userId, expenseCategory, amount, date, isTransfer);
    }

    private void saveExpenseForUser(UUID expenseUserId, Category expenseCategory, BigDecimal amount,
                                     LocalDate date, boolean isTransfer) {
        expenseRepository.save(Expense.builder()
                .userId(expenseUserId)
                .category(expenseCategory)
                .amount(amount)
                .date(date)
                .isTransfer(isTransfer)
                .build());
    }
}
