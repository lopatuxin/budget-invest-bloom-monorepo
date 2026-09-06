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
 * Доходы, расходы и сбережённое за один месяц окна из 12 месяцев.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Доходы, расходы и сбережённое за месяц")
public class MonthTotalsDto {

    @Schema(description = "Номер месяца (1-12)", example = "10")
    private Integer month;

    @Schema(description = "Год", example = "2025")
    private Integer year;

    @Schema(description = "Доходы за месяц", example = "142000.00")
    private BigDecimal income;

    @Schema(description = "Расходы за месяц", example = "98400.00")
    private BigDecimal expenses;

    @Schema(description = "Сбережено за месяц (доходы минус расходы, может быть отрицательным)", example = "43600.00")
    private BigDecimal saved;

    @Schema(description = "Месяц ещё не закончился (только у текущего месяца)", example = "false")
    private Boolean partial;
}
