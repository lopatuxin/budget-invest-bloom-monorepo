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
 * Данные по финансовой метрике за один календарный год.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Данные по метрике за один календарный год")
public class YearlyMetricDto {

    /**
     * Календарный год.
     */
    @Schema(description = "Год", example = "2026")
    private Integer year;

    /**
     * Суммарное значение метрики за год.
     */
    @Schema(description = "Сумма расходов за год", example = "250000.00")
    private BigDecimal amount;
}
