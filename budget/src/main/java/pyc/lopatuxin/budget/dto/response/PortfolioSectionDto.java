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
 * Инвестиционный портфель пользователя, как показан на плитке обзора.
 * При {@code available = false} (биржа недоступна) числовые поля и {@code pnl}/{@code nextDividend} равны null.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Инвестиционный портфель пользователя")
public class PortfolioSectionDto {

    @Schema(description = "Оценка портфеля доступна (биржа не вернула ошибку)", example = "true")
    private Boolean available;

    @Schema(description = "Текущая стоимость портфеля", example = "865400.00")
    private BigDecimal value;

    @Schema(description = "Себестоимость портфеля", example = "770100.00")
    private BigDecimal cost;

    @Schema(description = "Нереализованная прибыль портфеля", example = "95300.00")
    private BigDecimal pnlAmount;

    @Schema(description = "Прибыль портфеля в процентах от себестоимости")
    private ChangeDto pnl;

    @Schema(description = "Число различных бумаг в портфеле", example = "19")
    private Integer assetsCount;

    @Schema(description = "Дивиденды за последние 12 месяцев", example = "38200.00")
    private BigDecimal dividends12m;

    @Schema(description = "Ближайшая предстоящая выплата дивидендов")
    private NextDividendDto nextDividend;
}
