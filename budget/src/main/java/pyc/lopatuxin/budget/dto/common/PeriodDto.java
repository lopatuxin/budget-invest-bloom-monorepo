package pyc.lopatuxin.budget.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Период (месяц и год), за который запрошены данные.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Период, за который запрошены данные бюджета")
public class PeriodDto {

    @Schema(description = "Номер месяца (1-12)", example = "3")
    private Integer month;

    @Schema(description = "Год", example = "2024")
    private Integer year;
}