package pyc.lopatuxin.budget.entity.enums;

/**
 * Status of a comparison between an actual amount and the user's personal norm
 * ("usual by this day"), based on the deviation percentage.
 */
public enum NormStatus {

    ABOVE_MUCH,
    ABOVE,
    NORMAL,
    BELOW,
    NO_HISTORY
}
