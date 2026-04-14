package pyc.lopatuxin.budget.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.budget.dto.common.PeriodDto;
import pyc.lopatuxin.budget.dto.common.TrendsDto;
import pyc.lopatuxin.budget.dto.response.BudgetSummaryResponseDto;
import pyc.lopatuxin.budget.dto.response.CategorySummaryDto;
import pyc.lopatuxin.budget.entity.Category;
import pyc.lopatuxin.budget.repository.CategoryRepository;
import pyc.lopatuxin.budget.repository.ExpenseRepository;
import pyc.lopatuxin.budget.service.PeriodAggregateService.PeriodAggregates;
import pyc.lopatuxin.budget.util.TrendFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for building an aggregated budget summary for the budget page.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BudgetSummaryService {

    private final PeriodAggregateService periodAggregateService;
    private final CategorySummaryBuilder categorySummaryBuilder;
    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;

    /**
     * Builds an aggregated budget summary for the given month and year.
     *
     * @param userId identifier of the user
     * @param month  month number (1-12)
     * @param year   year
     * @return budget summary DTO
     */
    public BudgetSummaryResponseDto getSummary(UUID userId, int month, int year) {
        log.debug("Начало формирования сводки бюджета для userId={}, period={}/{}", userId, month, year);

        PeriodAggregates current = periodAggregateService.buildPeriodAggregates(userId, month, year);

        BigDecimal personalInflation = calculatePersonalInflation(userId, month, year);

        TrendsDto trends = calculateTrends(userId, month, year, current, personalInflation);

        List<CategorySummaryDto> categories = buildAllCategorySummaries(userId, current.startDate(), current.endDate());

        log.debug("Сводка бюджета сформирована для userId={}, period={}/{}", userId, month, year);

        return BudgetSummaryResponseDto.builder()
                .period(PeriodDto.builder().month(month).year(year).build())
                .income(current.income())
                .expenses(current.expenses())
                .balance(current.balance())
                .personalInflation(personalInflation)
                .trends(trends)
                .categories(categories)
                .build();
    }

    /**
     * Calculates personal inflation as the percentage change in average monthly expenses
     * of the current year compared to the previous year.
     * The average is calculated by the actual number of months with data, not calendar months.
     */
    private BigDecimal calculatePersonalInflation(UUID userId, int month, int year) {
        List<Object[]> currentYearMonthly = expenseRepository
                .findMonthlyExpenseByUserIdAndYear(userId, year);
        List<Object[]> currentYearUpToMonth = currentYearMonthly.stream()
                .filter(row -> ((Number) row[0]).intValue() <= month)
                .toList();

        if (currentYearUpToMonth.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal currentYearAvg = calculateAverageFromMonthlyRows(currentYearUpToMonth);

        List<Object[]> previousYearMonthly = expenseRepository
                .findMonthlyExpenseByUserIdAndYear(userId, year - 1);

        if (previousYearMonthly.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal previousYearAvg = calculateAverageFromMonthlyRows(previousYearMonthly);

        return currentYearAvg.subtract(previousYearAvg)
                .divide(previousYearAvg, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateAverageFromMonthlyRows(List<Object[]> rows) {
        BigDecimal total = rows.stream()
                .map(row -> (BigDecimal) row[1])
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(rows.size()), 10, RoundingMode.HALF_UP);
    }

    /**
     * Calculates trends of indicators relative to the previous month.
     */
    private TrendsDto calculateTrends(UUID userId, int month, int year,
                                      PeriodAggregates current, BigDecimal personalInflation) {
        int prevMonth = (month == 1) ? 12 : month - 1;
        int prevYear = (month == 1) ? year - 1 : year;

        PeriodAggregates prev = periodAggregateService.buildPeriodAggregates(userId, prevMonth, prevYear);

        BigDecimal prevInflation = calculatePersonalInflation(userId, prevMonth, prevYear);

        return TrendsDto.builder()
                .income(TrendFormatter.formatTrend(current.income(), prev.income()))
                .expenses(TrendFormatter.formatTrend(current.expenses(), prev.expenses()))
                .balance(TrendFormatter.formatTrend(current.balance(), prev.balance()))
                .inflation(TrendFormatter.formatTrend(personalInflation, prevInflation))
                .build();
    }

    /**
     * Returns all categories sorted by expense amount descending.
     */
    private List<CategorySummaryDto> buildAllCategorySummaries(UUID userId, LocalDate startDate, LocalDate endDate) {
        List<Category> categories = categoryRepository.findByUserId(userId);

        Map<UUID, BigDecimal> expensesByCategory = expenseRepository
                .sumAmountByCategoryForUserAndDateBetween(userId, startDate, endDate)
                .stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> (BigDecimal) row[1]
                ));

        return categories.stream()
                .map(category -> categorySummaryBuilder.buildCategorySummary(category, expensesByCategory))
                .sorted(Comparator.comparing(CategorySummaryDto::getAmount).reversed())
                .toList();
    }
}
