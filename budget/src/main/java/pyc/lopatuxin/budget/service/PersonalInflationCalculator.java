package pyc.lopatuxin.budget.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pyc.lopatuxin.budget.repository.ExpenseRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Calculates personal inflation as the percentage change in average monthly expenses of the
 * current year compared to the previous year. The average is calculated by the actual number
 * of months with data, not calendar months, and excludes an incomplete current month (its last
 * day is still in the future) from the current year — the previous year is always complete.
 * Used by the budget summary, the overview page and the analytics page.
 */
@Service
@RequiredArgsConstructor
public class PersonalInflationCalculator {

    private final ExpenseRepository expenseRepository;

    /**
     * Same as {@link #calculate(UUID, int, int, Map, LocalDate)} with {@code today} defaulted to
     * {@link LocalDate#now()}.
     */
    public BigDecimal calculate(UUID userId, int month, int year, Map<Integer, List<Object[]>> monthlyExpensesByYear) {
        return calculate(userId, month, year, monthlyExpensesByYear, LocalDate.now());
    }

    /**
     * Same calculation as {@link #calculateOptional}, but returns {@link BigDecimal#ZERO} instead
     * of empty when there isn't enough history for either year — matches the budget summary's
     * original contract.
     *
     * @param monthlyExpensesByYear cache shared across calls within one request, keyed by year
     * @param today                 date used to decide whether {@code month} is complete
     */
    public BigDecimal calculate(UUID userId, int month, int year, Map<Integer, List<Object[]>> monthlyExpensesByYear,
                                 LocalDate today) {
        return calculateOptional(userId, month, year, monthlyExpensesByYear, today).orElse(BigDecimal.ZERO);
    }

    /**
     * Same as {@link #calculateOptional(UUID, int, int, Map, LocalDate)} with {@code today}
     * defaulted to {@link LocalDate#now()}.
     */
    public Optional<BigDecimal> calculateOptional(UUID userId, int month, int year,
                                                   Map<Integer, List<Object[]>> monthlyExpensesByYear) {
        return calculateOptional(userId, month, year, monthlyExpensesByYear, LocalDate.now());
    }

    /**
     * Calculates personal inflation, or returns empty when the current year has no complete month
     * up to {@code month} or the previous year has no data at all — used by the overview page,
     * which shows "no data" instead of a misleading zero. A month is complete when its last day is
     * not after {@code today}; the previous year is always complete.
     *
     * @param monthlyExpensesByYear cache shared across calls within one request, keyed by year
     * @param today                 date used to decide whether a current-year month is complete
     */
    public Optional<BigDecimal> calculateOptional(UUID userId, int month, int year,
                                                   Map<Integer, List<Object[]>> monthlyExpensesByYear,
                                                   LocalDate today) {
        List<Object[]> currentYearMonthly = monthlyExpensesForYear(userId, year, monthlyExpensesByYear);
        List<Object[]> currentYearUpToMonth = currentYearMonthly.stream()
                .filter(row -> {
                    int rowMonth = ((Number) row[0]).intValue();
                    return rowMonth <= month && isMonthComplete(year, rowMonth, today);
                })
                .toList();

        if (currentYearUpToMonth.isEmpty()) {
            return Optional.empty();
        }

        List<Object[]> previousYearMonthly = monthlyExpensesForYear(userId, year - 1, monthlyExpensesByYear);
        if (previousYearMonthly.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal currentYearAvg = calculateAverageFromMonthlyRows(currentYearUpToMonth);
        BigDecimal previousYearAvg = calculateAverageFromMonthlyRows(previousYearMonthly);

        return Optional.of(currentYearAvg.subtract(previousYearAvg)
                .divide(previousYearAvg, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP));
    }

    /** A month is complete when its last day is not after {@code today}. */
    private boolean isMonthComplete(int year, int month, LocalDate today) {
        return !YearMonth.of(year, month).atEndOfMonth().isAfter(today);
    }

    /**
     * Returns non-transfer monthly expense totals for the given year, fetching them at most
     * once per cache instance (current and previous period usually share a year).
     */
    private List<Object[]> monthlyExpensesForYear(UUID userId, int year, Map<Integer, List<Object[]>> cache) {
        return cache.computeIfAbsent(year,
                y -> expenseRepository.findMonthlyNonTransferExpenseByUserIdAndYear(userId, y));
    }

    private BigDecimal calculateAverageFromMonthlyRows(List<Object[]> rows) {
        BigDecimal total = rows.stream()
                .map(row -> (BigDecimal) row[1])
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(rows.size()), 10, RoundingMode.HALF_UP);
    }
}
