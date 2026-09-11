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
 * Один раздел страницы аналитики (расходы, доходы или сбережения) за год Y против года P:
 * сумма за год, средний учтённый месяц с изменением год к году, самый дорогой и самый дешёвый
 * месяц, и все 12 месяцев для столбчатого графика.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Раздел страницы аналитики за год Y против года P")
public class AnalyticsSectionDto {

    @Schema(description = "Сумма за год Y (все месяцы, включая незавершённый)", example = "863050.00")
    private BigDecimal total;

    @Schema(description = "Сумма за год P", example = "1119600.00")
    private BigDecimal previousTotal;

    @Schema(description = "Число учтённых (завершённых, с данными раздела) месяцев года Y", example = "8")
    private Integer monthsCounted;

    @Schema(description = "Число учтённых месяцев года P", example = "12")
    private Integer previousMonthsCounted;

    @Schema(description = "Средний учтённый месяц года Y; null, если учтённых месяцев нет", example = "98300.00")
    private BigDecimal average;

    @Schema(description = "Средний учтённый месяц года P; null, если учтённых месяцев нет", example = "93300.00")
    private BigDecimal previousAverage;

    @Schema(description = "Изменение среднего месяца Y против P")
    private ChangeDto change;

    @Schema(description = "Месяц с наибольшей суммой среди учтённых месяцев года Y")
    private AnalyticsMonthExtremeDto maxMonth;

    @Schema(description = "Месяц с наименьшей суммой среди учтённых месяцев года Y")
    private AnalyticsMonthExtremeDto minMonth;

    @Schema(description = "Все 12 месяцев года Y с суммами обоих лет, для столбчатого графика")
    private List<AnalyticsMonthPointDto> months;
}
