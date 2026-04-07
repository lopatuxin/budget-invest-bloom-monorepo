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
 * DTO запроса аналитики по категории расходов.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Параметры запроса аналитики по категории")
public class CategoryAnalyticsRequestDto {

    /**
     * Название категории.
     */
    @NotBlank(message = "Название категории обязательно")
    @Schema(description = "Название категории", example = "Еда")
    private String categoryName;

    /**
     * Год для графика по месяцам и отчёта по дням.
     */
    @NotNull(message = "Год обязателен")
    @Min(value = 1950, message = "Год не может быть меньше 1950")
    @Max(value = 2100, message = "Год не может быть больше 2100")
    @Schema(description = "Год для аналитики", example = "2026")
    private Integer year;

    /**
     * Месяц для отчёта по дням. Если null - возвращаются расходы за весь год.
     */
    @Min(value = 1, message = "Месяц должен быть от 1 до 12")
    @Max(value = 12, message = "Месяц должен быть от 1 до 12")
    @Schema(description = "Месяц для детального отчёта (1-12). Если не указан - за весь год", example = "4")
    private Integer month;
}
