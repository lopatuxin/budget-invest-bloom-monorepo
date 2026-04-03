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
 * Период (месяц и год), за который запрошены данные.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Период, за который запрошены данные бюджета")
public class PeriodDto {

    @NotNull(message = "Параметр month обязателен")
    @Min(value = 1, message = "Значение параметра month должно быть от 1 до 12")
    @Max(value = 12, message = "Значение параметра month должно быть от 1 до 12")
    @Schema(description = "Номер месяца (1-12)", example = "3")
    private Integer month;

    @NotNull(message = "Параметр year обязателен")
    @Min(value = 2020, message = "Значение параметра year должно быть от 2020 до 2100")
    @Max(value = 2100, message = "Значение параметра year должно быть от 2020 до 2100")
    @Schema(description = "Год", example = "2024")
    private Integer year;
}