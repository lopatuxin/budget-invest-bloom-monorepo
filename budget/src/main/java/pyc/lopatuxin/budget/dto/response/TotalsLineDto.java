package pyc.lopatuxin.budget.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pyc.lopatuxin.budget.dto.common.ChangeDto;

import java.math.BigDecimal;

/**
 * Одна строка итогов за 12 месяцев (доходы, расходы или сбережено) с сравнением
 * против предыдущих 12 месяцев.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Строка итогов за 12 месяцев")
public class TotalsLineDto {

    @Schema(description = "Сумма за последние 12 месяцев", example = "1778000.00")
    private BigDecimal amount;

    @Schema(description = "Сумма за предыдущие 12 месяцев", example = "1631000.00")
    private BigDecimal previous;

    @Schema(description = "Изменение против предыдущих 12 месяцев")
    private ChangeDto change;
}
