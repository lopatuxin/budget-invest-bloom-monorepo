package pyc.lopatuxin.budget.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Страница аналитики: год Y против предыдущего года P по трём разделам (расходы, доходы,
 * сбережения) и разбивка вклада категорий в личную инфляцию. Один запрос считает всё на бэкенде.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Страница аналитики («Год к году»)")
public class AnalyticsPageResponseDto {

    @Schema(description = "Запрошенный год Y", example = "2026")
    private Integer year;

    @Schema(description = "Год сравнения P = Y - 1", example = "2025")
    private Integer previousYear;

    @Schema(description = "Год самой ранней не-трансферной записи; null, если записей нет", example = "2024")
    private Integer earliestYear;

    @Schema(description = "Текущий календарный месяц")
    private CurrentMonthDto currentMonth;

    @Schema(description = "В году P есть хотя бы один учтённый месяц по расходам или доходам", example = "true")
    private Boolean previousYearHasData;

    @Schema(description = "Раздел «Расходы»")
    private AnalyticsSectionDto expenses;

    @Schema(description = "Раздел «Доходы»")
    private AnalyticsSectionDto income;

    @Schema(description = "Раздел «Сбережения» (доходы минус расходы по месяцам, может быть отрицательным)")
    private AnalyticsSectionDto savings;

    @Schema(description = "Норма сбережений за год Y в процентах, зажата в [-99, 99]", example = "35")
    private Integer savingsRatePercent;

    @Schema(description = "Норма сбережений за год P в процентах", example = "34")
    private Integer previousSavingsRatePercent;

    @Schema(description = "Личная инфляция: изменение среднего месяца расходов Y против P, равно expenses.change.percent",
            example = "5.3")
    private BigDecimal personalInflationPercent;

    @Schema(description = "Категории расходов с вкладом в личную инфляцию, отсортированные по убыванию |вклада|")
    private List<AnalyticsCategoryDto> categories;
}
