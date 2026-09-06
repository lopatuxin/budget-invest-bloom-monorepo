package pyc.lopatuxin.budget.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.budget.dto.request.CreateExpenseDto;
import pyc.lopatuxin.budget.dto.request.DeleteExpenseRequestDto;
import pyc.lopatuxin.budget.dto.response.ExpenseResponseDto;
import pyc.lopatuxin.budget.entity.Category;
import pyc.lopatuxin.budget.entity.Expense;
import pyc.lopatuxin.budget.exception.BudgetConflictException;
import pyc.lopatuxin.budget.repository.CategoryRepository;
import pyc.lopatuxin.budget.repository.ExpenseRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Сервис для управления расходами пользователя.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;

    /**
     * Создаёт новый расход для указанного пользователя.
     *
     * @param userId идентификатор пользователя
     * @param dto    данные для создания расхода
     * @return DTO с данными созданного расхода
     */
    @Transactional("budgetTransactionManager")
    public ExpenseResponseDto createExpense(UUID userId, CreateExpenseDto dto) {
        Category category = categoryRepository.findByIdAndUserId(dto.getCategoryId(), userId)
                .orElseThrow(() -> new EntityNotFoundException("Категория не найдена"));

        LocalDate date = dto.getDate() != null ? dto.getDate() : LocalDate.now();

        Expense expense = Expense.builder()
                .userId(userId)
                .category(category)
                .amount(dto.getAmount())
                .description(dto.getDescription())
                .date(date)
                .build();

        expense = expenseRepository.save(expense);

        log.info("Создан расход {} для пользователя {}", expense.getId(), userId);

        return ExpenseResponseDto.builder()
                .id(expense.getId())
                .categoryId(category.getId())
                .categoryName(category.getName())
                .amount(expense.getAmount())
                .description(expense.getDescription())
                .date(expense.getDate())
                .build();
    }

    /**
     * Creates an expense bypassing user-facing validation.
     * Used by internal services (e.g. investment entry recording).
     * The category must already belong to the user — no ownership check is performed here.
     *
     * @param userId      identifier of the user
     * @param category    category entity (must belong to userId)
     * @param amount      expense amount
     * @param date        expense date
     * @param description optional description
     * @param isTransfer  true if this expense represents a transfer between assets (e.g. investment buy)
     * @return created expense entity
     */
    @Transactional("budgetTransactionManager")
    public Expense createInternal(UUID userId, Category category, BigDecimal amount,
                                  LocalDate date, String description, boolean isTransfer) {
        Expense expense = Expense.builder()
                .userId(userId)
                .category(category)
                .amount(amount)
                .description(description)
                .date(date)
                .isTransfer(isTransfer)
                .build();

        expense = expenseRepository.save(expense);
        log.info("Created internal expense {} for user {}", expense.getId(), userId);
        return expense;
    }

    /**
     * Удаляет расход с проверкой принадлежности пользователю.
     *
     * @param userId  идентификатор пользователя
     * @param request данные для удаления расхода
     */
    @Transactional("budgetTransactionManager")
    public void deleteExpense(UUID userId, DeleteExpenseRequestDto request) {
        Expense expense = expenseRepository.findById(request.getExpenseId())
                .orElseThrow(() -> new EntityNotFoundException("Расход не найден"));

        if (!expense.getUserId().equals(userId)) {
            throw new EntityNotFoundException("Расход не найден");
        }

        if (expense.isTransfer()) {
            throw new BudgetConflictException(
                    "Запись-перевод нельзя удалить обычным способом");
        }

        expenseRepository.delete(expense);

        log.info("Удалён расход {} для пользователя {}", request.getExpenseId(), userId);
    }
}
