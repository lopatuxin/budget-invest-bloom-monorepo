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
import java.util.List;

/**
 * Капитал пользователя (свободные деньги + портфель) с траекторией за 12 месяцев.
 * При {@code change.status = NO_HISTORY} поля {@code yearAgo} и {@code changeAbs} равны null.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Капитал пользователя с траекторией за 12 месяцев")
public class CapitalSectionDto {

    @Schema(description = "Капитал сейчас: свободные деньги + текущая стоимость портфеля", example = "2105400.00")
    private BigDecimal total;

    @Schema(description = "Свободные деньги: все доходы минус все расходы за всю историю", example = "1240000.00")
    private BigDecimal freeMoney;

    @Schema(description = "Текущая стоимость портфеля", example = "865400.00")
    private BigDecimal portfolioValue;

    @Schema(description = "Капитал год назад (конец месяца M-12)", example = "1695400.00")
    private BigDecimal yearAgo;

    @Schema(description = "Изменение капитала за 12 месяцев в абсолютном выражении", example = "410000.00")
    private BigDecimal changeAbs;

    @Schema(description = "Изменение капитала за 12 месяцев в процентах")
    private ChangeDto change;

    @Schema(description = "13 точек траектории капитала в хронологическом порядке")
    private List<CapitalPointDto> history;

    @Schema(description = "История цен части бумаг ещё не загружена — прошлые точки могут измениться",
            example = "false")
    private Boolean portfolioHistoryPending;
}
