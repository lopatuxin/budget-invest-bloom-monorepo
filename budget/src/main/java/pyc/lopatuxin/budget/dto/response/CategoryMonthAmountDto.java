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
 * Сумма расходов категории за один месяц окна графика «последние 12 месяцев».
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Сумма расходов категории за один месяц графика")
public class CategoryMonthAmountDto {

    @Schema(description = "Номер месяца (1-12)", example = "9")
    private int month;

    @Schema(description = "Год", example = "2026")
    private int year;

    @Schema(description = "Сумма расходов категории за месяц (0, если записей нет)", example = "9150.00")
    private BigDecimal amount;

    @Schema(description = "true только для текущего календарного месяца, если он ещё не закончен", example = "true")
    private boolean partial;
}
