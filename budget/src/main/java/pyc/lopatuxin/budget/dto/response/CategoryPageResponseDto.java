package pyc.lopatuxin.budget.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pyc.lopatuxin.budget.dto.common.NormComparisonDto;
import pyc.lopatuxin.budget.dto.common.PeriodDto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Данные страницы категории: потрачено за месяц против личной нормы, последние 12 месяцев
 * и операции категории за месяц.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Данные страницы категории")
public class CategoryPageResponseDto {

    @Schema(description = "Категория страницы")
    private CategoryPageCategoryDto category;

    @Schema(description = "Период, за который запрошена страница")
    private PeriodDto period;

    @Schema(description = "День месяца, до которого считается норма; 0 для будущего месяца", example = "11")
    private int dayOfMonth;

    @Schema(description = "Число дней в запрошенном месяце", example = "30")
    private int daysInMonth;

    @Schema(description = "Потрачено по категории за запрошенный месяц", example = "9150.00")
    private BigDecimal spent;

    @Schema(description = "Сравнение расходов категории с личной нормой («обычно к этому дню»), " +
            "рассчитанное тем же кодом, что и для карточки категории на странице бюджета")
    private NormComparisonDto norm;

    @Schema(description = "Число месяцев окна с расходами пользователя, по которым посчитана норма; 0 при NO_HISTORY", example = "11")
    private int normMonthsCounted;

    @Schema(description = "Доля категории во всех не-трансферных расходах за окно графика, в процентах, 1 знак; " +
            "null, если всех расходов за окно нет", example = "27.0")
    private BigDecimal sharePercent;

    @Schema(description = "Число операций категории за запрошенный месяц", example = "9")
    private int operationsCount;

    @Schema(description = "Средний чек за месяц; null при отсутствии операций", example = "1016.67")
    private BigDecimal averageCheck;

    @Schema(description = "Крупнейшая операция за месяц; null при отсутствии операций", example = "2680.00")
    private BigDecimal largestAmount;

    @Schema(description = "12 месяцев окна графика, заканчивающегося запрошенным месяцем, по порядку")
    private List<CategoryMonthAmountDto> months;

    @Schema(description = "Операции категории за месяц, отсортированные по дате и времени создания по убыванию")
    private List<OperationDto> operations;
}
