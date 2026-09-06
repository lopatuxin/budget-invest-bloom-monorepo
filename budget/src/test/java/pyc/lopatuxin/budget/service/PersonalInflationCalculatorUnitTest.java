package pyc.lopatuxin.budget.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.budget.repository.ExpenseRepository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PersonalInflationCalculatorUnitTest")
class PersonalInflationCalculatorUnitTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @InjectMocks
    private PersonalInflationCalculator personalInflationCalculator;

    private final UUID userId = UUID.randomUUID();

    // ─── calculate() — matches BudgetSummaryService's original contract ────────

    @Test
    @DisplayName("Должен рассчитать инфляцию как изменение среднего расхода в месяц год к году")
    void shouldCalculatePersonalInflationCorrectly() {
        // currentYearTotal = 99000 (3 месяца) → avg = 33000
        // previousYearTotal = 360000 (12 месяцев) → avg = 30000
        // inflation = (33000 - 30000) / 30000 * 100 = 10.0%
        int month = 3;
        int year = 2024;

        when(expenseRepository.findMonthlyNonTransferExpenseByUserIdAndYear(userId, year))
                .thenReturn(List.of(
                        new Object[]{1, new BigDecimal("33000")},
                        new Object[]{2, new BigDecimal("33000")},
                        new Object[]{3, new BigDecimal("33000")}
                ));
        when(expenseRepository.findMonthlyNonTransferExpenseByUserIdAndYear(userId, year - 1))
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

        BigDecimal result = personalInflationCalculator.calculate(userId, month, year, new HashMap<>());

        assertThat(result).isEqualByComparingTo(new BigDecimal("10.0"));
    }

    @Test
    @DisplayName("Должен вернуть ноль когда за текущий год нет данных")
    void shouldReturnZeroWhenNoCurrentYearData() {
        when(expenseRepository.findMonthlyNonTransferExpenseByUserIdAndYear(userId, 2024))
                .thenReturn(Collections.emptyList());

        BigDecimal result = personalInflationCalculator.calculate(userId, 3, 2024, new HashMap<>());

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Должен вернуть ноль когда за прошлый год нет данных")
    void shouldReturnZeroWhenNoPreviousYearData() {
        when(expenseRepository.findMonthlyNonTransferExpenseByUserIdAndYear(userId, 2024))
                .thenReturn(List.<Object[]>of(new Object[]{1, new BigDecimal("30000")}));
        when(expenseRepository.findMonthlyNonTransferExpenseByUserIdAndYear(userId, 2023))
                .thenReturn(Collections.emptyList());

        BigDecimal result = personalInflationCalculator.calculate(userId, 1, 2024, new HashMap<>());

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Должен запросить данные года не более одного раза благодаря переданному кешу")
    void shouldCacheMonthlyExpensesByYearAcrossCalls() {
        Map<Integer, List<Object[]>> cache = new HashMap<>();
        when(expenseRepository.findMonthlyNonTransferExpenseByUserIdAndYear(userId, 2024))
                .thenReturn(List.<Object[]>of(new Object[]{1, new BigDecimal("30000")}));
        when(expenseRepository.findMonthlyNonTransferExpenseByUserIdAndYear(userId, 2023))
                .thenReturn(List.<Object[]>of(new Object[]{1, new BigDecimal("30000")}));

        personalInflationCalculator.calculate(userId, 3, 2024, cache);
        personalInflationCalculator.calculate(userId, 1, 2024, cache);

        verify(expenseRepository, times(1)).findMonthlyNonTransferExpenseByUserIdAndYear(userId, 2024);
    }

    // ─── calculateOptional() — used by the overview page ────────────────────────

    @Test
    @DisplayName("Должен вернуть значение через Optional когда данные обоих лет есть")
    void shouldReturnOptionalValueWhenBothYearsHaveData() {
        when(expenseRepository.findMonthlyNonTransferExpenseByUserIdAndYear(userId, 2024))
                .thenReturn(List.<Object[]>of(new Object[]{1, new BigDecimal("33000")}));
        when(expenseRepository.findMonthlyNonTransferExpenseByUserIdAndYear(userId, 2023))
                .thenReturn(List.<Object[]>of(new Object[]{1, new BigDecimal("30000")}));

        Optional<BigDecimal> result = personalInflationCalculator.calculateOptional(userId, 1, 2024, new HashMap<>());

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualByComparingTo(new BigDecimal("10.0"));
    }

    @Test
    @DisplayName("Должен вернуть пустой Optional когда за прошлый год нет данных")
    void shouldReturnEmptyOptionalWhenNoPreviousYearData() {
        when(expenseRepository.findMonthlyNonTransferExpenseByUserIdAndYear(userId, 2024))
                .thenReturn(List.<Object[]>of(new Object[]{1, new BigDecimal("30000")}));
        when(expenseRepository.findMonthlyNonTransferExpenseByUserIdAndYear(userId, 2023))
                .thenReturn(Collections.emptyList());

        Optional<BigDecimal> result = personalInflationCalculator.calculateOptional(userId, 1, 2024, new HashMap<>());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Должен вернуть пустой Optional когда за текущий год нет данных")
    void shouldReturnEmptyOptionalWhenNoCurrentYearData() {
        when(expenseRepository.findMonthlyNonTransferExpenseByUserIdAndYear(userId, 2024))
                .thenReturn(Collections.emptyList());

        Optional<BigDecimal> result = personalInflationCalculator.calculateOptional(userId, 3, 2024, new HashMap<>());

        assertThat(result).isEmpty();
    }
}
