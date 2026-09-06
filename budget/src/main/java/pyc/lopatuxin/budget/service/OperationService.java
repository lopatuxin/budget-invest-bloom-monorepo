package pyc.lopatuxin.budget.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.budget.dto.common.PeriodDto;
import pyc.lopatuxin.budget.dto.response.OperationDto;
import pyc.lopatuxin.budget.dto.response.OperationsResponseDto;
import pyc.lopatuxin.budget.entity.Expense;
import pyc.lopatuxin.budget.entity.Income;
import pyc.lopatuxin.budget.entity.enums.OperationKind;
import pyc.lopatuxin.budget.repository.ExpenseRepository;
import pyc.lopatuxin.budget.repository.IncomeRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Service that builds the monthly feed of non-transfer expenses and incomes for the budget page.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(value = "budgetTransactionManager", readOnly = true)
public class OperationService {

    private static final Comparator<OperationRow> OPERATION_ORDER = Comparator
            .comparing(OperationRow::date)
            .thenComparing(OperationRow::createdAt)
            .reversed();

    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;

    /**
     * Builds the feed of non-transfer operations (expenses and incomes) for the given month and year,
     * sorted by date descending, and by creation time descending for entries on the same date.
     *
     * @param userId identifier of the user
     * @param month  month number (1-12)
     * @param year   year
     * @return operations feed for the period
     */
    public OperationsResponseDto getOperations(UUID userId, int month, int year) {
        log.debug("Начало формирования ленты операций для userId={}, period={}/{}", userId, month, year);

        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        List<Expense> expenses = expenseRepository.findByUserIdAndDateBetweenAndIsTransferFalse(userId, startDate, endDate);
        List<Income> incomes = incomeRepository.findByUserIdAndDateBetweenAndIsTransferFalse(userId, startDate, endDate);

        List<OperationRow> rows = new ArrayList<>(expenses.size() + incomes.size());
        expenses.forEach(expense -> rows.add(toRow(expense)));
        incomes.forEach(income -> rows.add(toRow(income)));
        rows.sort(OPERATION_ORDER);

        List<OperationDto> items = rows.stream().map(OperationRow::dto).toList();

        log.debug("Лента операций сформирована для userId={}, period={}/{}, всего={}", userId, month, year, items.size());

        return OperationsResponseDto.builder()
                .period(PeriodDto.builder().month(month).year(year).build())
                .total(items.size())
                .items(items)
                .build();
    }

    private OperationRow toRow(Expense expense) {
        OperationDto dto = OperationDto.builder()
                .id(expense.getId())
                .kind(OperationKind.EXPENSE)
                .date(expense.getDate())
                .amount(expense.getAmount())
                .description(expense.getDescription())
                .categoryId(expense.getCategory().getId())
                .categoryName(expense.getCategory().getName())
                .categoryEmoji(expense.getCategory().getEmoji())
                .build();
        return new OperationRow(expense.getDate(), expense.getCreatedAt(), dto);
    }

    private OperationRow toRow(Income income) {
        OperationDto dto = OperationDto.builder()
                .id(income.getId())
                .kind(OperationKind.INCOME)
                .date(income.getDate())
                .amount(income.getAmount())
                .description(income.getDescription())
                .source(income.getSource())
                .sourceName(income.getSource().getDisplayName())
                .build();
        return new OperationRow(income.getDate(), income.getCreatedAt(), dto);
    }

    /**
     * Sort key for one operation row, kept separate from {@link OperationDto} so the sort-only
     * {@code createdAt} field never leaks into the JSON response.
     */
    private record OperationRow(LocalDate date, Instant createdAt, OperationDto dto) {
    }
}
