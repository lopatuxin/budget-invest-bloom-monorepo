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
 * Детальная финансовая метрика пользователя за указанный год.
 * Содержит помесячную разбивку и агрегированные показатели.
 */
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Детальная метрика пользователя за указанный год")
public class MetricResponseDto {

    @Schema(description = "Запрошенный год", example = "2026")
    private Integer year;

    @Schema(description = "Значение за последний месяц с данными", example = "150000.00")
    private BigDecimal currentValue;

    @Schema(description = "Значение за предпоследний месяц с данными", example = "148000.00")
    private BigDecimal previousValue;

    @Schema(description = "Процент изменения текущего значения относительно предыдущего", example = "+1.4%")
    private String changePercent;

    @Schema(description = "Среднемесячное значение за год (по месяцам с данными)", example = "149250.00")
    private BigDecimal yearlyAverage;

    @Schema(description = "Максимальное месячное значение за год", example = "153000.00")
    private BigDecimal yearlyMax;

    @Schema(description = "Помесячная разбивка (12 записей, для месяцев без данных amount = 0)")
    private List<MonthlyMetricDto> monthlyData;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Разбивка инфляции по категориям (только для метрики инфляции)")
    private List<CategoryInflationDto> categoryBreakdown;
}
