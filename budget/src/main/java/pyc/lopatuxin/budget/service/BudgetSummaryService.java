package pyc.lopatuxin.budget.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.budget.dto.common.NormComparisonDto;
import pyc.lopatuxin.budget.dto.common.PeriodDto;
import pyc.lopatuxin.budget.dto.common.TrendsDto;
import pyc.lopatuxin.budget.dto.response.BudgetSummaryResponseDto;
import pyc.lopatuxin.budget.dto.response.CategorySummaryDto;
import pyc.lopatuxin.budget.entity.Category;
import pyc.lopatuxin.budget.entity.enums.NormStatus;
import pyc.lopatuxin.budget.repository.CategoryRepository;
import pyc.lopatuxin.budget.repository.ExpenseRepository;
import pyc.lopatuxin.budget.repository.IncomeRepository;
import pyc.lopatuxin.budget.service.NormCalculationService.MonthlyAggregateRow;
import pyc.lopatuxin.budget.service.PeriodAggregateService.PeriodAggregates;
import pyc.lopatuxin.budget.util.TrendFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
@Transactional(value = "budgetTransactionManager", readOnly = true)
public class BudgetSummaryService {

    private static final Comparator<CategorySummaryDto> CATEGORY_ORDER = Comparator
            .comparing(BudgetSummaryService::isNoHistory)
            .thenComparing(BudgetSummaryService::deviationForSort, Comparator.reverseOrder())
            .thenComparing(CategorySummaryDto::getAmount, Comparator.reverseOrder());

    private final PeriodAggregateService periodAggregateService;
    private final CategorySummaryBuilder categorySummaryBuilder;
    private final NormCalculationService normCalculationService;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
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

        // Cached by year so the current-period and previous-period inflation calculations
        // (which usually share a year) don't run the same yearly query twice.
        Map<Integer, List<Object[]>> monthlyExpensesByYear = new HashMap<>();

        PeriodAggregates current = periodAggregateService.buildPeriodAggregates(userId, month, year);

        BigDecimal personalInflation = calculatePersonalInflation(userId, month, year, monthlyExpensesByYear);

        TrendsDto trends = calculateTrends(userId, month, year, current, personalInflation, monthlyExpensesByYear);

        int dayOfMonth = resolveDayOfMonth(current.startDate());
        int daysInMonth = current.startDate().lengthOfMonth();

        NormsContext norms = buildNormsContext(userId, current.startDate(), dayOfMonth);
        NormComparisonDto expenseNorm = normCalculationService.calculateNorm(norms.expenseDataMonths(), current.expenses(), dayOfMonth);
        NormComparisonDto incomeNorm = normCalculationService.calculateNorm(norms.incomeDataMonths(), current.income(), dayOfMonth);

        List<CategorySummaryDto> categories = buildAllCategorySummaries(
                userId, current.startDate(), current.endDate(), norms, dayOfMonth);

        log.debug("Сводка бюджета сформирована для userId={}, period={}/{}", userId, month, year);

