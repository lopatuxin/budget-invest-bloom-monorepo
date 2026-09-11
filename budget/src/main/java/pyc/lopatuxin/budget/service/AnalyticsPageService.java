package pyc.lopatuxin.budget.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.budget.dto.common.ChangeDto;
import pyc.lopatuxin.budget.dto.response.AnalyticsCategoryDto;
import pyc.lopatuxin.budget.dto.response.AnalyticsMonthExtremeDto;
import pyc.lopatuxin.budget.dto.response.AnalyticsMonthPointDto;
import pyc.lopatuxin.budget.dto.response.AnalyticsPageResponseDto;
import pyc.lopatuxin.budget.dto.response.AnalyticsSectionDto;
import pyc.lopatuxin.budget.dto.response.CurrentMonthDto;
import pyc.lopatuxin.budget.entity.enums.NormStatus;
import pyc.lopatuxin.budget.repository.ExpenseRepository;
import pyc.lopatuxin.budget.repository.IncomeRepository;
import pyc.lopatuxin.budget.util.ComparisonMath;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for building the analytics page ("Year to year"): three sections (expenses, income,
 * savings) comparing the requested year Y against the previous year P, plus a category breakdown
 * of the personal inflation shown on the expenses tab. All calculations run here so the frontend
 * needs a single request.
 *
 * <p>An incomplete calendar month (its last day is still in the future) never enters an average,
 * a maximum/minimum month or a category total — it only shows up in the raw {@code months} list
 * and in the year's {@code total}. The previous year P is always complete under normal use.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(value = "budgetTransactionManager", readOnly = true)
public class AnalyticsPageService {

    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;

    /**
     * Builds the analytics page for "now" — see {@link #getAnalytics(UUID, int, LocalDate)}.
     *
     * @param userId identifier of the user
     * @param year   requested year Y
     * @return analytics page DTO
     */
    public AnalyticsPageResponseDto getAnalytics(UUID userId, int year) {
        return getAnalytics(userId, year, LocalDate.now());
    }

    /**
     * Builds the analytics page for year Y against the previous year P = Y - 1, as of {@code today}.
     *
     * @param userId identifier of the user
     * @param year   requested year Y
     * @param today  date used to decide which months are complete
     * @return analytics page DTO
     */
    AnalyticsPageResponseDto getAnalytics(UUID userId, int year, LocalDate today) {
        log.debug("Building analytics page for userId={}, year={}", userId, year);

        int previousYear = year - 1;
        YearMonth currentCalendarMonth = YearMonth.from(today);
        boolean currentMonthPartial = today.getDayOfMonth() != today.lengthOfMonth();

        LocalDate rangeStart = LocalDate.of(previousYear, 1, 1);
        LocalDate rangeEnd = LocalDate.of(year, 12, 31);
        Map<YearMonth, BigDecimal> incomeByMonth = toMonthlyMap(
                incomeRepository.findMonthlyNonTransferIncomeByUserIdAndDateBetween(userId, rangeStart, rangeEnd));
        Map<YearMonth, BigDecimal> expenseByMonth = toMonthlyMap(
                expenseRepository.findMonthlyNonTransferExpenseByUserIdAndDateBetween(userId, rangeStart, rangeEnd));

        AnalyticsSectionDto expenses = buildSection(incomeByMonth, expenseByMonth,
                (inc, exp) -> exp, (inc, exp) -> exp.signum() > 0,
                year, previousYear, currentCalendarMonth, currentMonthPartial, today);
        AnalyticsSectionDto income = buildSection(incomeByMonth, expenseByMonth,
                (inc, exp) -> inc, (inc, exp) -> inc.signum() > 0,
                year, previousYear, currentCalendarMonth, currentMonthPartial, today);
        AnalyticsSectionDto savings = buildSection(incomeByMonth, expenseByMonth,
                BigDecimal::subtract, (inc, exp) -> inc.signum() > 0 || exp.signum() > 0,
                year, previousYear, currentCalendarMonth, currentMonthPartial, today);

        boolean previousYearHasData = savings.getPreviousMonthsCounted() > 0;
        List<AnalyticsCategoryDto> categories = buildCategories(userId, year, previousYear, expenses, today);

        log.debug("Analytics page built for userId={}, year={}", userId, year);

        return AnalyticsPageResponseDto.builder()
                .year(year)
                .previousYear(previousYear)
                .earliestYear(findEarliestYear(userId))
                .currentMonth(CurrentMonthDto.builder()
                        .month(currentCalendarMonth.getMonthValue())
                        .year(currentCalendarMonth.getYear())
                        .partial(currentMonthPartial)
                        .build())
                .previousYearHasData(previousYearHasData)
                .expenses(expenses)
                .income(income)
                .savings(savings)
                .savingsRatePercent(savingsRatePercentFor(incomeByMonth, expenseByMonth, year, today))
                .previousSavingsRatePercent(savingsRatePercentFor(incomeByMonth, expenseByMonth, previousYear, today))
                .personalInflationPercent(expenses.getChange().getPercent())
                .categories(categories)
                .build();
    }

