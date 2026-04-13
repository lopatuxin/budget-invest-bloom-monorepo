package pyc.lopatuxin.budget.service;

import lombok.extern.slf4j.Slf4j;
import pyc.lopatuxin.budget.dto.response.MetricResponseDto;
import pyc.lopatuxin.budget.dto.response.MonthlyMetricDto;
import pyc.lopatuxin.budget.entity.enums.Month;
import pyc.lopatuxin.budget.util.TrendFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Абстрактный сервис для формирования детальной метрики пользователя за год.
 * Реализует паттерн Template Method: общая логика расчёта метрик вынесена сюда,
 * а конкретные сервисы предоставляют источник данных и название метрики.
 */
@Slf4j
public abstract class AbstractMetricService {

    private static final Month[] MONTHS = Month.values();

    /**
     * Загружает помесячные данные из БД.
     *
     * @param userId идентификатор пользователя
     * @param year   календарный год
     * @return список массивов [номер_месяца (Integer), сумма (BigDecimal)]
     */
    protected abstract List<Object[]> findMonthlyData(UUID userId, int year);

    /**
     * Возвращает название метрики для логирования (например, "доходов", "расходов").
     */
    protected abstract String getMetricName();

    /**
     * Формирует детальную метрику за указанный год.
     *
     * @param userId идентификатор пользователя
     * @param year   календарный год
     * @return объект с помесячной разбивкой и агрегированными показателями
     */
    public MetricResponseDto getMetric(UUID userId, int year) {
        log.debug("Начало формирования метрики {} для userId={}, year={}", getMetricName(), userId, year);

        Map<Integer, BigDecimal> dataByMonth = buildMonthlyMap(findMonthlyData(userId, year));
        List<MonthlyMetricDto> monthlyData = buildMonthlyData(dataByMonth, year);
        List<BigDecimal> nonZeroAmounts = extractNonZeroAmounts(monthlyData);

        BigDecimal yearlyAverage = BigDecimal.ZERO;
        BigDecimal yearlyMax = BigDecimal.ZERO;
        if (!nonZeroAmounts.isEmpty()) {
            BigDecimal total = sumAmounts(nonZeroAmounts);
            yearlyAverage = total.divide(BigDecimal.valueOf(nonZeroAmounts.size()), 2, RoundingMode.HALF_UP);
            yearlyMax = nonZeroAmounts.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        }

        BigDecimal currentValue = BigDecimal.ZERO;
        BigDecimal previousValue = BigDecimal.ZERO;
        if (!nonZeroAmounts.isEmpty()) {
            currentValue = nonZeroAmounts.getLast();
        }
        if (nonZeroAmounts.size() >= 2) {
            previousValue = nonZeroAmounts.get(nonZeroAmounts.size() - 2);
        }

        String changePercent = TrendFormatter.formatTrend(currentValue, previousValue);

        log.debug("Метрика {} сформирована для userId={}, year={}", getMetricName(), userId, year);

        return MetricResponseDto.builder()
                .year(year)
                .currentValue(currentValue)
                .previousValue(previousValue)
                .changePercent(changePercent)
                .yearlyAverage(yearlyAverage)
                .yearlyMax(yearlyMax)
                .monthlyData(monthlyData)
                .build();
    }

    protected Map<Integer, BigDecimal> buildMonthlyMap(List<Object[]> rawData) {
        return rawData.stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).intValue(),
                        row -> (BigDecimal) row[1]
                ));
    }

    private List<MonthlyMetricDto> buildMonthlyData(Map<Integer, BigDecimal> dataByMonth, int year) {
        int currentYear = java.time.LocalDate.now().getYear();
        int maxMonth = (year == currentYear) ? java.time.LocalDate.now().getMonthValue() : MONTHS.length;

        List<MonthlyMetricDto> result = new ArrayList<>(maxMonth);
        for (Month month : MONTHS) {
            if (month.getNumber() > maxMonth) break;
            result.add(MonthlyMetricDto.builder()
                    .month(month.getNumber())
                    .monthName(month.getShortName())
                    .amount(dataByMonth.getOrDefault(month.getNumber(), BigDecimal.ZERO))
                    .build());
        }
        return result;
    }

    protected List<BigDecimal> extractNonZeroAmounts(List<MonthlyMetricDto> monthlyData) {
        return monthlyData.stream()
                .map(MonthlyMetricDto::getAmount)
                .filter(amount -> amount.compareTo(BigDecimal.ZERO) > 0)
                .toList();
    }

    private BigDecimal sumAmounts(List<BigDecimal> amounts) {
        return amounts.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
