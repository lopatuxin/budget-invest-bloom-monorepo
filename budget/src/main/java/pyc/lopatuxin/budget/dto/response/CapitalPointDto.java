package pyc.lopatuxin.budget.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Одна точка траектории капитала: конец месяца из окна истории, или сегодняшняя дата
 * для текущего месяца.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Точка траектории капитала")
public class CapitalPointDto {

    @Schema(description = "Номер месяца точки (1-12)", example = "9")
    private Integer month;

    @Schema(description = "Год точки", example = "2025")
    private Integer year;

    @Schema(description = "Дата точки: конец месяца, либо сегодня для последней точки", example = "2025-09-30")
    private LocalDate date;

    @Schema(description = "Свободные деньги на дату", example = "1010000.00")
    private BigDecimal freeMoney;

    @Schema(description = "Стоимость портфеля на дату", example = "685400.00")
    private BigDecimal portfolioValue;

    @Schema(description = "Капитал на дату (свободные деньги + портфель)", example = "1695400.00")
    private BigDecimal total;
}
