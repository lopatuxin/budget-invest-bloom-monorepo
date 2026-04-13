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
import java.util.UUID;

/**
 * Сервис для формирования детальной метрики личной инфляции пользователя за год.
 * Инфляция рассчитывается как процентное изменение среднемесячных расходов текущего года
 * относительно среднемесячных расходов предыдущего года.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
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

    private List<CategoryInflationDto> buildCategoryBreakdown(UUID userId, int year) {
        List<Object[]> currentStats = expenseRepository.findCategoryStatsByUserIdAndYear(userId, year);
        List<Object[]> previousStats = expenseRepository.findCategoryStatsByUserIdAndYear(userId, year - 1);

        Map<UUID, Object[]> previousMap = new HashMap<>();
        for (Object[] row : previousStats) {
            previousMap.put((UUID) row[0], row);
        }

        record CategoryCalc(UUID categoryId, String name, String emoji,
                            BigDecimal avgCurrent, BigDecimal avgPrevious, BigDecimal changePercent) {}

        List<CategoryCalc> calculated = new ArrayList<>();
        for (Object[] row : currentStats) {
            UUID categoryId = (UUID) row[0];
            String name = (String) row[1];
            String emoji = (String) row[2];
            long monthCount = (Long) row[3];
            BigDecimal totalAmount = (BigDecimal) row[4];

            if (monthCount == 0) continue;
            BigDecimal avgCurrent = totalAmount.divide(BigDecimal.valueOf(monthCount), 10, RoundingMode.HALF_UP);

            Object[] prevRow = previousMap.get(categoryId);
            if (prevRow == null) {
                continue;
            }
            long prevMonthCount = (Long) prevRow[3];
            if (prevMonthCount == 0) continue;
            BigDecimal prevTotalAmount = (BigDecimal) prevRow[4];
            BigDecimal avgPrevious = prevTotalAmount.divide(BigDecimal.valueOf(prevMonthCount), 10, RoundingMode.HALF_UP);

            if (avgPrevious.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            BigDecimal changePercent = calculatePercentChange(avgCurrent, avgPrevious);
            calculated.add(new CategoryCalc(categoryId, name, emoji, avgCurrent, avgPrevious, changePercent));
        }

        BigDecimal totalAvgCurrent = calculated.stream()
                .map(CategoryCalc::avgCurrent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalAvgCurrent.compareTo(BigDecimal.ZERO) == 0) {
            return List.of();
        }

        return calculated.stream()
                .map(c -> {
                    BigDecimal rawWeight = c.avgCurrent()
                            .divide(totalAvgCurrent, 10, RoundingMode.HALF_UP);
                    BigDecimal weightPercent = rawWeight
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(1, RoundingMode.HALF_UP);
                    BigDecimal contribution = rawWeight
                            .multiply(c.changePercent())
                            .setScale(1, RoundingMode.HALF_UP);
                    return CategoryInflationDto.builder()
                            .categoryId(c.categoryId())
                            .categoryName(c.name())
                            .emoji(c.emoji())
                            .avgCurrent(c.avgCurrent().setScale(2, RoundingMode.HALF_UP))
                            .avgPrevious(c.avgPrevious().setScale(2, RoundingMode.HALF_UP))
                            .changePercent(c.changePercent())
                            .weightPercent(weightPercent)
                            .contribution(contribution)
                            .build();
                })
                .sorted((a, b) -> b.getContribution().abs().compareTo(a.getContribution().abs()))
                .toList();
    }

    @Override
    protected List<Object[]> findMonthlyData(UUID userId, int year) {
        BigDecimal previousYearAvg = calculateAverage(
                expenseRepository.findMonthlyExpenseByUserIdAndYear(userId, year - 1));

        if (previousYearAvg.compareTo(BigDecimal.ZERO) == 0) {
            return List.of();
        }

        Map<Integer, BigDecimal> expenseByMonth = buildMonthlyMap(
                expenseRepository.findMonthlyExpenseByUserIdAndYear(userId, year)
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
