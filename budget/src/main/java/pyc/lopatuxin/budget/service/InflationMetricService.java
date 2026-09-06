package pyc.lopatuxin.budget.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.budget.dto.response.CategoryInflationDto;
import pyc.lopatuxin.budget.dto.response.MetricResponseDto;
import pyc.lopatuxin.budget.dto.response.MonthlyMetricDto;
import pyc.lopatuxin.budget.repository.ExpenseRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Сервис для формирования детальной метрики личной инфляции пользователя за год.
 * Инфляция рассчитывается как процентное изменение среднемесячных расходов текущего года
 * относительно среднемесячных расходов предыдущего года.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(value = "budgetTransactionManager", readOnly = true)
public class InflationMetricService extends AbstractMetricService {

    private final ExpenseRepository expenseRepository;

    /**
     * Формирует детальную метрику личной инфляции за указанный год.
     *
     * @param userId идентификатор пользователя
     * @param year   календарный год
     * @return объект с помесячной разбивкой и агрегированными показателями
     */
    public MetricResponseDto getInflationMetric(UUID userId, int year) {
        MetricResponseDto base = getMetric(userId, year);
        List<CategoryInflationDto> breakdown = buildCategoryBreakdown(userId, year);
        return base.toBuilder().categoryBreakdown(breakdown).build();
    }

    private record CategoryCalc(UUID categoryId, String name, String emoji,
                                BigDecimal avgCurrent, BigDecimal avgPrevious, BigDecimal changePercent) {}

    /**
     * Builds the per-category inflation breakdown so it reconciles with the overall figure:
     * every category's average is divided by the same number of months (the count of months
     * with any non-transfer expense that year) used by {@link #findMonthlyData}, instead of
     * each category's own count of active months.
     */
    private List<CategoryInflationDto> buildCategoryBreakdown(UUID userId, int year) {
        long currentMonths = expenseRepository.findMonthlyNonTransferExpenseByUserIdAndYear(userId, year).size();
        long previousMonths = expenseRepository.findMonthlyNonTransferExpenseByUserIdAndYear(userId, year - 1).size();
        if (currentMonths == 0 || previousMonths == 0) {
            return List.of();
        }

        List<Object[]> currentStats = expenseRepository.findNonTransferCategoryStatsByUserIdAndYear(userId, year);
        List<Object[]> previousStats = expenseRepository.findNonTransferCategoryStatsByUserIdAndYear(userId, year - 1);
        Map<UUID, BigDecimal> previousTotals = toTotalsByCategory(previousStats);

        List<CategoryCalc> calculated = currentStats.stream()
                .map(row -> toCategoryCalc(row, previousTotals, currentMonths, previousMonths))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        BigDecimal totalAvgCurrent = calculated.stream()
                .map(CategoryCalc::avgCurrent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalAvgCurrent.compareTo(BigDecimal.ZERO) == 0) {
            return List.of();
        }

        return calculated.stream()
                .map(calc -> toDto(calc, totalAvgCurrent))
                .sorted((a, b) -> b.getContribution().abs().compareTo(a.getContribution().abs()))
                .toList();
    }

