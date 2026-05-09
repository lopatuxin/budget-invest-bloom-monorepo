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
 * DTO with lifetime balance aggregates for a user.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Агрегированный баланс за всё время")
public class LifetimeBalanceResponseDto {

    @Schema(description = "Свободный капитал (totalIncome - totalExpense)", example = "125000.00")
    private BigDecimal freeCapital;

    @Schema(description = "Суммарный доход за всё время", example = "500000.00")
    private BigDecimal totalIncome;

    @Schema(description = "Суммарный расход за всё время", example = "375000.00")
    private BigDecimal totalExpense;
}
