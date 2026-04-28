package pyc.lopatuxin.investment.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ProjectionResultDto {
    private BigDecimal startValue;
    private BigDecimal portfolioWeightedAnnualReturn;
    private BigDecimal monthlyReturn;
    private List<ProjectionPointDto> series;
    private List<String> pendingHistoryTickers;
}
