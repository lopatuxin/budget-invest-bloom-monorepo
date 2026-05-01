package pyc.lopatuxin.investment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotResponseDto {

    private String ticker;
    private BigDecimal lastPrice;
    private BigDecimal previousClose;
    private Instant fetchedAt;
    private boolean stale;
}
