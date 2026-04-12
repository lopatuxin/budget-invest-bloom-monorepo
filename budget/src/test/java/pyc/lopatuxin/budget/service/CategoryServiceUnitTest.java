package pyc.lopatuxin.budget.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.budget.dto.request.CreateCategoryDto;
import pyc.lopatuxin.budget.dto.request.UpdateCategoryRequestDto;
import pyc.lopatuxin.budget.dto.response.CategoryResponseDto;
import pyc.lopatuxin.budget.entity.Category;
import pyc.lopatuxin.budget.repository.CategoryRepository;

import jakarta.persistence.EntityNotFoundException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryServiceUnitTest")
class CategoryServiceUnitTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Должен создать категорию и вернуть корректный CategoryResponseDto")
    void shouldCreateCategoryAndReturnCorrectDto() {
        CreateCategoryDto dto = CreateCategoryDto.builder()
                .name("Продукты")
                .budget(new BigDecimal("15000.00"))
                .emoji("\uD83D\uDED2")
                .build();

        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category saved = invocation.getArgument(0);
            saved.setId(categoryId);
            return saved;
        });

        CategoryResponseDto result = categoryService.createCategory(userId, dto);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(categoryId);
        assertThat(result.getName()).isEqualTo("Продукты");
        assertThat(result.getBudget()).isEqualByComparingTo(new BigDecimal("15000.00"));
        assertThat(result.getEmoji()).isEqualTo("\uD83D\uDED2");

        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    @DisplayName("Должен корректно сохранить категорию с userId и полями из DTO")
    void shouldSaveCategoryWithCorrectUserIdAndFields() {
        CreateCategoryDto dto = CreateCategoryDto.builder()
                .name("Транспорт")
                .budget(new BigDecimal("5000.00"))
                .emoji("\uD83D\uDE8C")
                .build();

        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        categoryService.createCategory(userId, dto);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());

        Category savedCategory = captor.getValue();
        assertThat(savedCategory.getUserId()).isEqualTo(userId);
        assertThat(savedCategory.getName()).isEqualTo("Транспорт");
        assertThat(savedCategory.getBudget()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(savedCategory.getEmoji()).isEqualTo("\uD83D\uDE8C");
    }

    @Test
    @DisplayName("Должен создать категорию без emoji (null)")
    void shouldCreateCategoryWithNullEmoji() {
        CreateCategoryDto dto = CreateCategoryDto.builder()
                .name("Развлечения")
                .budget(new BigDecimal("3000.00"))
                .build();

        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        CategoryResponseDto result = categoryService.createCategory(userId, dto);

        assertThat(result.getEmoji()).isNull();
        assertThat(result.getName()).isEqualTo("Развлечения");
        assertThat(result.getBudget()).isEqualByComparingTo(new BigDecimal("3000.00"));
    }

    @Test
    @DisplayName("Должен создать категорию с нулевым бюджетом")
    void shouldCreateCategoryWithZeroBudget() {
        CreateCategoryDto dto = CreateCategoryDto.builder()
                .name("Прочее")
                .budget(BigDecimal.ZERO)
                .build();

        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category saved = invocation.getArgument(0);
            saved.setId(categoryId);
            return saved;
        });

        CategoryResponseDto result = categoryService.createCategory(userId, dto);

        assertThat(result.getId()).isEqualTo(categoryId);
        assertThat(result.getBudget()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("updateCategory: должен установить новое emoji, если оно передано")
    void updateCategory_shouldSetNewEmoji_whenEmojiProvided() {
        UUID categoryId = UUID.randomUUID();
        Category existing = Category.builder()
                .id(categoryId)
                .userId(userId)
                .name("Продукты")
                .budget(new BigDecimal("10000.00"))
                .emoji("\uD83D\uDED2")
                .build();

        UpdateCategoryRequestDto request = UpdateCategoryRequestDto.builder()
                .categoryId(categoryId)
                .name("Продукты")
                .budget(new BigDecimal("10000.00"))
                .emoji("\uD83C\uDF55")
                .build();

        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(existing));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponseDto result = categoryService.updateCategory(userId, request);

        assertThat(result.getEmoji()).isEqualTo("\uD83C\uDF55");
    }

    @Test
    @DisplayName("updateCategory: должен сохранить старое emoji, если в запросе emoji = null")
    void updateCategory_shouldKeepOldEmoji_whenEmojiIsNull() {
        UUID categoryId = UUID.randomUUID();
        Category existing = Category.builder()
                .id(categoryId)
                .userId(userId)
                .name("Транспорт")
                .budget(new BigDecimal("5000.00"))
                .emoji("\uD83D\uDE8C")
                .build();

        UpdateCategoryRequestDto request = UpdateCategoryRequestDto.builder()
                .categoryId(categoryId)
                .name("Транспорт")
                .budget(new BigDecimal("5000.00"))
                .emoji(null)
                .build();

        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(existing));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponseDto result = categoryService.updateCategory(userId, request);

        assertThat(result.getEmoji()).isEqualTo("\uD83D\uDE8C");
    }

    @Test
    @DisplayName("updateCategory: должен сбросить emoji в null, если передана пустая строка")
    void updateCategory_shouldResetEmojiToNull_whenEmojiIsBlank() {
        UUID categoryId = UUID.randomUUID();
        Category existing = Category.builder()
                .id(categoryId)
                .userId(userId)
                .name("Развлечения")
                .budget(new BigDecimal("3000.00"))
                .emoji("\uD83C\uDFAC")
                .build();

        UpdateCategoryRequestDto request = UpdateCategoryRequestDto.builder()
                .categoryId(categoryId)
                .name("Развлечения")
                .budget(new BigDecimal("3000.00"))
                .emoji("")
                .build();

        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(existing));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponseDto result = categoryService.updateCategory(userId, request);

        assertThat(result.getEmoji()).isNull();
    }
}
