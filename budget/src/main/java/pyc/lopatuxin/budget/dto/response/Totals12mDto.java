package pyc.lopatuxin.budget.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Итоги за последние 12 месяцев против предыдущих 12 месяцев.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Итоги за 12 месяцев против предыдущих 12 месяцев")
public class Totals12mDto {

    @Schema(description = "Итоги по доходам")
    private TotalsLineDto income;

    @Schema(description = "Итоги по расходам")
    private TotalsLineDto expenses;

    @Schema(description = "Итоги по сбережённому")
    private TotalsLineDto saved;
}
