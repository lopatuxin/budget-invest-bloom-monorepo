package pyc.lopatuxin.budget.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pyc.lopatuxin.budget.entity.enums.NormStatus;

import java.math.BigDecimal;

/**
 * Сравнение фактической суммы с личной нормой пользователя («обычно к этому дню»).
 * При статусе {@link NormStatus#NO_HISTORY} денежные поля и процент отклонения равны null.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Сравнение фактической суммы с личной нормой («обычно к этому дню»)")
public class NormComparisonDto {

    @Schema(description = "Средняя сумма к текущему дню месяца по месяцам с историей", example = "72300.00")
    private BigDecimal usualByDay;

    @Schema(description = "Средняя сумма за полный месяц по месяцам с историей", example = "102200.00")
    private BigDecimal averageMonthly;

    @Schema(description = "Отклонение факта от usualByDay в процентах, округлено до 1 знака", example = "6.3")
    private BigDecimal deviationPercent;

    @Schema(description = "Статус отклонения от нормы")
    private NormStatus status;
}
