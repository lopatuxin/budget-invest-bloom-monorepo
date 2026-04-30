package pyc.lopatuxin.investment.client.moex;

import pyc.lopatuxin.investment.entity.enums.SecurityType;

import java.util.Optional;

public final class MoexSecurityClassifier {

    private MoexSecurityClassifier() {}

    // Maps ISS `group` field to SecurityType. Returns empty if unknown/unsupported.
    public static Optional<SecurityType> fromGroup(String group) {
        if (group == null) return Optional.empty();
        return switch (group) {
            case "stock_shares", "stock_pref_shares" -> Optional.of(SecurityType.STOCK);
            case "stock_etf", "stock_ppif" -> Optional.of(SecurityType.ETF);
            case "stock_bonds_ofz", "stock_bonds_state" -> Optional.of(SecurityType.OFZ);
            case "stock_bonds" -> Optional.of(SecurityType.BOND);
            default -> Optional.empty();
        };
    }
}
