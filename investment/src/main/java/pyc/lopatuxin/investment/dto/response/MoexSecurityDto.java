package pyc.lopatuxin.investment.dto.response;

import pyc.lopatuxin.investment.entity.enums.SecurityType;

import java.time.LocalDate;

public record MoexSecurityDto(
        String ticker,
        String boardId,
        String name,
        SecurityType securityType,
        String sector,
        String currency,
        LocalDate maturityDate
) {
}
