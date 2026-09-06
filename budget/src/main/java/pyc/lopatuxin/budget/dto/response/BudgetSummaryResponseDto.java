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
import pyc.lopatuxin.budget.dto.common.TrendsDto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Агрегированная сводка бюджета пользователя за указанный месяц и год.
 * Содержит финансовые метрики, тренды и список категорий с расходами.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Агрегированная сводка бюджета пользователя за указанный период")
public class BudgetSummaryResponseDto {

    @Schema(description = "Период, за который сформирована сводка")
    private PeriodDto period;

    @Schema(description = "Суммарные доходы пользователя за месяц", example = "150000.00")
    private BigDecimal income;

    @Schema(description = "Суммарные расходы пользователя за месяц", example = "89500.00")
    private BigDecimal expenses;

    @Schema(description = "Баланс (доходы минус расходы) за месяц", example = "60500.00")
    private BigDecimal balance;

    @Schema(description = "Личная инфляция в процентах: отношение средних расходов текущего года к предыдущему", example = "5.3")
    private BigDecimal personalInflation;

    @Schema(description = "Тренды показателей относительно предыдущего месяца")
    private TrendsDto trends;

    @Schema(description = "Текущий день месяца: сегодняшнее число для текущего месяца, длина месяца для прошлого, 0 для будущего", example = "18")
    private Integer dayOfMonth;

    @Schema(description = "Число дней в запрошенном месяце", example = "30")
    private Integer daysInMonth;

    @Schema(description = "Сравнение расходов месяца с личной нормой («обычно к этому дню»)")
    private NormComparisonDto expenseNorm;

    @Schema(description = "Сравнение доходов месяца с личной нормой («обычно к этому дню»)")
    private NormComparisonDto incomeNorm;

    @Schema(description = "Список категорий расходов, отсортированный по отклонению от нормы")
    private List<CategorySummaryDto> categories;
}