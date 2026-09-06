package pyc.lopatuxin.budget.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pyc.lopatuxin.budget.dto.common.PeriodDto;

import java.util.List;

/**
 * Лента не-трансферных операций (расходов и доходов) пользователя за месяц.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Лента операций пользователя за месяц")
public class OperationsResponseDto {

    @Schema(description = "Период, за который получена лента")
    private PeriodDto period;

    @Schema(description = "Общее число операций за месяц", example = "47")
    private int total;

    @Schema(description = "Операции месяца, отсортированные по дате и времени создания по убыванию")
    private List<OperationDto> items;
}
