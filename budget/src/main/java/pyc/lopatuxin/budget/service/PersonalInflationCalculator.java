package pyc.lopatuxin.budget.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pyc.lopatuxin.budget.repository.ExpenseRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Calculates personal inflation as the percentage change in average monthly expenses of the
 * current year compared to the previous year. The average is calculated by the actual number
 * of months with data, not calendar months. Used by both the budget summary and the overview page.
 */
@Service
@RequiredArgsConstructor
public class PersonalInflationCalculator {

    private final ExpenseRepository expenseRepository;

    /**
     * Same calculation as {@link #calculateOptional}, but returns {@link BigDecimal#ZERO} instead
     * of empty when there isn't enough history for either year — matches the budget summary's
     * original contract.
     *
     * @param monthlyExpensesByYear cache shared across calls within one request, keyed by year
     */
    public BigDecimal calculate(UUID userId, int month, int year, Map<Integer, List<Object[]>> monthlyExpensesByYear) {
        return calculateOptional(userId, month, year, monthlyExpensesByYear).orElse(BigDecimal.ZERO);
    }

    /**
     * Calculates personal inflation, or returns empty when the current year has no data up to
     * {@code month} or the previous year has no data at all — used by the overview page, which
     * shows "no data" instead of a misleading zero.
     *
     * @param monthlyExpensesByYear cache shared across calls within one request, keyed by year
     */
    public Optional<BigDecimal> calculateOptional(UUID userId, int month, int year,
                                                   Map<Integer, List<Object[]>> monthlyExpensesByYear) {
        List<Object[]> currentYearMonthly = monthlyExpensesForYear(userId, year, monthlyExpensesByYear);
        List<Object[]> currentYearUpToMonth = currentYearMonthly.stream()
                .filter(row -> ((Number) row[0]).intValue() <= month)
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
