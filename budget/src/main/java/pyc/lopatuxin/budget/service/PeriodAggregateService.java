package pyc.lopatuxin.budget.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.budget.repository.ExpenseRepository;
import pyc.lopatuxin.budget.repository.IncomeRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Service for aggregating income, expenses and balance for a calendar month.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PeriodAggregateService {

    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;

    /**
     * Aggregates income, expenses and balance for the given month and year.
     *
     * @param userId identifier of the user
     * @param month  month number (1-12)
     * @param year   year
     * @return {@link PeriodAggregates} with period boundaries and aggregated amounts
     */
    public PeriodAggregates buildPeriodAggregates(UUID userId, int month, int year) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        BigDecimal income = incomeRepository
                .sumAmountByUserIdAndDateBetween(userId, startDate, endDate)
                .orElse(BigDecimal.ZERO);

        BigDecimal expenses = expenseRepository
                .sumAmountByUserIdAndDateBetween(userId, startDate, endDate)
                .orElse(BigDecimal.ZERO);

        return new PeriodAggregates(startDate, endDate, income, expenses, income.subtract(expenses));
    }

    /**
     * Aggregated data for one calendar month.
     *
     * @param startDate first day of the period
     * @param endDate   last day of the period
     * @param income    total income for the period
     * @param expenses  total expenses for the period
     * @param balance   balance (income minus expenses)
     */
    public record PeriodAggregates(
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal balance
    ) {
    }
}
