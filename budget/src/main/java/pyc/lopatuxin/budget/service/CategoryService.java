package pyc.lopatuxin.budget.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.budget.dto.request.CreateCategoryDto;
import pyc.lopatuxin.budget.dto.request.DeleteCategoryRequestDto;
import pyc.lopatuxin.budget.dto.request.UpdateCategoryRequestDto;
import pyc.lopatuxin.budget.dto.response.CategoryResponseDto;
import pyc.lopatuxin.budget.entity.Category;
import pyc.lopatuxin.budget.repository.CategoryRepository;
import pyc.lopatuxin.budget.repository.ExpenseRepository;

import java.util.Objects;
import java.util.UUID;

/**
 * Сервис для управления категориями расходов.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;

    /**
     * Создаёт новую категорию для указанного пользователя.
     *
     * @param userId идентификатор пользователя
     * @param dto    данные для создания категории
     * @return DTO с данными созданной категории
     */
    @Transactional
    public CategoryResponseDto createCategory(UUID userId, CreateCategoryDto dto) {
        Objects.requireNonNull(userId, "userId не может быть null");

        Category category = Category.builder()
                .userId(userId)
                .name(dto.getName())
                .budget(dto.getBudget())
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
    @Transactional
    public CategoryResponseDto updateCategory(UUID userId, UpdateCategoryRequestDto request) {
        Category category = categoryRepository.findByIdAndUserId(request.getCategoryId(), userId)
                .orElseThrow(() -> new EntityNotFoundException("Категория не найдена"));

        category.setName(request.getName());
        category.setBudget(request.getBudget());
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
     * Удаляет категорию пользователя, если у неё нет связанных расходов.
     *
     * @param userId идентификатор пользователя
     * @param dto    данные для удаления категории
     */
    @Transactional
    public void deleteCategory(UUID userId, DeleteCategoryRequestDto dto) {
        Category category = categoryRepository.findByIdAndUserId(dto.getCategoryId(), userId)
                .orElseThrow(() -> new EntityNotFoundException("Категория не найдена"));

        long expenseCount = expenseRepository.countByCategoryId(category.getId());
        boolean force = Boolean.TRUE.equals(dto.getForce());

        if (expenseCount > 0 && !force) {
            throw new IllegalStateException(
                    "Невозможно удалить категорию: есть связанные расходы (" + expenseCount + ")");
        }

        if (expenseCount > 0) {
            int deleted = expenseRepository.deleteAllByCategoryId(category.getId());
            log.info("Каскадно удалено {} расходов категории {} пользователя {}", deleted, category.getId(), userId);
        }

        categoryRepository.delete(category);
        log.info("Удалена категория {} пользователя {}", category.getId(), userId);
    }
}
