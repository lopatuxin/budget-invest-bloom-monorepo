package pyc.lopatuxin.budget.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.budget.dto.request.CreateExpenseDto;
import pyc.lopatuxin.budget.dto.response.ExpenseResponseDto;
import pyc.lopatuxin.budget.entity.Category;
import pyc.lopatuxin.budget.entity.Expense;
import pyc.lopatuxin.budget.repository.CategoryRepository;
import pyc.lopatuxin.budget.repository.ExpenseRepository;

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
    @Transactional
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
}
