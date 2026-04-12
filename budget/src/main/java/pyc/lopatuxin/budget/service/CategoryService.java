package pyc.lopatuxin.budget.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.budget.dto.request.CreateCategoryDto;
import pyc.lopatuxin.budget.dto.request.UpdateCategoryRequestDto;
import pyc.lopatuxin.budget.dto.response.CategoryResponseDto;
import pyc.lopatuxin.budget.entity.Category;
import pyc.lopatuxin.budget.repository.CategoryRepository;

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
}
