package pyc.lopatuxin.shared.port;

/**
 * DIVIDEND for a stock/ETF payout, COUPON for a bond/OFZ one — mirrors the investment module's
 * own {@code entity.enums.PayoutKind}, duplicated here because {@code shared} must not depend on
 * {@code investment} (module boundary rule), used by {@link PortfolioNextDividend}.
 */
public enum PayoutKind {
    DIVIDEND,
    COUPON
}
