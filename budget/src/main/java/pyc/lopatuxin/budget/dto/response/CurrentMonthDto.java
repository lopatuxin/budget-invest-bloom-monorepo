package pyc.lopatuxin.budget.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Текущий (сегодняшний) календарный месяц, за который построена страница обзора.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Текущий календарный месяц страницы обзора")
public class CurrentMonthDto {

    @Schema(description = "Номер месяца (1-12)", example = "9")
    private Integer month;

    @Schema(description = "Год", example = "2026")
    private Integer year;

    @Schema(description = "Месяц ещё не закончился (кроме случая, когда сегодня — его последний день)", example = "true")
    private Boolean partial;
}
