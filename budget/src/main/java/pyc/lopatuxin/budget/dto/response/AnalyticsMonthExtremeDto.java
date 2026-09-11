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
 * Месяц с наибольшей или наименьшей суммой среди учтённых месяцев года (самый дорогой/доходный
 * или самый дешёвый/скромный месяц).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Месяц с наибольшей или наименьшей суммой среди учтённых месяцев года")
public class AnalyticsMonthExtremeDto {

    @Schema(description = "Номер месяца (1-12)", example = "5")
    private Integer month;

    @Schema(description = "Сумма за месяц", example = "112300.00")
    private BigDecimal amount;
}
