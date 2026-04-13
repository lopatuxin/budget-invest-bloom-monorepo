package pyc.lopatuxin.budget.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pyc.lopatuxin.budget.dto.request.CategoryAnalyticsRequestDto;
import pyc.lopatuxin.budget.dto.response.CategoryAnalyticsResponseDto;
import pyc.lopatuxin.budget.dto.response.ExpenseResponseDto;
import pyc.lopatuxin.budget.dto.response.MonthlyMetricDto;
import pyc.lopatuxin.budget.dto.response.YearlyMetricDto;
import pyc.lopatuxin.budget.entity.Category;
import pyc.lopatuxin.budget.entity.Expense;
import pyc.lopatuxin.budget.entity.enums.Month;
import pyc.lopatuxin.budget.mapper.ExpenseMapper;
import pyc.lopatuxin.budget.repository.CategoryRepository;
import pyc.lopatuxin.budget.repository.ExpenseRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Сервис для формирования детальной аналитики по категории расходов.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryAnalyticsService {

    private static final Month[] MONTHS = Month.values();

    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseMapper expenseMapper;

    /**
     * Формирует детальную аналитику по категории для указанного пользователя.
     *
     * @param userId  идентификатор пользователя
     * @param request параметры запроса (название категории, год, месяц)
     * @return аналитика с помесячными, годовыми данными и списком расходов
     */
    public CategoryAnalyticsResponseDto getAnalytics(UUID userId, CategoryAnalyticsRequestDto request) {
        log.debug("Начало формирования аналитики категории '{}' для userId={}, year={}, month={}",
                request.getCategoryName(), userId, request.getYear(), request.getMonth());

        Category category = categoryRepository.findByNameAndUserId(request.getCategoryName(), userId)
                .orElseThrow(() -> new EntityNotFoundException("Категория не найдена"));

        List<MonthlyMetricDto> monthlyData = buildMonthlyData(userId, category.getId(), request.getYear());
        List<YearlyMetricDto> yearlyData = buildYearlyData(userId, category.getId());
        List<Expense> expenses = findExpensesForPeriod(userId, category.getId(), request.getYear(), request.getMonth());
        List<ExpenseResponseDto> expenseDtos = expenseMapper.toDtoList(expenses);
        BigDecimal totalExpenses = calculateTotal(expenses);
        BigDecimal totalYear = calculateTotalYear(monthlyData);
        BigDecimal averageYear = calculateAverageYear(monthlyData, totalYear);

        log.debug("Аналитика категории '{}' сформирована для userId={}", request.getCategoryName(), userId);

        return CategoryAnalyticsResponseDto.builder()
                .categoryId(category.getId())
                .categoryName(category.getName())
                .emoji(category.getEmoji())
                .budget(category.getBudget())
                .monthlyData(monthlyData)
                .yearlyData(yearlyData)
                .expenses(expenseDtos)
                .totalExpenses(totalExpenses)
                .totalYear(totalYear)
                .averageYear(averageYear)
                .build();
    }

    private List<MonthlyMetricDto> buildMonthlyData(UUID userId, UUID categoryId, int year) {
        Map<Integer, BigDecimal> dataByMonth = expenseRepository
                .findMonthlyExpenseByCategoryAndUserIdAndYear(userId, categoryId, year)
                .stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).intValue(),
                        row -> (BigDecimal) row[1]
                ));

        List<MonthlyMetricDto> result = new ArrayList<>(MONTHS.length);
        for (Month month : MONTHS) {
            result.add(MonthlyMetricDto.builder()
                    .month(month.getNumber())
                    .monthName(month.getShortName())
                    .amount(dataByMonth.getOrDefault(month.getNumber(), BigDecimal.ZERO))
                    .build());
        }
        return result;
    }

    private List<YearlyMetricDto> buildYearlyData(UUID userId, UUID categoryId) {
        return expenseRepository.findYearlyExpenseByCategoryAndUserId(userId, categoryId)
                .stream()
                .map(row -> YearlyMetricDto.builder()
                        .year(((Number) row[0]).intValue())
                        .amount((BigDecimal) row[1])
                        .build())
                .toList();
    }

    private List<Expense> findExpensesForPeriod(UUID userId, UUID categoryId, int year, Integer month) {
        LocalDate startDate;
        LocalDate endDate;

        if (month != null) {
            YearMonth yearMonth = YearMonth.of(year, month);
            startDate = yearMonth.atDay(1);
            endDate = yearMonth.atEndOfMonth();
        } else {
            startDate = LocalDate.of(year, 1, 1);
            endDate = LocalDate.of(year, 12, 31);
        }

        return expenseRepository.findByUserIdAndCategoryIdAndDateBetweenOrderByDateDesc(
                userId, categoryId, startDate, endDate);
    }

    private BigDecimal calculateTotal(List<Expense> expenses) {
        return expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateTotalYear(List<MonthlyMetricDto> monthlyData) {
        return monthlyData.stream()
                .map(MonthlyMetricDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateAverageYear(List<MonthlyMetricDto> monthlyData, BigDecimal totalYear) {
        long monthsWithData = monthlyData.stream()
                .filter(m -> m.getAmount().compareTo(BigDecimal.ZERO) > 0)
                .count();
        if (monthsWithData == 0) {
            return BigDecimal.ZERO;
        }
        return totalYear.divide(BigDecimal.valueOf(monthsWithData), 2, RoundingMode.HALF_UP);
    }
}
