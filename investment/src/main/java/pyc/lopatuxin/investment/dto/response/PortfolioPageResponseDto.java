package pyc.lopatuxin.investment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioPageResponseDto {

    private PortfolioOverviewDto overview;
    private PortfolioAllocationDto allocation;
    private List<PositionGroupDto> groups;
    // Flat list of the same positions as in groups, kept for the security page and older tests.
    private List<PositionResponseDto> positions;
    private List<UpcomingDividendDto> upcomingDividends;
    private List<UpcomingDividendDto> recentDividends;
    private List<TransactionResponseDto> recentTransactions;
    private long transactionsTotal;
}
