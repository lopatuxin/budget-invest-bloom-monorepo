package pyc.lopatuxin.investment.client.moex.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MoexCandleDto(
        String ticker,
        LocalDate tradeDate,
        BigDecimal open,
        BigDecimal close,
        BigDecimal high,
        BigDecimal low,
        Long volume
) {
}
