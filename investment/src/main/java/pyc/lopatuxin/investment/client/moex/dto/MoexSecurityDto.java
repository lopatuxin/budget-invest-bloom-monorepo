package pyc.lopatuxin.investment.client.moex.dto;

import pyc.lopatuxin.investment.entity.enums.SecurityType;

public record MoexSecurityDto(
        String ticker,
        String boardId,
        String name,
        SecurityType securityType,
        String sector,
        String currency
) {
}
