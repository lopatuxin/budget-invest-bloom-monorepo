package pyc.lopatuxin.budget.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pyc.lopatuxin.budget.AbstractIntegrationTest;
import pyc.lopatuxin.budget.entity.Income;
import pyc.lopatuxin.budget.entity.enums.IncomeSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for transfer-aware query methods in IncomeRepository.
 * Verifies that isTransfer=true records are excluded from non-transfer aggregation queries.
 */
@DisplayName("IncomeRepository — фильтрация transfer-записей")
class IncomeRepositoryTransferTest extends AbstractIntegrationTest {

    private UUID userId;

    @BeforeEach
    void setUp() {
        incomeRepository.deleteAll();
        userId = UUID.randomUUID();
    }

    // ─── findMonthlyNonTransferIncomeByUserIdAndYear ──────────────────────────

    @Test
    @DisplayName("findMonthlyNonTransferIncomeByUserIdAndYear: пропускает transfer-записи в помесячной разбивке")
    void findMonthlyNonTransfer_shouldExcludeTransferEntries() {
        // Январь: 50000 non-transfer + 15000 transfer (SELL)
        saveIncome(new BigDecimal("50000.00"), LocalDate.of(2025, 1, 5),  false);
        saveIncome(new BigDecimal("15000.00"), LocalDate.of(2025, 1, 20), true);

        // Март: только transfer
        saveIncome(new BigDecimal("20000.00"), LocalDate.of(2025, 3, 10), true);

        List<Object[]> result = incomeRepository.findMonthlyNonTransferIncomeByUserIdAndYear(userId, 2025);

        // Только Январь (50000), Март (всё transfer) — пропускается
        assertThat(result).hasSize(1);
        int month = ((Number) result.get(0)[0]).intValue();
        BigDecimal amount = (BigDecimal) result.get(0)[1];
        assertThat(month).isEqualTo(1);
        assertThat(amount).isEqualByComparingTo(new BigDecimal("50000.00"));
    }

    @Test
    @DisplayName("findMonthlyNonTransferIncomeByUserIdAndYear: возвращает пустой список если все записи года — transfer")
    void findMonthlyNonTransfer_allTransfer_shouldReturnEmptyList() {
        saveIncome(new BigDecimal("30000.00"), LocalDate.of(2025, 2, 1), true);
        saveIncome(new BigDecimal("40000.00"), LocalDate.of(2025, 5, 1), true);

        List<Object[]> result = incomeRepository.findMonthlyNonTransferIncomeByUserIdAndYear(userId, 2025);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findMonthlyNonTransferIncomeByUserIdAndYear: несколько non-transfer месяцев суммируются корректно")
    void findMonthlyNonTransfer_severalMonths_shouldReturnCorrectSums() {
        // Февраль: 70000 non-transfer
        saveIncome(new BigDecimal("70000.00"), LocalDate.of(2024, 2, 15), false);
        // Июнь: 90000 non-transfer + 25000 transfer
        saveIncome(new BigDecimal("90000.00"), LocalDate.of(2024, 6, 1),  false);
        saveIncome(new BigDecimal("25000.00"), LocalDate.of(2024, 6, 30), true);

        List<Object[]> result = incomeRepository.findMonthlyNonTransferIncomeByUserIdAndYear(userId, 2024);

        assertThat(result).hasSize(2);
        assertThat(((Number) result.get(0)[0]).intValue()).isEqualTo(2);
        assertThat((BigDecimal) result.get(0)[1]).isEqualByComparingTo(new BigDecimal("70000.00"));
        assertThat(((Number) result.get(1)[0]).intValue()).isEqualTo(6);
        assertThat((BigDecimal) result.get(1)[1]).isEqualByComparingTo(new BigDecimal("90000.00"));
    }

    // ─── sumAmountByUserIdAndDateBetween (isTransfer = false) ─────────────────

    @Test
    @DisplayName("sumAmountByUserIdAndDateBetween: не учитывает transfer-записи при подсчёте суммы периода")
    void sumAmountByUserIdAndDateBetween_shouldExcludeTransfer() {
        LocalDate date = LocalDate.of(2025, 6, 10);
        saveIncome(new BigDecimal("100000.00"), date, false);  // учитывается
        saveIncome(new BigDecimal("30000.00"),  date, true);   // transfer — не учитывается

        Optional<BigDecimal> result = incomeRepository.sumAmountByUserIdAndDateBetween(
                userId,
                LocalDate.of(2025, 6, 1),
                LocalDate.of(2025, 6, 30)
        );

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualByComparingTo(new BigDecimal("100000.00"));
    }

    @Test
    @DisplayName("sumAmountByUserIdAndDateBetween: возвращает пустой Optional если все записи — transfer")
    void sumAmountByUserIdAndDateBetween_allTransfer_shouldReturnEmpty() {
        saveIncome(new BigDecimal("50000.00"), LocalDate.of(2025, 7, 5), true);

        Optional<BigDecimal> result = incomeRepository.sumAmountByUserIdAndDateBetween(
                userId,
                LocalDate.of(2025, 7, 1),
                LocalDate.of(2025, 7, 31)
        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("sumAmountByUserIdAndDateBetween: суммирует все non-transfer записи в диапазоне дат")
    void sumAmountByUserIdAndDateBetween_mixedDates_shouldSumOnlyNonTransferInRange() {
        // В диапазоне non-transfer
        saveIncome(new BigDecimal("60000.00"), LocalDate.of(2025, 8, 1),  false);
        saveIncome(new BigDecimal("40000.00"), LocalDate.of(2025, 8, 31), false);
        // За пределами диапазона
        saveIncome(new BigDecimal("20000.00"), LocalDate.of(2025, 9, 1),  false);
        // Transfer в диапазоне — не считается
        saveIncome(new BigDecimal("10000.00"), LocalDate.of(2025, 8, 15), true);

        Optional<BigDecimal> result = incomeRepository.sumAmountByUserIdAndDateBetween(
                userId,
                LocalDate.of(2025, 8, 1),
                LocalDate.of(2025, 8, 31)
        );

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualByComparingTo(new BigDecimal("100000.00"));
    }

    // ─── sumByUserId (lifetime, без фильтра isTransfer) ──────────────────────

    @Test
    @DisplayName("sumByUserId: учитывает transfer-записи (lifetime-баланс не фильтрует)")
    void sumByUserId_shouldIncludeTransferEntries() {
        saveIncome(new BigDecimal("80000.00"), LocalDate.of(2025, 1, 5), false);
        saveIncome(new BigDecimal("20000.00"), LocalDate.of(2025, 1, 6), true);   // transfer

        BigDecimal total = incomeRepository.sumByUserId(userId);

        assertThat(total).isEqualByComparingTo(new BigDecimal("100000.00"));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void saveIncome(BigDecimal amount, LocalDate date, boolean isTransfer) {
        incomeRepository.save(Income.builder()
                .userId(userId)
                .source(IncomeSource.SALARY)
                .amount(amount)
                .date(date)
                .isTransfer(isTransfer)
                .build());
    }
}
