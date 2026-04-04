package pyc.lopatuxin.budget.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Год, за который запрошены данные метрики.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Год, за который запрошены данные метрики")
public class YearDto {

    @NotNull(message = "Параметр year обязателен")
    @Min(value = 1950, message = "Значение параметра year должно быть от 1950 до 2100")
    @Max(value = 2100, message = "Значение параметра year должно быть от 1950 до 2100")
    @Schema(description = "Год", example = "2026")
    private Integer year;
}
