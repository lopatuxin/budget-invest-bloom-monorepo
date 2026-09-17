package pyc.lopatuxin.budget.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
import pyc.lopatuxin.budget.util.ComparisonMath;
import pyc.lopatuxin.shared.port.PortfolioCurrentValuation;
import pyc.lopatuxin.shared.port.PortfolioNextDividend;
import pyc.lopatuxin.shared.port.PortfolioReceivedPayout;
import pyc.lopatuxin.shared.port.PortfolioValuation;
import pyc.lopatuxin.shared.port.PortfolioValueAt;
import pyc.lopatuxin.shared.port.PortfolioValueSeries;

import java.math.BigDecimal;
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
// Not wrapped in a class-level @Transactional: loadCurrentPortfolio below crosses into the
// investment module (PortfolioValuation.current), which hits the exchange over the network
// before it is done with the database. The monolith's single Hikari pool has only 5
// connections (see app/src/main/resources/application.yml) shared across budget and
// investment — a transaction spanning this whole method would pin one of those connections
// for as long as MOEX takes to answer, and a couple of concurrent page loads during a slow
// exchange response could exhaust the pool. Each repository call below still runs in its own
// short Spring Data transaction, same as PortfolioValuationAdapter/PortfolioService already do
// on the investment side for the same reason.
@Slf4j
@Service
@RequiredArgsConstructor
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

        // Free money for capital includes isTransfer=true records, unlike monthly.income()/expenses()
        // above: an investment purchase or sale must not disappear from or double-count in capital.
        // Received dividends/coupons are added on top of that: they are never budget records at
        // all (see loadReceivedPayouts), so without this they would be missing from capital entirely.
        List<PortfolioReceivedPayout> receivedPayouts = loadReceivedPayouts(userId);
        BigDecimal freeMoneyNow = incomeRepository.sumByUserId(userId)
                .subtract(expenseRepository.sumByUserId(userId))
                .add(sumPayoutsUpTo(receivedPayouts, today));
        PortfolioSnapshot currentPortfolio = loadCurrentPortfolio(userId);

        CapitalSectionDto capital = buildCapitalSection(
                userId, currentMonth, today, windows.history(), freeMoneyNow, currentPortfolio, receivedPayouts);

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
                .currentMonthBalance(ComparisonMath.money(monthAmount(monthly.income(), currentMonth)
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
                                                  List<YearMonth> historyMonths,
                                                  BigDecimal freeMoneyNow, PortfolioSnapshot currentPortfolio,
                                                  List<PortfolioReceivedPayout> receivedPayouts) {
        List<LocalDate> historyDates = historyMonths.stream().map(YearMonth::atEndOfMonth).toList();
        // History points include isTransfer=true records too — same rule as freeMoneyNow above.
        BigDecimal baseIncome = incomeRepository.sumByUserIdAndDateLessThanEqual(userId, historyDates.getFirst());
        BigDecimal baseExpense = expenseRepository.sumByUserIdAndDateLessThanEqual(userId, historyDates.getFirst());
        MonthlyAmounts monthlyAll = loadMonthlyAllAmounts(userId, historyMonths.getFirst().atDay(1), historyDates.getLast());

        PortfolioHistoryResult portfolioHistory = loadPortfolioHistory(userId, historyDates);
        List<CapitalPointDto> points = buildHistoryPoints(
                historyMonths, historyDates, monthlyAll, baseIncome, baseExpense, portfolioHistory.valueByDate(), receivedPayouts);

        BigDecimal lastPortfolioValue = currentPortfolio.available()
                ? currentPortfolio.valuation().totalValue() : BigDecimal.ZERO;
        BigDecimal capitalTotal = freeMoneyNow.add(lastPortfolioValue);
        points.add(CapitalPointDto.builder()
                .month(currentMonth.getMonthValue())
                .year(currentMonth.getYear())
                .date(today)
                .freeMoney(ComparisonMath.money(freeMoneyNow))
                .portfolioValue(ComparisonMath.money(lastPortfolioValue))
                .total(ComparisonMath.money(capitalTotal))
                .build());

        BigDecimal payoutsAtFirstPoint = sumPayoutsUpTo(receivedPayouts, historyDates.getFirst());
        YearOverYearChange yoy = computeYearOverYearChange(baseIncome, baseExpense, payoutsAtFirstPoint, points.getFirst(), capitalTotal);

        return CapitalSectionDto.builder()
                .total(ComparisonMath.money(capitalTotal))
                .freeMoney(ComparisonMath.money(freeMoneyNow))
                .portfolioValue(ComparisonMath.money(lastPortfolioValue))
                .yearAgo(ComparisonMath.money(yoy.yearAgo()))
                .changeAbs(ComparisonMath.money(yoy.changeAbs()))
                .change(yoy.change())
                .history(points)
                .portfolioHistoryPending(portfolioHistory.historyPending())
                .pricesStale(portfolioHistory.pricesStale())
                .staleTickers(portfolioHistory.staleTickers())
                .build();
    }

    private List<CapitalPointDto> buildHistoryPoints(List<YearMonth> months, List<LocalDate> dates, MonthlyAmounts monthlyAll,
                                                      BigDecimal baseIncome, BigDecimal baseExpense,
                                                      Map<LocalDate, BigDecimal> portfolioByDate,
                                                      List<PortfolioReceivedPayout> receivedPayouts) {
        List<CapitalPointDto> points = new ArrayList<>();
        BigDecimal cumulativeIncome = baseIncome;
        BigDecimal cumulativeExpense = baseExpense;
        for (int i = 0; i < months.size(); i++) {
            YearMonth month = months.get(i);
            if (i > 0) {
                cumulativeIncome = cumulativeIncome.add(monthAmount(monthlyAll.income(), month));
                cumulativeExpense = cumulativeExpense.add(monthAmount(monthlyAll.expenses(), month));
            }
            // Received payouts are not budget records (see loadReceivedPayouts), so they are
            // added on top of the cumulative income/expenses here instead of folded into monthlyAll.
            BigDecimal freeMoney = cumulativeIncome.subtract(cumulativeExpense)
                    .add(sumPayoutsUpTo(receivedPayouts, dates.get(i)));
            BigDecimal portfolioValue = portfolioByDate.getOrDefault(dates.get(i), BigDecimal.ZERO);
            BigDecimal total = freeMoney.add(portfolioValue);
            points.add(CapitalPointDto.builder()
                    .month(month.getMonthValue())
                    .year(month.getYear())
                    .date(dates.get(i))
                    .freeMoney(ComparisonMath.money(freeMoney))
                    .portfolioValue(ComparisonMath.money(portfolioValue))
                    .total(ComparisonMath.money(total))
                    .build());
        }
        return points;
    }

    /**
     * Year-over-year change is {@code NO_HISTORY} when the user had no income, no expenses, no
     * received payouts and no portfolio value at all a year ago (the first history point) — a
     * brand-new account, not just a coincidental zero total. A user whose only history a year ago
     * is a received dividend/coupon is not a brand-new account either, hence payoutsAtFirstPoint.
     */
    private YearOverYearChange computeYearOverYearChange(BigDecimal baseIncome, BigDecimal baseExpense,
                                                          BigDecimal payoutsAtFirstPoint,
                                                          CapitalPointDto firstPoint, BigDecimal capitalTotal) {
        boolean noHistory = baseIncome.signum() == 0 && baseExpense.signum() == 0
                && payoutsAtFirstPoint.signum() == 0 && firstPoint.getPortfolioValue().signum() == 0;
        if (noHistory) {
            return new YearOverYearChange(null, null, ChangeDto.builder().status(NormStatus.NO_HISTORY).build());
        }
        BigDecimal yearAgo = firstPoint.getTotal();
        BigDecimal changeAbs = capitalTotal.subtract(yearAgo);
        return new YearOverYearChange(yearAgo, changeAbs, ComparisonMath.changeFrom(changeAbs, yearAgo));
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

    // Received dividends/coupons are never written as budget records (unlike a buy, sale or bond
    // redemption, which land as isTransfer=true income/expense rows) — this is the one place that
    // pulls that money into free money, reusing the investment side's own received-payout rules
    // (PortfolioService.getReceivedPayouts) instead of duplicating them here.
    private List<PortfolioReceivedPayout> loadReceivedPayouts(UUID userId) {
        try {
            return portfolioValuation.receivedPayouts(userId);
        } catch (RuntimeException e) {
            log.warn("Не удалось получить полученные выплаты по портфелю для userId={}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    private BigDecimal sumPayoutsUpTo(List<PortfolioReceivedPayout> payouts, LocalDate date) {
        return payouts.stream()
                .filter(p -> !p.receivedDate().isAfter(date))
                .map(PortfolioReceivedPayout::netAmountRub)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private PortfolioHistoryResult loadPortfolioHistory(UUID userId, List<LocalDate> dates) {
        try {
            PortfolioValueSeries series = portfolioValuation.valueAt(userId, dates);
            Map<LocalDate, BigDecimal> valueByDate = series.points().stream()
                    .collect(Collectors.toMap(PortfolioValueAt::date,
                            point -> point.value() == null ? BigDecimal.ZERO : point.value()));
            return new PortfolioHistoryResult(valueByDate, series.historyPending(), series.pricesStale(), series.staleTickers());
        } catch (RuntimeException e) {
            log.warn("Не удалось получить историю стоимости портфеля для userId={}: {}", userId, e.getMessage());
            return new PortfolioHistoryResult(Map.of(), false, false, List.of());
        }
    }

    private PortfolioSectionDto buildPortfolioSection(PortfolioSnapshot snapshot) {
        if (!snapshot.available()) {
            return PortfolioSectionDto.builder().available(false).assetsCount(0).build();
        }

        PortfolioCurrentValuation valuation = snapshot.valuation();
        ChangeDto pnl = valuation.assetsCount() > 0 ? ComparisonMath.changeFrom(valuation.totalPnl(), valuation.totalCost()) : null;

        return PortfolioSectionDto.builder()
                .available(true)
                .value(ComparisonMath.money(valuation.totalValue()))
                .cost(ComparisonMath.money(valuation.totalCost()))
                .pnlAmount(ComparisonMath.money(valuation.totalPnl()))
                .pnl(pnl)
                .assetsCount(valuation.assetsCount())
                .dividends12m(ComparisonMath.money(valuation.dividends12m()))
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
                .recordDate(dividend.recordDate())
                .paymentDate(dividend.paymentDate())
                .totalAmount(ComparisonMath.money(dividend.totalAmount()))
                .currency(dividend.currency())
                .kind(dividend.kind())
                .build();
    }

    // ─── Savings, months, totals ──────────────────────────────────────────────

    private SavingsSectionDto buildSavingsSection(MonthlyAmounts monthly, List<YearMonth> windowW,
                                                  YearMonth currentMonth) {
        BigDecimal windowIncome = sumOverMonths(monthly.income(), windowW);
        BigDecimal windowExpenses = sumOverMonths(monthly.expenses(), windowW);
        return SavingsSectionDto.builder()
                .rate12m(ComparisonMath.savingsRate(windowIncome, windowExpenses))
                .currentMonthRate(ComparisonMath.savingsRate(monthAmount(monthly.income(), currentMonth),
                        monthAmount(monthly.expenses(), currentMonth)))
                .build();
    }

    private List<MonthTotalsDto> buildMonths(List<YearMonth> windowW, MonthlyAmounts monthly,
                                             YearMonth currentMonth, boolean currentMonthPartial) {
        return windowW.stream().map(month -> {
            BigDecimal income = monthAmount(monthly.income(), month);
            BigDecimal expenses = monthAmount(monthly.expenses(), month);
            return MonthTotalsDto.builder()
                    .month(month.getMonthValue())
                    .year(month.getYear())
                    .income(ComparisonMath.money(income))
                    .expenses(ComparisonMath.money(expenses))
                    .saved(ComparisonMath.money(income.subtract(expenses)))
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
                .amount(ComparisonMath.money(amount))
                .previous(ComparisonMath.money(previous))
                .change(totalsChange(amount, previous))
                .build();
    }

    /** {@code NO_HISTORY} when the previous 12 months summed to zero or less. */
    private ChangeDto totalsChange(BigDecimal amount, BigDecimal previous) {
        if (previous.signum() <= 0) {
            return ChangeDto.builder().status(NormStatus.NO_HISTORY).build();
        }
        return ComparisonMath.changeFrom(amount.subtract(previous), previous);
    }

    // ─── Monthly amounts loading ──────────────────────────────────────────────

    private MonthlyAmounts loadMonthlyAmounts(UUID userId, LocalDate rangeStart, LocalDate rangeEnd) {
        Map<YearMonth, BigDecimal> income = toMonthlyMap(
                incomeRepository.findMonthlyNonTransferIncomeByUserIdAndDateBetween(userId, rangeStart, rangeEnd));
        Map<YearMonth, BigDecimal> expenses = toMonthlyMap(
                expenseRepository.findMonthlyNonTransferExpenseByUserIdAndDateBetween(userId, rangeStart, rangeEnd));
        return new MonthlyAmounts(income, expenses);
    }

    /** Same as {@link #loadMonthlyAmounts}, but including isTransfer=true records — for capital history only. */
    private MonthlyAmounts loadMonthlyAllAmounts(UUID userId, LocalDate rangeStart, LocalDate rangeEnd) {
        Map<YearMonth, BigDecimal> income = toMonthlyMap(
                incomeRepository.findMonthlyIncomeByUserIdAndDateBetween(userId, rangeStart, rangeEnd));
        Map<YearMonth, BigDecimal> expenses = toMonthlyMap(
                expenseRepository.findMonthlyExpenseByUserIdAndDateBetween(userId, rangeStart, rangeEnd));
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

    // ─── Internal helper types ────────────────────────────────────────────────

    private record Windows(List<YearMonth> w, List<YearMonth> p, List<YearMonth> history) {
    }

    private record MonthlyAmounts(Map<YearMonth, BigDecimal> income, Map<YearMonth, BigDecimal> expenses) {
    }

    private record PortfolioSnapshot(boolean available, PortfolioCurrentValuation valuation) {
    }

    private record PortfolioHistoryResult(Map<LocalDate, BigDecimal> valueByDate, boolean historyPending,
                                          boolean pricesStale, List<String> staleTickers) {
    }

    private record YearOverYearChange(BigDecimal yearAgo, BigDecimal changeAbs, ChangeDto change) {
    }
}
