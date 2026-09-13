package pyc.lopatuxin.investment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Dividend summary for this ticker (plan point 5). {@code yield12mPercent} and {@code next} are
 * absent ({@code null}) for a closed position / zero investment and when there is no upcoming
 * dividend, respectively.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecurityPageDividendsDto {

    private BigDecimal total12m;
    private BigDecimal totalAll;
    private BigDecimal yield12mPercent;
    private SecurityNextDividendDto next;
}
