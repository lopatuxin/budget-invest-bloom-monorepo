package pyc.lopatuxin.investment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioOverviewDto {

    private BigDecimal totalValue;
    private BigDecimal totalCost;
    private BigDecimal totalPnl;
    private BigDecimal dailyPnl;
    private int assetsCount;
}
