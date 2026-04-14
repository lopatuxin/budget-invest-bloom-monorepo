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
 * Aggregated overview summary for the index page.
 * Contains financial metrics, trends and top categories without personal inflation.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Aggregated overview summary for the specified period")
public class OverviewSummaryResponseDto {

    @Schema(description = "Period for which the overview is generated")
    private PeriodDto period;

    @Schema(description = "Total user income for the month", example = "150000.00")
    private BigDecimal income;

    @Schema(description = "Total user expenses for the month", example = "89500.00")
    private BigDecimal expenses;

    @Schema(description = "Balance (income minus expenses) for the month", example = "60500.00")
    private BigDecimal balance;

    @Schema(description = "User capital amount (from CapitalRecord or latest known)", example = "1200000.00")
    private BigDecimal capital;

    @Schema(description = "Trends of indicators relative to previous month")
    private TrendsDto trends;

    @Schema(description = "Top categories by expense amount with usage percentage")
    private List<CategorySummaryDto> categories;
}
