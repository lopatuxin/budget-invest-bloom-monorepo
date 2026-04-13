package pyc.lopatuxin.budget.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO для создания новой категории расходов.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Данные для создания категории расходов")
public class CreateCategoryDto {

    /**
     * Название категории.
     */
    @NotBlank(message = "Название категории обязательно")
    @Size(max = 100, message = "Название категории не должно превышать 100 символов")
    @Schema(description = "Название категории", example = "Еда")
    private String name;

    /**
     * Лимит бюджета категории.
     */
    @PositiveOrZero(message = "Лимит бюджета не может быть отрицательным")
    @Digits(integer = 13, fraction = 2, message = "Лимит бюджета не должен превышать 13 целых и 2 дробных знака")
    @Schema(description = "Лимит бюджета категории", example = "15000.00")
    private BigDecimal budget;

    /**
     * Эмодзи-иконка категории.
     */
    @Size(max = 10, message = "Эмодзи не должно превышать 10 символов")
    @Schema(description = "Эмодзи-иконка категории", example = "\uD83D\uDED2")
    private String emoji;
}
