package pyc.lopatuxin.investment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * "Итог" block of the security page (plan point 7): result by price, realized result from sells,
 * dividends and their sum, against everything ever invested. {@code totalPercent} is absent
 * ({@code null}) when nothing has ever been invested.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecurityPageResultDto {

    private BigDecimal pricePnl;
    private BigDecimal realizedPnl;
    private BigDecimal dividendsAll;
    private BigDecimal total;
    private BigDecimal investedAll;
    private BigDecimal totalPercent;
}
