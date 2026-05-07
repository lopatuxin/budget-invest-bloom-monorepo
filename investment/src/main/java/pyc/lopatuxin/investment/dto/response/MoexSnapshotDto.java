package pyc.lopatuxin.investment.dto.response;

import java.math.BigDecimal;

public record MoexSnapshotDto(
        String ticker,
        BigDecimal lastPrice,
        BigDecimal previousClose
) {
}
