package pyc.lopatuxin.budget.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.budget.dto.response.IncomeMetricResponseDto;
import pyc.lopatuxin.budget.dto.response.MonthlyIncomeDto;
import pyc.lopatuxin.budget.entity.enums.Month;
import pyc.lopatuxin.budget.repository.IncomeRepository;
import pyc.lopatuxin.budget.util.TrendFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Сервис для формирования детальной метрики доходов пользователя за год.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IncomeMetricService {

    private static final Month[] MONTHS = Month.values();

    private final IncomeRepository incomeRepository;

    /**
     * Формирует детальную метрику доходов за указанный год.
     *
     * @param userId идентификатор пользователя
     * @param year   календарный год
     * @return объект с помесячной разбивкой и агрегированными показателями
     */
    public IncomeMetricResponseDto getIncomeMetric(UUID userId, int year) {
        log.debug("Начало формирования метрики доходов для userId={}, year={}", userId, year);

        Map<Integer, BigDecimal> incomeByMonth = getMonthlyIncomeMap(userId, year);
        List<MonthlyIncomeDto> monthlyData = buildMonthlyData(incomeByMonth);
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

        log.debug("Метрика доходов сформирована для userId={}, year={}", userId, year);

        return IncomeMetricResponseDto.builder()
                .year(year)
                .currentValue(currentValue)
                .previousValue(previousValue)
                .changePercent(changePercent)
                .yearlyAverage(yearlyAverage)
                .yearlyMax(yearlyMax)
                .monthlyData(monthlyData)
                .build();
    }

    /**
     * Загружает помесячные суммы доходов из БД и собирает в Map(номер месяца → сумма).
     */
    private Map<Integer, BigDecimal> getMonthlyIncomeMap(UUID userId, int year) {
        return incomeRepository
                .findMonthlyIncomeByUserIdAndYear(userId, year)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Integer) row[0],
                        row -> (BigDecimal) row[1]
                ));
    }

    /**
     * Собирает список из 12 записей помесячных доходов.
     * Для месяцев без данных amount = 0.
     */
    private List<MonthlyIncomeDto> buildMonthlyData(Map<Integer, BigDecimal> incomeByMonth) {
        List<MonthlyIncomeDto> result = new ArrayList<>(MONTHS.length);
        for (Month month : MONTHS) {
            result.add(MonthlyIncomeDto.builder()
                    .month(month.getNumber())
                    .monthName(month.getShortName())
                    .amount(incomeByMonth.getOrDefault(month.getNumber(), BigDecimal.ZERO))
                    .build());
        }
        return result;
    }

    /**
     * Возвращает суммы доходов только за месяцы, в которых были реальные поступления.
     * Месяцы без данных (amount = 0) исключаются, чтобы не искажать среднее и максимум.
     */
    private List<BigDecimal> extractNonZeroAmounts(List<MonthlyIncomeDto> monthlyData) {
        return monthlyData.stream()
                .map(MonthlyIncomeDto::getAmount)
                .filter(amount -> amount.compareTo(BigDecimal.ZERO) > 0)
                .toList();
    }

    /**
     * Суммирует список сумм.
     */
    private BigDecimal sumAmounts(List<BigDecimal> amounts) {
        return amounts.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

}
