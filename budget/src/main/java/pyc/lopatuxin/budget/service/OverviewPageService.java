package pyc.lopatuxin.budget.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.budget.dto.common.ChangeDto;
import pyc.lopatuxin.budget.dto.response.CapitalPointDto;
import pyc.lopatuxin.budget.dto.response.CapitalSectionDto;
import pyc.lopatuxin.budget.dto.response.CurrentMonthDto;
import pyc.lopatuxin.budget.dto.response.MonthTotalsDto;
import pyc.lopatuxin.budget.dto.response.NextDividendDto;
import pyc.lopatuxin.budget.dto.response.OverviewPageResponseDto;
import pyc.lopatuxin.budget.dto.response.PortfolioSectionDto;
import pyc.lopatuxin.budget.dto.response.SavingsSectionDto;
import pyc.lopatuxin.budget.dto.response.Totals12mDto;
import pyc.lopatuxin.budget.dto.response.TotalsLineDto;
import pyc.lopatuxin.budget.entity.enums.NormStatus;
import pyc.lopatuxin.budget.repository.ExpenseRepository;
import pyc.lopatuxin.budget.repository.IncomeRepository;
import pyc.lopatuxin.shared.port.PortfolioCurrentValuation;
import pyc.lopatuxin.shared.port.PortfolioNextDividend;
import pyc.lopatuxin.shared.port.PortfolioValuation;
import pyc.lopatuxin.shared.port.PortfolioValueAt;
import pyc.lopatuxin.shared.port.PortfolioValueSeries;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Service for building the overview page ("Capital"): free money plus portfolio with a
 * 12-month trajectory, four tiles, monthly income bars and 12-month totals against the
 * previous 12 months. All calculations run here so the frontend needs a single request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(value = "budgetTransactionManager", readOnly = true)
public class OverviewPageService {

    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;
    private final PersonalInflationCalculator personalInflationCalculator;
    private final PortfolioValuation portfolioValuation;

    /**
     * Builds the overview page for "now": the current calendar month, the 12-month window W
     * (this month and 11 previous) compared against the 12-month window P before it, and a
     * 13-point capital trajectory (the 12 preceding month-ends plus today).
     *
     * @param userId identifier of the user
     * @return overview page DTO
     */
    public OverviewPageResponseDto getOverview(UUID userId) {
        log.debug("Building overview page for userId={}", userId);

        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);
        boolean currentMonthPartial = today.getDayOfMonth() != today.lengthOfMonth();
        Windows windows = buildWindows(currentMonth);

        MonthlyAmounts monthly = loadMonthlyAmounts(userId, windows.p().getFirst().atDay(1), today);

        BigDecimal freeMoneyNow = incomeRepository.sumNonTransferByUserId(userId)
                .subtract(expenseRepository.sumNonTransferByUserId(userId));
        PortfolioSnapshot currentPortfolio = loadCurrentPortfolio(userId);

        CapitalSectionDto capital = buildCapitalSection(
                userId, currentMonth, today, windows.history(), monthly, freeMoneyNow, currentPortfolio);

        BigDecimal personalInflationPercent = personalInflationCalculator
                .calculateOptional(userId, today.getMonthValue(), today.getYear(), new HashMap<>())
                .orElse(null);

        log.debug("Overview page built for userId={}", userId);

