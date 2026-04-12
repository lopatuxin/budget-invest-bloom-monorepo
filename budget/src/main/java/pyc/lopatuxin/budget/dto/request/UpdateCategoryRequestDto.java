package pyc.lopatuxin.budget.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO запроса на обновление категории расходов.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Данные для обновления категории расходов")
public class UpdateCategoryRequestDto {

    /**
     * Идентификатор категории для обновления.
     */
    @NotNull(message = "Идентификатор категории обязателен")
    @Schema(description = "Идентификатор категории", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID categoryId;

    /**
     * Новое название категории.
     */
    @NotBlank(message = "Название категории обязательно")
    @Size(max = 100, message = "Название категории не должно превышать 100 символов")
    @Schema(description = "Новое название категории", example = "Продукты")
    private String name;

    /**
     * Emoji icon for the category. Null means do not change; empty string resets the emoji.
     */
    @Size(max = 10, message = "Emoji не должен превышать 10 символов")
    @Schema(description = "Emoji-иконка категории (null — не менять, пустая строка — сбросить)", example = "🛒")
    private String emoji;

    /**
     * Новый лимит бюджета категории.
     */
    @NotNull(message = "Лимит бюджета обязателен")
    @PositiveOrZero(message = "Лимит бюджета не может быть отрицательным")
    @Digits(integer = 13, fraction = 2, message = "Лимит бюджета не должен превышать 13 целых и 2 дробных знака")
    @Schema(description = "Новый лимит бюджета категории", example = "20000.00")
    private BigDecimal budget;
}
