package pyc.lopatuxin.budget.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.budget.dto.request.CreateCategoryDto;
import pyc.lopatuxin.budget.dto.request.DeleteCategoryRequestDto;
import pyc.lopatuxin.budget.dto.request.UpdateCategoryRequestDto;
import pyc.lopatuxin.budget.dto.response.CategoryListItemDto;
import pyc.lopatuxin.budget.dto.response.CategoryResponseDto;
import pyc.lopatuxin.budget.entity.Category;
import pyc.lopatuxin.budget.exception.BudgetConflictException;
import pyc.lopatuxin.budget.exception.CategoryHasExpensesException;
import pyc.lopatuxin.budget.repository.CategoryRepository;
import pyc.lopatuxin.budget.repository.ExpenseRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Сервис для управления категориями расходов.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final int OPERATION_FORM_FREQUENCY_WINDOW_DAYS = 90;

    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;

    /**
     * Создаёт новую категорию для указанного пользователя.
     *
     * @param userId идентификатор пользователя
     * @param dto    данные для создания категории
     * @return DTO с данными созданной категории
     */
    @Transactional("budgetTransactionManager")
    public CategoryResponseDto createCategory(UUID userId, CreateCategoryDto dto) {
        Objects.requireNonNull(userId, "userId не может быть null");

        Category category = Category.builder()
                .userId(userId)
                .name(dto.getName())
                .budget(dto.getBudget() != null ? dto.getBudget() : BigDecimal.ZERO)
                .emoji(dto.getEmoji())
                .build();

        category = categoryRepository.save(category);

        log.info("Создана категория {} для пользователя {}", category.getId(), userId);

        return CategoryResponseDto.builder()
                .id(category.getId())
                .name(category.getName())
                .budget(category.getBudget())
                .emoji(category.getEmoji())
                .build();
    }

    /**
     * Обновляет название и лимит бюджета категории.
     *
     * @param userId  идентификатор пользователя
     * @param request данные для обновления категории
     * @return DTO с обновлёнными данными категории
     */
    @Transactional("budgetTransactionManager")
    public CategoryResponseDto updateCategory(UUID userId, UpdateCategoryRequestDto request) {
        Category category = categoryRepository.findByIdAndUserId(request.getCategoryId(), userId)
                .orElseThrow(() -> new EntityNotFoundException("Категория не найдена"));

        if (category.isSystem()) {
            throw new BudgetConflictException(
                    "Системную категорию «" + category.getName() + "» нельзя переименовать или изменить");
        }

        category.setName(request.getName());
        if (request.getBudget() != null) {
            category.setBudget(request.getBudget());
        }
        if (request.getEmoji() != null) {
            category.setEmoji(request.getEmoji().isBlank() ? null : request.getEmoji());
        }

        category = categoryRepository.save(category);

        log.info("Обновлена категория {} для пользователя {}", category.getId(), userId);

        return CategoryResponseDto.builder()
                .id(category.getId())
                .name(category.getName())
                .budget(category.getBudget())
                .emoji(category.getEmoji())
                .build();
    }

    /**
     * Returns or creates a system category with the given name for the user.
     *
     * <p>Lookup order:
     * <ol>
     *   <li>If a system category with {@code (userId, name, system=true)} already exists — return it.</li>
     *   <li>If no category with that name exists — create a new one with {@code system=true}.</li>
     *   <li>If a user-owned (non-system) category with the same name exists — this is a conflict.
     *       The unique constraint {@code uq_categories_user_name} prevents creating a duplicate.
     *       In this edge case a {@link BudgetConflictException} is thrown and a WARNING is logged.
     *       The investment service should surface this to the user as a configuration issue.</li>
     * </ol>
     *
     * @param userId идентификатор пользователя
     * @param name   название системной категории
     * @param emoji  эмодзи-иконка (может быть null)
     * @return существующая или только что созданная системная категория
     * @throws BudgetConflictException если пользователь уже создал обычную категорию с таким же именем
     */
    @Transactional("budgetTransactionManager")
    public Category ensureSystemCategory(UUID userId, String name, String emoji) {
        Optional<Category> existing = categoryRepository.findSystemCategoryByUserIdAndName(userId, name);
        if (existing.isPresent()) {
            return existing.get();
        }

        Optional<Category> userConflict = categoryRepository.findByNameAndUserId(name, userId);
        if (userConflict.isPresent()) {
            log.warn("User {} has a non-system category named '{}' — cannot create system category with same name",
                    userId, name);
            throw new BudgetConflictException(
                    "Невозможно создать системную категорию «" + name + "»: пользователь уже создал категорию с таким именем");
        }

        Category category = Category.builder()
                .userId(userId)
                .name(name)
                .emoji(emoji)
                .budget(BigDecimal.ZERO)
                .system(true)
                .build();

        category = categoryRepository.save(category);
        log.info("Created system category '{}' ({}) for user {}", name, category.getId(), userId);
        return category;
    }

    /**
     * Удаляет категорию пользователя, если у неё нет связанных расходов.
     *
     * @param userId идентификатор пользователя
     * @param dto    данные для удаления категории
     */
    @Transactional("budgetTransactionManager")
    public void deleteCategory(UUID userId, DeleteCategoryRequestDto dto) {
        Category category = categoryRepository.findByIdAndUserId(dto.getCategoryId(), userId)
                .orElseThrow(() -> new EntityNotFoundException("Категория не найдена"));

        if (category.isSystem()) {
            throw new BudgetConflictException(
                    "Системную категорию «" + category.getName() + "» нельзя удалить");
        }

        long expenseCount = expenseRepository.countByCategoryId(category.getId());
        boolean force = Boolean.TRUE.equals(dto.getForce());

        if (expenseCount > 0 && !force) {
            throw new CategoryHasExpensesException((int) expenseCount);
        }

        if (expenseCount > 0) {
            int deleted = expenseRepository.deleteAllByCategoryId(category.getId());
            log.info("Каскадно удалено {} расходов категории {} пользователя {}", deleted, category.getId(), userId);
        }

        categoryRepository.delete(category);
        log.info("Удалена категория {} пользователя {}", category.getId(), userId);
    }

    /**
     * Возвращает категории пользователя для формы новой операции, отсортированные по числу
     * не-трансферных расходов за последние 90 дней по убыванию, затем по названию по возрастанию.
     *
     * @param userId идентификатор пользователя
     * @return список категорий без лимита бюджета
     */
    @Transactional(value = "budgetTransactionManager", readOnly = true)
    public List<CategoryListItemDto> listForOperationForm(UUID userId) {
        List<Category> categories = categoryRepository.findUserCategoriesByUserId(userId);

        LocalDate sinceDate = LocalDate.now().minusDays(OPERATION_FORM_FREQUENCY_WINDOW_DAYS);
        Map<UUID, Long> expenseCountByCategory = expenseRepository
                .countNonTransferExpensesByCategorySince(userId, sinceDate)
                .stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));

        return categories.stream()
                .sorted(Comparator
                        .comparing((Category category) -> expenseCountByCategory.getOrDefault(category.getId(), 0L))
                        .reversed()
                        .thenComparing(Category::getName))
                .map(category -> CategoryListItemDto.builder()
                        .id(category.getId())
                        .name(category.getName())
                        .emoji(category.getEmoji())
                        .build())
                .toList();
    }
}
