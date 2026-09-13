package pyc.lopatuxin.investment.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pyc.lopatuxin.investment.entity.enums.DividendSource;
import pyc.lopatuxin.investment.entity.enums.PayoutKind;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of the security page's timeline (plan point 4): a trade or a dividend, flattened into
 * one shape discriminated by {@link #kind} — only the fields relevant to that kind are set, the
 * rest stay {@code null} and are dropped from the response by {@code NON_NULL}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecurityEventDto {

    private SecurityEventKind kind;
    private LocalDate date;

    // BUY / SELL only
    private UUID transactionId;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal amount;
    private SecurityEventPositionDto positionAfter;
    private Boolean first;
    private BigDecimal realizedPnl;

    // DIVIDEND_PAID / DIVIDEND_UPCOMING only
    private UUID dividendId;
    private BigDecimal amountPerShare;
    private BigDecimal netAmount;
    private String currency;
    private LocalDate paymentDate;
    private DividendSource source;
    // DIVIDEND for a stock/ETF payout, COUPON for a bond/OFZ one (plan point 8) — named
    // differently from the discriminator field `kind` above (BUY/SELL/DIVIDEND_PAID/...),
    // which already owns that name.
    private PayoutKind payoutKind;

    // Sort-only tie-break for events sharing the same `date` (plan point 4: "по времени
    // создания"); a Transaction carries a real creation instant, a Dividend does not, so this is
    // null for dividend events. Never serialized — it is an internal ordering aid, not part of
    // the fixed response contract.
    @JsonIgnore
    private Instant createdAt;
}
