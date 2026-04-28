package pyc.lopatuxin.investment.client.moex.dto;

import java.math.BigDecimal;

public record MoexSnapshotDto(
        String ticker,
        BigDecimal lastPrice,
        BigDecimal previousClose
) {
}
