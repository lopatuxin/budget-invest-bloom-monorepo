package pyc.lopatuxin.investment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pyc.lopatuxin.investment.entity.enums.SecurityType;

import java.math.BigDecimal;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TypeAllocationDto {

    private SecurityType securityType;
    private BigDecimal value;
    private BigDecimal percent;
    private int assetsCount;
}
