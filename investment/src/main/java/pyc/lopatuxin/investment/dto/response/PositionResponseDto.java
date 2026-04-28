package pyc.lopatuxin.investment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pyc.lopatuxin.investment.entity.enums.SecurityType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PositionResponseDto {

    private UUID id;
    private String ticker;
    private String securityName;
    private SecurityType securityType;
    private String sector;
    private BigDecimal quantity;
    private BigDecimal averagePrice;
    private BigDecimal totalCost;
    private BigDecimal currentPrice;
    private BigDecimal pnl;
    private Instant updatedAt;
}
