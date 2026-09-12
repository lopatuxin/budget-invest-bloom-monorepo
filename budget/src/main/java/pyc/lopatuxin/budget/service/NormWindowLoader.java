package pyc.lopatuxin.budget.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pyc.lopatuxin.budget.repository.ExpenseRepository;
import pyc.lopatuxin.budget.repository.IncomeRepository;
import pyc.lopatuxin.budget.service.NormCalculationService.MonthlyAggregateRow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Loads and pre-aggregates the 12-month history window preceding a requested month, used to
 * compute personal norms ("usual by this day") for overall expenses, overall income and
 * individual categories. Shared by {@link BudgetSummaryService} (all categories at once) and
 * the category page service (one category), so both compute the exact same norm for the same
 * category and month.
 */
@Service
@RequiredArgsConstructor
public class NormWindowLoader {

    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;

    /**
     * Loads the 12-month history window preceding the requested month (M-12..M-1) and
     * pre-aggregates it for {@link NormCalculationService}: overall expense and income
     * months-with-data, and the same expense data broken down per category. The per-category
     * rows carry the overall expense series' daily-tracking flag for their month, not a
     * per-category one — the point where day-by-day tracking started is determined from expenses
     * as a whole, since a single category can legitimately have just one purchase in a month.
     *
     * @param userId              identifier of the user
     * @param requestedMonthStart first day of the requested month M
     * @param dayOfMonth          day of month the window is compared up to
     * @return pre-aggregated norms context for the window
     */
    public NormsContext loadForMonth(UUID userId, LocalDate requestedMonthStart, int dayOfMonth) {
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

    /**
     * Resolves the day of month the current period is compared up to: today's day number for the
     * current calendar month, the full month length for a past month, or 0 for a future month.
     * Shared by {@link BudgetSummaryService} and the category page service so both compute the
     * exact same norm window for the same month. Static and side-effect-free — both {@code today}
     * and {@code requestedMonthStart} are explicit parameters, so callers stay testable without
     * having to stub this on a mocked {@link NormWindowLoader}.
     *
     * @param requestedMonthStart first day of the requested month
     * @param today               the current date, passed explicitly so callers stay testable
     * @return day of month to compare up to
     */
    public static int resolveDayOfMonth(LocalDate requestedMonthStart, LocalDate today) {
        LocalDate currentMonthStart = today.withDayOfMonth(1);
        if (requestedMonthStart.isEqual(currentMonthStart)) {
            return today.getDayOfMonth();
        }
        if (requestedMonthStart.isBefore(currentMonthStart)) {
            return requestedMonthStart.lengthOfMonth();
        }
        return 0;
    }

    /**
     * Zero-filled per-category rows for every data month in {@code norms}, for {@code categoryId}:
     * a real row for months where the category has records, a zero row (carrying the month's
     * overall daily-tracking flag) where it does not.
     *
     * @param norms      pre-aggregated norms context from {@link #loadForMonth}
     * @param categoryId identifier of the category
     * @return one row per data month, in the same order as {@code norms.dataMonths()}
     */
    public List<MonthlyAggregateRow> categoryDataMonths(NormsContext norms, UUID categoryId) {
        return norms.dataMonths().stream()
                .map(yearMonth -> {
                    boolean dailyGranularity = norms.expenseDailyGranularityByMonth().getOrDefault(yearMonth, false);
                    return norms.categoryRowsByMonth().getOrDefault(yearMonth, Map.of())
                            .getOrDefault(categoryId, new MonthlyAggregateRow(
                                    yearMonth.getYear(), yearMonth.getMonthValue(), BigDecimal.ZERO, BigDecimal.ZERO, dailyGranularity));
                })
                .toList();
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
     * Pre-aggregated 12-month history window used to compute the expense, income and per-category norms.
     */
    public record NormsContext(
            List<MonthlyAggregateRow> expenseDataMonths,
            List<MonthlyAggregateRow> incomeDataMonths,
            List<YearMonth> dataMonths,
            Map<YearMonth, Boolean> expenseDailyGranularityByMonth,
            Map<YearMonth, Map<UUID, MonthlyAggregateRow>> categoryRowsByMonth) {
    }
}
