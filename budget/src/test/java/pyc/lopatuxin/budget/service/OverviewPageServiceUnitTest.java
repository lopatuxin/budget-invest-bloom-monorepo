package pyc.lopatuxin.budget.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.budget.dto.response.CapitalPointDto;
import pyc.lopatuxin.budget.dto.response.MonthTotalsDto;
import pyc.lopatuxin.budget.dto.response.OverviewPageResponseDto;
import pyc.lopatuxin.budget.entity.enums.NormStatus;
import pyc.lopatuxin.budget.repository.ExpenseRepository;
import pyc.lopatuxin.budget.repository.IncomeRepository;
import pyc.lopatuxin.shared.port.PortfolioCurrentValuation;
import pyc.lopatuxin.shared.port.PortfolioNextDividend;
import pyc.lopatuxin.shared.port.PortfolioReceivedPayout;
import pyc.lopatuxin.shared.port.PortfolioValuation;
import pyc.lopatuxin.shared.port.PortfolioValueAt;
import pyc.lopatuxin.shared.port.PortfolioValueSeries;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OverviewPageServiceUnitTest")
class OverviewPageServiceUnitTest {

    @Mock
    private IncomeRepository incomeRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private PersonalInflationCalculator personalInflationCalculator;

    @Mock
    private PortfolioValuation portfolioValuation;

    @InjectMocks
    private OverviewPageService overviewPageService;

    private final UUID userId = UUID.randomUUID();
    private final LocalDate today = LocalDate.now();
    private final YearMonth currentMonth = YearMonth.from(today);

