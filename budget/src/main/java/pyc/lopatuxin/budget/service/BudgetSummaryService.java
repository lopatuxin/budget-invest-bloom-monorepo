package pyc.lopatuxin.budget.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.budget.dto.common.PeriodDto;
import pyc.lopatuxin.budget.dto.common.TrendsDto;
import pyc.lopatuxin.budget.dto.response.BudgetSummaryResponseDto;
import pyc.lopatuxin.budget.dto.response.CategorySummaryDto;
import pyc.lopatuxin.budget.entity.CapitalRecord;
import pyc.lopatuxin.budget.entity.Category;
import pyc.lopatuxin.budget.repository.CapitalRecordRepository;
import pyc.lopatuxin.budget.repository.CategoryRepository;
import pyc.lopatuxin.budget.repository.ExpenseRepository;
import pyc.lopatuxin.budget.repository.IncomeRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Сервис для формирования агрегированной сводки бюджета пользователя.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BudgetSummaryService {

    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final CapitalRecordRepository capitalRecordRepository;
    private final CategoryRepository categoryRepository;

    /**
     * Формирует агрегированную сводку бюджета за указанный месяц и год.
     *
     * @param userId идентификатор пользователя
     * @param month  номер месяца (1-12)
     * @param year   год
     * @return объект со сводкой бюджета
     */
    public BudgetSummaryResponseDto getSummary(UUID userId, int month, int year) {
        log.debug("Начало формирования сводки бюджета для userId={}, period={}/{}", userId, month, year);

        PeriodAggregates current = buildPeriodAggregates(userId, month, year);

        BigDecimal capital = capitalRecordRepository
                .findByUserIdAndMonthAndYear(userId, month, year)
                .map(CapitalRecord::getAmount)
                .orElseGet(() -> capitalRecordRepository
                        .findLatestByUserId(userId, PageRequest.of(0, 1))
                        .stream()
                        .findFirst()
                        .map(CapitalRecord::getAmount)
                        .orElse(BigDecimal.ZERO));

        BigDecimal personalInflation = calculatePersonalInflation(userId, month, year);

        TrendsDto trends = calculateTrends(userId, month, year, current.income, current.expenses,
                current.balance, capital, personalInflation);

        List<CategorySummaryDto> categories = buildCategorySummaries(userId, current.startDate, current.endDate);

        log.debug("Сводка бюджета сформирована для userId={}, period={}/{}", userId, month, year);

        return BudgetSummaryResponseDto.builder()
                .period(PeriodDto.builder().month(month).year(year).build())
                .income(current.income)
                .expenses(current.expenses)
                .balance(current.balance)
                .capital(capital)
                .personalInflation(personalInflation)
                .trends(trends)
                .categories(categories)
                .build();
    }

    /**
     * Рассчитывает личную инфляцию как процентное изменение средних месячных расходов
     * текущего года по сравнению с предыдущим.
     * Количество месяцев текущего года — от 1 до текущего месяца включительно.
     */
    private BigDecimal calculatePersonalInflation(UUID userId, int month, int year) {
        BigDecimal currentYearTotal = expenseRepository
                .sumAmountByUserIdAndYear(userId, year)
                .orElse(BigDecimal.ZERO);

        BigDecimal currentYearAvg = currentYearTotal.divide(BigDecimal.valueOf(month), 10, RoundingMode.HALF_UP);

        BigDecimal previousYearTotal = expenseRepository
                .sumAmountByUserIdAndYear(userId, year - 1)
                .orElse(BigDecimal.ZERO);

        if (previousYearTotal.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal previousYearAvg = previousYearTotal.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);

        return currentYearAvg.subtract(previousYearAvg)
                .divide(previousYearAvg, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * Рассчитывает тренды показателей относительно предыдущего месяца.
     */
    private TrendsDto calculateTrends(UUID userId, int month, int year,
                                      BigDecimal currentIncome, BigDecimal currentExpenses,
                                      BigDecimal balance, BigDecimal capital,
                                      BigDecimal personalInflation) {
        int prevMonth = (month == 1) ? 12 : month - 1;
        int prevYear = (month == 1) ? year - 1 : year;

        PeriodAggregates prev = buildPeriodAggregates(userId, prevMonth, prevYear);

        BigDecimal prevCapital = capitalRecordRepository
                .findByUserIdAndMonthAndYear(userId, prevMonth, prevYear)
                .map(CapitalRecord::getAmount)
                .orElse(BigDecimal.ZERO);

        BigDecimal prevInflation = calculatePersonalInflation(userId, prevMonth, prevYear);

        return TrendsDto.builder()
                .income(formatTrend(currentIncome, prev.income()))
                .expenses(formatTrend(currentExpenses, prev.expenses()))
                .balance(formatTrend(balance, prev.balance()))
                .capital(formatTrend(capital, prevCapital))
                .inflation(formatTrend(personalInflation, prevInflation))
                .build();
    }

    /**
     * Вычисляет процентное изменение показателя и форматирует его в строку вида "+8.2%" или "-3.1%".
     * Если предыдущее значение равно нулю — возвращает "+0.0%".
     */
    private String formatTrend(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return "+0.0%";
        }

        BigDecimal percentChange = current.subtract(previous)
                .divide(previous.abs(), 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);

        String formatted = percentChange.toPlainString() + "%";

        return (percentChange.compareTo(BigDecimal.ZERO) >= 0) ? "+" + formatted : formatted;
    }

    /**
     * Формирует список DTO категорий с расходами за период.
     */
    private List<CategorySummaryDto> buildCategorySummaries(UUID userId, LocalDate startDate, LocalDate endDate) {
        List<Category> categories = categoryRepository.findByUserId(userId);

        Map<UUID, BigDecimal> expensesByCategory = expenseRepository
                .sumAmountByCategoryForUserAndDateBetween(userId, startDate, endDate)
                .stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> (BigDecimal) row[1]
                ));

        return categories.stream()
                .map(category -> buildCategorySummary(category, expensesByCategory))
                .toList();
    }

    /**
     * Строит DTO сводки для одной категории.
     */
    private CategorySummaryDto buildCategorySummary(Category category, Map<UUID, BigDecimal> expensesByCategory) {
        BigDecimal amount = expensesByCategory.getOrDefault(category.getId(), BigDecimal.ZERO);
        BigDecimal budget = category.getBudget();
        BigDecimal percentUsed = calculatePercentUsed(amount, budget);

        return CategorySummaryDto.builder()
                .id(category.getId())
                .name(category.getName())
                .emoji(category.getEmoji())
                .amount(amount)
                .budget(budget)
                .percentUsed(percentUsed)
                .build();
    }

    /**
     * Вычисляет процент использования бюджетного лимита.
     * Результат ограничен значением 100 (перерасход отображается как 100%).
     * Если бюджет равен нулю — возвращает 0.
     */
    private BigDecimal calculatePercentUsed(BigDecimal amount, BigDecimal budget) {
        if (budget.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal percent = calculatePercent(amount, budget);

        return percent.min(new BigDecimal("100.00"));
    }

    private BigDecimal calculatePercent(BigDecimal amount, BigDecimal budget) {
        return amount.divide(budget, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Агрегирует доходы, расходы и баланс за один календарный месяц.
     * Используется для исключения дублирования при расчёте текущего и предыдущего периодов.
     *
     * @param userId идентификатор пользователя
     * @param month  номер месяца (1-12)
     * @param year   год
     * @return объект {@link PeriodAggregates} с границами периода и агрегированными суммами
     */
    private PeriodAggregates buildPeriodAggregates(UUID userId, int month, int year) {
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
     * Вспомогательный record для хранения агрегированных данных одного календарного месяца.
     *
     * @param startDate первый день периода
     * @param endDate   последний день периода
     * @param income    суммарный доход за период
     * @param expenses  суммарные расходы за период
     * @param balance   баланс (доходы минус расходы)
     */
    private record PeriodAggregates(
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal balance
    ) {
    }
}