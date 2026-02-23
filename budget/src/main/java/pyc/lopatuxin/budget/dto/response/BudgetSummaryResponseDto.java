package pyc.lopatuxin.budget.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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

    @Schema(description = "Размер капитала пользователя (из записи CapitalRecord или последней известной)", example = "1200000.00")
    private BigDecimal capital;

    @Schema(description = "Личная инфляция в процентах: отношение средних расходов текущего года к предыдущему", example = "5.3")
    private BigDecimal personalInflation;

    @Schema(description = "Тренды показателей относительно предыдущего месяца")
    private TrendsDto trends;

    @Schema(description = "Список категорий расходов с суммами и процентом использования лимита")
    private List<CategorySummaryDto> categories;
}