package pyc.lopatuxin.budget.entity.enums;

import java.math.BigDecimal;

/**
 * Status of a comparison between an actual amount and the user's personal norm
 * ("usual by this day"), based on the deviation percentage.
 */
public enum NormStatus {

    ABOVE_MUCH,
    ABOVE,
    NORMAL,
    BELOW,
    NO_HISTORY;

    private static final BigDecimal ABOVE_MUCH_THRESHOLD = new BigDecimal("50");
    private static final BigDecimal ABOVE_THRESHOLD = new BigDecimal("10");
    private static final BigDecimal BELOW_THRESHOLD = new BigDecimal("-10");

    /**
     * Resolves the status for a percent deviation from a baseline: above +50% is ABOVE_MUCH,
     * above +10% is ABOVE, below -10% is BELOW, otherwise NORMAL. Shared by norm comparisons
     * (deviation from a personal norm) and the overview page (year-over-year and 12-month changes).
     */
    public static NormStatus fromDeviationPercent(BigDecimal deviationPercent) {
        if (deviationPercent.compareTo(ABOVE_MUCH_THRESHOLD) > 0) {
            return ABOVE_MUCH;
        }
        if (deviationPercent.compareTo(ABOVE_THRESHOLD) > 0) {
            return ABOVE;
        }
        if (deviationPercent.compareTo(BELOW_THRESHOLD) < 0) {
            return BELOW;
        }
        return NORMAL;
    }
}
