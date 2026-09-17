package pyc.lopatuxin.budget.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.budget.dto.request.InvestmentEntryRequestDto;
import pyc.lopatuxin.budget.dto.response.InvestmentEntryResponseDto;
import pyc.lopatuxin.budget.entity.Category;
import pyc.lopatuxin.budget.entity.Expense;
import pyc.lopatuxin.budget.entity.Income;
import pyc.lopatuxin.budget.entity.enums.IncomeSource;
import pyc.lopatuxin.budget.repository.ExpenseRepository;
import pyc.lopatuxin.budget.repository.IncomeRepository;
import pyc.lopatuxin.shared.port.EntryType;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Service for registering investment-related budget entries on behalf of the investment service.
 *
 * <p>BUY operations are recorded as Expense in the system category «Инвестиции».
 * SELL and REDEMPTION operations are recorded as Income with source INVESTMENTS, with a
 * description that tells a sale from a bond redemption.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvestmentEntryService {

    private static final String INVESTMENT_CATEGORY_NAME = "Инвестиции";
    private static final String INVESTMENT_CATEGORY_EMOJI = "💎";
    private static final String SELL_DESCRIPTION = "Продажа активов";
    private static final String REDEMPTION_DESCRIPTION = "Погашение облигаций";

    private final CategoryService categoryService;
    private final ExpenseService expenseService;
    private final IncomeService incomeService;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;

    /**
     * Creates a budget entry for an investment operation.
     * BUY → Expense in system category «Инвестиции».
     * SELL / REDEMPTION → Income with source INVESTMENTS, described as a sale or a redemption.
     *
     * @param userId identifier of the user
     * @param dto    investment entry data
     * @return response with the UUID of the created Expense or Income record
     */
    @Transactional("budgetTransactionManager")
    public InvestmentEntryResponseDto create(UUID userId, InvestmentEntryRequestDto dto) {
        LocalDate date = dto.getExecutedAt().atZone(ZoneId.systemDefault()).toLocalDate();

        return switch (dto.getType()) {
            case BUY -> createBuyEntry(userId, dto, date);
            case SELL -> createIncomeEntry(userId, dto, date, SELL_DESCRIPTION);
            case REDEMPTION -> createIncomeEntry(userId, dto, date, REDEMPTION_DESCRIPTION);
        };
    }

    /**
     * Deletes the budget entry corresponding to an investment operation.
     * Idempotent: if the record is not found, logs info and returns normally.
     *
     * @param userId  identifier of the user
     * @param entryId UUID of the Expense or Income to delete
     * @param type    BUY (Expense) or SELL/REDEMPTION (Income)
     */
    @Transactional("budgetTransactionManager")
    public void delete(UUID userId, UUID entryId, EntryType type) {
        if (type == EntryType.BUY) {
            deleteBuyEntry(userId, entryId);
        } else {
            deleteIncomeEntry(userId, entryId, type);
        }
    }

    private InvestmentEntryResponseDto createBuyEntry(UUID userId, InvestmentEntryRequestDto dto, LocalDate date) {
        Category category = categoryService.ensureSystemCategory(userId, INVESTMENT_CATEGORY_NAME,
                INVESTMENT_CATEGORY_EMOJI);
        Expense expense = expenseService.createInternal(userId, category, dto.getAmount(), date, null, true);
        return InvestmentEntryResponseDto.builder().entryId(expense.getId()).build();
    }

    private InvestmentEntryResponseDto createIncomeEntry(UUID userId, InvestmentEntryRequestDto dto, LocalDate date,
                                                          String description) {
        Income income = incomeService.createInternal(userId, IncomeSource.INVESTMENTS,
                dto.getAmount(), date, description, true);
        return InvestmentEntryResponseDto.builder().entryId(income.getId()).build();
    }

    private void deleteBuyEntry(UUID userId, UUID entryId) {
        expenseRepository.findById(entryId).ifPresentOrElse(
                expense -> {
                    if (!expense.getUserId().equals(userId)) {
                        log.info("Investment BUY entry {} does not belong to user {} — skipping delete",
                                entryId, userId);
                        return;
                    }
                    expenseRepository.delete(expense);
                    log.info("Deleted investment BUY expense {} for user {}", entryId, userId);
                },
                () -> log.info("Investment BUY expense {} not found — skipping delete", entryId)
        );
    }

    private void deleteIncomeEntry(UUID userId, UUID entryId, EntryType type) {
        incomeRepository.findById(entryId).ifPresentOrElse(
                income -> {
                    if (!income.getUserId().equals(userId)) {
                        log.info("Investment {} entry {} does not belong to user {} — skipping delete",
                                type, entryId, userId);
                        return;
                    }
                    incomeRepository.delete(income);
                    log.info("Deleted investment {} income {} for user {}", type, entryId, userId);
                },
                () -> log.info("Investment {} income {} not found — skipping delete", type, entryId)
        );
    }
}
