package pyc.lopatuxin.investment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Position snapshot right after a BUY or SELL event: quantity, cost basis and average price
 * once that trade is applied (plan point 4 of the security page redesign).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityEventPositionDto {

    private BigDecimal quantity;
    private BigDecimal invested;
    private BigDecimal averagePrice;
}
