package pyc.lopatuxin.budget.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.budget.dto.response.MetricResponseDto;
import pyc.lopatuxin.budget.dto.response.MonthlyMetricDto;
import pyc.lopatuxin.budget.repository.ExpenseRepository;
import pyc.lopatuxin.budget.repository.IncomeRepository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BalanceMetricService")
class BalanceMetricServiceUnitTest {

    @Mock
    private IncomeRepository incomeRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @InjectMocks
    private BalanceMetricService balanceMetricService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Должен вернуть корректную метрику баланса (доходы минус расходы) за несколько месяцев")
    void shouldReturnCorrectBalanceMetricForSeveralMonths() {
        int year = 2025;
        List<Object[]> incomeData = List.of(
                new Object[]{1, new BigDecimal("100000.00")},
                new Object[]{3, new BigDecimal("120000.00")},
                new Object[]{6, new BigDecimal("150000.00")}
        );
        List<Object[]> expenseData = List.of(
                new Object[]{1, new BigDecimal("60000.00")},
                new Object[]{3, new BigDecimal("80000.00")},
                new Object[]{6, new BigDecimal("90000.00")}
        );
        when(incomeRepository.findMonthlyIncomeByUserIdAndYear(userId, year)).thenReturn(incomeData);
        when(expenseRepository.findMonthlyExpenseByUserIdAndYear(userId, year)).thenReturn(expenseData);

        MetricResponseDto result = balanceMetricService.getBalanceMetric(userId, year);

        assertThat(result).isNotNull();
        assertThat(result.getYear()).isEqualTo(year);
        assertThat(result.getMonthlyData()).hasSize(12);

        // Январь: 100000 - 60000 = 40000
        assertThat(result.getMonthlyData().get(0).getAmount()).isEqualByComparingTo(new BigDecimal("40000.00"));
        assertThat(result.getMonthlyData().get(0).getMonthName()).isEqualTo("Янв");

        // Март: 120000 - 80000 = 40000
        assertThat(result.getMonthlyData().get(2).getAmount()).isEqualByComparingTo(new BigDecimal("40000.00"));

        // Июнь: 150000 - 90000 = 60000
        assertThat(result.getMonthlyData().get(5).getAmount()).isEqualByComparingTo(new BigDecimal("60000.00"));

        // Месяцы без данных = 0
        assertThat(result.getMonthlyData().get(1).getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getMonthlyData().get(11).getAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        // yearlyAverage = (40000 + 40000 + 60000) / 3 = 46666.67
        assertThat(result.getYearlyAverage()).isEqualByComparingTo(new BigDecimal("46666.67"));

        // yearlyMax = 60000
        assertThat(result.getYearlyMax()).isEqualByComparingTo(new BigDecimal("60000.00"));

        // currentValue = последний ненулевой (Июнь = 60000)
        assertThat(result.getCurrentValue()).isEqualByComparingTo(new BigDecimal("60000.00"));

        // previousValue = предпоследний ненулевой (Март = 40000)
        assertThat(result.getPreviousValue()).isEqualByComparingTo(new BigDecimal("40000.00"));

        // changePercent = (60000 - 40000) / 40000 * 100 = +50.0%
        assertThat(result.getChangePercent()).isEqualTo("+50.0%");

        verify(incomeRepository).findMonthlyIncomeByUserIdAndYear(userId, year);
        verify(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year);
    }

    @Test
    @DisplayName("Должен вернуть нулевые показатели при отсутствии данных за год")
    void shouldReturnZeroValuesWhenNoDataExists() {
        int year = 2025;
        when(incomeRepository.findMonthlyIncomeByUserIdAndYear(userId, year)).thenReturn(Collections.emptyList());
        when(expenseRepository.findMonthlyExpenseByUserIdAndYear(userId, year)).thenReturn(Collections.emptyList());

        MetricResponseDto result = balanceMetricService.getBalanceMetric(userId, year);

        assertThat(result.getYear()).isEqualTo(year);
        assertThat(result.getMonthlyData()).hasSize(12);
        assertThat(result.getCurrentValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getPreviousValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getYearlyAverage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getYearlyMax()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getChangePercent()).isEqualTo("+0.0%");

        for (MonthlyMetricDto monthly : result.getMonthlyData()) {
            assertThat(monthly.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Test
    @DisplayName("Должен корректно обработать месяц только с доходами (расход = 0)")
    void shouldHandleMonthWithOnlyIncome() {
        int year = 2025;
        List<Object[]> incomeData = List.<Object[]>of(
                new Object[]{5, new BigDecimal("80000.00")}
        );
        when(incomeRepository.findMonthlyIncomeByUserIdAndYear(userId, year)).thenReturn(incomeData);
        when(expenseRepository.findMonthlyExpenseByUserIdAndYear(userId, year)).thenReturn(Collections.emptyList());

        MetricResponseDto result = balanceMetricService.getBalanceMetric(userId, year);

        // Май: 80000 - 0 = 80000
        assertThat(result.getMonthlyData().get(4).getAmount()).isEqualByComparingTo(new BigDecimal("80000.00"));
        assertThat(result.getMonthlyData().get(4).getMonthName()).isEqualTo("Май");

        assertThat(result.getCurrentValue()).isEqualByComparingTo(new BigDecimal("80000.00"));
        assertThat(result.getPreviousValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getYearlyAverage()).isEqualByComparingTo(new BigDecimal("80000.00"));
        assertThat(result.getYearlyMax()).isEqualByComparingTo(new BigDecimal("80000.00"));
        assertThat(result.getChangePercent()).isEqualTo("+0.0%");
    }

    @Test
    @DisplayName("Должен корректно обработать месяц только с расходами (доход = 0, баланс отрицательный)")
    void shouldHandleMonthWithOnlyExpense() {
        int year = 2025;
        List<Object[]> expenseData = List.<Object[]>of(
                new Object[]{3, new BigDecimal("50000.00")}
        );
        when(incomeRepository.findMonthlyIncomeByUserIdAndYear(userId, year)).thenReturn(Collections.emptyList());
        when(expenseRepository.findMonthlyExpenseByUserIdAndYear(userId, year)).thenReturn(expenseData);

        MetricResponseDto result = balanceMetricService.getBalanceMetric(userId, year);

        // Март: 0 - 50000 = -50000 (отрицательное значение записывается в monthlyData)
        assertThat(result.getMonthlyData().get(2).getAmount()).isEqualByComparingTo(new BigDecimal("-50000.00"));

        // BalanceMetricService переопределяет extractNonZeroAmounts с фильтром != 0,
        // поэтому отрицательный баланс попадает в nonZeroAmounts
        assertThat(result.getCurrentValue()).isEqualByComparingTo(new BigDecimal("-50000.00"));
        assertThat(result.getYearlyAverage()).isEqualByComparingTo(new BigDecimal("-50000.00"));
        assertThat(result.getYearlyMax()).isEqualByComparingTo(new BigDecimal("-50000.00"));
        assertThat(result.getChangePercent()).isEqualTo("+0.0%");
    }

    @Test
    @DisplayName("Должен корректно обработать разные месяцы для доходов и расходов")
    void shouldHandleDifferentMonthsForIncomeAndExpense() {
        int year = 2025;
        // Доход в январе, расход в феврале — разные месяцы
        List<Object[]> incomeData = List.<Object[]>of(
                new Object[]{1, new BigDecimal("100000.00")}
        );
        List<Object[]> expenseData = List.<Object[]>of(
                new Object[]{2, new BigDecimal("70000.00")}
        );
        when(incomeRepository.findMonthlyIncomeByUserIdAndYear(userId, year)).thenReturn(incomeData);
        when(expenseRepository.findMonthlyExpenseByUserIdAndYear(userId, year)).thenReturn(expenseData);

        MetricResponseDto result = balanceMetricService.getBalanceMetric(userId, year);

        // Январь: 100000 - 0 = 100000
        assertThat(result.getMonthlyData().get(0).getAmount()).isEqualByComparingTo(new BigDecimal("100000.00"));

        // Февраль: 0 - 70000 = -70000
        assertThat(result.getMonthlyData().get(1).getAmount()).isEqualByComparingTo(new BigDecimal("-70000.00"));

        // BalanceMetricService переопределяет extractNonZeroAmounts с фильтром != 0,
        // поэтому -70000 попадает в nonZeroAmounts.
        // nonZeroAmounts = [100000, -70000]
        // currentValue = последний ненулевой (Февраль = -70000)
        assertThat(result.getCurrentValue()).isEqualByComparingTo(new BigDecimal("-70000.00"));

        // previousValue = предпоследний (Январь = 100000)
        assertThat(result.getPreviousValue()).isEqualByComparingTo(new BigDecimal("100000.00"));

        // yearlyAverage = (100000 + (-70000)) / 2 = 15000
        assertThat(result.getYearlyAverage()).isEqualByComparingTo(new BigDecimal("15000.00"));

        // yearlyMax = max(100000, -70000) = 100000
        assertThat(result.getYearlyMax()).isEqualByComparingTo(new BigDecimal("100000.00"));

        // changePercent = (-70000 - 100000) / |100000| * 100 = -170.0%
        assertThat(result.getChangePercent()).isEqualTo("-170.0%");
    }

    @Test
    @DisplayName("Должен корректно вызывать оба репозитория с правильными параметрами")
    void shouldCallBothRepositoriesWithCorrectParams() {
        int year = 2025;
        when(incomeRepository.findMonthlyIncomeByUserIdAndYear(userId, year)).thenReturn(Collections.emptyList());
        when(expenseRepository.findMonthlyExpenseByUserIdAndYear(userId, year)).thenReturn(Collections.emptyList());

        balanceMetricService.getBalanceMetric(userId, year);

        verify(incomeRepository).findMonthlyIncomeByUserIdAndYear(userId, year);
        verify(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year);
    }

    @Test
    @DisplayName("getMetricName должен возвращать 'баланса'")
    void shouldReturnCorrectMetricName() {
        assertThat(balanceMetricService.getMetricName()).isEqualTo("баланса");
    }

    @Test
    @DisplayName("Должен вернуть отрицательный changePercent при снижении баланса")
    void shouldReturnNegativeChangePercentWhenBalanceDecreased() {
        int year = 2025;
        List<Object[]> incomeData = List.of(
                new Object[]{2, new BigDecimal("200000")},
                new Object[]{5, new BigDecimal("100000")}
        );
        List<Object[]> expenseData = List.of(
                new Object[]{2, new BigDecimal("100000")},
                new Object[]{5, new BigDecimal("60000")}
        );
        when(incomeRepository.findMonthlyIncomeByUserIdAndYear(userId, year)).thenReturn(incomeData);
        when(expenseRepository.findMonthlyExpenseByUserIdAndYear(userId, year)).thenReturn(expenseData);

        MetricResponseDto result = balanceMetricService.getBalanceMetric(userId, year);

        // Февраль: 200000 - 100000 = 100000
        // Май: 100000 - 60000 = 40000
        // changePercent = (40000 - 100000) / 100000 * 100 = -60.0%
        assertThat(result.getChangePercent()).isEqualTo("-60.0%");
    }

    @Test
    @DisplayName("Должен корректно учитывать отрицательный баланс в currentValue, previousValue и yearlyAverage")
    void shouldCorrectlyAccountForNegativeBalanceInMetrics() {
        int year = 2025;
        // Январь: доход 50000, расход 80000 → баланс -30000
        // Март: доход 100000, расход 40000 → баланс 60000
        // Июнь: доход 20000, расход 70000 → баланс -50000
        List<Object[]> incomeData = List.of(
                new Object[]{1, new BigDecimal("50000.00")},
                new Object[]{3, new BigDecimal("100000.00")},
                new Object[]{6, new BigDecimal("20000.00")}
        );
        List<Object[]> expenseData = List.of(
                new Object[]{1, new BigDecimal("80000.00")},
                new Object[]{3, new BigDecimal("40000.00")},
                new Object[]{6, new BigDecimal("70000.00")}
        );
        when(incomeRepository.findMonthlyIncomeByUserIdAndYear(userId, year)).thenReturn(incomeData);
        when(expenseRepository.findMonthlyExpenseByUserIdAndYear(userId, year)).thenReturn(expenseData);

        MetricResponseDto result = balanceMetricService.getBalanceMetric(userId, year);

        // nonZeroAmounts = [-30000, 60000, -50000] (все != 0 включены)
        // currentValue = последний ненулевой = -50000 (Июнь)
        assertThat(result.getCurrentValue()).isEqualByComparingTo(new BigDecimal("-50000.00"));

        // previousValue = предпоследний = 60000 (Март)
        assertThat(result.getPreviousValue()).isEqualByComparingTo(new BigDecimal("60000.00"));

        // yearlyAverage = (-30000 + 60000 + (-50000)) / 3 = -20000 / 3 = -6666.67
        assertThat(result.getYearlyAverage()).isEqualByComparingTo(new BigDecimal("-6666.67"));

        // yearlyMax = max(-30000, 60000, -50000) = 60000
        assertThat(result.getYearlyMax()).isEqualByComparingTo(new BigDecimal("60000.00"));

        // changePercent = (-50000 - 60000) / |60000| * 100 = -183.3%
        assertThat(result.getChangePercent()).isEqualTo("-183.3%");

        // Проверяем что monthlyData содержит отрицательные значения
        assertThat(result.getMonthlyData().get(0).getAmount()).isEqualByComparingTo(new BigDecimal("-30000.00"));
        assertThat(result.getMonthlyData().get(2).getAmount()).isEqualByComparingTo(new BigDecimal("60000.00"));
        assertThat(result.getMonthlyData().get(5).getAmount()).isEqualByComparingTo(new BigDecimal("-50000.00"));
    }

    @Test
    @DisplayName("Должен вернуть положительный changePercent при росте баланса")
    void shouldReturnPositiveChangePercentWhenBalanceIncreased() {
        int year = 2025;
        List<Object[]> incomeData = List.of(
                new Object[]{3, new BigDecimal("80000")},
                new Object[]{7, new BigDecimal("150000")}
        );
        List<Object[]> expenseData = List.of(
                new Object[]{3, new BigDecimal("50000")},
                new Object[]{7, new BigDecimal("50000")}
        );
        when(incomeRepository.findMonthlyIncomeByUserIdAndYear(userId, year)).thenReturn(incomeData);
        when(expenseRepository.findMonthlyExpenseByUserIdAndYear(userId, year)).thenReturn(expenseData);

        MetricResponseDto result = balanceMetricService.getBalanceMetric(userId, year);

        // Март: 80000 - 50000 = 30000
        // Июль: 150000 - 50000 = 100000
        // changePercent = (100000 - 30000) / 30000 * 100 = +233.3%
        assertThat(result.getChangePercent()).isEqualTo("+233.3%");
    }
}
