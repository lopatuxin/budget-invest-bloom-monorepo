package pyc.lopatuxin.investment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpcomingDividendDto {

    private String ticker;
    private String securityName;
    private LocalDate recordDate;
    private LocalDate paymentDate;
    private BigDecimal amountPerShare;
    private BigDecimal quantity;
    private BigDecimal totalAmount;
    private String currency;
}
