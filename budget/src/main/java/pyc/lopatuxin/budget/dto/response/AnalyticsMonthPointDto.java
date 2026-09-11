package pyc.lopatuxin.budget.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Один месяц столбчатого графика «{Y} против {P}»: суммы обоих лет для одного календарного
 * месяца (1-12).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Данные одного месяца графика «Y против P»")
public class AnalyticsMonthPointDto {

    @Schema(description = "Номер месяца (1-12)", example = "9")
    private Integer month;

    @Schema(description = "Сумма за месяц в году Y; null для месяцев после текущего и в будущем году", example = "76850.00")
    private BigDecimal current;

    @Schema(description = "Сумма за тот же месяц в году P; 0, если данных нет", example = "90000.00")
    private BigDecimal previous;

    @Schema(description = "Месяц Y ещё не закончился", example = "true")
    private Boolean partial;
}
