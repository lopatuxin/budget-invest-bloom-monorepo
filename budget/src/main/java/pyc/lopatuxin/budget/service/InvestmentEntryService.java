package pyc.lopatuxin.budget.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.budget.dto.request.InvestmentEntryRequestDto;
import pyc.lopatuxin.budget.dto.request.InvestmentEntryRequestDto.EntryType;
import pyc.lopatuxin.budget.dto.response.InvestmentEntryResponseDto;
import pyc.lopatuxin.budget.entity.Category;
import pyc.lopatuxin.budget.entity.Expense;
import pyc.lopatuxin.budget.entity.Income;
import pyc.lopatuxin.budget.entity.enums.IncomeSource;
import pyc.lopatuxin.budget.repository.ExpenseRepository;
import pyc.lopatuxin.budget.repository.IncomeRepository;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Service for registering investment-related budget entries on behalf of the investment service.
 *
 * <p>BUY operations are recorded as Expense in the system category «Инвестиции».
 * SELL operations are recorded as Income with source INVESTMENTS.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvestmentEntryService {

    private static final String INVESTMENT_CATEGORY_NAME = "Инвестиции";
    private static final String INVESTMENT_CATEGORY_EMOJI = "💎";
    private static final String SELL_DESCRIPTION = "Продажа активов";

    private final CategoryService categoryService;
    private final ExpenseService expenseService;
    private final IncomeService incomeService;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;

    /**
     * Creates a budget entry for an investment operation.
     * BUY → Expense in system category «Инвестиции».
     * SELL → Income with source INVESTMENTS.
     *
     * @param userId identifier of the user
     * @param dto    investment entry data
     * @return response with the UUID of the created Expense or Income record
     */
    @Transactional
    public InvestmentEntryResponseDto create(UUID userId, InvestmentEntryRequestDto dto) {
        LocalDate date = dto.getExecutedAt().atZone(ZoneOffset.UTC).toLocalDate();

        if (dto.getType() == EntryType.BUY) {
            return createBuyEntry(userId, dto, date);
        } else {
            return createSellEntry(userId, dto, date);
        }
    }

    /**
     * Deletes the budget entry corresponding to an investment operation.
     * Idempotent: if the record is not found, logs info and returns normally.
     *
     * @param userId  identifier of the user
     * @param entryId UUID of the Expense or Income to delete
     * @param type    BUY (Expense) or SELL (Income)
     */
    @Transactional
    public void delete(UUID userId, UUID entryId, EntryType type) {
        if (type == EntryType.BUY) {
            deleteBuyEntry(userId, entryId);
        } else {
            deleteSellEntry(userId, entryId);
        }
    }

    private InvestmentEntryResponseDto createBuyEntry(UUID userId, InvestmentEntryRequestDto dto, LocalDate date) {
        Category category = categoryService.ensureSystemCategory(userId, INVESTMENT_CATEGORY_NAME,
                INVESTMENT_CATEGORY_EMOJI);
        Expense expense = expenseService.createInternal(userId, category, dto.getAmount(), date, null);
        return InvestmentEntryResponseDto.builder().entryId(expense.getId()).build();
    }

    private InvestmentEntryResponseDto createSellEntry(UUID userId, InvestmentEntryRequestDto dto, LocalDate date) {
        Income income = incomeService.createInternal(userId, IncomeSource.INVESTMENTS,
                dto.getAmount(), date, SELL_DESCRIPTION);
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

    private void deleteSellEntry(UUID userId, UUID entryId) {
        incomeRepository.findById(entryId).ifPresentOrElse(
                income -> {
                    if (!income.getUserId().equals(userId)) {
                        log.info("Investment SELL entry {} does not belong to user {} — skipping delete",
                                entryId, userId);
                        return;
                    }
                    incomeRepository.delete(income);
                    log.info("Deleted investment SELL income {} for user {}", entryId, userId);
                },
                () -> log.info("Investment SELL income {} not found — skipping delete", entryId)
        );
    }
}
