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

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExpenseMetricService")
class ExpenseMetricServiceUnitTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @InjectMocks
    private ExpenseMetricService expenseMetricService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Должен делегировать вызов в AbstractMetricService и вернуть корректную метрику расходов")
    void shouldDelegateToAbstractAndReturnCorrectMetric() {
        int year = 2025;
        List<Object[]> dbData = List.of(
                new Object[]{1, new BigDecimal("30000.00")},
                new Object[]{4, new BigDecimal("45000.00")},
                new Object[]{8, new BigDecimal("38000.00")}
        );
        when(expenseRepository.findMonthlyExpenseByUserIdAndYear(userId, year)).thenReturn(dbData);

        MetricResponseDto result = expenseMetricService.getExpenseMetric(userId, year);

        assertThat(result).isNotNull();
        assertThat(result.getYear()).isEqualTo(year);
        assertThat(result.getMonthlyData()).hasSize(12);

        // Месяцы с данными
        assertThat(result.getMonthlyData().get(0).getAmount()).isEqualByComparingTo(new BigDecimal("30000.00"));
        assertThat(result.getMonthlyData().get(0).getMonthName()).isEqualTo("Янв");
        assertThat(result.getMonthlyData().get(3).getAmount()).isEqualByComparingTo(new BigDecimal("45000.00"));
        assertThat(result.getMonthlyData().get(7).getAmount()).isEqualByComparingTo(new BigDecimal("38000.00"));

        // Месяцы без данных = 0
        assertThat(result.getMonthlyData().get(1).getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getMonthlyData().get(11).getAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        // yearlyAverage = (30000 + 45000 + 38000) / 3 = 37666.67
        assertThat(result.getYearlyAverage()).isEqualByComparingTo(new BigDecimal("37666.67"));

        // yearlyMax = 45000
        assertThat(result.getYearlyMax()).isEqualByComparingTo(new BigDecimal("45000.00"));

        // currentValue = последний ненулевой (Август = 38000)
        assertThat(result.getCurrentValue()).isEqualByComparingTo(new BigDecimal("38000.00"));

        // previousValue = предпоследний ненулевой (Апрель = 45000)
        assertThat(result.getPreviousValue()).isEqualByComparingTo(new BigDecimal("45000.00"));

        // changePercent = (38000 - 45000) / 45000 * 100 = -15.6%
        assertThat(result.getChangePercent()).isEqualTo("-15.6%");

        verify(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year);
    }

    @Test
    @DisplayName("Должен вернуть нулевые показатели при отсутствии данных за год")
    void shouldReturnZeroValuesWhenNoDataExists() {
        int year = 2025;
        when(expenseRepository.findMonthlyExpenseByUserIdAndYear(userId, year)).thenReturn(Collections.emptyList());

        MetricResponseDto result = expenseMetricService.getExpenseMetric(userId, year);

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
    @DisplayName("Должен корректно обработать один месяц с данными")
    void shouldHandleSingleMonthWithData() {
        int year = 2025;
        List<Object[]> dbData = List.<Object[]>of(
                new Object[]{7, new BigDecimal("55000.00")}
        );
        when(expenseRepository.findMonthlyExpenseByUserIdAndYear(userId, year)).thenReturn(dbData);

        MetricResponseDto result = expenseMetricService.getExpenseMetric(userId, year);

        assertThat(result.getMonthlyData().get(6).getAmount()).isEqualByComparingTo(new BigDecimal("55000.00"));
        assertThat(result.getMonthlyData().get(6).getMonthName()).isEqualTo("Июл");

        assertThat(result.getYearlyAverage()).isEqualByComparingTo(new BigDecimal("55000.00"));
        assertThat(result.getYearlyMax()).isEqualByComparingTo(new BigDecimal("55000.00"));

        assertThat(result.getCurrentValue()).isEqualByComparingTo(new BigDecimal("55000.00"));
        assertThat(result.getPreviousValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getChangePercent()).isEqualTo("+0.0%");
    }

    @Test
    @DisplayName("Должен корректно вызывать findMonthlyExpenseByUserIdAndYear через репозиторий")
    void shouldCallRepositoryWithCorrectParams() {
        int year = 2025;
        when(expenseRepository.findMonthlyExpenseByUserIdAndYear(userId, year)).thenReturn(Collections.emptyList());

        expenseMetricService.getExpenseMetric(userId, year);

        verify(expenseRepository).findMonthlyExpenseByUserIdAndYear(userId, year);
    }

    @Test
    @DisplayName("getMetricName должен возвращать 'расходов'")
    void shouldReturnCorrectMetricName() {
        assertThat(expenseMetricService.getMetricName()).isEqualTo("расходов");
    }

    @Test
    @DisplayName("Должен вернуть положительный changePercent при росте расходов")
    void shouldReturnPositiveChangePercentWhenExpenseIncreased() {
        int year = 2025;
        List<Object[]> dbData = List.of(
                new Object[]{2, new BigDecimal("40000")},
                new Object[]{5, new BigDecimal("60000")}
        );
        when(expenseRepository.findMonthlyExpenseByUserIdAndYear(userId, year)).thenReturn(dbData);

        MetricResponseDto result = expenseMetricService.getExpenseMetric(userId, year);

        // (60000 - 40000) / 40000 * 100 = +50.0%
        assertThat(result.getChangePercent()).isEqualTo("+50.0%");
    }
}