    // ─── Section (expenses / income / savings) ────────────────────────────────

    /**
     * Builds one section: 12 months of Y against the same 12 months of P, the year totals, the
     * average of the completed+counted months of each year with its change, and the max/min month
     * of Y. {@code valueFn} turns a month's (income, expense) pair into the section's amount
     * (expenses, income, or income - expenses for savings); {@code countedFn} decides whether a
     * completed month counts towards the average, extremes and totals used by category weights —
     * per the shared rule, for expenses/income it is "amount > 0", for savings "income > 0 or
     * expense > 0".
     */
    private AnalyticsSectionDto buildSection(Map<YearMonth, BigDecimal> incomeByMonth, Map<YearMonth, BigDecimal> expenseByMonth,
                                             BiFunction<BigDecimal, BigDecimal, BigDecimal> valueFn,
                                             BiPredicate<BigDecimal, BigDecimal> countedFn,
                                             int year, int previousYear, YearMonth currentCalendarMonth,
                                             boolean currentMonthPartial, LocalDate today) {
        List<MonthValue> values = monthValues(incomeByMonth, expenseByMonth, valueFn, year, previousYear);
        List<AnalyticsMonthPointDto> months = buildMonthPoints(values, currentCalendarMonth, currentMonthPartial);
        SectionAggregate aggregate = aggregate(values, countedFn, currentCalendarMonth, currentMonthPartial, today);

        return AnalyticsSectionDto.builder()
                .total(ComparisonMath.money(aggregate.total()))
                .previousTotal(ComparisonMath.money(aggregate.previousTotal()))
                .monthsCounted(aggregate.monthsCounted())
                .previousMonthsCounted(aggregate.previousMonthsCounted())
                .average(ComparisonMath.money(aggregate.average()))
                .previousAverage(ComparisonMath.money(aggregate.previousAverage()))
                .change(changeFromAverages(aggregate.average(), aggregate.previousAverage()))
                .maxMonth(aggregate.maxMonth())
                .minMonth(aggregate.minMonth())
                .months(months)
                .build();
    }

    /** One calendar month's section amount for Y and for the matching month of P. */
    private record MonthValue(int month, YearMonth yearMonth, YearMonth previousYearMonth,
                              BigDecimal income, BigDecimal expense, BigDecimal value,
                              BigDecimal previousIncome, BigDecimal previousExpense, BigDecimal previousValue) {
    }