        return OverviewPageResponseDto.builder()
                .asOf(today)
                .currentMonth(CurrentMonthDto.builder()
                        .month(currentMonth.getMonthValue())
                        .year(currentMonth.getYear())
                        .partial(currentMonthPartial)
                        .build())
                .capital(capital)
                .currentMonthBalance(money(monthAmount(monthly.income(), currentMonth)
                        .subtract(monthAmount(monthly.expenses(), currentMonth))))
                .portfolio(buildPortfolioSection(currentPortfolio))
                .savings(buildSavingsSection(monthly, windows.w(), currentMonth))
                .months(buildMonths(windows.w(), monthly, currentMonth, currentMonthPartial))
                .totals12m(buildTotals12m(windows.w(), windows.p(), monthly))
                .personalInflationPercent(personalInflationPercent)
                .build();
    }

    /**
     * Builds the three 12-month windows the page needs: W (this month and 11 previous), P (the
     * 12 months before W, for the 12-month comparisons) and the capital history window (the 12
     * months before the current one, i.e. M-12..M-1 — the 13th history point is "today", added
     * separately in {@link #buildCapitalSection}).
     */
    private Windows buildWindows(YearMonth currentMonth) {
        List<YearMonth> w = monthsRange(currentMonth.minusMonths(11), currentMonth);
        List<YearMonth> p = monthsRange(currentMonth.minusMonths(23), currentMonth.minusMonths(12));
        List<YearMonth> history = monthsRange(currentMonth.minusMonths(12), currentMonth.minusMonths(1));
        return new Windows(w, p, history);
    }

    // ─── Capital section ──────────────────────────────────────────────────────

    private CapitalSectionDto buildCapitalSection(UUID userId, YearMonth currentMonth, LocalDate today,
                                                  List<YearMonth> historyMonths, MonthlyAmounts monthly,
                                                  BigDecimal freeMoneyNow, PortfolioSnapshot currentPortfolio) {
        List<LocalDate> historyDates = historyMonths.stream().map(YearMonth::atEndOfMonth).toList();
        BigDecimal baseIncome = incomeRepository.sumNonTransferByUserIdAndDateLessThanEqual(userId, historyDates.getFirst());
        BigDecimal baseExpense = expenseRepository.sumNonTransferByUserIdAndDateLessThanEqual(userId, historyDates.getFirst());

        PortfolioHistoryResult portfolioHistory = loadPortfolioHistory(userId, historyDates);
        List<CapitalPointDto> points = buildHistoryPoints(
                historyMonths, historyDates, monthly, baseIncome, baseExpense, portfolioHistory.valueByDate());

        BigDecimal lastPortfolioValue = currentPortfolio.available()
                ? currentPortfolio.valuation().totalValue() : BigDecimal.ZERO;
        BigDecimal capitalTotal = freeMoneyNow.add(lastPortfolioValue);
        points.add(CapitalPointDto.builder()
                .month(currentMonth.getMonthValue())
                .year(currentMonth.getYear())
                .date(today)
                .freeMoney(money(freeMoneyNow))
                .portfolioValue(money(lastPortfolioValue))
                .total(money(capitalTotal))
                .build());

        YearOverYearChange yoy = computeYearOverYearChange(baseIncome, baseExpense, points.getFirst(), capitalTotal);

        return CapitalSectionDto.builder()
                .total(money(capitalTotal))
                .freeMoney(money(freeMoneyNow))
                .portfolioValue(money(lastPortfolioValue))
                .yearAgo(money(yoy.yearAgo()))
                .changeAbs(money(yoy.changeAbs()))
                .change(yoy.change())
                .history(points)
                .portfolioHistoryPending(portfolioHistory.historyPending())
                .build();
    }

    private List<CapitalPointDto> buildHistoryPoints(List<YearMonth> months, List<LocalDate> dates, MonthlyAmounts monthly,
                                                      BigDecimal baseIncome, BigDecimal baseExpense,
                                                      Map<LocalDate, BigDecimal> portfolioByDate) {
        List<CapitalPointDto> points = new ArrayList<>();
        BigDecimal cumulativeIncome = baseIncome;
        BigDecimal cumulativeExpense = baseExpense;
        for (int i = 0; i < months.size(); i++) {
            YearMonth month = months.get(i);
            if (i > 0) {
                cumulativeIncome = cumulativeIncome.add(monthAmount(monthly.income(), month));
                cumulativeExpense = cumulativeExpense.add(monthAmount(monthly.expenses(), month));
            }
            BigDecimal freeMoney = cumulativeIncome.subtract(cumulativeExpense);
            BigDecimal portfolioValue = portfolioByDate.getOrDefault(dates.get(i), BigDecimal.ZERO);
            BigDecimal total = freeMoney.add(portfolioValue);
            points.add(CapitalPointDto.builder()
                    .month(month.getMonthValue())
                    .year(month.getYear())
                    .date(dates.get(i))
                    .freeMoney(money(freeMoney))
                    .portfolioValue(money(portfolioValue))
                    .total(money(total))
                    .build());
        }
        return points;
    }

    /**
     * Year-over-year change is {@code NO_HISTORY} when the user had no income, no expenses and no
     * portfolio value at all a year ago (the first history point) — a brand-new account, not just
     * a coincidental zero total.
     */
    private YearOverYearChange computeYearOverYearChange(BigDecimal baseIncome, BigDecimal baseExpense,
                                                          CapitalPointDto firstPoint, BigDecimal capitalTotal) {
        boolean noHistory = baseIncome.signum() == 0 && baseExpense.signum() == 0
                && firstPoint.getPortfolioValue().signum() == 0;
        if (noHistory) {
            return new YearOverYearChange(null, null, ChangeDto.builder().status(NormStatus.NO_HISTORY).build());
        }
        BigDecimal yearAgo = firstPoint.getTotal();
        BigDecimal changeAbs = capitalTotal.subtract(yearAgo);
        return new YearOverYearChange(yearAgo, changeAbs, changeFrom(changeAbs, yearAgo));
    }

    // ─── Portfolio section ────────────────────────────────────────────────────

    private PortfolioSnapshot loadCurrentPortfolio(UUID userId) {
        try {
            return new PortfolioSnapshot(true, portfolioValuation.current(userId));
        } catch (RuntimeException e) {
            log.warn("Не удалось получить текущую оценку портфеля для userId={}: {}", userId, e.getMessage());
            return new PortfolioSnapshot(false, null);
        }
    }

    private PortfolioHistoryResult loadPortfolioHistory(UUID userId, List<LocalDate> dates) {
        try {
            PortfolioValueSeries series = portfolioValuation.valueAt(userId, dates);
            Map<LocalDate, BigDecimal> valueByDate = series.points().stream()
                    .collect(Collectors.toMap(PortfolioValueAt::date,
                            point -> point.value() == null ? BigDecimal.ZERO : point.value()));
            return new PortfolioHistoryResult(valueByDate, series.historyPending());
        } catch (RuntimeException e) {
            log.warn("Не удалось получить историю стоимости портфеля для userId={}: {}", userId, e.getMessage());
            return new PortfolioHistoryResult(Map.of(), false);
        }
    }

    private PortfolioSectionDto buildPortfolioSection(PortfolioSnapshot snapshot) {
        if (!snapshot.available()) {
            return PortfolioSectionDto.builder().available(false).assetsCount(0).build();
        }

        PortfolioCurrentValuation valuation = snapshot.valuation();
        ChangeDto pnl = valuation.assetsCount() > 0 ? changeFrom(valuation.totalPnl(), valuation.totalCost()) : null;

        return PortfolioSectionDto.builder()
                .available(true)
                .value(money(valuation.totalValue()))
                .cost(money(valuation.totalCost()))
                .pnlAmount(money(valuation.totalPnl()))
                .pnl(pnl)
                .assetsCount(valuation.assetsCount())
                .dividends12m(money(valuation.dividends12m()))
                .nextDividend(toNextDividendDto(valuation.nextDividend()))
                .build();
    }

    private NextDividendDto toNextDividendDto(PortfolioNextDividend dividend) {
        if (dividend == null) {
            return null;
        }
        return NextDividendDto.builder()
                .ticker(dividend.ticker())
                .securityName(dividend.securityName())
                .paymentDate(dividend.paymentDate())
                .totalAmount(money(dividend.totalAmount()))
                .build();
    }

    // ─── Savings, months, totals ──────────────────────────────────────────────

    private SavingsSectionDto buildSavingsSection(MonthlyAmounts monthly, List<YearMonth> windowW,
                                                  YearMonth currentMonth) {
        BigDecimal windowIncome = sumOverMonths(monthly.income(), windowW);
        BigDecimal windowExpenses = sumOverMonths(monthly.expenses(), windowW);
        return SavingsSectionDto.builder()
                .rate12m(savingsRate(windowIncome, windowExpenses))
                .currentMonthRate(savingsRate(monthAmount(monthly.income(), currentMonth),
                        monthAmount(monthly.expenses(), currentMonth)))
                .build();
    }

    /**
     * Savings rate as (income - expenses) / income * 100, clamped to [-99, 99].
     * Returns null when income is non-positive (the tile shows "—" instead of a misleading 0).
     */
    private Integer savingsRate(BigDecimal income, BigDecimal expenses) {
        if (income == null || income.signum() <= 0) {
            return null;
        }
        int raw = income.subtract(expenses)
                .divide(income, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
        return Math.clamp(raw, -99, 99);
    }

    private List<MonthTotalsDto> buildMonths(List<YearMonth> windowW, MonthlyAmounts monthly,
                                             YearMonth currentMonth, boolean currentMonthPartial) {
        return windowW.stream().map(month -> {
            BigDecimal income = monthAmount(monthly.income(), month);
            BigDecimal expenses = monthAmount(monthly.expenses(), month);
            return MonthTotalsDto.builder()
                    .month(month.getMonthValue())
                    .year(month.getYear())
                    .income(money(income))
                    .expenses(money(expenses))
                    .saved(money(income.subtract(expenses)))
                    .partial(month.equals(currentMonth) && currentMonthPartial)
                    .build();
        }).toList();
    }

    private Totals12mDto buildTotals12m(List<YearMonth> windowW, List<YearMonth> windowP, MonthlyAmounts monthly) {
        BigDecimal incomeW = sumOverMonths(monthly.income(), windowW);
        BigDecimal expensesW = sumOverMonths(monthly.expenses(), windowW);
        BigDecimal incomeP = sumOverMonths(monthly.income(), windowP);
        BigDecimal expensesP = sumOverMonths(monthly.expenses(), windowP);

        return Totals12mDto.builder()
                .income(totalsLine(incomeW, incomeP))
                .expenses(totalsLine(expensesW, expensesP))
                .saved(totalsLine(incomeW.subtract(expensesW), incomeP.subtract(expensesP)))
                .build();
    }

    private TotalsLineDto totalsLine(BigDecimal amount, BigDecimal previous) {
        return TotalsLineDto.builder()
                .amount(money(amount))
                .previous(money(previous))
                .change(totalsChange(amount, previous))
                .build();
    }

    /** {@code NO_HISTORY} when the previous 12 months summed to zero or less. */
    private ChangeDto totalsChange(BigDecimal amount, BigDecimal previous) {
        if (previous.signum() <= 0) {
            return ChangeDto.builder().status(NormStatus.NO_HISTORY).build();
        }
        return changeFrom(amount.subtract(previous), previous);
    }

    /**
     * A non-positive base has no meaningful percentage: dividing by a negative one flips the sign,
     * so growing from -50000 to +50000 would report -200% and a "falling" badge. Such a change is
     * reported as {@code NO_HISTORY} and the frontend hides the badge instead.
     */
    private ChangeDto changeFrom(BigDecimal delta, BigDecimal base) {
        if (base.signum() <= 0) {
            return ChangeDto.builder().status(NormStatus.NO_HISTORY).build();
        }
        BigDecimal percent = delta.divide(base, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
        return ChangeDto.builder().percent(percent).status(NormStatus.fromDeviationPercent(percent)).build();
    }

    // ─── Monthly amounts loading ──────────────────────────────────────────────

    private MonthlyAmounts loadMonthlyAmounts(UUID userId, LocalDate rangeStart, LocalDate rangeEnd) {
        Map<YearMonth, BigDecimal> income = toMonthlyMap(
                incomeRepository.findMonthlyNonTransferIncomeByUserIdAndDateBetween(userId, rangeStart, rangeEnd));
        Map<YearMonth, BigDecimal> expenses = toMonthlyMap(
                expenseRepository.findMonthlyNonTransferExpenseByUserIdAndDateBetween(userId, rangeStart, rangeEnd));
        return new MonthlyAmounts(income, expenses);
    }

    private Map<YearMonth, BigDecimal> toMonthlyMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(
                row -> YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue()),
                row -> (BigDecimal) row[2]));
    }

    private BigDecimal monthAmount(Map<YearMonth, BigDecimal> byMonth, YearMonth month) {
        return byMonth.getOrDefault(month, BigDecimal.ZERO);
    }

    private BigDecimal sumOverMonths(Map<YearMonth, BigDecimal> byMonth, List<YearMonth> months) {
        return months.stream().map(month -> monthAmount(byMonth, month)).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<YearMonth> monthsRange(YearMonth start, YearMonth end) {
        int count = (int) ChronoUnit.MONTHS.between(start, end) + 1;
        return IntStream.range(0, count).mapToObj(start::plusMonths).toList();
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    // ─── Internal helper types ────────────────────────────────────────────────

    private record Windows(List<YearMonth> w, List<YearMonth> p, List<YearMonth> history) {
    }

    private record MonthlyAmounts(Map<YearMonth, BigDecimal> income, Map<YearMonth, BigDecimal> expenses) {
    }

    private record PortfolioSnapshot(boolean available, PortfolioCurrentValuation valuation) {
    }

    private record PortfolioHistoryResult(Map<LocalDate, BigDecimal> valueByDate, boolean historyPending) {
    }

    private record YearOverYearChange(BigDecimal yearAgo, BigDecimal changeAbs, ChangeDto change) {
    }
}
