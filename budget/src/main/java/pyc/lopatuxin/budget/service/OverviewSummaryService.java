package pyc.lopatuxin.budget.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.budget.dto.common.PeriodDto;
import pyc.lopatuxin.budget.dto.common.TrendsDto;
import pyc.lopatuxin.budget.dto.response.CategorySummaryDto;
import pyc.lopatuxin.budget.dto.response.OverviewSummaryResponseDto;
import pyc.lopatuxin.budget.entity.CapitalRecord;
import pyc.lopatuxin.budget.entity.Category;
import pyc.lopatuxin.budget.repository.CapitalRecordRepository;
import pyc.lopatuxin.budget.repository.CategoryRepository;
import pyc.lopatuxin.budget.repository.ExpenseRepository;
import pyc.lopatuxin.budget.service.PeriodAggregateService.PeriodAggregates;
import pyc.lopatuxin.budget.util.TrendFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for aggregating overview data for the index page.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OverviewSummaryService {

    private static final int TOP_CATEGORIES_LIMIT = 4;

    private final PeriodAggregateService periodAggregateService;
    private final CategorySummaryBuilder categorySummaryBuilder;
    private final CapitalRecordRepository capitalRecordRepository;
    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;

    /**
     * Builds an aggregated overview for the given month and year.
     *
     * @param userId identifier of the user
     * @param month  month number (1-12)
     * @param year   year
     * @return overview summary DTO
     */
    public OverviewSummaryResponseDto getOverview(UUID userId, int month, int year) {
        log.debug("Building overview for userId={}, period={}/{}", userId, month, year);

        PeriodAggregates current = periodAggregateService.buildPeriodAggregates(userId, month, year);

        int savingsRate = calculateSavingsRate(current.income(), current.expenses());

        BigDecimal capital = resolveCapital(userId, month, year);

        TrendsDto trends = calculateOverviewTrends(userId, month, year, current, capital);

        List<CategorySummaryDto> categories = buildTopCategorySummaries(userId, current.startDate(), current.endDate());

        log.debug("Overview built for userId={}, period={}/{}", userId, month, year);

        return OverviewSummaryResponseDto.builder()
                .period(PeriodDto.builder().month(month).year(year).build())
                .income(current.income())
                .expenses(current.expenses())
                .balance(current.balance())
                .capital(capital)
                .trends(trends)
                .categories(categories)
                .savingsRate(savingsRate)
                .build();
    }

    private BigDecimal resolveCapital(UUID userId, int month, int year) {
        return capitalRecordRepository
                .findByUserIdAndMonthAndYear(userId, month, year)
                .map(CapitalRecord::getAmount)
                .orElseGet(() -> capitalRecordRepository
                        .findLatestByUserId(userId, PageRequest.of(0, 1))
                        .stream()
                        .findFirst()
                        .map(CapitalRecord::getAmount)
                        .orElse(BigDecimal.ZERO));
    }

    private TrendsDto calculateOverviewTrends(UUID userId, int month, int year,
                                              PeriodAggregates current, BigDecimal capital) {
        int prevMonth = (month == 1) ? 12 : month - 1;
        int prevYear = (month == 1) ? year - 1 : year;

        PeriodAggregates prev = periodAggregateService.buildPeriodAggregates(userId, prevMonth, prevYear);

        BigDecimal prevCapital = capitalRecordRepository
                .findByUserIdAndMonthAndYear(userId, prevMonth, prevYear)
                .map(CapitalRecord::getAmount)
                .orElse(BigDecimal.ZERO);

        return TrendsDto.builder()
                .expenses(TrendFormatter.formatTrend(current.expenses(), prev.expenses()))
                .balance(TrendFormatter.formatTrend(current.balance(), prev.balance()))
                .capital(TrendFormatter.formatTrend(capital, prevCapital))
                .build();
    }

    /**
     * Calculates savings rate as (income - expenses) / income * 100, clamped to [-99, 99].
     * Returns 0 if income is null or non-positive.
     */
    private int calculateSavingsRate(BigDecimal income, BigDecimal expenses) {
        if (income == null || income.signum() <= 0) {
            return 0;
        }
        int raw = income.subtract(expenses)
                .divide(income, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
        return Math.clamp(raw, -99, 99);
    }

    private List<CategorySummaryDto> buildTopCategorySummaries(UUID userId, LocalDate startDate, LocalDate endDate) {
        Map<UUID, BigDecimal> expensesByCategory = expenseRepository
                .sumAmountByCategoryForUserAndDateBetween(userId, startDate, endDate)
                .stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> (BigDecimal) row[1]
                ));

        List<UUID> topCategoryIds = expensesByCategory.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(TOP_CATEGORIES_LIMIT)
                .map(Map.Entry::getKey)
                .toList();

        if (topCategoryIds.isEmpty()) {
            return List.of();
        }

        List<Category> topCategories = categoryRepository.findAllById(topCategoryIds);

        return topCategories.stream()
                .map(category -> categorySummaryBuilder.buildCategorySummary(category, expensesByCategory))
                .sorted(Comparator.comparing(CategorySummaryDto::getAmount).reversed())
                .toList();
    }
}