    private List<MonthValue> monthValues(Map<YearMonth, BigDecimal> incomeByMonth, Map<YearMonth, BigDecimal> expenseByMonth,
                                         BiFunction<BigDecimal, BigDecimal, BigDecimal> valueFn, int year, int previousYear) {
        List<MonthValue> result = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(year, m);
            YearMonth pym = YearMonth.of(previousYear, m);
            BigDecimal income = monthAmount(incomeByMonth, ym);
            BigDecimal expense = monthAmount(expenseByMonth, ym);
            BigDecimal previousIncome = monthAmount(incomeByMonth, pym);
            BigDecimal previousExpense = monthAmount(expenseByMonth, pym);
            result.add(new MonthValue(m, ym, pym, income, expense, valueFn.apply(income, expense),
                    previousIncome, previousExpense, valueFn.apply(previousIncome, previousExpense)));
        }
        return result;
    }

    private List<AnalyticsMonthPointDto> buildMonthPoints(List<MonthValue> values, YearMonth currentCalendarMonth,
                                                          boolean currentMonthPartial) {
        return values.stream().map(v -> {
            boolean afterToday = v.yearMonth().isAfter(currentCalendarMonth);
            boolean partial = v.yearMonth().equals(currentCalendarMonth) && currentMonthPartial;
            return AnalyticsMonthPointDto.builder()
                    .month(v.month())
                    .current(afterToday ? null : ComparisonMath.money(v.value()))
                    .previous(ComparisonMath.money(v.previousValue()))
                    .partial(partial)
                    .build();
        }).toList();
    }

    private record SectionAggregate(BigDecimal total, BigDecimal previousTotal, int monthsCounted, int previousMonthsCounted,
                                    BigDecimal average, BigDecimal previousAverage,
                                    AnalyticsMonthExtremeDto maxMonth, AnalyticsMonthExtremeDto minMonth) {
    }

    private SectionAggregate aggregate(List<MonthValue> values, BiPredicate<BigDecimal, BigDecimal> countedFn,
                                       YearMonth currentCalendarMonth, boolean currentMonthPartial, LocalDate today) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal previousTotal = BigDecimal.ZERO;
        BigDecimal countedSum = BigDecimal.ZERO;
        BigDecimal previousCountedSum = BigDecimal.ZERO;
        int monthsCounted = 0;
        int previousMonthsCounted = 0;
        AnalyticsMonthExtremeDto maxMonth = null;
        AnalyticsMonthExtremeDto minMonth = null;

        for (MonthValue v : values) {
            total = total.add(v.value());
            previousTotal = previousTotal.add(v.previousValue());

            boolean afterToday = v.yearMonth().isAfter(currentCalendarMonth);
            boolean partial = v.yearMonth().equals(currentCalendarMonth) && currentMonthPartial;
            if (!afterToday && !partial && countedFn.test(v.income(), v.expense())) {
                monthsCounted++;
                countedSum = countedSum.add(v.value());
                if (maxMonth == null || v.value().compareTo(maxMonth.getAmount()) > 0) {
                    maxMonth = extreme(v.month(), v.value());
                }
                if (minMonth == null || v.value().compareTo(minMonth.getAmount()) < 0) {
                    minMonth = extreme(v.month(), v.value());
                }
            }
            if (isMonthComplete(v.previousYearMonth(), today) && countedFn.test(v.previousIncome(), v.previousExpense())) {
                previousMonthsCounted++;
                previousCountedSum = previousCountedSum.add(v.previousValue());
            }
        }

        BigDecimal average = average(countedSum, monthsCounted);
        BigDecimal previousAverage = average(previousCountedSum, previousMonthsCounted);
        return new SectionAggregate(total, previousTotal, monthsCounted, previousMonthsCounted,
                average, previousAverage, maxMonth, minMonth);
    }

    private AnalyticsMonthExtremeDto extreme(int month, BigDecimal amount) {
        return AnalyticsMonthExtremeDto.builder().month(month).amount(ComparisonMath.money(amount)).build();
    }

    private BigDecimal average(BigDecimal sum, int count) {
        return count > 0 ? sum.divide(BigDecimal.valueOf(count), 10, RoundingMode.HALF_UP) : null;
    }

    /** {@code NO_HISTORY} when either average is missing (no counted months that year). */
    private ChangeDto changeFromAverages(BigDecimal average, BigDecimal previousAverage) {
        if (average == null || previousAverage == null) {
            return ChangeDto.builder().status(NormStatus.NO_HISTORY).build();
        }
        return ComparisonMath.changeFrom(average.subtract(previousAverage), previousAverage);
    }

    /**
     * Savings rate for one calendar year: (sum of income - sum of expenses) / sum of income * 100
     * over the year's completed months where income or expenses are non-zero, clamped to [-99, 99].
     */
    private Integer savingsRatePercentFor(Map<YearMonth, BigDecimal> incomeByMonth, Map<YearMonth, BigDecimal> expenseByMonth,
                                          int year, LocalDate today) {
        BigDecimal incomeSum = BigDecimal.ZERO;
        BigDecimal expenseSum = BigDecimal.ZERO;
        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(year, m);
            BigDecimal income = monthAmount(incomeByMonth, ym);
            BigDecimal expense = monthAmount(expenseByMonth, ym);
            if (isMonthComplete(ym, today) && (income.signum() > 0 || expense.signum() > 0)) {
                incomeSum = incomeSum.add(income);
                expenseSum = expenseSum.add(expense);
            }
        }
        return ComparisonMath.savingsRate(incomeSum, expenseSum);
    }

    // ─── Categories (expenses tab) ─────────────────────────────────────────────

    /**
     * Builds the category breakdown for the expenses tab, weighted by the previous year P so the
     * contributions reconcile with {@code expenses.change.percent} (see class javadoc and plan
     * "Функциональные требования" п. 10). Empty when Y has no counted expense month yet.
     */
    private List<AnalyticsCategoryDto> buildCategories(UUID userId, int year, int previousYear,
                                                        AnalyticsSectionDto expenses, LocalDate today) {
        int monthsCounted = expenses.getMonthsCounted();
        if (monthsCounted == 0) {
            return List.of();
        }

        LocalDate lastCompleteMonthEnd = lastCompleteMonthEnd(year, today);
        List<Object[]> rows = expenseRepository.findNonTransferCategoryTotalsByYearForUserAndDateBetween(
                userId, LocalDate.of(previousYear, 1, 1), lastCompleteMonthEnd);
        CategoryTotals totals = parseCategoryTotals(rows, year);

        Set<UUID> categoryIds = new LinkedHashSet<>(totals.current().keySet());
        categoryIds.addAll(totals.previous().keySet());

        int previousMonthsCounted = expenses.getPreviousMonthsCounted();
        BigDecimal expensesAverage = expenses.getAverage();
        BigDecimal expensesPreviousAverage = expenses.getPreviousAverage();

        return categoryIds.stream()
                .map(id -> toCategoryDto(id, totals.names().get(id), totals.emojis().get(id),
                        totals.current().getOrDefault(id, BigDecimal.ZERO), totals.previous().getOrDefault(id, BigDecimal.ZERO),
                        monthsCounted, previousMonthsCounted, expensesAverage, expensesPreviousAverage))
                .sorted(categoryOrder(expensesPreviousAverage))
                .toList();
    }

    private record CategoryTotals(Map<UUID, BigDecimal> current, Map<UUID, BigDecimal> previous,
                                  Map<UUID, String> names, Map<UUID, String> emojis) {
    }

    /** Splits {@code findNonTransferCategoryTotalsByYearForUserAndDateBetween} rows into Y and P totals. */
    private CategoryTotals parseCategoryTotals(List<Object[]> rows, int year) {
        Map<UUID, BigDecimal> currentTotals = new HashMap<>();
        Map<UUID, BigDecimal> previousTotals = new HashMap<>();
        Map<UUID, String> names = new HashMap<>();
        Map<UUID, String> emojis = new HashMap<>();
        for (Object[] row : rows) {
            int rowYear = ((Number) row[0]).intValue();
            UUID categoryId = (UUID) row[1];
            names.put(categoryId, (String) row[2]);
            emojis.put(categoryId, (String) row[3]);
            BigDecimal sum = (BigDecimal) row[4];
            if (rowYear == year) {
                currentTotals.put(categoryId, sum);
            } else {
                previousTotals.put(categoryId, sum);
            }
        }
        return new CategoryTotals(currentTotals, previousTotals, names, emojis);
    }

    private AnalyticsCategoryDto toCategoryDto(UUID categoryId, String name, String emoji,
                                               BigDecimal totalCurrent, BigDecimal totalPrevious,
                                               int monthsCounted, int previousMonthsCounted,
                                               BigDecimal expensesAverage, BigDecimal expensesPreviousAverage) {
        BigDecimal averageCurrent = totalCurrent.divide(BigDecimal.valueOf(monthsCounted), 10, RoundingMode.HALF_UP);
        BigDecimal averagePrevious = previousMonthsCounted > 0
                ? totalPrevious.divide(BigDecimal.valueOf(previousMonthsCounted), 10, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal contributionPoints = (expensesPreviousAverage == null || expensesPreviousAverage.signum() == 0)
                ? null
                : averageCurrent.subtract(averagePrevious)
                        .divide(expensesPreviousAverage, 10, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP);
        BigDecimal sharePercent = averageCurrent.divide(expensesAverage, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);

        return AnalyticsCategoryDto.builder()
                .categoryId(categoryId)
                .categoryName(name)
                .emoji(emoji)
                .averageCurrent(ComparisonMath.money(averageCurrent))
                .averagePrevious(ComparisonMath.money(averagePrevious))
                .change(ComparisonMath.changeFrom(averageCurrent.subtract(averagePrevious), averagePrevious))
                .contributionPoints(contributionPoints)
                .sharePercent(sharePercent)
                .build();
    }

    /**
     * By descending |contributionPoints|, ties broken by descending averageCurrent; when P has no
     * data at all (contributionPoints is null for every category), sorted by averageCurrent alone.
     */
    private Comparator<AnalyticsCategoryDto> categoryOrder(BigDecimal expensesPreviousAverage) {
        Comparator<AnalyticsCategoryDto> byAverageCurrentDesc =
                Comparator.comparing(AnalyticsCategoryDto::getAverageCurrent, Comparator.reverseOrder());
        if (expensesPreviousAverage == null || expensesPreviousAverage.signum() == 0) {
            return byAverageCurrentDesc;
        }
        Comparator<AnalyticsCategoryDto> byAbsContributionDesc = Comparator.comparing(
                (AnalyticsCategoryDto c) -> c.getContributionPoints().abs(), Comparator.reverseOrder());
        return byAbsContributionDesc.thenComparing(byAverageCurrentDesc);
    }

    /** Last day of the latest complete month of {@code year}, or null if it has none yet. */
    private LocalDate lastCompleteMonthEnd(int year, LocalDate today) {
        YearMonth last = null;
        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(year, m);
            if (!isMonthComplete(ym, today)) {
                break;
            }
            last = ym;
        }
        return last == null ? null : last.atEndOfMonth();
    }

    // ─── earliestYear ───────────────────────────────────────────────────────────

    private Integer findEarliestYear(UUID userId) {
        return Stream.of(
                        expenseRepository.findMinNonTransferDateByUserId(userId),
                        incomeRepository.findMinNonTransferDateByUserId(userId))
                .flatMap(Optional::stream)
                .map(LocalDate::getYear)
                .min(Integer::compareTo)
                .orElse(null);
    }

    // ─── Shared helpers ─────────────────────────────────────────────────────────

    /** A month is complete when its last day is not after {@code today}. */
    private boolean isMonthComplete(YearMonth month, LocalDate today) {
        return !month.atEndOfMonth().isAfter(today);
    }

    private Map<YearMonth, BigDecimal> toMonthlyMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(
                row -> YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue()),
                row -> (BigDecimal) row[2]));
    }

    private BigDecimal monthAmount(Map<YearMonth, BigDecimal> byMonth, YearMonth month) {
        return byMonth.getOrDefault(month, BigDecimal.ZERO);
    }
}
