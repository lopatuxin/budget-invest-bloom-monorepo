package pyc.lopatuxin.investment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaidDividendDto {

    private String ticker;
    private LocalDate recordDate;
    private LocalDate paymentDate;
    private BigDecimal amountPerShare;
    private String currency;
}
