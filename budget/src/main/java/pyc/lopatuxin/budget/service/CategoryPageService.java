package pyc.lopatuxin.budget.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.budget.dto.common.NormComparisonDto;
import pyc.lopatuxin.budget.dto.common.PeriodDto;
import pyc.lopatuxin.budget.dto.request.CategoryPageRequestDto;
import pyc.lopatuxin.budget.dto.response.CategoryMonthAmountDto;
import pyc.lopatuxin.budget.dto.response.CategoryPageCategoryDto;
import pyc.lopatuxin.budget.dto.response.CategoryPageResponseDto;
import pyc.lopatuxin.budget.dto.response.OperationDto;
import pyc.lopatuxin.budget.entity.Category;
import pyc.lopatuxin.budget.entity.Expense;
import pyc.lopatuxin.budget.entity.enums.NormStatus;
import pyc.lopatuxin.budget.mapper.ExpenseMapper;
import pyc.lopatuxin.budget.repository.CategoryRepository;
import pyc.lopatuxin.budget.repository.ExpenseRepository;
import pyc.lopatuxin.budget.service.NormCalculationService.MonthlyAggregateRow;
import pyc.lopatuxin.budget.service.NormWindowLoader.NormsContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for building the category page: the requested month's spending against the category's
 * personal norm, the last 12 months chart and the month's operations feed — everything the
 * frontend needs from a single request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(value = "budgetTransactionManager", readOnly = true)
public class CategoryPageService {

    private static final int CHART_WINDOW_MONTHS = 12;

    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;
    private final NormWindowLoader normWindowLoader;
    private final NormCalculationService normCalculationService;
    private final ExpenseMapper expenseMapper;

    /**
     * Builds the category page for "now".
     *
     * @param userId  identifier of the user
     * @param request category name and requested period
     * @return category page DTO
     */
    public CategoryPageResponseDto getPage(UUID userId, CategoryPageRequestDto request) {
        return getPage(userId, request, LocalDate.now());
    }

    /**
     * Package-private overload taking an explicit "today" so tests can fix the current date
     * instead of depending on {@link LocalDate#now()}.
     */
    CategoryPageResponseDto getPage(UUID userId, CategoryPageRequestDto request, LocalDate today) {
        log.debug("Начало формирования страницы категории '{}' для userId={}, period={}/{}",
                request.getCategoryName(), userId, request.getMonth(), request.getYear());

        Category category = categoryRepository.findByNameAndUserId(request.getCategoryName(), userId)
                .orElseThrow(() -> new EntityNotFoundException("Категория не найдена"));

        YearMonth requestedMonth = YearMonth.of(request.getYear(), request.getMonth());
        LocalDate monthStart = requestedMonth.atDay(1);
        LocalDate monthEnd = requestedMonth.atEndOfMonth();
        int dayOfMonth = NormWindowLoader.resolveDayOfMonth(monthStart, today);
        int daysInMonth = requestedMonth.lengthOfMonth();

        YearMonth chartWindowStart = requestedMonth.minusMonths(CHART_WINDOW_MONTHS - 1L);
        LocalDate chartWindowStartDate = chartWindowStart.atDay(1);

        Map<YearMonth, BigDecimal> categoryMonthlyAmounts = toMonthlyAmountMap(
                expenseRepository.findMonthlyNonTransferExpenseByCategoryAndDateBetween(
                        userId, category.getId(), chartWindowStartDate, monthEnd));
        BigDecimal spent = categoryMonthlyAmounts.getOrDefault(requestedMonth, BigDecimal.ZERO);

        NormsContext norms = normWindowLoader.loadForMonth(userId, monthStart, dayOfMonth);
        List<MonthlyAggregateRow> categoryDataMonths = normWindowLoader.categoryDataMonths(norms, category.getId());
        NormComparisonDto norm = normCalculationService.calculateNorm(categoryDataMonths, spent, dayOfMonth);
        int normMonthsCounted = norm.getStatus() == NormStatus.NO_HISTORY ? 0 : categoryDataMonths.size();

        List<CategoryMonthAmountDto> months = buildMonths(chartWindowStart, categoryMonthlyAmounts, today);
        BigDecimal sharePercent = calculateSharePercent(userId, chartWindowStartDate, monthEnd, categoryMonthlyAmounts.values());

        List<Expense> expenses = expenseRepository.findByUserIdAndCategoryIdAndDateBetweenOrderByDateDesc(
                userId, category.getId(), monthStart, monthEnd);
        List<OperationDto> operations = expenses.stream().map(expenseMapper::toOperationDto).toList();
        OperationStats stats = buildOperationStats(expenses, spent);

        log.debug("Страница категории '{}' сформирована для userId={}", request.getCategoryName(), userId);

        return CategoryPageResponseDto.builder()
                .category(toCategoryDto(category))
                .period(PeriodDto.builder().month(request.getMonth()).year(request.getYear()).build())
                .dayOfMonth(dayOfMonth)
                .daysInMonth(daysInMonth)
                .spent(spent)
                .norm(norm)
                .normMonthsCounted(normMonthsCounted)
                .sharePercent(sharePercent)
                .operationsCount(stats.count())
                .averageCheck(stats.averageCheck())
                .largestAmount(stats.largestAmount())
                .months(months)
                .operations(operations)
                .build();
    }

