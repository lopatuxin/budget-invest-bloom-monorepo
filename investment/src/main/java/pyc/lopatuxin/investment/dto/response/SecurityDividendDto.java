package pyc.lopatuxin.investment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pyc.lopatuxin.investment.entity.enums.DividendSource;
import pyc.lopatuxin.investment.entity.enums.DividendStatus;
import pyc.lopatuxin.investment.entity.enums.PayoutKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SecurityDividendDto {

    private UUID id;
    private LocalDate recordDate;
    private LocalDate paymentDate;
    private BigDecimal amountPerShare;
    private String currency;
    private DividendStatus status;
    private DividendSource source;
    private PayoutKind kind;
}
