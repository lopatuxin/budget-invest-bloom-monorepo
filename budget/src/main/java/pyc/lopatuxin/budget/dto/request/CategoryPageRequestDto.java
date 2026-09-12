package pyc.lopatuxin.budget.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO запроса страницы категории: название категории и период.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Параметры запроса страницы категории")
public class CategoryPageRequestDto {

    @NotBlank(message = "Название категории обязательно")
    @Schema(description = "Название категории", example = "Продукты")
    private String categoryName;

    @NotNull(message = "Параметр month обязателен")
    @Min(value = 1, message = "Значение параметра month должно быть от 1 до 12")
    @Max(value = 12, message = "Значение параметра month должно быть от 1 до 12")
    @Schema(description = "Номер месяца (1-12)", example = "9")
    private Integer month;

    @NotNull(message = "Параметр year обязателен")
    @Min(value = 1950, message = "Год не может быть меньше 1950")
    @Max(value = 2100, message = "Год не может быть больше 2100")
    @Schema(description = "Год", example = "2026")
    private Integer year;
}
