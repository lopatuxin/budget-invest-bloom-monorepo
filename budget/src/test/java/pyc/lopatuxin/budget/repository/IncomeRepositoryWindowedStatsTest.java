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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link IncomeRepository#findWindowedNonTransferIncomeStats}:
 * day-of-month cutoff, transfer exclusion and window boundaries.
 */
@DisplayName("IncomeRepository — окно истории для расчёта нормы")
class IncomeRepositoryWindowedStatsTest extends AbstractIntegrationTest {

    private UUID userId;

    @BeforeEach
    void setUp() {
        incomeRepository.deleteAll();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Делит сумму месяца на cutoff (день <= D) и полную")
    void shouldSplitMonthlySumIntoCutoffAndFull() {
        saveIncome(new BigDecimal("40000.00"), LocalDate.of(2025, 6, 5));
        saveIncome(new BigDecimal("60000.00"), LocalDate.of(2025, 6, 25));

        List<Object[]> result = incomeRepository.findWindowedNonTransferIncomeStats(
                userId, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), 10);

        assertThat(result).hasSize(1);
        assertThat((BigDecimal) result.getFirst()[2]).isEqualByComparingTo(new BigDecimal("40000.00"));
        assertThat((BigDecimal) result.getFirst()[3]).isEqualByComparingTo(new BigDecimal("100000.00"));
    }

    @Test
    @DisplayName("Возвращает число различных дней месяца, на которые приходятся записи")
    void shouldReturnDistinctDaysCount() {
        saveIncome(new BigDecimal("40000.00"), LocalDate.of(2025, 6, 1));

        List<Object[]> result = incomeRepository.findWindowedNonTransferIncomeStats(
                userId, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), 10);

        assertThat(result).hasSize(1);
        assertThat(((Number) result.getFirst()[4]).intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("Число различных дней растёт при подневном учёте (несколько дат в месяце)")
    void shouldCountMultipleDistinctDaysWhenRecordsSpanSeveralDates() {
        saveIncome(new BigDecimal("40000.00"), LocalDate.of(2025, 6, 5));
        saveIncome(new BigDecimal("10000.00"), LocalDate.of(2025, 6, 5)); // тот же день, не увеличивает счётчик
        saveIncome(new BigDecimal("60000.00"), LocalDate.of(2025, 6, 25));

        List<Object[]> result = incomeRepository.findWindowedNonTransferIncomeStats(
                userId, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), 10);

        assertThat(result).hasSize(1);
        assertThat(((Number) result.getFirst()[4]).intValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("Не учитывает трансферные записи")
    void shouldExcludeTransferRecords() {
        saveIncome(new BigDecimal("40000.00"), LocalDate.of(2025, 6, 5));
        incomeRepository.save(Income.builder()
                .userId(userId).source(IncomeSource.INVESTMENTS)
                .amount(new BigDecimal("100000.00"))
                .date(LocalDate.of(2025, 6, 10))
                .isTransfer(true)
                .build());

        List<Object[]> result = incomeRepository.findWindowedNonTransferIncomeStats(
                userId, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), 31);

        assertThat(result).hasSize(1);
        assertThat((BigDecimal) result.getFirst()[3]).isEqualByComparingTo(new BigDecimal("40000.00"));
    }

    @Test
    @DisplayName("Не включает записи за пределами окна дат")
    void shouldExcludeRecordsOutsideTheWindow() {
        saveIncome(new BigDecimal("100.00"), LocalDate.of(2024, 12, 31));
        saveIncome(new BigDecimal("200.00"), LocalDate.of(2025, 1, 1));
        saveIncome(new BigDecimal("400.00"), LocalDate.of(2026, 1, 1));

        List<Object[]> result = incomeRepository.findWindowedNonTransferIncomeStats(
                userId, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), 31);

        assertThat(result).hasSize(1);
        assertThat((BigDecimal) result.getFirst()[3]).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    private void saveIncome(BigDecimal amount, LocalDate date) {
        incomeRepository.save(Income.builder()
                .userId(userId)
                .source(IncomeSource.SALARY)
                .amount(amount)
                .date(date)
                .build());
    }
}
