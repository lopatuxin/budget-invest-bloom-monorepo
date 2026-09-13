package pyc.lopatuxin.investment.dto.response;

/**
 * A breakdown row's payout kind (plan point 13): DIVIDEND/COUPON mirror
 * {@link pyc.lopatuxin.investment.entity.enums.PayoutKind}, plus NONE for a security with no
 * payout history at all — a value {@code PayoutKind} itself has no room for.
 */
public enum ProjectionPayoutKind {
    DIVIDEND,
    COUPON,
    NONE
}
