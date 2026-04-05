package pyc.lopatuxin.budget.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.budget.dto.response.MetricResponseDto;
import pyc.lopatuxin.budget.dto.response.MonthlyMetricDto;
import pyc.lopatuxin.budget.repository.ExpenseRepository;
import pyc.lopatuxin.budget.repository.IncomeRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Сервис для формирования детальной метрики баланса пользователя за год.
 * Баланс рассчитывается как разница между доходами и расходами за каждый месяц.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BalanceMetricService extends AbstractMetricService {

    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;

    /**
     * Формирует детальную метрику баланса за указанный год.
     *
     * @param userId идентификатор пользователя
     * @param year   календарный год
     * @return объект с помесячной разбивкой и агрегированными показателями
     */
    public MetricResponseDto getBalanceMetric(UUID userId, int year) {
        return getMetric(userId, year);
    }

    @Override
    protected List<Object[]> findMonthlyData(UUID userId, int year) {
        Map<Integer, BigDecimal> incomeByMonth = buildMonthlyMap(
                incomeRepository.findMonthlyIncomeByUserIdAndYear(userId, year)
        );
        Map<Integer, BigDecimal> expenseByMonth = buildMonthlyMap(
                expenseRepository.findMonthlyExpenseByUserIdAndYear(userId, year)
        );

        TreeSet<Integer> allMonths = new TreeSet<>();
        allMonths.addAll(incomeByMonth.keySet());
        allMonths.addAll(expenseByMonth.keySet());

        List<Object[]> result = new ArrayList<>(allMonths.size());
        for (Integer month : allMonths) {
            BigDecimal income = incomeByMonth.getOrDefault(month, BigDecimal.ZERO);
            BigDecimal expense = expenseByMonth.getOrDefault(month, BigDecimal.ZERO);
            BigDecimal balance = income.subtract(expense);
            result.add(new Object[]{month, balance});
        }
        return result;
    }

    @Override
    protected String getMetricName() {
        return "баланса";
    }

    @Override
    protected List<BigDecimal> extractNonZeroAmounts(List<MonthlyMetricDto> monthlyData) {
        return monthlyData.stream()
                .map(MonthlyMetricDto::getAmount)
                .filter(amount -> amount.compareTo(BigDecimal.ZERO) != 0)
                .toList();
    }
}
