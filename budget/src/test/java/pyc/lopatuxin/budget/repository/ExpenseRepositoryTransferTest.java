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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for transfer-aware query methods in ExpenseRepository.
 * Verifies that isTransfer=true records are excluded from non-transfer aggregation queries.
 */
@DisplayName("ExpenseRepository — фильтрация transfer-записей")
class ExpenseRepositoryTransferTest extends AbstractIntegrationTest {

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
                .budget(new BigDecimal("30000.00"))
                .build());
    }

    // ─── sumNonTransferAmountByCategoryForUserAndDateBetween ──────────────────

    @Test
    @DisplayName("sumNonTransferAmountByCategoryForUserAndDateBetween: возвращает пустой список если все расходы помечены isTransfer=true")
    void sumNonTransfer_allTransfer_shouldReturnEmptyList() {
        LocalDate date = LocalDate.of(2025, 3, 10);
        saveExpense(new BigDecimal("5000.00"), date, true);
        saveExpense(new BigDecimal("3000.00"), date, true);

        List<Object[]> result = expenseRepository.sumNonTransferAmountByCategoryForUserAndDateBetween(
                userId,
                LocalDate.of(2025, 3, 1),
                LocalDate.of(2025, 3, 31)
        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("sumNonTransferAmountByCategoryForUserAndDateBetween: суммирует только isTransfer=false при смешанном наборе")
    void sumNonTransfer_mixedSet_shouldSumOnlyNonTransfer() {
        LocalDate date = LocalDate.of(2025, 4, 15);
        saveExpense(new BigDecimal("10000.00"), date, false);  // должен учитываться
        saveExpense(new BigDecimal("7000.00"),  date, false);  // должен учитываться
        saveExpense(new BigDecimal("5000.00"),  date, true);   // transfer — исключается

        List<Object[]> result = expenseRepository.sumNonTransferAmountByCategoryForUserAndDateBetween(
                userId,
                LocalDate.of(2025, 4, 1),
                LocalDate.of(2025, 4, 30)
        );

        assertThat(result).hasSize(1);
        BigDecimal sum = (BigDecimal) result.get(0)[1];
        assertThat(sum).isEqualByComparingTo(new BigDecimal("17000.00"));
    }

    @Test
    @DisplayName("sumNonTransferAmountByCategoryForUserAndDateBetween: когда все записи non-transfer — возвращает их полную сумму")
    void sumNonTransfer_allNonTransfer_shouldReturnFullSum() {
        LocalDate date = LocalDate.of(2025, 5, 10);
        saveExpense(new BigDecimal("2000.00"), date, false);
        saveExpense(new BigDecimal("3000.00"), date, false);

        List<Object[]> result = expenseRepository.sumNonTransferAmountByCategoryForUserAndDateBetween(
                userId,
                LocalDate.of(2025, 5, 1),
                LocalDate.of(2025, 5, 31)
        );

        assertThat(result).hasSize(1);
        BigDecimal sum = (BigDecimal) result.get(0)[1];
        assertThat(sum).isEqualByComparingTo(new BigDecimal("5000.00"));
    }

    // ─── findMonthlyNonTransferExpenseByUserIdAndYear ─────────────────────────

    @Test
    @DisplayName("findMonthlyNonTransferExpenseByUserIdAndYear: пропускает transfer-записи в помесячной разбивке")
    void findMonthlyNonTransfer_shouldExcludeTransferEntries() {
        // Январь: 8000 non-transfer + 2000 transfer
        saveExpense(new BigDecimal("8000.00"), LocalDate.of(2025, 1, 10), false);
        saveExpense(new BigDecimal("2000.00"), LocalDate.of(2025, 1, 20), true);

        // Март: только transfer
        saveExpense(new BigDecimal("15000.00"), LocalDate.of(2025, 3, 5), true);

        List<Object[]> result = expenseRepository.findMonthlyNonTransferExpenseByUserIdAndYear(userId, 2025);

        // Должен вернуть только Январь (8000), Март (только transfer) — не включается
        assertThat(result).hasSize(1);
        int month = ((Number) result.get(0)[0]).intValue();
        BigDecimal amount = (BigDecimal) result.get(0)[1];
        assertThat(month).isEqualTo(1);
        assertThat(amount).isEqualByComparingTo(new BigDecimal("8000.00"));
    }

    @Test
    @DisplayName("findMonthlyNonTransferExpenseByUserIdAndYear: возвращает пустой список если все записи года — transfer")
    void findMonthlyNonTransfer_allTransfer_shouldReturnEmptyList() {
        saveExpense(new BigDecimal("10000.00"), LocalDate.of(2025, 2, 14), true);
        saveExpense(new BigDecimal("5000.00"),  LocalDate.of(2025, 6, 1),  true);

        List<Object[]> result = expenseRepository.findMonthlyNonTransferExpenseByUserIdAndYear(userId, 2025);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findMonthlyNonTransferExpenseByUserIdAndYear: несколько non-transfer месяцев суммируются корректно")
    void findMonthlyNonTransfer_severalMonths_shouldReturnCorrectSums() {
        // Февраль: 4000 non-transfer
        saveExpense(new BigDecimal("4000.00"), LocalDate.of(2024, 2, 10), false);
        // Апрель: 6000 non-transfer + 9000 transfer
        saveExpense(new BigDecimal("6000.00"), LocalDate.of(2024, 4, 5),  false);
        saveExpense(new BigDecimal("9000.00"), LocalDate.of(2024, 4, 20), true);

        List<Object[]> result = expenseRepository.findMonthlyNonTransferExpenseByUserIdAndYear(userId, 2024);

        assertThat(result).hasSize(2);

        // Сортировка по месяцу — Февраль (index 0), Апрель (index 1)
        assertThat(((Number) result.get(0)[0]).intValue()).isEqualTo(2);
        assertThat((BigDecimal) result.get(0)[1]).isEqualByComparingTo(new BigDecimal("4000.00"));
        assertThat(((Number) result.get(1)[0]).intValue()).isEqualTo(4);
        assertThat((BigDecimal) result.get(1)[1]).isEqualByComparingTo(new BigDecimal("6000.00"));
    }

    // ─── sumAmountByUserIdAndDateBetween (isTransfer = false) ─────────────────

    @Test
    @DisplayName("sumAmountByUserIdAndDateBetween: не учитывает transfer-записи при подсчёте суммы периода")
    void sumAmountByUserIdAndDateBetween_shouldExcludeTransfer() {
        LocalDate date = LocalDate.of(2025, 7, 10);
        saveExpense(new BigDecimal("12000.00"), date, false);  // учитывается
        saveExpense(new BigDecimal("8000.00"),  date, true);   // transfer — не учитывается

        Optional<BigDecimal> result = expenseRepository.sumAmountByUserIdAndDateBetween(
                userId,
                LocalDate.of(2025, 7, 1),
                LocalDate.of(2025, 7, 31)
        );

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualByComparingTo(new BigDecimal("12000.00"));
    }

    @Test
    @DisplayName("sumAmountByUserIdAndDateBetween: возвращает пустой Optional если все записи — transfer")
    void sumAmountByUserIdAndDateBetween_allTransfer_shouldReturnEmpty() {
        saveExpense(new BigDecimal("20000.00"), LocalDate.of(2025, 8, 5), true);

        Optional<BigDecimal> result = expenseRepository.sumAmountByUserIdAndDateBetween(
                userId,
                LocalDate.of(2025, 8, 1),
                LocalDate.of(2025, 8, 31)
        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("sumAmountByUserIdAndDateBetween: включает все non-transfer записи в указанном диапазоне дат")
    void sumAmountByUserIdAndDateBetween_mixedDates_shouldSumOnlyNonTransferInRange() {
        // В диапазоне
        saveExpense(new BigDecimal("3000.00"), LocalDate.of(2025, 9, 1),  false);
        saveExpense(new BigDecimal("5000.00"), LocalDate.of(2025, 9, 30), false);
        // За пределами диапазона
        saveExpense(new BigDecimal("10000.00"), LocalDate.of(2025, 10, 1), false);
        // Transfer в диапазоне — не считается
        saveExpense(new BigDecimal("7000.00"), LocalDate.of(2025, 9, 15), true);

        Optional<BigDecimal> result = expenseRepository.sumAmountByUserIdAndDateBetween(
                userId,
                LocalDate.of(2025, 9, 1),
                LocalDate.of(2025, 9, 30)
        );

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualByComparingTo(new BigDecimal("8000.00"));
    }

    // ─── sumByUserId (lifetime, без фильтра isTransfer) ──────────────────────

    @Test
    @DisplayName("sumByUserId: учитывает transfer-записи (lifetime-баланс не фильтрует)")
    void sumByUserId_shouldIncludeTransferEntries() {
        saveExpense(new BigDecimal("10000.00"), LocalDate.of(2025, 1, 5), false);
        saveExpense(new BigDecimal("5000.00"),  LocalDate.of(2025, 1, 6), true);   // transfer

        BigDecimal total = expenseRepository.sumByUserId(userId);

        assertThat(total).isEqualByComparingTo(new BigDecimal("15000.00"));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void saveExpense(BigDecimal amount, LocalDate date, boolean isTransfer) {
        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(category)
                .amount(amount)
                .date(date)
                .isTransfer(isTransfer)
                .build());
    }
}
