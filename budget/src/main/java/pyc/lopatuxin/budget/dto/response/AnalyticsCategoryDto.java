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
import java.util.UUID;

/**
 * Строка таблицы категорий на вкладке «Расходы»: средний месяц категории в Y и в P, изменение
 * и вклад в личную инфляцию, посчитанный по весам года P (сумма вкладов по всем категориям
 * сходится с {@code personalInflationPercent}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Категория расходов с вкладом в личную инфляцию")
public class AnalyticsCategoryDto {

    @Schema(description = "Идентификатор категории", example = "8b1f2c3d-0000-0000-0000-000000000000")
    private UUID categoryId;

    @Schema(description = "Название категории на момент запроса", example = "Жильё и ЖКХ")
    private String categoryName;

    @Schema(description = "Эмодзи-иконка категории", example = "🏠")
    private String emoji;

    @Schema(description = "Средний учтённый месяц категории в году Y", example = "24500.00")
    private BigDecimal averageCurrent;

    @Schema(description = "Средний учтённый месяц категории в году P; 0, если категории в P не было", example = "22000.00")
    private BigDecimal averagePrevious;

    @Schema(description = "Изменение среднего месяца категории Y против P")
    private ChangeDto change;

    @Schema(description = "Вклад категории в личную инфляцию, в процентных пунктах; " +
            "null, если у раздела расходов нет previousAverage", example = "2.7")
    private BigDecimal contributionPoints;

    @Schema(description = "Доля категории в среднем месяце расходов Y, в процентах", example = "24.9")
    private BigDecimal sharePercent;
}
