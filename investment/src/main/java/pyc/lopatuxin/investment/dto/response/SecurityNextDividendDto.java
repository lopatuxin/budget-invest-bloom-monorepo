package pyc.lopatuxin.investment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pyc.lopatuxin.investment.entity.enums.PayoutKind;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The security page's "ближайшая выплата" card (plan point 5/12): the closest upcoming dividend
 * for this ticker, or {@code null} in {@link SecurityPageDividendsDto#getNext()} when there is none.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecurityNextDividendDto {

    private LocalDate recordDate;
    private LocalDate paymentDate;
    private BigDecimal amountPerShare;
    private BigDecimal quantity;
    private BigDecimal netAmount;
    private String currency;
    private PayoutKind kind;
}
