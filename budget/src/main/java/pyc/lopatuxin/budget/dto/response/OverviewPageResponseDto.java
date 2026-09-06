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
import java.util.List;

/**
 * Данные страницы обзора («Капитал»): капитал с траекторией за 12 месяцев, четыре плитки,
 * доходы по месяцам и итоги за 12 месяцев против предыдущих 12.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Данные страницы обзора")
public class OverviewPageResponseDto {

    @Schema(description = "Дата, на которую построена страница (сегодня)", example = "2026-09-18")
    private LocalDate asOf;

    @Schema(description = "Текущий календарный месяц")
    private CurrentMonthDto currentMonth;

    @Schema(description = "Капитал с траекторией за 12 месяцев")
    private CapitalSectionDto capital;

    @Schema(description = "Доходы минус расходы за текущий месяц, со знаком", example = "73150.00")
    private BigDecimal currentMonthBalance;

    @Schema(description = "Инвестиционный портфель")
    private PortfolioSectionDto portfolio;

    @Schema(description = "Норма сбережений")
    private SavingsSectionDto savings;

    @Schema(description = "12 месяцев окна W по порядку: доходы, расходы, сбережено")
    private List<MonthTotalsDto> months;

    @Schema(description = "Итоги за 12 месяцев против предыдущих 12")
    private Totals12mDto totals12m;

    @Schema(description = "Личная инфляция: средний месяц этого года против прошлого, в процентах; " +
            "null при отсутствии данных за прошлый год", example = "5.3")
    private BigDecimal personalInflationPercent;
}
