package pyc.lopatuxin.budget.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.budget.dto.request.CreateCategoryDto;
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
}
