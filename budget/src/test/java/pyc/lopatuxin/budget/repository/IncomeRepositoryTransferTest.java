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

    // ─── sumByUserId (lifetime, includes transfer incomes) ────────────────────

    @Test
    @DisplayName("sumByUserId: включает transfer-записи в lifetime-сумму (используется для капитала на обзоре)")
    void sumByUserId_shouldIncludeTransferEntries() {
        saveIncome(new BigDecimal("80000.00"), LocalDate.of(2025, 1, 5), false);
        saveIncome(new BigDecimal("20000.00"), LocalDate.of(2025, 1, 6), true);   // transfer — учитывается

        BigDecimal total = incomeRepository.sumByUserId(userId);

        assertThat(total).isEqualByComparingTo(new BigDecimal("100000.00"));
    }

    @Test
    @DisplayName("sumByUserId: возвращает 0 если у пользователя нет записей")
    void sumByUserId_noRecords_shouldReturnZero() {
        BigDecimal total = incomeRepository.sumByUserId(userId);

        assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("sumByUserId: не учитывает записи другого пользователя")
    void sumByUserId_shouldExcludeOtherUsersEntries() {
        UUID otherUserId = UUID.randomUUID();
        saveIncome(new BigDecimal("60000.00"), LocalDate.of(2024, 6, 1),  false);
        saveIncomeForUser(otherUserId, new BigDecimal("999999.00"), LocalDate.of(2025, 1, 15), true);

        BigDecimal total = incomeRepository.sumByUserId(userId);

        assertThat(total).isEqualByComparingTo(new BigDecimal("60000.00"));
    }

    // ─── sumByUserIdAndDateLessThanEqual (includes transfer incomes) ─────────

    @Test
    @DisplayName("sumByUserIdAndDateLessThanEqual: включает transfer-записи с датой не позже указанной")
    void sumByUserIdAndDateLessThanEqual_shouldIncludeTransferEntriesUpToDate() {
        saveIncome(new BigDecimal("50000.00"), LocalDate.of(2025, 3, 1),  false);
        saveIncome(new BigDecimal("30000.00"), LocalDate.of(2025, 3, 15), true);   // transfer, в пределах даты — учитывается
        saveIncome(new BigDecimal("99999.00"), LocalDate.of(2025, 4, 1),  false);  // после даты — не учитывается

        BigDecimal total = incomeRepository.sumByUserIdAndDateLessThanEqual(userId, LocalDate.of(2025, 3, 31));

        assertThat(total).isEqualByComparingTo(new BigDecimal("80000.00"));
    }

    // ─── findMonthlyIncomeByUserIdAndDateBetween (includes transfer incomes) ─

    @Test
    @DisplayName("findMonthlyIncomeByUserIdAndDateBetween: суммирует transfer и non-transfer записи месяца вместе")
    void findMonthlyIncome_shouldSumTransferAndNonTransferTogether() {
        saveIncome(new BigDecimal("50000.00"), LocalDate.of(2025, 1, 5),  false);
        saveIncome(new BigDecimal("15000.00"), LocalDate.of(2025, 1, 20), true);

        List<Object[]> result = incomeRepository.findMonthlyIncomeByUserIdAndDateBetween(
                userId, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertThat(result).hasSize(1);
        assertThat(((Number) result.get(0)[0]).intValue()).isEqualTo(2025);
        assertThat(((Number) result.get(0)[1]).intValue()).isEqualTo(1);
        assertThat((BigDecimal) result.get(0)[2]).isEqualByComparingTo(new BigDecimal("65000.00"));
    }

    @Test
    @DisplayName("findMonthlyIncomeByUserIdAndDateBetween: не учитывает записи другого пользователя")
    void findMonthlyIncome_shouldExcludeOtherUsersEntries() {
        UUID otherUserId = UUID.randomUUID();
        saveIncome(new BigDecimal("50000.00"), LocalDate.of(2025, 1, 5), false);
        saveIncomeForUser(otherUserId, new BigDecimal("777777.00"), LocalDate.of(2025, 1, 6), true);

        List<Object[]> result = incomeRepository.findMonthlyIncomeByUserIdAndDateBetween(
                userId, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertThat(result).hasSize(1);
        assertThat((BigDecimal) result.get(0)[2]).isEqualByComparingTo(new BigDecimal("50000.00"));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void saveIncome(BigDecimal amount, LocalDate date, boolean isTransfer) {
        saveIncomeForUser(userId, amount, date, isTransfer);
    }

    private void saveIncomeForUser(UUID forUserId, BigDecimal amount, LocalDate date, boolean isTransfer) {
        incomeRepository.save(Income.builder()
                .userId(forUserId)
                .source(IncomeSource.SALARY)
                .amount(amount)
                .date(date)
                .isTransfer(isTransfer)
                .build());
    }
}