    @BeforeEach
    void setUp() {
        // Default lenient stubs — a brand-new user with no records, no portfolio, no history.
        lenient().when(incomeRepository.sumByUserId(userId)).thenReturn(BigDecimal.ZERO);
        lenient().when(expenseRepository.sumByUserId(userId)).thenReturn(BigDecimal.ZERO);
        lenient().when(incomeRepository.sumByUserIdAndDateLessThanEqual(eq(userId), any()))
                .thenReturn(BigDecimal.ZERO);
        lenient().when(expenseRepository.sumByUserIdAndDateLessThanEqual(eq(userId), any()))
                .thenReturn(BigDecimal.ZERO);
        lenient().when(incomeRepository.findMonthlyNonTransferIncomeByUserIdAndDateBetween(eq(userId), any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(expenseRepository.findMonthlyNonTransferExpenseByUserIdAndDateBetween(eq(userId), any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(incomeRepository.findMonthlyIncomeByUserIdAndDateBetween(eq(userId), any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(expenseRepository.findMonthlyExpenseByUserIdAndDateBetween(eq(userId), any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(portfolioValuation.current(userId)).thenReturn(
                new PortfolioCurrentValuation(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO, null));
        lenient().when(portfolioValuation.receivedPayouts(userId)).thenReturn(List.of());
        lenient().when(portfolioValuation.valueAt(eq(userId), any())).thenAnswer(invocation -> {
            List<LocalDate> dates = invocation.getArgument(1);
            return new PortfolioValueSeries(dates.stream().map(date -> new PortfolioValueAt(date, BigDecimal.ZERO)).toList(),
                    false, false, List.of());
        });
        lenient().when(personalInflationCalculator.calculateOptional(eq(userId), anyInt(), anyInt(), any()))
                .thenReturn(Optional.empty());
    }

    // ─── asOf / currentMonth ──────────────────────────────────────────────────

    @Test
    @DisplayName("asOf и currentMonth должны отражать сегодняшнюю дату")
    void shouldSetAsOfAndCurrentMonth() {
        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        assertThat(result.getAsOf()).isEqualTo(today);
        assertThat(result.getCurrentMonth().getMonth()).isEqualTo(currentMonth.getMonthValue());
        assertThat(result.getCurrentMonth().getYear()).isEqualTo(currentMonth.getYear());
        assertThat(result.getCurrentMonth().getPartial()).isEqualTo(today.getDayOfMonth() != today.lengthOfMonth());
    }

    // ─── Window W and months ──────────────────────────────────────────────────

    @Test
    @DisplayName("Окно W должно содержать текущий месяц и 11 предыдущих в хронологическом порядке")
    void shouldBuildWindowWWithCurrentAndElevenPreviousMonths() {
        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        assertThat(result.getMonths()).hasSize(12);
        YearMonth expectedFirst = currentMonth.minusMonths(11);
        assertThat(result.getMonths().getFirst().getMonth()).isEqualTo(expectedFirst.getMonthValue());
        assertThat(result.getMonths().getFirst().getYear()).isEqualTo(expectedFirst.getYear());
        assertThat(result.getMonths().getLast().getMonth()).isEqualTo(currentMonth.getMonthValue());
        assertThat(result.getMonths().getLast().getYear()).isEqualTo(currentMonth.getYear());
    }

    @Test
    @DisplayName("saved может быть отрицательным при перерасходе, partial выставлен только у текущего месяца")
    void shouldAllowNegativeSavedAndMarkOnlyCurrentMonthPartial() {
        YearMonth overspentMonth = currentMonth.minusMonths(3);
        stubMonthlyAmounts(
                List.<Object[]>of(new Object[]{overspentMonth.getYear(), overspentMonth.getMonthValue(), new BigDecimal("10000.00")}),
                List.<Object[]>of(new Object[]{overspentMonth.getYear(), overspentMonth.getMonthValue(), new BigDecimal("15000.00")}));

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        MonthTotalsDto overspent = findMonth(result, overspentMonth);
        assertThat(overspent.getSaved()).isEqualByComparingTo("-5000.00");
        assertThat(overspent.getPartial()).isFalse();
        result.getMonths().stream()
                .filter(m -> !(m.getMonth() == currentMonth.getMonthValue() && m.getYear() == currentMonth.getYear()))
                .forEach(m -> assertThat(m.getPartial()).isFalse());
    }

    // ─── Capital history: 13 points, cumulative free money ───────────────────

    @Test
    @DisplayName("История капитала должна содержать 13 точек: концы 12 месяцев и сегодня")
    void shouldBuildThirteenHistoryPoints() {
        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        List<CapitalPointDto> history = result.getCapital().getHistory();
        assertThat(history).hasSize(13);
        assertThat(history.getFirst().getDate()).isEqualTo(currentMonth.minusMonths(12).atEndOfMonth());
        assertThat(history.get(11).getDate()).isEqualTo(currentMonth.minusMonths(1).atEndOfMonth());
        assertThat(history.getLast().getDate()).isEqualTo(today);
    }

    @Test
    @DisplayName("Свободные деньги в истории должны накапливаться от базовой суммы по месячным доходам и расходам")
    void shouldAccumulateFreeMoneyAcrossHistoryPoints() {
        LocalDate firstHistoryDate = currentMonth.minusMonths(12).atEndOfMonth();
        YearMonth secondMonth = currentMonth.minusMonths(11);
        when(incomeRepository.sumByUserIdAndDateLessThanEqual(userId, firstHistoryDate))
                .thenReturn(new BigDecimal("100000.00"));
        when(expenseRepository.sumByUserIdAndDateLessThanEqual(userId, firstHistoryDate))
                .thenReturn(new BigDecimal("40000.00"));
        stubMonthlyAllAmounts(
                List.<Object[]>of(new Object[]{secondMonth.getYear(), secondMonth.getMonthValue(), new BigDecimal("20000.00")}),
                List.<Object[]>of(new Object[]{secondMonth.getYear(), secondMonth.getMonthValue(), new BigDecimal("5000.00")}));

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        List<CapitalPointDto> history = result.getCapital().getHistory();
        assertThat(history.get(0).getFreeMoney()).isEqualByComparingTo("60000.00");
        assertThat(history.get(1).getFreeMoney()).isEqualByComparingTo("75000.00");
    }

    @Test
    @DisplayName("История капитала должна накапливать transfer-записи (инвестиционные операции), а не только обычные доходы/расходы")
    void shouldAccumulateTransferRecordsInHistoryToo() {
        LocalDate firstHistoryDate = currentMonth.minusMonths(12).atEndOfMonth();
        YearMonth secondMonth = currentMonth.minusMonths(11);
        when(incomeRepository.sumByUserIdAndDateLessThanEqual(userId, firstHistoryDate))
                .thenReturn(new BigDecimal("100000.00"));
        when(expenseRepository.sumByUserIdAndDateLessThanEqual(userId, firstHistoryDate))
                .thenReturn(new BigDecimal("40000.00"));
        // Only a transfer expense (e.g. a BUY) in the second month — findMonthlyNonTransfer... stays empty
        // (default stub), only the all-records findMonthlyExpenseByUserIdAndDateBetween sees it.
        stubMonthlyAllAmounts(
                Collections.emptyList(),
                List.<Object[]>of(new Object[]{secondMonth.getYear(), secondMonth.getMonthValue(), new BigDecimal("15000.00")}));

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        List<CapitalPointDto> history = result.getCapital().getHistory();
        assertThat(history.get(0).getFreeMoney()).isEqualByComparingTo("60000.00");
        assertThat(history.get(1).getFreeMoney()).isEqualByComparingTo("45000.00");
    }

    @Test
    @DisplayName("Капитал должен равняться сумме свободных денег и текущей стоимости портфеля")
    void shouldSumFreeMoneyAndPortfolioIntoCapitalTotal() {
        when(incomeRepository.sumByUserId(userId)).thenReturn(new BigDecimal("500000.00"));
        when(expenseRepository.sumByUserId(userId)).thenReturn(new BigDecimal("200000.00"));
        when(portfolioValuation.current(userId)).thenReturn(new PortfolioCurrentValuation(
                new BigDecimal("100000.00"), new BigDecimal("80000.00"), new BigDecimal("20000.00"), 5, BigDecimal.ZERO, null));

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        assertThat(result.getCapital().getFreeMoney()).isEqualByComparingTo("300000.00");
        assertThat(result.getCapital().getPortfolioValue()).isEqualByComparingTo("100000.00");
        assertThat(result.getCapital().getTotal()).isEqualByComparingTo("400000.00");
    }

    // ─── Free money / capital include transfer records ────────────────────────

    @Test
    @DisplayName("Transfer-расход (например, покупка бумаги) должен снижать свободные деньги и капитал")
    void transferExpenseShouldLowerFreeMoneyAndCapital() {
        when(incomeRepository.sumByUserId(userId)).thenReturn(new BigDecimal("500000.00"));
        // 200000 non-transfer + 50000 transfer (BUY), lumped into the same lifetime sum.
        when(expenseRepository.sumByUserId(userId)).thenReturn(new BigDecimal("250000.00"));

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        assertThat(result.getCapital().getFreeMoney()).isEqualByComparingTo("250000.00");
        assertThat(result.getCapital().getTotal()).isEqualByComparingTo("250000.00");
    }

    @Test
    @DisplayName("Transfer-доход (например, продажа или погашение облигации) должен повышать свободные деньги и капитал")
    void transferIncomeShouldRaiseFreeMoneyAndCapital() {
        // 300000 non-transfer + 71000 transfer (bond redemption), lumped into the same lifetime sum.
        when(incomeRepository.sumByUserId(userId)).thenReturn(new BigDecimal("371000.00"));
        when(expenseRepository.sumByUserId(userId)).thenReturn(new BigDecimal("200000.00"));

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        assertThat(result.getCapital().getFreeMoney()).isEqualByComparingTo("171000.00");
        assertThat(result.getCapital().getTotal()).isEqualByComparingTo("171000.00");
    }

    @Test
    @DisplayName("Погашение облигации не должно менять капитал: стоимость портфеля падает на X, свободные деньги растут на X")
    void bondRedemptionShouldLeaveCapitalTotalUnchanged() {
        // Before redemption: free money 100000, portfolio value 71000 (the bond about to be redeemed) → capital 171000.
        // After redemption: 71000 leaves the portfolio and arrives as isTransfer=true income, so lifetime income
        // grows by 71000 and free money becomes 171000, while portfolio value drops to 0. Capital must stay 171000.
        when(incomeRepository.sumByUserId(userId)).thenReturn(new BigDecimal("171000.00"));
        when(expenseRepository.sumByUserId(userId)).thenReturn(BigDecimal.ZERO);
        when(portfolioValuation.current(userId)).thenReturn(
                new PortfolioCurrentValuation(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO, null));

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        assertThat(result.getCapital().getFreeMoney()).isEqualByComparingTo("171000.00");
        assertThat(result.getCapital().getPortfolioValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getCapital().getTotal()).isEqualByComparingTo("171000.00");
    }

    // ─── Received payouts (dividends/coupons) raise free money and capital ────

    @Test
    @DisplayName("Полученная выплата (дивиденд/купон) должна повышать свободные деньги и капитал")
    void receivedPayoutsShouldRaiseFreeMoneyAndCapital() {
        when(incomeRepository.sumByUserId(userId)).thenReturn(new BigDecimal("500000.00"));
        when(expenseRepository.sumByUserId(userId)).thenReturn(new BigDecimal("200000.00"));
        when(portfolioValuation.receivedPayouts(userId)).thenReturn(List.of(
                new PortfolioReceivedPayout(today.minusDays(10), new BigDecimal("1000.00")),
                new PortfolioReceivedPayout(today.minusDays(5), new BigDecimal("500.00"))));

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        assertThat(result.getCapital().getFreeMoney()).isEqualByComparingTo("301500.00");
        assertThat(result.getCapital().getTotal()).isEqualByComparingTo("301500.00");
    }

    @Test
    @DisplayName("История капитала должна учитывать полученную выплату только с точки, дата которой не раньше даты её получения")
    void historyShouldAccumulateReceivedPayoutsOnlyFromTheirDateOnward() {
        LocalDate firstHistoryDate = currentMonth.minusMonths(12).atEndOfMonth();
        LocalDate payoutDate = firstHistoryDate.plusDays(3);
        when(portfolioValuation.receivedPayouts(userId)).thenReturn(
                List.of(new PortfolioReceivedPayout(payoutDate, new BigDecimal("1000.00"))));

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        List<CapitalPointDto> history = result.getCapital().getHistory();
        assertThat(history.get(0).getFreeMoney()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(history.get(1).getFreeMoney()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("Год назад была только полученная выплата — изменение капитала за год не должно считаться NO_HISTORY")
    void yearAgoOnlyReceivedPayoutShouldNotBeNoHistory() {
        LocalDate firstHistoryDate = currentMonth.minusMonths(12).atEndOfMonth();
        when(portfolioValuation.receivedPayouts(userId)).thenReturn(
                List.of(new PortfolioReceivedPayout(firstHistoryDate, new BigDecimal("1000.00"))));

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        assertThat(result.getCapital().getYearAgo()).isEqualByComparingTo("1000.00");
        assertThat(result.getCapital().getChange().getStatus()).isNotEqualTo(NormStatus.NO_HISTORY);
    }

    @Test
    @DisplayName("Исключение порта при получении выплат не должно ронять обзор — свободные деньги считаются без них")
    void shouldHandleReceivedPayoutsPortException() {
        when(portfolioValuation.receivedPayouts(userId)).thenThrow(new RuntimeException("биржа недоступна"));
        when(incomeRepository.sumByUserId(userId)).thenReturn(new BigDecimal("500000.00"));
        when(expenseRepository.sumByUserId(userId)).thenReturn(new BigDecimal("200000.00"));

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        assertThat(result.getCapital().getFreeMoney()).isEqualByComparingTo("300000.00");
        assertThat(result.getCapital().getTotal()).isEqualByComparingTo("300000.00");
    }

    @Test
    @DisplayName("Столбики месяцев, итоги за 12 месяцев, норма сбережений, баланс месяца и dividends12m портфеля не должны меняться от полученных выплат")
    void barsAndTotalsAndPortfolioDividends12mShouldIgnoreReceivedPayouts() {
        stubMonthlyAmounts(
                List.<Object[]>of(new Object[]{currentMonth.getYear(), currentMonth.getMonthValue(), new BigDecimal("100000.00")}),
                List.<Object[]>of(new Object[]{currentMonth.getYear(), currentMonth.getMonthValue(), new BigDecimal("40000.00")}));
        when(portfolioValuation.receivedPayouts(userId)).thenReturn(
                List.of(new PortfolioReceivedPayout(today.minusDays(2), new BigDecimal("5000.00"))));
        when(portfolioValuation.current(userId)).thenReturn(new PortfolioCurrentValuation(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, new BigDecimal("38200.00"), null));

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        MonthTotalsDto currentMonthTotals = findMonth(result, currentMonth);
        assertThat(currentMonthTotals.getIncome()).isEqualByComparingTo("100000.00");
        assertThat(currentMonthTotals.getExpenses()).isEqualByComparingTo("40000.00");
        assertThat(currentMonthTotals.getSaved()).isEqualByComparingTo("60000.00");
        assertThat(result.getCurrentMonthBalance()).isEqualByComparingTo("60000.00");
        assertThat(result.getSavings().getCurrentMonthRate()).isEqualTo(60);
        assertThat(result.getTotals12m().getIncome().getAmount()).isEqualByComparingTo("100000.00");
        assertThat(result.getPortfolio().getDividends12m()).isEqualByComparingTo("38200.00");
    }

    // ─── Statistics other than capital keep ignoring transfer records ────────

    @Test
    @DisplayName("Столбики месяцев, итоги за 12 месяцев, норма сбережений и баланс месяца должны игнорировать transfer-записи")
    void barsAndTotalsAndSavingsAndCurrentMonthBalanceShouldIgnoreTransfers() {
        // The non-transfer monthly map (bars/totals/savings/currentMonthBalance) sees only 100000/40000.
        // The all-records monthly map (capital history only) additionally carries a 50000 transfer expense —
        // it must not leak into any of the statistics asserted below.
        stubMonthlyAmounts(
                List.<Object[]>of(new Object[]{currentMonth.getYear(), currentMonth.getMonthValue(), new BigDecimal("100000.00")}),
                List.<Object[]>of(new Object[]{currentMonth.getYear(), currentMonth.getMonthValue(), new BigDecimal("40000.00")}));
        stubMonthlyAllAmounts(
                List.<Object[]>of(new Object[]{currentMonth.getYear(), currentMonth.getMonthValue(), new BigDecimal("100000.00")}),
                List.<Object[]>of(new Object[]{currentMonth.getYear(), currentMonth.getMonthValue(), new BigDecimal("90000.00")}));

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        MonthTotalsDto currentMonthTotals = findMonth(result, currentMonth);
        assertThat(currentMonthTotals.getIncome()).isEqualByComparingTo("100000.00");
        assertThat(currentMonthTotals.getExpenses()).isEqualByComparingTo("40000.00");
        assertThat(currentMonthTotals.getSaved()).isEqualByComparingTo("60000.00");
        assertThat(result.getCurrentMonthBalance()).isEqualByComparingTo("60000.00");
        assertThat(result.getSavings().getCurrentMonthRate()).isEqualTo(60);
        assertThat(result.getTotals12m().getIncome().getAmount()).isEqualByComparingTo("100000.00");
        assertThat(result.getTotals12m().getExpenses().getAmount()).isEqualByComparingTo("40000.00");
    }

    // ─── Year-over-year change ────────────────────────────────────────────────

    @Test
    @DisplayName("Изменение капитала за год должно быть NO_HISTORY, если год назад не было записей")
    void shouldReturnNoHistoryChangeWhenNoDataYearAgo() {
        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        assertThat(result.getCapital().getYearAgo()).isNull();
        assertThat(result.getCapital().getChangeAbs()).isNull();
        assertThat(result.getCapital().getChange().getPercent()).isNull();
        assertThat(result.getCapital().getChange().getStatus()).isEqualTo(NormStatus.NO_HISTORY);
    }

    @Test
    @DisplayName("Должен рассчитать процент и статус изменения капитала за год при наличии истории")
    void shouldCalculateYearOverYearChangeWhenHistoryExists() {
        LocalDate firstHistoryDate = currentMonth.minusMonths(12).atEndOfMonth();
        when(incomeRepository.sumByUserIdAndDateLessThanEqual(userId, firstHistoryDate))
                .thenReturn(new BigDecimal("200000.00"));
        when(expenseRepository.sumByUserIdAndDateLessThanEqual(userId, firstHistoryDate))
                .thenReturn(new BigDecimal("100000.00"));
        when(incomeRepository.sumByUserId(userId)).thenReturn(new BigDecimal("300000.00"));
        when(expenseRepository.sumByUserId(userId)).thenReturn(new BigDecimal("100000.00"));

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        // yearAgo=100000, now=200000, changeAbs=100000, percent=100.0% → ABOVE_MUCH
        assertThat(result.getCapital().getYearAgo()).isEqualByComparingTo("100000.00");
        assertThat(result.getCapital().getChangeAbs()).isEqualByComparingTo("100000.00");
        assertThat(result.getCapital().getChange().getPercent()).isEqualByComparingTo("100.0");
        assertThat(result.getCapital().getChange().getStatus()).isEqualTo(NormStatus.ABOVE_MUCH);
    }

    @Test
    @DisplayName("Изменение капитала за год не должно давать процент, если год назад капитал был отрицательным")
    void shouldNotReportPercentWhenCapitalYearAgoWasNegative() {
        LocalDate firstHistoryDate = currentMonth.minusMonths(12).atEndOfMonth();
        when(incomeRepository.sumByUserIdAndDateLessThanEqual(userId, firstHistoryDate))
                .thenReturn(new BigDecimal("100000.00"));
        when(expenseRepository.sumByUserIdAndDateLessThanEqual(userId, firstHistoryDate))
                .thenReturn(new BigDecimal("150000.00"));
        when(incomeRepository.sumByUserId(userId)).thenReturn(new BigDecimal("200000.00"));
        when(expenseRepository.sumByUserId(userId)).thenReturn(new BigDecimal("150000.00"));

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        // yearAgo=-50000, now=+50000: a percentage against a negative base would flip the sign.
        assertThat(result.getCapital().getYearAgo()).isEqualByComparingTo("-50000.00");
        assertThat(result.getCapital().getChangeAbs()).isEqualByComparingTo("100000.00");
        assertThat(result.getCapital().getChange().getPercent()).isNull();
        assertThat(result.getCapital().getChange().getStatus()).isEqualTo(NormStatus.NO_HISTORY);
    }

    // ─── Savings rate ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Норма сбережений за 12 месяцев должна округляться до целого по HALF_UP")
    void shouldCalculateSavingsRate12m() {
        stubMonthlyAmounts(
                List.<Object[]>of(new Object[]{currentMonth.getYear(), currentMonth.getMonthValue(), new BigDecimal("150000.00")}),
                List.<Object[]>of(new Object[]{currentMonth.getYear(), currentMonth.getMonthValue(), new BigDecimal("100500.00")}));

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        // (150000-100500)/150000*100 = 33.0%
        assertThat(result.getSavings().getRate12m()).isEqualTo(33);
    }

    @Test
    @DisplayName("Норма сбережений за 12 месяцев должна быть null при нулевых доходах в окне")
    void shouldReturnNullSavingsRateWhenNoIncomeInWindow() {
        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        assertThat(result.getSavings().getRate12m()).isNull();
    }

    @Test
    @DisplayName("Норма сбережений и баланс текущего месяца должны считаться по тем же помесячным суммам, что и столбик месяца")
    void shouldCalculateCurrentMonthSavingsRate() {
        stubMonthlyAmounts(
                List.<Object[]>of(new Object[]{currentMonth.getYear(), currentMonth.getMonthValue(), new BigDecimal("100000.00")}),
                List.<Object[]>of(new Object[]{currentMonth.getYear(), currentMonth.getMonthValue(), new BigDecimal("51000.00")}));

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        assertThat(result.getSavings().getCurrentMonthRate()).isEqualTo(49);
        assertThat(result.getCurrentMonthBalance()).isEqualByComparingTo("49000.00");
        assertThat(findMonth(result, currentMonth).getSaved()).isEqualByComparingTo("49000.00");
    }

    // ─── Totals 12m thresholds ────────────────────────────────────────────────

    @ParameterizedTest(name = "amount={0}, previous={1} → {2}")
    @DisplayName("Итоги за 12 месяцев: статус изменения должен определяться порогами")
    @CsvSource({
            "110000.00, 100000.00, NORMAL",
            "110100.00, 100000.00, ABOVE",
            "150100.00, 100000.00, ABOVE_MUCH",
            "89900.00,  100000.00, BELOW"
    })
    void shouldResolveTotals12mStatusByThresholds(BigDecimal amount, BigDecimal previous, NormStatus expectedStatus) {
        stubMonthlyAmounts(
                List.of(
                        new Object[]{currentMonth.getYear(), currentMonth.getMonthValue(), amount},
                        new Object[]{currentMonth.minusMonths(12).getYear(), currentMonth.minusMonths(12).getMonthValue(), previous}
                ),
                Collections.emptyList());

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        assertThat(result.getTotals12m().getIncome().getChange().getStatus()).isEqualTo(expectedStatus);
    }

    @Test
    @DisplayName("Итоги за 12 месяцев должны быть NO_HISTORY, если предыдущие 12 месяцев дали ноль или меньше")
    void shouldReturnNoHistoryTotalsWhenPreviousIsZeroOrLess() {
        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        assertThat(result.getTotals12m().getIncome().getChange().getStatus()).isEqualTo(NormStatus.NO_HISTORY);
        assertThat(result.getTotals12m().getIncome().getChange().getPercent()).isNull();
    }

    // ─── Portfolio port failures do not fail the page ────────────────────────

    @Test
    @DisplayName("Исключение порта при получении текущей оценки не должно ронять обзор")
    void shouldHandlePortfolioCurrentException() {
        when(portfolioValuation.current(userId)).thenThrow(new RuntimeException("биржа недоступна"));
        when(incomeRepository.sumByUserId(userId)).thenReturn(new BigDecimal("500000.00"));
        when(expenseRepository.sumByUserId(userId)).thenReturn(new BigDecimal("200000.00"));

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        assertThat(result.getPortfolio().getAvailable()).isFalse();
        assertThat(result.getCapital().getPortfolioValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getCapital().getFreeMoney()).isEqualByComparingTo("300000.00");
        assertThat(result.getCapital().getTotal()).isEqualByComparingTo("300000.00");
    }

    @Test
    @DisplayName("Флаг portfolioHistoryPending должен пробрасываться из порта")
    void shouldPropagatePortfolioHistoryPendingFlag() {
        when(portfolioValuation.valueAt(eq(userId), any())).thenAnswer(invocation -> {
            List<LocalDate> dates = invocation.getArgument(1);
            return new PortfolioValueSeries(
                    dates.stream().map(date -> new PortfolioValueAt(date, BigDecimal.ZERO)).toList(), true, false, List.of());
        });

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        assertThat(result.getCapital().getPortfolioHistoryPending()).isTrue();
    }

    @Test
    @DisplayName("Флаги pricesStale/staleTickers должны пробрасываться из порта отдельно от portfolioHistoryPending")
    void shouldPropagatePricesStaleFlagSeparatelyFromHistoryPending() {
        when(portfolioValuation.valueAt(eq(userId), any())).thenAnswer(invocation -> {
            List<LocalDate> dates = invocation.getArgument(1);
            return new PortfolioValueSeries(
                    dates.stream().map(date -> new PortfolioValueAt(date, BigDecimal.ZERO)).toList(),
                    false, true, List.of("SBER"));
        });

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        assertThat(result.getCapital().getPortfolioHistoryPending()).isFalse();
        assertThat(result.getCapital().getPricesStale()).isTrue();
        assertThat(result.getCapital().getStaleTickers()).containsExactly("SBER");
    }

    // ─── Portfolio tile ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Без бумаг в портфеле бейдж прибыли должен отсутствовать")
    void shouldOmitPnlBadgeWhenNoAssets() {
        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        assertThat(result.getPortfolio().getAssetsCount()).isZero();
        assertThat(result.getPortfolio().getPnl()).isNull();
    }

    @Test
    @DisplayName("Бейдж прибыли портфеля должен считаться от себестоимости")
    void shouldCalculatePnlBadgeFromCost() {
        when(portfolioValuation.current(userId)).thenReturn(new PortfolioCurrentValuation(
                new BigDecimal("112000.00"), new BigDecimal("100000.00"), new BigDecimal("12000.00"), 3,
                new BigDecimal("2000.00"), null));

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        assertThat(result.getPortfolio().getPnl().getPercent()).isEqualByComparingTo("12.0");
        assertThat(result.getPortfolio().getPnl().getStatus()).isEqualTo(NormStatus.ABOVE);
    }

    @Test
    @DisplayName("Ближайшая выплата дивидендов без даты выплаты должна пробрасывать только дату отсечки")
    void shouldMapNextDividendWithoutPaymentDate() {
        PortfolioNextDividend dividend = new PortfolioNextDividend(
                "LKOH", "ЛУКОЙЛ", LocalDate.of(2026, 10, 3), null, new BigDecimal("4800.00"), "RUB", null);
        when(portfolioValuation.current(userId)).thenReturn(new PortfolioCurrentValuation(
                new BigDecimal("100000.00"), new BigDecimal("90000.00"), new BigDecimal("10000.00"), 5,
                new BigDecimal("38200.00"), dividend));

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        assertThat(result.getPortfolio().getNextDividend().getTicker()).isEqualTo("LKOH");
        assertThat(result.getPortfolio().getNextDividend().getTotalAmount()).isEqualByComparingTo("4800.00");
        assertThat(result.getPortfolio().getNextDividend().getRecordDate()).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(result.getPortfolio().getNextDividend().getPaymentDate()).isNull();
        assertThat(result.getPortfolio().getDividends12m()).isEqualByComparingTo("38200.00");
    }

    @Test
    @DisplayName("Ближайшая выплата дивидендов с известной датой выплаты должна пробрасывать её из порта")
    void shouldMapNextDividendWithPaymentDate() {
        PortfolioNextDividend dividend = new PortfolioNextDividend(
                "SBER", "Сбербанк", LocalDate.of(2026, 7, 18), LocalDate.of(2026, 8, 1), new BigDecimal("3484.00"), "RUB", null);
        when(portfolioValuation.current(userId)).thenReturn(new PortfolioCurrentValuation(
                new BigDecimal("100000.00"), new BigDecimal("90000.00"), new BigDecimal("10000.00"), 5,
                new BigDecimal("38200.00"), dividend));

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        assertThat(result.getPortfolio().getNextDividend().getRecordDate()).isEqualTo(LocalDate.of(2026, 7, 18));
        assertThat(result.getPortfolio().getNextDividend().getPaymentDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    }

    @Test
    @DisplayName("Валюта ближайшей выплаты дивидендов должна пробрасываться в DTO страницы обзора")
    void shouldMapNextDividendCurrency() {
        PortfolioNextDividend dividend = new PortfolioNextDividend(
                "AAPL", "Apple", LocalDate.of(2026, 10, 3), null, new BigDecimal("20.00"), "USD", null);
        when(portfolioValuation.current(userId)).thenReturn(new PortfolioCurrentValuation(
                new BigDecimal("100000.00"), new BigDecimal("90000.00"), new BigDecimal("10000.00"), 5,
                new BigDecimal("38200.00"), dividend));

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        assertThat(result.getPortfolio().getNextDividend().getCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("Вид ближайшей выплаты (купон облигации) должен пробрасываться в DTO страницы обзора")
    void shouldMapNextDividendKind() {
        PortfolioNextDividend dividend = new PortfolioNextDividend(
                "SU26219RMFS4", "ОФЗ 26219", LocalDate.of(2026, 10, 3), null,
                new BigDecimal("38.64"), "RUB", pyc.lopatuxin.shared.port.PayoutKind.COUPON);
        when(portfolioValuation.current(userId)).thenReturn(new PortfolioCurrentValuation(
                new BigDecimal("100000.00"), new BigDecimal("90000.00"), new BigDecimal("10000.00"), 5,
                new BigDecimal("38200.00"), dividend));

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        assertThat(result.getPortfolio().getNextDividend().getKind()).isEqualTo(pyc.lopatuxin.shared.port.PayoutKind.COUPON);
    }

    // ─── Personal inflation ───────────────────────────────────────────────────

    @Test
    @DisplayName("Личная инфляция должна быть null при отсутствии данных за прошлый год")
    void shouldReturnNullPersonalInflationWhenNoData() {
        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        assertThat(result.getPersonalInflationPercent()).isNull();
    }

    @Test
    @DisplayName("Личная инфляция должна быть взята из PersonalInflationCalculator")
    void shouldReturnPersonalInflationFromCalculator() {
        when(personalInflationCalculator.calculateOptional(
                eq(userId), eq(today.getMonthValue()), eq(today.getYear()), any()))
                .thenReturn(Optional.of(new BigDecimal("5.3")));

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        assertThat(result.getPersonalInflationPercent()).isEqualByComparingTo("5.3");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void stubMonthlyAmounts(List<Object[]> incomeRows, List<Object[]> expenseRows) {
        when(incomeRepository.findMonthlyNonTransferIncomeByUserIdAndDateBetween(eq(userId), any(), any()))
                .thenReturn(incomeRows);
        when(expenseRepository.findMonthlyNonTransferExpenseByUserIdAndDateBetween(eq(userId), any(), any()))
                .thenReturn(expenseRows);
    }

    /** Stubs the all-records (transfers included) monthly queries used only for capital history. */
    private void stubMonthlyAllAmounts(List<Object[]> incomeRows, List<Object[]> expenseRows) {
        when(incomeRepository.findMonthlyIncomeByUserIdAndDateBetween(eq(userId), any(), any()))
                .thenReturn(incomeRows);
        when(expenseRepository.findMonthlyExpenseByUserIdAndDateBetween(eq(userId), any(), any()))
                .thenReturn(expenseRows);
    }

    private MonthTotalsDto findMonth(OverviewPageResponseDto result, YearMonth month) {
        return result.getMonths().stream()
                .filter(m -> m.getMonth() == month.getMonthValue() && m.getYear() == month.getYear())
                .findFirst()
                .orElseThrow();
    }
}
