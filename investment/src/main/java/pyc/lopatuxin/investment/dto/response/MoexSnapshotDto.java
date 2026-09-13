package pyc.lopatuxin.investment.dto.response;

import java.math.BigDecimal;

public record MoexSnapshotDto(
        String ticker,
        BigDecimal lastPrice,
        BigDecimal previousClose,
        // FACEVALUE / ACCRUEDINT from the exchange's bond-board "securities" table — null for a
        // stock/ETF or when MOEX has not returned them (see MoexResponseParser, BondPricing).
        BigDecimal faceValue,
        BigDecimal accruedInterest
) {
    public MoexSnapshotDto(String ticker, BigDecimal lastPrice, BigDecimal previousClose) {
        this(ticker, lastPrice, previousClose, null, null);
    }
}
