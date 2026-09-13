package pyc.lopatuxin.investment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response of {@code POST /api/investment/securities/page} — the "ownership history" page for a
 * single ticker (security-page-redesign plan, API item 1): price with the user's own trade
 * markers, one chronological timeline of trades and dividends, and the "Итог" summary.
 * {@code price} and {@code position} are absent ({@code null}) without an exchange snapshot and
 * for a fully closed position, respectively (plan points 2-3).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecurityPageResponseDto {

    private SecurityPageSecurityDto security;
    private SecurityPagePriceDto price;
    private PositionResponseDto position;
    private SecurityPageDividendsDto dividends;
    private SecurityPageResultDto result;
    private int transactionsCount;
    private int buysCount;
    private int sellsCount;
    private List<SecurityMarkerDto> markers;
    private List<SecurityEventDto> events;
}