    private Map<YearMonth, BigDecimal> toMonthlyAmountMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(
                row -> YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue()),
                row -> (BigDecimal) row[2]));
    }

    /**
     * Builds the 12 chart months from {@code chartWindowStart} through the requested month,
     * zero-filling months without records. {@code partial} is true only for the current calendar
     * month, and only if today is not its last day.
     */
    private List<CategoryMonthAmountDto> buildMonths(YearMonth chartWindowStart, Map<YearMonth, BigDecimal> amounts,
                                                      LocalDate today) {
        YearMonth currentCalendarMonth = YearMonth.from(today);
        boolean currentMonthPartial = today.getDayOfMonth() != today.lengthOfMonth();

        List<CategoryMonthAmountDto> months = new ArrayList<>(CHART_WINDOW_MONTHS);
        for (int i = 0; i < CHART_WINDOW_MONTHS; i++) {
            YearMonth month = chartWindowStart.plusMonths(i);
            BigDecimal amount = amounts.getOrDefault(month, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            months.add(CategoryMonthAmountDto.builder()
                    .month(month.getMonthValue())
                    .year(month.getYear())
                    .amount(amount)
                    .partial(month.equals(currentCalendarMonth) && currentMonthPartial)
                    .build());
        }
        return months;
    }

    /**
     * Share of the category in all non-transfer expenses over the chart window, 1 decimal,
     * {@code null} when there are no expenses at all in the window.
     */
    private BigDecimal calculateSharePercent(UUID userId, LocalDate windowStart, LocalDate windowEnd,
                                             Collection<BigDecimal> categoryMonthlyAmounts) {
        BigDecimal categoryTotal = categoryMonthlyAmounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal allExpensesTotal = expenseRepository
                .findMonthlyNonTransferExpenseByUserIdAndDateBetween(userId, windowStart, windowEnd)
                .stream()
                .map(row -> (BigDecimal) row[2])
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (allExpensesTotal.signum() <= 0) {
            return null;
        }
        return categoryTotal.divide(allExpensesTotal, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    private OperationStats buildOperationStats(List<Expense> expenses, BigDecimal spent) {
        if (expenses.isEmpty()) {
            return new OperationStats(0, null, null);
        }
        BigDecimal averageCheck = spent.divide(BigDecimal.valueOf(expenses.size()), 2, RoundingMode.HALF_UP);
        BigDecimal largestAmount = expenses.stream().map(Expense::getAmount).max(Comparator.naturalOrder()).orElseThrow();
        return new OperationStats(expenses.size(), averageCheck, largestAmount);
    }

    private CategoryPageCategoryDto toCategoryDto(Category category) {
        return CategoryPageCategoryDto.builder()
                .id(category.getId())
                .name(category.getName())
                .emoji(category.getEmoji())
                .system(category.isSystem())
                .build();
    }

    private record OperationStats(int count, BigDecimal averageCheck, BigDecimal largestAmount) {
    }
}
