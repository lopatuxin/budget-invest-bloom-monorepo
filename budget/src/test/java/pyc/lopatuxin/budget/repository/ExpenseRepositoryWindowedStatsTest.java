package pyc.lopatuxin.budget.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pyc.lopatuxin.budget.AbstractIntegrationTest;
import pyc.lopatuxin.budget.entity.Category;
import pyc.lopatuxin.budget.entity.Expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the windowed norm-history queries in {@link ExpenseRepository}:
 * the day-of-month cutoff (JPQL {@code DAY()}), the 12-month window boundaries, and the
 * per-category breakdown that omits a category from a month it has no records in.
 */
@DisplayName("ExpenseRepository — окно истории для расчёта нормы")
class ExpenseRepositoryWindowedStatsTest extends AbstractIntegrationTest {

    private UUID userId;
    private Category category;

    @BeforeEach
    void setUp() {
        expenseRepository.deleteAll();
        categoryRepository.deleteAll();

        userId = UUID.randomUUID();
        category = categoryRepository.save(Category.builder()
                .userId(userId)
                .name("Продукты")
                .emoji("🛒")
                .budget(BigDecimal.ZERO)
                .build());
    }

    @Test
    @DisplayName("findWindowedNonTransferExpenseStats: делит сумму месяца на cutoff (день <= D) и полную")
    void shouldSplitMonthlySumIntoCutoffAndFull() {
        saveExpense(new BigDecimal("1000.00"), LocalDate.of(2025, 6, 10));
        saveExpense(new BigDecimal("2000.00"), LocalDate.of(2025, 6, 20));

        List<Object[]> result = expenseRepository.findWindowedNonTransferExpenseStats(
                userId, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), 15);

        assertThat(result).hasSize(1);
        Object[] row = result.getFirst();
        assertThat(((Number) row[0]).intValue()).isEqualTo(2025);
        assertThat(((Number) row[1]).intValue()).isEqualTo(6);
        assertThat((BigDecimal) row[2]).isEqualByComparingTo(new BigDecimal("1000.00")); // cutoff (day <= 15)
        assertThat((BigDecimal) row[3]).isEqualByComparingTo(new BigDecimal("3000.00")); // full month
    }

    @Test
    @DisplayName("findWindowedNonTransferExpenseStats: включает весь месяц, если день сравнения превышает длину месяца (февраль, D=30)")
    void shouldIncludeWholeMonthWhenDayExceedsMonthLength() {
        saveExpense(new BigDecimal("500.00"), LocalDate.of(2025, 2, 1));
        saveExpense(new BigDecimal("700.00"), LocalDate.of(2025, 2, 28));

        List<Object[]> result = expenseRepository.findWindowedNonTransferExpenseStats(
                userId, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), 30);

        assertThat(result).hasSize(1);
        Object[] row = result.getFirst();
        BigDecimal cutoff = (BigDecimal) row[2];
        BigDecimal full = (BigDecimal) row[3];
        assertThat(cutoff).isEqualByComparingTo(new BigDecimal("1200.00"));
        assertThat(cutoff).isEqualByComparingTo(full);
    }

    @Test
    @DisplayName("findWindowedNonTransferExpenseStats: не включает записи за пределами окна дат")
    void shouldExcludeRecordsOutsideTheWindow() {
        saveExpense(new BigDecimal("100.00"), LocalDate.of(2024, 12, 31)); // до окна
        saveExpense(new BigDecimal("200.00"), LocalDate.of(2025, 1, 1));   // начало окна
        saveExpense(new BigDecimal("300.00"), LocalDate.of(2025, 12, 31)); // конец окна
        saveExpense(new BigDecimal("400.00"), LocalDate.of(2026, 1, 1));   // после окна

        List<Object[]> result = expenseRepository.findWindowedNonTransferExpenseStats(
                userId, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), 31);

        assertThat(result).hasSize(2);
        BigDecimal total = result.stream().map(row -> (BigDecimal) row[3]).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("findWindowedNonTransferExpenseStats: возвращает число различных дней месяца, на которые приходятся записи")
    void shouldReturnDistinctDaysCount() {
        saveExpense(new BigDecimal("1000.00"), LocalDate.of(2025, 6, 1));

        List<Object[]> result = expenseRepository.findWindowedNonTransferExpenseStats(
                userId, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), 15);

        assertThat(result).hasSize(1);
        assertThat(((Number) result.getFirst()[4]).intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("findWindowedNonTransferExpenseStats: число различных дней растёт при подневном учёте (несколько дат в месяце)")
    void shouldCountMultipleDistinctDaysWhenRecordsSpanSeveralDates() {
        saveExpense(new BigDecimal("1000.00"), LocalDate.of(2025, 6, 3));
        saveExpense(new BigDecimal("500.00"), LocalDate.of(2025, 6, 3)); // тот же день, не увеличивает счётчик
        saveExpense(new BigDecimal("700.00"), LocalDate.of(2025, 6, 20));

        List<Object[]> result = expenseRepository.findWindowedNonTransferExpenseStats(
                userId, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), 15);

        assertThat(result).hasSize(1);
        assertThat(((Number) result.getFirst()[4]).intValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("findWindowedNonTransferExpenseStats: не учитывает трансферные записи")
    void shouldExcludeTransferRecords() {
        saveExpense(new BigDecimal("1000.00"), LocalDate.of(2025, 6, 10));
        expenseRepository.save(Expense.builder()
                .userId(userId).category(category)
                .amount(new BigDecimal("50000.00"))
                .date(LocalDate.of(2025, 6, 12))
                .isTransfer(true)
                .build());

        List<Object[]> result = expenseRepository.findWindowedNonTransferExpenseStats(
                userId, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), 31);

        assertThat(result).hasSize(1);
        assertThat((BigDecimal) result.getFirst()[3]).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("findWindowedNonTransferExpenseStatsByCategory: не возвращает строку для категории в месяце без её расходов")
    void shouldOmitCategoryFromMonthWithoutItsRecords() {
        Category otherCategory = categoryRepository.save(Category.builder()
                .userId(userId).name("Транспорт").emoji("🚌").budget(BigDecimal.ZERO).build());

        saveExpense(new BigDecimal("1000.00"), LocalDate.of(2025, 6, 5)); // только "Продукты"
        expenseRepository.save(Expense.builder()
                .userId(userId).category(otherCategory)
                .amount(new BigDecimal("2000.00"))
                .date(LocalDate.of(2025, 7, 5))
                .build()); // "Транспорт" — только в июле

        List<Object[]> result = expenseRepository.findWindowedNonTransferExpenseStatsByCategory(
                userId, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), 31);

        // Ровно две строки: (июнь, Продукты) и (июль, Транспорт) — категория "Продукты" в июле не встречается,
        // как и "Транспорт" в июне.
        assertThat(result).hasSize(2);
        assertThat(result).noneMatch(row ->
                ((Number) row[1]).intValue() == 7 && row[2].equals(category.getId()));
        assertThat(result).noneMatch(row ->
                ((Number) row[1]).intValue() == 6 && row[2].equals(otherCategory.getId()));
    }

    private void saveExpense(BigDecimal amount, LocalDate date) {
        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(category)
                .amount(amount)
                .date(date)
                .build());
    }
}
