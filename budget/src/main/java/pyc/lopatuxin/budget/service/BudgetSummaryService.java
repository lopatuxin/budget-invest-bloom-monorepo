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
import pyc.lopatuxin.budget.service.NormCalculationService.MonthlyAggregateRow;
import pyc.lopatuxin.budget.service.NormWindowLoader.NormsContext;
import pyc.lopatuxin.budget.service.PeriodAggregateService.PeriodAggregates;
import pyc.lopatuxin.budget.util.TrendFormatter;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    private final PersonalInflationCalculator personalInflationCalculator;
    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;
    private final NormWindowLoader normWindowLoader;

    /**
     * Builds an aggregated budget summary for the given month and year, for "now".
     *
     * @param userId identifier of the user
     * @param month  month number (1-12)
     * @param year   year
     * @return budget summary DTO
     */
    public BudgetSummaryResponseDto getSummary(UUID userId, int month, int year) {
        return getSummary(userId, month, year, LocalDate.now());
    }

    /**
     * Package-private overload taking an explicit "today" so tests can fix the current date
     * instead of depending on {@link LocalDate#now()}.
     */
    BudgetSummaryResponseDto getSummary(UUID userId, int month, int year, LocalDate today) {
        log.debug("Начало формирования сводки бюджета для userId={}, period={}/{}", userId, month, year);

        // Cached by year so the current-period and previous-period inflation calculations
        // (which usually share a year) don't run the same yearly query twice.
        Map<Integer, List<Object[]>> monthlyExpensesByYear = new HashMap<>();

        PeriodAggregates current = periodAggregateService.buildPeriodAggregates(userId, month, year);

        BigDecimal personalInflation = personalInflationCalculator.calculate(userId, month, year, monthlyExpensesByYear);

        TrendsDto trends = calculateTrends(userId, month, year, current, personalInflation, monthlyExpensesByYear);

        int dayOfMonth = NormWindowLoader.resolveDayOfMonth(current.startDate(), today);
        int daysInMonth = current.startDate().lengthOfMonth();

        NormsContext norms = normWindowLoader.loadForMonth(userId, current.startDate(), dayOfMonth);
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
     * Calculates trends of indicators relative to the previous month.
     */
    private TrendsDto calculateTrends(UUID userId, int month, int year, PeriodAggregates current,
                                      BigDecimal personalInflation, Map<Integer, List<Object[]>> monthlyExpensesByYear) {
        int prevMonth = (month == 1) ? 12 : month - 1;
        int prevYear = (month == 1) ? year - 1 : year;

        PeriodAggregates prev = periodAggregateService.buildPeriodAggregates(userId, prevMonth, prevYear);

        BigDecimal prevInflation = personalInflationCalculator.calculate(userId, prevMonth, prevYear, monthlyExpensesByYear);

        return TrendsDto.builder()
                .income(TrendFormatter.formatTrend(current.income(), prev.income()))
                .expenses(TrendFormatter.formatTrend(current.expenses(), prev.expenses()))
                .balance(TrendFormatter.formatTrend(current.balance(), prev.balance()))
                .inflation(TrendFormatter.formatTrend(personalInflation, prevInflation))
                .build();
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

        List<MonthlyAggregateRow> categoryDataMonths = normWindowLoader.categoryDataMonths(norms, category.getId());

        dto.setNorm(normCalculationService.calculateNorm(categoryDataMonths, dto.getAmount(), dayOfMonth));
        return dto;
    }

    private static boolean isNoHistory(CategorySummaryDto dto) {
        return dto.getNorm() == null || dto.getNorm().getStatus() == NormStatus.NO_HISTORY;
    }

    private static BigDecimal deviationForSort(CategorySummaryDto dto) {
        return isNoHistory(dto) ? BigDecimal.ZERO : dto.getNorm().getDeviationPercent();
    }
}