    private Map<UUID, BigDecimal> toTotalsByCategory(List<Object[]> rows) {
        Map<UUID, BigDecimal> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((UUID) row[0], (BigDecimal) row[3]);
        }
        return map;
    }

    private Optional<CategoryCalc> toCategoryCalc(Object[] row, Map<UUID, BigDecimal> previousTotals,
                                                  long currentMonths, long previousMonths) {
        UUID categoryId = (UUID) row[0];
        BigDecimal previousTotal = previousTotals.get(categoryId);
        if (previousTotal == null || previousTotal.compareTo(BigDecimal.ZERO) == 0) {
            return Optional.empty();
        }

        String name = (String) row[1];
        String emoji = (String) row[2];
        BigDecimal currentTotal = (BigDecimal) row[3];

        BigDecimal avgCurrent = average(currentTotal, currentMonths);
        BigDecimal avgPrevious = average(previousTotal, previousMonths);
        BigDecimal changePercent = calculatePercentChange(avgCurrent, avgPrevious);

        return Optional.of(new CategoryCalc(categoryId, name, emoji, avgCurrent, avgPrevious, changePercent));
    }

    private BigDecimal average(BigDecimal total, long months) {
        return total.divide(BigDecimal.valueOf(months), 10, RoundingMode.HALF_UP);
    }

    private CategoryInflationDto toDto(CategoryCalc calc, BigDecimal totalAvgCurrent) {
        BigDecimal rawWeight = calc.avgCurrent()
                .divide(totalAvgCurrent, 10, RoundingMode.HALF_UP);
        BigDecimal weightPercent = rawWeight
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
        BigDecimal contribution = rawWeight
                .multiply(calc.changePercent())
                .setScale(1, RoundingMode.HALF_UP);
        return CategoryInflationDto.builder()
                .categoryId(calc.categoryId())
                .categoryName(calc.name())
                .emoji(calc.emoji())
                .avgCurrent(calc.avgCurrent().setScale(2, RoundingMode.HALF_UP))
                .avgPrevious(calc.avgPrevious().setScale(2, RoundingMode.HALF_UP))
                .changePercent(calc.changePercent())
                .weightPercent(weightPercent)
                .contribution(contribution)
                .build();
    }

    @Override
    protected List<Object[]> findMonthlyData(UUID userId, int year) {
        BigDecimal previousYearAvg = calculateAverage(
                expenseRepository.findMonthlyNonTransferExpenseByUserIdAndYear(userId, year - 1));

        if (previousYearAvg.compareTo(BigDecimal.ZERO) == 0) {
            return List.of();
        }

        Map<Integer, BigDecimal> expenseByMonth = buildMonthlyMap(
                expenseRepository.findMonthlyNonTransferExpenseByUserIdAndYear(userId, year)
        );

        List<Object[]> result = new ArrayList<>();
        BigDecimal cumulativeSum = BigDecimal.ZERO;
        int monthsWithData = 0;

        for (int month = 1; month <= 12; month++) {
            BigDecimal monthAmount = expenseByMonth.getOrDefault(month, BigDecimal.ZERO);
            if (monthAmount.compareTo(BigDecimal.ZERO) > 0) {
                monthsWithData++;
            }
            cumulativeSum = cumulativeSum.add(monthAmount);

            if (cumulativeSum.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            BigDecimal currentAvg = cumulativeSum.divide(
                    BigDecimal.valueOf(monthsWithData), 10, RoundingMode.HALF_UP);
            result.add(new Object[]{month, calculatePercentChange(currentAvg, previousYearAvg)});
        }

        return result;
    }

    /**
     * Вычисляет среднее значение из помесячных данных.
     *
     * @param monthlyData список пар [номер_месяца, сумма]
     * @return среднее по месяцам с данными, или ZERO если данных нет
     */
    private BigDecimal calculateAverage(List<Object[]> monthlyData) {
        if (monthlyData.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = monthlyData.stream()
                .map(row -> (BigDecimal) row[1])
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(monthlyData.size()), 10, RoundingMode.HALF_UP);
    }

    /**
     * Вычисляет процентное изменение текущего значения относительно базового.
     *
     * @param current  текущее значение
     * @param baseline базовое значение (не должно быть нулём)
     * @return процент изменения с точностью до одного знака после запятой
     */
    private BigDecimal calculatePercentChange(BigDecimal current, BigDecimal baseline) {
        return current.subtract(baseline)
                .divide(baseline, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    @Override
    protected List<BigDecimal> extractNonZeroAmounts(List<MonthlyMetricDto> monthlyData) {
        return monthlyData.stream()
                .map(MonthlyMetricDto::getAmount)
                .filter(amount -> amount.compareTo(BigDecimal.ZERO) != 0)
                .toList();
    }

    @Override
    protected String getMetricName() {
        return "инфляции";
    }
}
