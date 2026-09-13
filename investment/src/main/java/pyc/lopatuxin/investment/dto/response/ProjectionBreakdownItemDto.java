package pyc.lopatuxin.investment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pyc.lopatuxin.investment.entity.enums.SecurityType;

import java.math.BigDecimal;

/**
 * One row of the forecast page's "Из чего сложилась доходность" table (plan point 13): a single
 * security's own price growth and payout yield, independent of its portfolio weight.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectionBreakdownItemDto {

    private String ticker;
    private String securityName;
    private SecurityType securityType;
    private BigDecimal weightPercent;
    private BigDecimal priceGrowthPercent;
    private BigDecimal payoutYieldPercent;
    private ProjectionPayoutKind payoutKind;
    private int yearsCounted;
    private Integer lastPayoutYear;
    private boolean historyPending;
}
