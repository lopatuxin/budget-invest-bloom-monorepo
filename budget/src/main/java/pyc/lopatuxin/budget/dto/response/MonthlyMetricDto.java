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
 * Данные по финансовой метрике за один календарный месяц.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Данные по метрике за один календарный месяц")
public class MonthlyMetricDto {

    @Schema(description = "Номер месяца (1-12)", example = "3")
    private Integer month;

    @Schema(description = "Краткое название месяца на русском языке", example = "Мар")
    private String monthName;

    @Schema(description = "Суммарное значение метрики за месяц", example = "150000.00")
    private BigDecimal amount;
}
