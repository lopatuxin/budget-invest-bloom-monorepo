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
        lenient().when(incomeRepository.sumNonTransferByUserId(userId)).thenReturn(BigDecimal.ZERO);
        lenient().when(expenseRepository.sumNonTransferByUserId(userId)).thenReturn(BigDecimal.ZERO);
        lenient().when(incomeRepository.sumNonTransferByUserIdAndDateLessThanEqual(eq(userId), any()))
                .thenReturn(BigDecimal.ZERO);
        lenient().when(expenseRepository.sumNonTransferByUserIdAndDateLessThanEqual(eq(userId), any()))
                .thenReturn(BigDecimal.ZERO);
        lenient().when(incomeRepository.findMonthlyNonTransferIncomeByUserIdAndDateBetween(eq(userId), any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(expenseRepository.findMonthlyNonTransferExpenseByUserIdAndDateBetween(eq(userId), any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(portfolioValuation.current(userId)).thenReturn(
                new PortfolioCurrentValuation(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO, null));
        lenient().when(portfolioValuation.valueAt(eq(userId), any())).thenAnswer(invocation -> {
            List<LocalDate> dates = invocation.getArgument(1);
            return new PortfolioValueSeries(dates.stream().map(date -> new PortfolioValueAt(date, BigDecimal.ZERO)).toList(), false);
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
        when(incomeRepository.sumNonTransferByUserIdAndDateLessThanEqual(userId, firstHistoryDate))
                .thenReturn(new BigDecimal("100000.00"));
        when(expenseRepository.sumNonTransferByUserIdAndDateLessThanEqual(userId, firstHistoryDate))
                .thenReturn(new BigDecimal("40000.00"));
        stubMonthlyAmounts(
                List.<Object[]>of(new Object[]{secondMonth.getYear(), secondMonth.getMonthValue(), new BigDecimal("20000.00")}),
                List.<Object[]>of(new Object[]{secondMonth.getYear(), secondMonth.getMonthValue(), new BigDecimal("5000.00")}));

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        List<CapitalPointDto> history = result.getCapital().getHistory();
        assertThat(history.get(0).getFreeMoney()).isEqualByComparingTo("60000.00");
        assertThat(history.get(1).getFreeMoney()).isEqualByComparingTo("75000.00");
    }

    @Test
    @DisplayName("Капитал должен равняться сумме свободных денег и текущей стоимости портфеля")
    void shouldSumFreeMoneyAndPortfolioIntoCapitalTotal() {
        when(incomeRepository.sumNonTransferByUserId(userId)).thenReturn(new BigDecimal("500000.00"));
        when(expenseRepository.sumNonTransferByUserId(userId)).thenReturn(new BigDecimal("200000.00"));
        when(portfolioValuation.current(userId)).thenReturn(new PortfolioCurrentValuation(
                new BigDecimal("100000.00"), new BigDecimal("80000.00"), new BigDecimal("20000.00"), 5, BigDecimal.ZERO, null));

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        assertThat(result.getCapital().getFreeMoney()).isEqualByComparingTo("300000.00");
        assertThat(result.getCapital().getPortfolioValue()).isEqualByComparingTo("100000.00");
        assertThat(result.getCapital().getTotal()).isEqualByComparingTo("400000.00");
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
        when(incomeRepository.sumNonTransferByUserIdAndDateLessThanEqual(userId, firstHistoryDate))
                .thenReturn(new BigDecimal("200000.00"));
        when(expenseRepository.sumNonTransferByUserIdAndDateLessThanEqual(userId, firstHistoryDate))
                .thenReturn(new BigDecimal("100000.00"));
        when(incomeRepository.sumNonTransferByUserId(userId)).thenReturn(new BigDecimal("300000.00"));
        when(expenseRepository.sumNonTransferByUserId(userId)).thenReturn(new BigDecimal("100000.00"));

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
        when(incomeRepository.sumNonTransferByUserIdAndDateLessThanEqual(userId, firstHistoryDate))
                .thenReturn(new BigDecimal("100000.00"));
        when(expenseRepository.sumNonTransferByUserIdAndDateLessThanEqual(userId, firstHistoryDate))
                .thenReturn(new BigDecimal("150000.00"));
        when(incomeRepository.sumNonTransferByUserId(userId)).thenReturn(new BigDecimal("200000.00"));
        when(expenseRepository.sumNonTransferByUserId(userId)).thenReturn(new BigDecimal("150000.00"));

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
        when(incomeRepository.sumNonTransferByUserId(userId)).thenReturn(new BigDecimal("500000.00"));
        when(expenseRepository.sumNonTransferByUserId(userId)).thenReturn(new BigDecimal("200000.00"));

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
            return new PortfolioValueSeries(dates.stream().map(date -> new PortfolioValueAt(date, BigDecimal.ZERO)).toList(), true);
        });

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        assertThat(result.getCapital().getPortfolioHistoryPending()).isTrue();
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
    @DisplayName("Ближайшая выплата дивидендов должна пробрасываться из порта")
    void shouldMapNextDividend() {
        PortfolioNextDividend dividend = new PortfolioNextDividend(
                "LKOH", "ЛУКОЙЛ", LocalDate.of(2026, 10, 3), new BigDecimal("4800.00"));
        when(portfolioValuation.current(userId)).thenReturn(new PortfolioCurrentValuation(
                new BigDecimal("100000.00"), new BigDecimal("90000.00"), new BigDecimal("10000.00"), 5,
                new BigDecimal("38200.00"), dividend));

        OverviewPageResponseDto result = overviewPageService.getOverview(userId);

        assertThat(result.getPortfolio().getNextDividend().getTicker()).isEqualTo("LKOH");
        assertThat(result.getPortfolio().getNextDividend().getTotalAmount()).isEqualByComparingTo("4800.00");
        assertThat(result.getPortfolio().getDividends12m()).isEqualByComparingTo("38200.00");
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

    private MonthTotalsDto findMonth(OverviewPageResponseDto result, YearMonth month) {
        return result.getMonths().stream()
                .filter(m -> m.getMonth() == month.getMonthValue() && m.getYear() == month.getYear())
                .findFirst()
                .orElseThrow();
    }
}