        return BudgetSummaryResponseDto.builder()
                .period(PeriodDto.builder().month(month).year(year).build())
                .income(current.income())
                .expenses(current.expenses())
                .balance(current.balance())
                .personalInflation(personalInflation)
                .trends(trends)
                .dayOfMonth(dayOfMonth)
                .daysInMonth(daysInMonth)
                .expenseNorm(expenseNorm)
                .incomeNorm(incomeNorm)
                .categories(categories)
                .build();
    }

    /**
     * Resolves the day of month the current period is compared up to: today's day number for the
     * current calendar month, the full month length for a past month, or 0 for a future month.
     */
    private int resolveDayOfMonth(LocalDate requestedMonthStart) {
        LocalDate currentMonthStart = LocalDate.now().withDayOfMonth(1);
        if (requestedMonthStart.isEqual(currentMonthStart)) {
            return LocalDate.now().getDayOfMonth();
        }
        if (requestedMonthStart.isBefore(currentMonthStart)) {
            return requestedMonthStart.lengthOfMonth();
        }
        return 0;
    }

    /**
     * Calculates personal inflation as the percentage change in average monthly expenses
     * of the current year compared to the previous year.
     * The average is calculated by the actual number of months with data, not calendar months.
     */
    private BigDecimal calculatePersonalInflation(UUID userId, int month, int year,
                                                  Map<Integer, List<Object[]>> monthlyExpensesByYear) {
        List<Object[]> currentYearMonthly = monthlyExpensesForYear(userId, year, monthlyExpensesByYear);
        List<Object[]> currentYearUpToMonth = currentYearMonthly.stream()
                .filter(row -> ((Number) row[0]).intValue() <= month)
                .toList();

        if (currentYearUpToMonth.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal currentYearAvg = calculateAverageFromMonthlyRows(currentYearUpToMonth);

        List<Object[]> previousYearMonthly = monthlyExpensesForYear(userId, year - 1, monthlyExpensesByYear);

        if (previousYearMonthly.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal previousYearAvg = calculateAverageFromMonthlyRows(previousYearMonthly);

        return currentYearAvg.subtract(previousYearAvg)
                .divide(previousYearAvg, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * Returns non-transfer monthly expense totals for the given year, fetching them at most
     * once per {@code getSummary} call (current and previous period usually share a year).
     */
    private List<Object[]> monthlyExpensesForYear(UUID userId, int year,
                                                  Map<Integer, List<Object[]>> cache) {
        return cache.computeIfAbsent(year,
                y -> expenseRepository.findMonthlyNonTransferExpenseByUserIdAndYear(userId, y));
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
    private TrendsDto calculateTrends(UUID userId, int month, int year, PeriodAggregates current,
                                      BigDecimal personalInflation, Map<Integer, List<Object[]>> monthlyExpensesByYear) {
        int prevMonth = (month == 1) ? 12 : month - 1;
        int prevYear = (month == 1) ? year - 1 : year;

        PeriodAggregates prev = periodAggregateService.buildPeriodAggregates(userId, prevMonth, prevYear);

        BigDecimal prevInflation = calculatePersonalInflation(userId, prevMonth, prevYear, monthlyExpensesByYear);

        return TrendsDto.builder()
                .income(TrendFormatter.formatTrend(current.income(), prev.income()))
                .expenses(TrendFormatter.formatTrend(current.expenses(), prev.expenses()))
                .balance(TrendFormatter.formatTrend(current.balance(), prev.balance()))
                .inflation(TrendFormatter.formatTrend(personalInflation, prevInflation))
                .build();
    }

    /**
     * Loads the 12-month history window preceding the requested month and pre-aggregates it
     * for {@link NormCalculationService}: overall expense and income months-with-data, and the
     * same expense data broken down per category. The per-category rows carry the overall expense
     * series' daily-tracking flag for their month, not a per-category one — the point where
     * day-by-day tracking started is determined from expenses as a whole, since a single category
     * can legitimately have just one purchase in a month.
     */
    private NormsContext buildNormsContext(UUID userId, LocalDate requestedMonthStart, int dayOfMonth) {
        LocalDate windowStart = requestedMonthStart.minusMonths(12);
        LocalDate windowEnd = requestedMonthStart.minusDays(1);

        List<MonthlyAggregateRow> expenseDataMonths = toMonthlyRows(
                expenseRepository.findWindowedNonTransferExpenseStats(userId, windowStart, windowEnd, dayOfMonth));
        List<MonthlyAggregateRow> incomeDataMonths = toMonthlyRows(
                incomeRepository.findWindowedNonTransferIncomeStats(userId, windowStart, windowEnd, dayOfMonth));

        Map<YearMonth, Boolean> expenseDailyGranularityByMonth = expenseDataMonths.stream()
                .collect(Collectors.toMap(
                        row -> YearMonth.of(row.year(), row.month()),
                        MonthlyAggregateRow::dailyGranularity));

        List<YearMonth> dataMonths = expenseDataMonths.stream()
                .map(row -> YearMonth.of(row.year(), row.month()))
                .toList();
        Map<YearMonth, Map<UUID, MonthlyAggregateRow>> categoryRowsByMonth = groupCategoryRowsByMonth(
                expenseRepository.findWindowedNonTransferExpenseStatsByCategory(userId, windowStart, windowEnd, dayOfMonth),
                expenseDailyGranularityByMonth);

        return new NormsContext(expenseDataMonths, incomeDataMonths, dataMonths, expenseDailyGranularityByMonth, categoryRowsByMonth);
    }

    private List<MonthlyAggregateRow> toMonthlyRows(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new MonthlyAggregateRow(
                        ((Number) row[0]).intValue(),
                        ((Number) row[1]).intValue(),
                        (BigDecimal) row[2],
                        (BigDecimal) row[3],
                        ((Number) row[4]).intValue() > 1))
                .toList();
    }

    private Map<YearMonth, Map<UUID, MonthlyAggregateRow>> groupCategoryRowsByMonth(
            List<Object[]> rows, Map<YearMonth, Boolean> expenseDailyGranularityByMonth) {
        Map<YearMonth, Map<UUID, MonthlyAggregateRow>> result = new HashMap<>();
        for (Object[] row : rows) {
            YearMonth yearMonth = YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue());
            UUID categoryId = (UUID) row[2];
            boolean dailyGranularity = expenseDailyGranularityByMonth.getOrDefault(yearMonth, false);
            MonthlyAggregateRow monthRow = new MonthlyAggregateRow(
                    yearMonth.getYear(), yearMonth.getMonthValue(), (BigDecimal) row[3], (BigDecimal) row[4], dailyGranularity);
            result.computeIfAbsent(yearMonth, key -> new HashMap<>()).put(categoryId, monthRow);
        }
        return result;
    }

    /**
     * Returns all categories with their norm comparison, sorted by deviation from the norm
     * descending, with {@link NormStatus#NO_HISTORY} categories last (sorted by amount descending).
     */
    private List<CategorySummaryDto> buildAllCategorySummaries(UUID userId, LocalDate startDate, LocalDate endDate,
                                                               NormsContext norms, int dayOfMonth) {
        List<Category> categories = categoryRepository.findUserCategoriesByUserId(userId);

        Map<UUID, BigDecimal> expensesByCategory = expenseRepository
                .sumNonTransferAmountByCategoryForUserAndDateBetween(userId, startDate, endDate)
                .stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> (BigDecimal) row[1]
                ));

        List<CategorySummaryDto> summaries = categories.stream()
                .map(category -> buildCategorySummaryWithNorm(category, expensesByCategory, norms, dayOfMonth))
                .collect(Collectors.toCollection(ArrayList::new));

        summaries.sort(CATEGORY_ORDER);
        return summaries;
    }

    private CategorySummaryDto buildCategorySummaryWithNorm(Category category, Map<UUID, BigDecimal> expensesByCategory,
                                                             NormsContext norms, int dayOfMonth) {
        CategorySummaryDto dto = categorySummaryBuilder.buildCategorySummary(category, expensesByCategory);

        List<MonthlyAggregateRow> categoryDataMonths = norms.dataMonths().stream()
                .map(yearMonth -> {
                    boolean dailyGranularity = norms.expenseDailyGranularityByMonth().getOrDefault(yearMonth, false);
                    return norms.categoryRowsByMonth().getOrDefault(yearMonth, Map.of())
                            .getOrDefault(category.getId(), new MonthlyAggregateRow(
                                    yearMonth.getYear(), yearMonth.getMonthValue(), BigDecimal.ZERO, BigDecimal.ZERO, dailyGranularity));
                })
                .toList();

        dto.setNorm(normCalculationService.calculateNorm(categoryDataMonths, dto.getAmount(), dayOfMonth));
        return dto;
    }

    private static boolean isNoHistory(CategorySummaryDto dto) {
        return dto.getNorm() == null || dto.getNorm().getStatus() == NormStatus.NO_HISTORY;
    }

    private static BigDecimal deviationForSort(CategorySummaryDto dto) {
        return isNoHistory(dto) ? BigDecimal.ZERO : dto.getNorm().getDeviationPercent();
    }

    /**
     * Pre-aggregated 12-month history window used to compute the expense, income and per-category norms.
     */
    private record NormsContext(
            List<MonthlyAggregateRow> expenseDataMonths,
            List<MonthlyAggregateRow> incomeDataMonths,
            List<YearMonth> dataMonths,
            Map<YearMonth, Boolean> expenseDailyGranularityByMonth,
            Map<YearMonth, Map<UUID, MonthlyAggregateRow>> categoryRowsByMonth) {
    }
}
